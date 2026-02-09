package com.zagot.syncengine.api

import android.util.Log
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.sqlite.db.SimpleSQLiteQuery
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.zagot.syncengine.dao.ConflictReconciler
import com.zagot.syncengine.dao.PullCoordinator
import com.zagot.syncengine.dao.PushCoordinator
import com.zagot.syncengine.db.SyncOutboxDao
import com.zagot.syncengine.network.NetworkMonitor
import com.zagot.syncengine.realtime.RealtimeBuffer
import com.zagot.syncengine.realtime.RealtimeManager
import com.zagot.syncengine.state.SyncEvent
import com.zagot.syncengine.state.SyncLogEntry
import com.zagot.syncengine.state.SyncState
import com.zagot.syncengine.state.SyncStateMachine
import com.zagot.syncengine.util.SyncTableConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.time.Instant
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Public API for the sync engine (spec Section 11).
 */
interface SyncEngine {
    /** Current sync state. */
    val state: StateFlow<SyncState>

    /** Sync log for observability (spec Section 14.6). */
    val syncLog: StateFlow<List<SyncLogEntry>>

    /** Start the engine (call on app startup). */
    fun start()

    /** Stop the engine (call on app shutdown). */
    suspend fun stop()

    /** Force an immediate full sync cycle. */
    suspend fun syncNow()

    /** Register a table for syncing. Must be called before start(). */
    fun registerTable(config: SyncTableConfig)

    /**
     * Notify the engine that a new outbox entry was created.
     * When LIVE, triggers an immediate push attempt.
     */
    fun notifyOutboxChanged()
}

/**
 * Main sync engine implementation (spec Sections 3–13).
 *
 * Orchestrates state machine, pull/push coordinators, conflict reconciler,
 * realtime subscriptions, and network monitoring.
 *
 * Lifecycle: start() on app launch, stop() on app shutdown.
 * Injectable as a Hilt singleton.
 */
