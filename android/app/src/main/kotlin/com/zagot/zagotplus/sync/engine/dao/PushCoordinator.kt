package com.zagot.zagotplus.sync.engine.dao

import android.util.Log
import com.zagot.zagotplus.sync.engine.api.Record
import com.zagot.zagotplus.sync.engine.api.SyncRemoteClient
import com.zagot.zagotplus.sync.engine.db.SyncOutboxDao
import com.zagot.zagotplus.sync.engine.db.SyncOutboxEntity
import com.zagot.zagotplus.sync.engine.state.SyncEvent
import com.zagot.zagotplus.sync.engine.state.SyncStateMachine
import com.zagot.zagotplus.sync.engine.util.JsonUtil
import com.zagot.zagotplus.sync.engine.util.SyncTableConfig
import io.github.jan.supabase.exceptions.BadRequestRestException
import io.github.jan.supabase.exceptions.NotFoundRestException
import io.github.jan.supabase.exceptions.RestException
import io.github.jan.supabase.exceptions.UnauthorizedRestException
import io.github.jan.supabase.exceptions.UnknownRestException
import kotlinx.coroutines.delay
import java.io.IOException
import java.net.SocketTimeoutException
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Error classification for push failures (spec Section 12).
 */
enum class PushErrorCategory {
    TRANSIENT,    // Network error, timeout, 500+ → retry with backoff
    AUTH,         // 401 → refresh token, retry once
    RATE_LIMIT,   // 429 → respect Retry-After
    CONFLICT,     // 409 → pull latest, resolve, re-push
    TERMINAL,     // 400, 404 → log, skip
}

/**
 * Push coordinator — drains outbox entries to Supabase (spec Section 10).
 *
 * During LIVE: pushes immediately (or with small debounce).
 * During CATCHING_UP: pushes remaining outbox entries after conflict resolution.
 *
 * Error handling follows the classification table in spec Section 12.
 */
