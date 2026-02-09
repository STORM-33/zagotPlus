package com.zagot.syncengine.dao

import android.util.Log
import com.zagot.syncengine.api.Record
import com.zagot.syncengine.api.SyncRemoteClient
import com.zagot.syncengine.db.SyncMetadataDao
import com.zagot.syncengine.db.SyncMetadataEntity
import com.zagot.syncengine.state.SyncEvent
import com.zagot.syncengine.state.SyncStateMachine
import com.zagot.syncengine.util.SyncTableConfig
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pull coordinator — fetches remote changes with overlap window,
 * applies them to the local store, and updates sync metadata (spec Section 5).
 *
 * The pull uses `last_synced_at - OVERLAP_WINDOW` to guard against clock skew
 * and same-timestamp races. Deduplication via UPSERT by PK handles any
 * re-fetched records cheaply.
 *
 * Uses compound cursor `(timestamp, pk)` pagination to handle tables with
 * more records than a single page (default 1000). When the timestamp cursor
 * doesn't advance (same-timestamp records), the PK cursor provides strict
 * forward progress, eliminating the need for in-memory dedup sets.
 */
@Singleton
class PullCoordinator @Inject constructor(
    private val syncMetadataDao: SyncMetadataDao,
    private val stateMachine: SyncStateMachine,
) {

    companion object {
        private const val TAG = "PullCoordinator"
        /** 5 seconds overlap window per spec Section 5. */
        const val OVERLAP_WINDOW_MS = 5_000L
        /** Default page size — overridden by [pullPageSize]. */
        private const val DEFAULT_PAGE_SIZE = 1000
    }

    /** Page size for pull requests. Set by SyncEngineImpl from SyncEngineConfig. */
    var pullPageSize: Int = DEFAULT_PAGE_SIZE

    /** Overlap window (ms) subtracted from last_synced_at for incremental pulls. */
    var overlapWindowMs: Long = OVERLAP_WINDOW_MS

    /**
     * Execute a paginated incremental pull for a single table, emitting each
     * page as a separate element in a cold [Flow].
     *
     * Callers should collect this flow inside a transaction boundary to apply
     * batches without accumulating all records in memory.
     *
     * @param remoteClient the remote data source
     * @param config table configuration
     * @return a cold Flow emitting one [List<Record>] per fetched page
     */
    fun pull(
        remoteClient: SyncRemoteClient,
        config: SyncTableConfig,
    ): Flow<List<Record>> = flow {
        pullStreaming(remoteClient, config) { batch, _ ->
            if (batch.isNotEmpty()) {
                emit(batch)
            }
        }
    }

    /**
     * Streaming pull: fetches page-by-page and invokes [onBatch] for each page.
     *
     * This is the core primitive used by SyncEngineImpl to apply pulled records
     * in short per-batch transactions to avoid OOM/WAL blowups on large tables.
     *
     * Uses compound cursor `(timestamp, pk)` for deterministic pagination —
     * each page is strictly after the previous one, so no dedup set is needed.
     *
     * @param remoteClient the remote data source
     * @param config table configuration
     * @param onBatch callback invoked per page with records and max timestamp
     * @return summary of the streaming pull
     */
    suspend fun pullStreaming(
        remoteClient: SyncRemoteClient,
        config: SyncTableConfig,
        onBatch: suspend (batch: List<Record>, batchMaxTimestamp: Long) -> Unit,
    ): PullStreamingResult {
        var since: Long
        if (config.fullPull) {
            since = 0L
            Log.d(TAG, "Full-pulling ${config.tableName} (no incremental filter)")
        } else {
            val lastSyncedAt = syncMetadataDao.getLastSyncedAt(config.tableName) ?: 0L
            since = maxOf(0L, lastSyncedAt - overlapWindowMs)
            Log.d(TAG, "Pulling ${config.tableName}: lastSyncedAt=$lastSyncedAt, overlapMs=$overlapWindowMs, effectiveSince=$since")
        }

        val pageSize = config.pullPageSize ?: pullPageSize
        var page = 0
        var totalRecords = 0
        var lastPk: String? = null

        do {
            val batch = remoteClient.pull(
                table = config.tableName,
                timestampColumn = config.timestampColumn,
                since = since,
                overlapWindowMs = 0L,
                limit = pageSize,
                primaryKey = config.primaryKey,
                afterPk = lastPk,
            )

            page++

            val batchMaxTs = maxTimestamp(batch, config.timestampColumn)

            if (batch.isNotEmpty()) {
                totalRecords += batch.size
                onBatch(batch, batchMaxTs)
            }

            if (batch.size == pageSize) {
                val batchLastPk = batch.lastOrNull()?.get(config.primaryKey)?.toString()
                if (batchLastPk != null) {
                    if (batchMaxTs > since || batchLastPk != lastPk) {
                        since = batchMaxTs
                        lastPk = batchLastPk
                    } else {
                        Log.w(TAG, "Cursor stuck for ${config.tableName} at $since, breaking pagination")
                        break
                    }
                } else {
                    Log.w(TAG, "Cursor missing PK for ${config.tableName} at $since, breaking pagination")
                    break
                }
            }

            Log.d(TAG, "Pulled page $page: ${batch.size} fetched from ${config.tableName} (total=$totalRecords)")
        } while (batch.size == pageSize)

        stateMachine.onEvent(SyncEvent.PullComplete(config.tableName, totalRecords))
        return PullStreamingResult(totalRecords = totalRecords, pagesProcessed = page)
    }

    /**
     * Compute the max updated_at timestamp from a list of pulled records.
     * Handles ISO-8601 strings from Supabase and numeric epoch millis.
     */
    fun maxTimestamp(records: List<Record>, timestampColumn: String): Long {
        return records.maxOfOrNull { record ->
            when (val ts = record[timestampColumn]) {
                is String -> try {
                    Instant.parse(ts).toEpochMilli()
                } catch (_: Exception) {
                    ts.toLongOrNull() ?: 0L
                }
                is Number -> ts.toLong()
                else -> 0L
            }
        } ?: 0L
    }

    /** Read last_synced_at for a table via the DAO. */
    suspend fun getLastSyncedAt(tableName: String): Long {
        return syncMetadataDao.getLastSyncedAt(tableName) ?: 0L
    }

    /**
     * Force-set last_synced_at for a table, even if it moves backwards.
     * Used for catch-up overflow retry rollback.
     */
    suspend fun setLastSyncedAt(tableName: String, lastSyncedAt: Long) {
        syncMetadataDao.upsert(SyncMetadataEntity(tableName, lastSyncedAt))
    }

    /**
     * Update last_synced_at for a table. Must be called within the same
     * Room transaction as applying the pulled records (crash safety — spec Section 5.1).
     */
    suspend fun updateLastSyncedAt(tableName: String, maxUpdatedAt: Long) {
        if (maxUpdatedAt <= 0L) return
        val current = syncMetadataDao.getLastSyncedAt(tableName) ?: 0L
        if (maxUpdatedAt > current) {
            syncMetadataDao.upsert(SyncMetadataEntity(tableName, maxUpdatedAt))
            Log.d(TAG, "Updated last_synced_at for $tableName: $current → $maxUpdatedAt")
        }
    }
}

/**
 * Summary of a streaming pull operation.
 */
data class PullStreamingResult(
    val totalRecords: Int,
    val pagesProcessed: Int,
)