@Singleton
class SyncEngineImpl @Inject constructor(
    private val stateMachine: SyncStateMachine,
    private val pullCoordinator: PullCoordinator,
    private val pushCoordinator: PushCoordinator,
    private val conflictReconciler: ConflictReconciler,
    private val realtimeManager: RealtimeManager,
    private val realtimeBuffer: RealtimeBuffer,
    private val networkMonitor: NetworkMonitor,
    private val realtimeChannel: RealtimeChannelContract,
    private val remoteClient: SyncRemoteClient,
    private val outboxDao: SyncOutboxDao,
    private val database: RoomDatabase,
    private val workManager: WorkManager,
    private val config: SyncEngineConfig,
) : SyncEngine {

    companion object {
        private const val TAG = "SyncEngine"
        /**
         * Max records to pass to applyToRoom in a single call.
         * Prevents Room from building an excessively large WAL journal
         * when applying thousands of pulled records within one transaction.
         */
        private const val APPLY_CHUNK_SIZE = 500
        /** Log a warning when total pulled records across all tables exceeds this. */
        private const val LARGE_PULL_THRESHOLD = 5_000
    }

    private val registeredTables = mutableListOf<SyncTableConfig>()
    @Volatile private var started = false
    private var engineScope: CoroutineScope? = null
    private var networkJob: Job? = null
    private var catchUpJob: Job? = null
    private var livePushJob: Job? = null

    /** Guards concurrent access to sync operations (catch-up vs safety sync). */
    private val syncMutex = Mutex()

    private val fkRetryJobs = ConcurrentHashMap<String, Job>()

    override val state: StateFlow<SyncState> get() = stateMachine.state
    override val syncLog: StateFlow<List<SyncLogEntry>> get() = stateMachine.syncLog

    override fun registerTable(config: SyncTableConfig) {
        check(!started) { "Cannot register tables after start() — call registerTable() before start()" }
        require(config.applyToRoom != null) {
            "SyncTableConfig.applyToRoom is required for ${config.tableName}"
        }
        registeredTables.add(config)
        Log.d(TAG, "Registered table: ${config.tableName}")
    }

    override fun notifyOutboxChanged() {
        val scope = engineScope ?: return
        if (stateMachine.state.value != SyncState.LIVE) return

        // Debounce: cancel previous pending push, schedule a new one
        livePushJob?.cancel()
        livePushJob = scope.launch {
            delay(config.livePushDebounceMs)
            try {
                pushCoordinator.pushPending(remoteClient, registeredTables)
            } catch (e: Exception) {
                Log.e(TAG, "LIVE push failed: ${e.message}")
            }
        }
    }

    override fun start() {
        if (engineScope != null) {
            Log.w(TAG, "SyncEngine already started")
            return
        }

        Log.d(TAG, "Starting SyncEngine with ${registeredTables.size} tables, config=$config")
        started = true
        pushCoordinator.pushBatchSize = config.pushBatchSize
        pullCoordinator.pullPageSize = config.pullPageSize
        pullCoordinator.overlapWindowMs = config.pullOverlapWindowMs
        realtimeBuffer.maxBufferSize = config.realtimeBufferMaxEvents
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        engineScope = scope

        // Start network monitoring (registers ConnectivityManager callbacks)
        networkMonitor.start()

        // Enqueue periodic safety sync (spec Section 8)
        val safetySyncRequest = PeriodicWorkRequestBuilder<SafetySyncWorker>(
            repeatInterval = config.safetySyncIntervalMinutes, repeatIntervalTimeUnit = TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()
        workManager.enqueueUniquePeriodicWork(
            SafetySyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            safetySyncRequest
        )

        // Wire realtime manager callbacks
        realtimeManager.onLiveEvent = { event ->
            applyRealtimeEvent(event)
        }

        // Monitor network state and drive state machine.
        // collectLatest cancels the previous lambda on new emissions,
        // so startCatchUp is automatically cancelled on connectivity loss.
        networkJob = scope.launch {
            networkMonitor.isConnected.collectLatest { connected ->
                if (connected) {
                    Log.d(TAG, "Network connected, starting catch-up")
                    stateMachine.onEvent(SyncEvent.ConnectivityRestored)
                    startCatchUp(scope)
                } else {
                    Log.d(TAG, "Network lost")
                    stateMachine.onEvent(SyncEvent.ConnectivityLost)
                    // No explicit cancelCatchUp() needed — collectLatest already
                    // cancelled the previous coroutine before entering this block.
                }
            }
        }
    }

    override suspend fun stop() {
        Log.d(TAG, "Stopping SyncEngine")
        livePushJob?.cancel()
        livePushJob = null
        cancelCatchUp()

        fkRetryJobs.values.forEach { it.cancel() }
        fkRetryJobs.clear()

        realtimeManager.stop()
        networkJob?.cancel()
        networkMonitor.stop()
        engineScope?.cancel()
        engineScope = null
        started = false
    }

    override suspend fun syncNow() {
        Log.d(TAG, "Manual sync requested")
        if (engineScope == null) return
        val currentState = stateMachine.state.value
        if (currentState == SyncState.OFFLINE) {
            Log.w(TAG, "Cannot sync while offline")
            return
        }
        if (currentState == SyncState.CATCHING_UP) {
            Log.d(TAG, "Skipping safety sync — catch-up in progress")
            return
        }
        executeSyncCycle()
    }

    /**
     * Start the CATCHING_UP cycle (spec Section 3 — CATCHING_UP steps 1–8).
     */
    private fun startCatchUp(scope: CoroutineScope) {
        cancelCatchUp()
        catchUpJob = scope.launch {
            try {
                executeCatchUp(scope)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Catch-up failed: ${e.message}", e)
                stateMachine.onEvent(SyncEvent.CatchUpFailed(e))
            }
        }
    }

    private fun cancelCatchUp() {
        catchUpJob?.cancel()
        catchUpJob = null
    }

    private class CatchUpCircuitBreakerException : Exception()

    private fun getLastSyncedAt(tableName: String): Long {
        val query = SimpleSQLiteQuery(
            "SELECT last_synced_at FROM sync_metadata WHERE table_name = ?",
            arrayOf(tableName)
        )
        return database.query(query).use { cursor ->
            if (cursor.moveToFirst()) cursor.getLong(0) else 0L
        }
    }

    private fun setLastSyncedAt(tableName: String, lastSyncedAt: Long) {
        database.openHelper.writableDatabase.execSQL(
            """
            INSERT INTO sync_metadata(table_name, last_synced_at)
            VALUES(?, ?)
            ON CONFLICT(table_name) DO UPDATE SET last_synced_at = excluded.last_synced_at
            """.trimIndent(),
            arrayOf(tableName, lastSyncedAt),
        )
    }

    private fun updateLastSyncedAt(tableName: String, maxUpdatedAt: Long) {
        if (maxUpdatedAt <= 0L) return
        val current = getLastSyncedAt(tableName)
        if (maxUpdatedAt > current) {
            setLastSyncedAt(tableName, maxUpdatedAt)
        }
    }

    private fun maxTimestamp(records: List<Record>, timestampColumn: String): Long {
        return records.maxOfOrNull { record ->
            parseTimestampSafe(record[timestampColumn])
        } ?: 0L
    }

    /**
     * Streaming pull: fetch page-by-page and invoke [onBatch] per page.
     *
     * Keeps the streaming architecture in SyncEngineImpl (apply per page),
     * while leaving PullCoordinator unchanged.
     */
    private suspend fun pullStreaming(
        tableConfig: SyncTableConfig,
        onBatch: suspend (batch: List<Record>, batchMaxTimestamp: Long) -> Unit,
    ) {
        val pageSize = tableConfig.pullPageSize ?: pullCoordinator.pullPageSize
        val seenPks = mutableSetOf<String>()

        var since: Long
        if (tableConfig.fullPull) {
            since = 0L
            Log.d(TAG, "Full-pulling ${tableConfig.tableName} (no incremental filter)")
        } else {
            val lastSyncedAt = getLastSyncedAt(tableConfig.tableName)
            since = maxOf(0L, lastSyncedAt - pullCoordinator.overlapWindowMs)
            Log.d(TAG, "Pulling ${tableConfig.tableName}: lastSyncedAt=$lastSyncedAt, effectiveSince=$since")
        }

        var page = 0
        var totalRecords = 0

        while (true) {
            val fetched = remoteClient.pull(
                table = tableConfig.tableName,
                timestampColumn = tableConfig.timestampColumn,
                since = since,
                overlapWindowMs = 0L, // already subtracted
                limit = pageSize,
            )

            if (fetched.isEmpty()) break

            val newRecords = mutableListOf<Record>()
            var newInBatch = 0
            for (record in fetched) {
                val pk = record[tableConfig.primaryKey]?.toString() ?: continue
                if (seenPks.add(pk)) {
                    newRecords.add(record)
                    newInBatch++
                }
            }

            page++

            val batchMaxTs = maxTimestamp(fetched, tableConfig.timestampColumn)
            if (newRecords.isNotEmpty()) {
                totalRecords += newRecords.size
                onBatch(newRecords, batchMaxTs)
            }

            if (fetched.size < pageSize) break

            if (batchMaxTs > since) {
                since = batchMaxTs
            } else if (newInBatch == 0) {
                Log.w(TAG, "Cursor stuck for ${tableConfig.tableName} at $since with no new records, breaking pagination")
                break
            }
        }

        stateMachine.onEvent(SyncEvent.PullComplete(tableConfig.tableName, totalRecords))
        Log.d(TAG, "Pulled $totalRecords records from ${tableConfig.tableName} in $page page(s)")
    }

    /**
     * Execute the full CATCHING_UP cycle per spec Section 3.
     *
     * Refactored to stream + apply per page batch to avoid OOM/WAL blowups.
     */
    private suspend fun executeCatchUp(scope: CoroutineScope) {
        val tableNames = registeredTables.map { it.tableName }
        val catchUpStartMs = System.currentTimeMillis()

        // Snapshot pre-catch-up cursors for overflow retry rollback.
        val preCatchUpCursors = registeredTables
            .filterNot { it.fullPull }
            .associate { it.tableName to getLastSyncedAt(it.tableName) }

        var transitionedToLive = false

        // Step 1: Subscribe to Realtime once; each attempt resets the buffer.
        realtimeManager.start(realtimeChannel, tableNames, scope)

        try {
            for (attempt in 1..config.maxCatchUpRetries) {
                realtimeBuffer.reset()

                // On overflow retry, roll back cursors so we re-pull from the original start point.
                if (attempt > 1) {
                    syncMutex.withLock {
                        database.withTransaction {
                            for ((tableName, lastSyncedAt) in preCatchUpCursors) {
                                setLastSyncedAt(tableName, lastSyncedAt)
                            }
                        }
                    }
                }

                var forcedLive = false

                // Steps 2 + 3 + 5 + 7: pullStreaming → reconcileBatch → apply → update cursor (per batch)
                for (tableConfig in registeredTables) {
                    try {
                        pullStreaming(tableConfig) { batch, batchMaxTs ->
                            if (batch.isEmpty()) return@pullStreaming

                            if (config.maxCatchUpDurationMs > 0
                                && System.currentTimeMillis() - catchUpStartMs > config.maxCatchUpDurationMs
                            ) {
                                forcedLive = true
                                throw CatchUpCircuitBreakerException()
                            }

                            syncMutex.withLock {
                                database.withTransaction {
                                    conflictReconciler.reconcile(batch, tableConfig)

                                    val batchPks = batch.mapNotNull { it[tableConfig.primaryKey]?.toString() }
                                    val localWinnerPks = if (batchPks.isNotEmpty()) {
                                        batchPks.chunked(900).flatMap { chunk ->
                                            outboxDao.findPendingForRecords(tableConfig.tableName, chunk)
                                        }.map { it.recordId }.toSet()
                                    } else emptySet()

                                    val safeBatch = if (localWinnerPks.isEmpty()) batch else {
                                        batch.filter { record ->
                                            val pk = record[tableConfig.primaryKey]?.toString()
                                            pk == null || pk !in localWinnerPks
                                        }
                                    }

                                    applyRecordsToRoom(tableConfig, safeBatch)
                                    if (!tableConfig.fullPull) {
                                        updateLastSyncedAt(tableConfig.tableName, batchMaxTs)
                                    }
                                }
                            }
                        }
                    } catch (_: CatchUpCircuitBreakerException) {
                        forcedLive = true
                        break
                    }

                    if (forcedLive) break
                }

                if (forcedLive) {
                    Log.w(TAG, "Catch-up exceeded max duration (${config.maxCatchUpDurationMs}ms), forcing LIVE")
                    if (!realtimeBuffer.overflowed) {
                        drainRealtimeBuffer()
                    }
                    transitionedToLive = true
                    stateMachine.onEvent(SyncEvent.CatchUpCompleted)
                    return
                }

                // Step 4: Push remaining outbox entries
                pushCoordinator.pushPending(remoteClient, registeredTables)

                // Step 6: Drain realtime buffer (deduplicated against Room)
                if (!realtimeBuffer.overflowed) {
                    drainRealtimeBuffer()
                } else {
                    Log.w(TAG, "Buffer overflowed during catch-up — skipping buffer drain")
                }

                // If buffer didn't overflow, we're done — go LIVE
                if (!realtimeBuffer.overflowed) {
                    transitionedToLive = true
                    stateMachine.onEvent(SyncEvent.CatchUpCompleted)
                    Log.d(TAG, "Catch-up completed on attempt $attempt, now LIVE")
                    return
                }

                Log.w(TAG, "Buffer overflow on attempt $attempt/$config.maxCatchUpRetries — retrying")
                delay(catchUpOverflowBackoffMs(attempt))
            }

            // Exhausted retries — go LIVE anyway with safety sync as backstop
            Log.e(TAG, "Catch-up failed to drain buffer after $config.maxCatchUpRetries attempts, going LIVE (safety sync will recover)")
            val overflowed = realtimeBuffer.overflowed
            transitionedToLive = true
            stateMachine.onEvent(SyncEvent.CatchUpCompleted)

            if (overflowed) {
                scope.launch {
                    try {
                        executeSyncCycle()
                    } catch (e: Exception) {
                        Log.e(TAG, "Post-catch-up safety sync failed: ${e.message}", e)
                    }
                }
            }
        } finally {
            if (!transitionedToLive) {
                realtimeManager.stop()
            }
        }
    }

    /**
     * Execute a safety sync (spec Section 9).
     * Pull → reconcileBatch → apply (per batch) → update cursor → push → prune.
     */
    private suspend fun executeSyncCycle() {
        Log.d(TAG, "Executing safety sync cycle")
        stateMachine.onEvent(SyncEvent.SafetySync)

        for (tableConfig in registeredTables) {
            try {
                pullStreaming(tableConfig) { batch, batchMaxTs ->
                    if (batch.isEmpty()) return@pullStreaming

                    syncMutex.withLock {
                        database.withTransaction {
                            conflictReconciler.reconcile(batch, tableConfig)

                            val batchPks = batch.mapNotNull { it[tableConfig.primaryKey]?.toString() }
                            val localWinnerPks = if (batchPks.isNotEmpty()) {
                                batchPks.chunked(900).flatMap { chunk ->
                                    outboxDao.findPendingForRecords(tableConfig.tableName, chunk)
                                }.map { it.recordId }.toSet()
                            } else emptySet()

                            val safeBatch = if (localWinnerPks.isEmpty()) batch else {
                                batch.filter { record ->
                                    val pk = record[tableConfig.primaryKey]?.toString()
                                    pk == null || pk !in localWinnerPks
                                }
                            }

                            applyRecordsToRoom(tableConfig, safeBatch)
                            if (!tableConfig.fullPull) {
                                updateLastSyncedAt(tableConfig.tableName, batchMaxTs)
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Safety sync failed for ${tableConfig.tableName}: ${e.message}")
            }
        }

        // Prune old synced outbox entries
        syncMutex.withLock {
            pruneOutbox()
        }

        // Push any pending outbox entries (losers already marked synced by reconciler)
        try {
            pushCoordinator.pushPending(remoteClient, registeredTables)
        } catch (e: Exception) {
            Log.e(TAG, "Safety sync push failed: ${e.message}")
        }
    }

    private suspend fun drainRealtimeBuffer() {
        val bufferedEvents = realtimeBuffer.drain()
        if (bufferedEvents.isEmpty()) return

        val fkFailures = mutableListOf<Pair<SyncTableConfig, RealtimeChangeEvent>>()

        syncMutex.withLock {
            database.withTransaction {
                for (event in bufferedEvents) {
                    val tableConfig = registeredTables.find { it.tableName == event.table } ?: continue
                    val pk = event.record[tableConfig.primaryKey]?.toString()
                    if (shouldApplyBufferEvent(event, tableConfig)) {
                        try {
                            applyRealtimeEventInternal(tableConfig, event)
                        } catch (e: android.database.sqlite.SQLiteConstraintException) {
                            fkFailures.add(tableConfig to event)
                        }
                    } else {
                        stateMachine.onEvent(SyncEvent.RealtimeEventSkipped(event.table, pk))
                    }
                }
            }
        }

        fkFailures.forEach { (cfg, event) ->
            scheduleFkRetry(cfg, event)
        }
    }

    /**
     * Apply records from a pull to Room in chunks.
     * Delegates to the per-table applyToRoom callback, splitting large
     * record sets to keep Room WAL pressure manageable.
     */
    private suspend fun applyRecordsToRoom(config: SyncTableConfig, records: List<Record>) {
        val callback = config.applyToRoom
        if (callback == null) {
            Log.w(TAG, "No applyToRoom callback for ${config.tableName}, ${records.size} records skipped")
            return
        }
        if (records.size > LARGE_PULL_THRESHOLD) {
            Log.w(TAG, "Large pull for ${config.tableName}: ${records.size} records — consider incremental sync frequency")
        }
        for (chunk in records.chunked(APPLY_CHUNK_SIZE)) {
            callback.invoke(chunk)
        }
    }

    /**
     * Apply a single realtime event to Room, with deduplication (spec Section 6).
     * On FK constraint failure, schedules a retry in a separate coroutine to avoid
     * blocking the realtime event collection loop.
     */
    private suspend fun applyRealtimeEvent(event: RealtimeChangeEvent) {
        val config = registeredTables.find { it.tableName == event.table }
        if (config == null) {
            Log.w(TAG, "No config for realtime event table: ${event.table}")
            return
        }

        val eventTs = event.record[config.timestampColumn] as? String
        val pk = event.record[config.primaryKey]?.toString()

        if (pk != null && eventTs != null) {
            Log.d(TAG, "Applying realtime event: ${event.table}/$pk ts=$eventTs")
        }

        if (event.operation == ChangeOperation.DELETE) {
            if (pk == null) {
                Log.w(TAG, "Realtime DELETE missing PK for ${config.tableName}")
                return
            }

            when {
                config.deleteFromRoom != null -> {
                    try {
                        syncMutex.withLock {
                            database.withTransaction {
                                config.deleteFromRoom.invoke(pk)
                            }
                        }
                    } catch (e: android.database.sqlite.SQLiteConstraintException) {
                        Log.w(TAG, "FK constraint applying ${event.table}/$pk, scheduling retry")
                        scheduleFkRetry(config, event)
                    }
                }

                config.softDeleteColumn != null -> {
                    Log.d(TAG, "Ignoring realtime DELETE for ${config.tableName}/$pk (soft-delete handled via UPDATE)")
                }

                else -> {
                    Log.w(TAG, "No deleteFromRoom configured for ${config.tableName}, ignoring realtime DELETE for $pk")
                }
            }

            return
        }

        try {
            syncMutex.withLock {
                database.withTransaction {
                    applyRealtimeEventInternal(config, event)
                }
            }
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // FK constraint — parent record hasn't arrived yet.
            // Retry in a separate coroutine to not block the realtime event pipeline.
            Log.w(TAG, "FK constraint applying ${event.table}/$pk, scheduling retry")
            scheduleFkRetry(config, event)
        }
    }

    private fun catchUpOverflowBackoffMs(attempt: Int): Long {
        val base = config.catchUpOverflowBackoffBaseMs
        val max = config.catchUpOverflowBackoffMaxMs
        val exp = 1L shl (attempt - 1).coerceAtLeast(0)
        return (base * exp).coerceAtMost(max)
    }

    private suspend fun applyRealtimeEventInternal(config: SyncTableConfig, event: RealtimeChangeEvent) {
        when (event.operation) {
            ChangeOperation.DELETE -> {
                val pk = event.record[config.primaryKey]?.toString()
                if (pk == null) {
                    Log.w(TAG, "Realtime DELETE missing PK for ${config.tableName}")
                    return
                }

                val deleteCallback = config.deleteFromRoom
                when {
                    deleteCallback != null -> deleteCallback.invoke(pk)
                    config.softDeleteColumn != null -> {
                        Log.d(TAG, "Ignoring realtime DELETE for ${config.tableName}/$pk (soft-delete handled via UPDATE)")
                    }
                    else -> Log.w(TAG, "No deleteFromRoom configured for ${config.tableName}, ignoring realtime DELETE for $pk")
                }
            }

            else -> {
                config.applyToRoom?.invoke(listOf(event.record))
                    ?: Log.w(TAG, "No applyToRoom callback for ${event.table}")
            }
        }
    }

    private fun scheduleFkRetry(config: SyncTableConfig, event: RealtimeChangeEvent) {
        val scope = engineScope ?: return
        val pk = event.record[config.primaryKey]?.toString() ?: return
        val maxAttempts = this.config.fkRetryMaxAttempts
        if (maxAttempts <= 0) return

        val key = "${config.tableName}:$pk"
        val existing = fkRetryJobs[key]
        if (existing?.isActive == true) return
        if (existing != null && !existing.isActive) {
            fkRetryJobs.remove(key, existing)
        }

        val job = scope.launch {
            for (attempt in 1..maxAttempts) {
                delay(this@SyncEngineImpl.config.fkRetryDelayMs * attempt)

                try {
                    syncMutex.withLock {
                        database.withTransaction {
                            applyRealtimeEventInternal(config, event)
                        }
                    }
                    Log.d(TAG, "FK retry succeeded for ${config.tableName}/$pk")
                    return@launch
                } catch (e: android.database.sqlite.SQLiteConstraintException) {
                    if (attempt == maxAttempts) {
                        Log.e(TAG, "FK retry failed for ${config.tableName}/$pk — will be caught by safety sync")
                    }
                } catch (e: CancellationException) {
                    throw e
                } catch (e: Exception) {
                    Log.e(TAG, "FK retry error for ${config.tableName}/$pk: ${e.message}")
                    return@launch
                }
            }
        }

        val previous = fkRetryJobs.putIfAbsent(key, job)
        if (previous != null) {
            job.cancel()
            return
        }

        job.invokeOnCompletion {
            fkRetryJobs.remove(key, job)
        }
    }

    private fun parseTimestampSafe(value: Any?): Long {
        return when (value) {
            is String -> {
                try {
                    Instant.parse(value).toEpochMilli()
                } catch (_: Exception) {
                    value.toLongOrNull() ?: 0L
                }
            }
            is Long -> value
            is Number -> value.toLong()
            else -> 0L
        }
    }

    private suspend fun shouldApplyBufferEvent(
        event: RealtimeChangeEvent,
        config: SyncTableConfig,
    ): Boolean {
        if (event.operation == ChangeOperation.DELETE) return true

        val pk = event.record[config.primaryKey]?.toString() ?: return false

        // Don't overwrite records with ANY pending local change (not just deletes)
        val hasPendingLocal = outboxDao.findPendingForRecords(config.tableName, listOf(pk)).isNotEmpty()
        if (hasPendingLocal) return false

        return isBufferEventNewer(event, config)
    }

    private suspend fun isBufferEventNewer(
        event: RealtimeChangeEvent,
        config: SyncTableConfig,
    ): Boolean {
        val pk = event.record[config.primaryKey]?.toString() ?: return true
        val eventTs = parseTimestampSafe(event.record[config.timestampColumn])
        if (eventTs == 0L) return true

        val query = SimpleSQLiteQuery(
            "SELECT ${config.timestampColumn} FROM ${config.tableName} WHERE ${config.primaryKey} = ?",
            arrayOf(pk)
        )

        val cursor = database.query(query)
        val currentTs = cursor.use {
            if (it.moveToFirst()) {
                val current = it.getString(0)
                parseTimestampSafe(current)
            } else {
                0L
            }
        }

        return eventTs > currentTs
    }

    /**
     * Prune synced outbox entries older than retention period (spec Section 4).
     */
    private suspend fun pruneOutbox() {
        val cutoff = System.currentTimeMillis() - config.outboxPruneRetentionMs
        val pruned = outboxDao.pruneSynced(cutoff)
        if (pruned > 0) {
            Log.d(TAG, "Pruned $pruned old outbox entries")
        }
    }
}