@Singleton
class PushCoordinator @Inject constructor(
    private val outboxDao: SyncOutboxDao,
    private val stateMachine: SyncStateMachine,
) {

    companion object {
        private const val TAG = "PushCoordinator"
        private const val MAX_RETRIES = 5
        private const val INITIAL_BACKOFF_MS = 1_000L
        private const val MAX_BACKOFF_MS = 5 * 60 * 1_000L // 5 minutes
    }

    /** Override for testing — set to 0 to disable delays. */
    var backoffMultiplier: Long = 1L

    /**
     * Push all pending outbox entries for a table.
     * Groups entries by table and pushes in FIFO order.
     *
     * @return number of successfully pushed entries
     */
    suspend fun pushPending(
        remoteClient: SyncRemoteClient,
        tableConfigs: List<SyncTableConfig>,
    ): Int {
        val pending = outboxDao.getPending()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending outbox entries")
            return 0
        }

        Log.d(TAG, "Pushing ${pending.size} pending entries")
        var successCount = 0

        // Group by table to batch pushes
        val byTable = pending.groupBy { it.tableName }

        for ((tableName, entries) in byTable) {
            val config = tableConfigs.find { it.tableName == tableName }
            if (config == null) {
                Log.w(TAG, "No config for table $tableName, skipping ${entries.size} entries")
                continue
            }

            try {
                val records = entries.map { entry ->
                    parsePayload(entry.payload)
                }

                pushWithRetry(remoteClient, config, records)

                // Mark all as synced
                val ids = entries.map { it.id }
                outboxDao.markSyncedBatch(ids)
                successCount += entries.size

                entries.forEach { entry ->
                    stateMachine.onEvent(SyncEvent.PushSuccess(tableName, entry.recordId))
                }

                Log.d(TAG, "Pushed ${entries.size} entries for $tableName")
            } catch (e: Exception) {
                val category = classifyError(e)
                Log.e(TAG, "Push failed for $tableName (${category}): ${e.message}")
                stateMachine.onEvent(SyncEvent.PushFailed(tableName, e.message ?: "unknown"))

                when (category) {
                    PushErrorCategory.TERMINAL -> {
                        // Skip terminal errors — mark as synced to avoid infinite retry
                        Log.e(TAG, "Terminal error, skipping entries for $tableName")
                        val ids = entries.map { it.id }
                        outboxDao.markSyncedBatch(ids)
                    }
                    PushErrorCategory.AUTH -> {
                        // Auth failure — stop pushing, caller should handle re-auth
                        throw e
                    }
                    else -> {
                        // Transient / rate limit / conflict — entries stay in outbox for retry
                    }
                }
            }
        }

        return successCount
    }

    /**
     * Push a single outbox entry immediately (used in LIVE state).
     */
    suspend fun pushSingle(
        remoteClient: SyncRemoteClient,
        config: SyncTableConfig,
        entry: SyncOutboxEntity,
    ) {
        val record = parsePayload(entry.payload)
        pushWithRetry(remoteClient, config, listOf(record))
        outboxDao.markSynced(entry.id)
        stateMachine.onEvent(SyncEvent.PushSuccess(config.tableName, entry.recordId))
    }

    /**
     * Push records with exponential backoff retry (spec Section 12).
     */
    private suspend fun pushWithRetry(
        remoteClient: SyncRemoteClient,
        config: SyncTableConfig,
        records: List<Record>,
    ) {
        var attempt = 0
        var backoff = INITIAL_BACKOFF_MS

        while (true) {
            try {
                remoteClient.push(config.tableName, config.primaryKey, records)
                return
            } catch (e: Exception) {
                val category = classifyError(e)
                attempt++

                if (category == PushErrorCategory.TERMINAL || category == PushErrorCategory.AUTH) {
                    throw e // don't retry these
                }

                if (attempt >= MAX_RETRIES) {
                    throw e // exhausted retries
                }

                Log.w(TAG, "Push attempt $attempt failed ($category), retrying in ${backoff}ms")
                delay(backoff * backoffMultiplier)
                backoff = minOf(backoff * 2, MAX_BACKOFF_MS)
            }
        }
    }

    /**
     * Classify a push error per spec Section 12.
     *
     * Checks exception types first (RestException with status code, IO errors),
     * then falls back to message parsing for untyped exceptions.
     */
    fun classifyError(e: Exception): PushErrorCategory {
        // Check specific RestException subclasses (supabase-kt 2.0.3 sealed hierarchy)
        when (e) {
            is UnauthorizedRestException -> return PushErrorCategory.AUTH
            is BadRequestRestException -> return PushErrorCategory.TERMINAL
            is NotFoundRestException -> return PushErrorCategory.TERMINAL
            is UnknownRestException -> {
                // UnknownRestException covers 409, 429, 5xx — check message for status
                val msg = e.message?.lowercase() ?: ""
                return when {
                    msg.contains("409") || msg.contains("conflict") -> PushErrorCategory.CONFLICT
                    msg.contains("429") || msg.contains("too many") -> PushErrorCategory.RATE_LIMIT
                    else -> PushErrorCategory.TRANSIENT
                }
            }
            is RestException -> return PushErrorCategory.TRANSIENT
        }
        if (e is SocketTimeoutException || e is IOException) {
            return PushErrorCategory.TRANSIENT
        }

        // Fallback: parse message for untyped exceptions
        val message = e.message?.lowercase() ?: ""
        return when {
            message.contains("401") || message.contains("unauthorized") -> PushErrorCategory.AUTH
            message.contains("409") || message.contains("conflict") -> PushErrorCategory.CONFLICT
            message.contains("429") || message.contains("too many") || message.contains("rate") -> PushErrorCategory.RATE_LIMIT
            message.contains("400") || message.contains("bad request") -> PushErrorCategory.TERMINAL
            message.contains("404") || message.contains("not found") -> PushErrorCategory.TERMINAL
            else -> PushErrorCategory.TRANSIENT
        }
    }

    private fun parsePayload(json: String): Record = JsonUtil.parsePayload(json)
}
