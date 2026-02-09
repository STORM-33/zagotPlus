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
import com.zagot.syncengine.dao.PushResult
import com.zagot.syncengine.db.SyncOutboxDao
import com.zagot.syncengine.db.SyncMetadataDao
import com.zagot.syncengine.network.NetworkMonitor
import com.zagot.syncengine.realtime.RealtimeBuffer
import com.zagot.syncengine.realtime.RealtimeManager
import com.zagot.syncengine.state.SyncEvent
import com.zagot.syncengine.state.SyncLogEntry
import com.zagot.syncengine.state.SyncPhase
import com.zagot.syncengine.state.SyncProgress
import com.zagot.syncengine.state.SyncState
import com.zagot.syncengine.state.SyncStateMachine
import com.zagot.syncengine.state.TableSyncStatus
import com.zagot.syncengine.util.SyncTableConfig
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
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

    /** Number of outbox entries that failed to push (terminal errors). */
    val failedCount: StateFlow<Int>

    /** Progress of the current sync operation, or null when idle. */
    val progress: StateFlow<SyncProgress?>

    /** Per-table sync status. Updated after each sync cycle. */
    val tableSyncStatus: StateFlow<List<TableSyncStatus>>

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

    /**
     * Reset failed outbox entries (synced=2) back to pending (synced=0) for retry.
     * Use when the user wants to re-attempt pushing records that hit terminal errors.
     */
    suspend fun retryFailedRecords(tableName: String)

    /**
     * Permanently delete failed outbox entries (synced=2) for a table.
     * Use when the user wants to discard records that cannot be pushed.
     */
    suspend fun discardFailedRecords(tableName: String)
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
    private val syncMetadataDao: SyncMetadataDao,
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
        /** SQLite IN-clause limit is 999; use 900 for safety margin. */
        private const val IN_CLAUSE_CHUNK_SIZE = 900
        /** If a server event is >5 minutes newer than the oldest pending outbox entry, apply it. */
        private const val STALE_LOCAL_THRESHOLD_MS = 5 * 60 * 1_000L
    }

    private val registeredTables = mutableListOf<SyncTableConfig>()
    @Volatile private var started = false
    private var engineScope: CoroutineScope? = null
    private var networkJob: Job? = null
    private var catchUpJob: Job? = null
    private var livePushJob: Job? = null

    /** Guards concurrent access to Room writes (sync apply vs realtime apply). */
    private val syncMutex = Mutex()

    /** FK retry jobs keyed by "table:pk" to prevent unbounded fire-and-forget retries. */
    private val fkRetryJobs = ConcurrentHashMap<String, Job>()

    override val state: StateFlow<SyncState> get() = stateMachine.state
    override val syncLog: StateFlow<List<SyncLogEntry>> get() = stateMachine.syncLog

    private val _failedCount = MutableStateFlow(0)
    override val failedCount: StateFlow<Int> = _failedCount.asStateFlow()

    private val _progress = MutableStateFlow<SyncProgress?>(null)
    override val progress: StateFlow<SyncProgress?> = _progress.asStateFlow()

    private val _tableSyncStatus = MutableStateFlow<List<TableSyncStatus>>(emptyList())
    override val tableSyncStatus: StateFlow<List<TableSyncStatus>> = _tableSyncStatus.asStateFlow()

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
                val result = pushCoordinator.pushPending(remoteClient, registeredTables)
                handlePushResult(result)
                refreshFailedCount()
                refreshTableSyncStatus()
            } catch (e: Exception) {
                Log.e(TAG, "LIVE push failed: ${e.message}")
            }
        }
    }

    override suspend fun retryFailedRecords(tableName: String) {
        outboxDao.retryAllFailed(tableName)
        refreshFailedCount()
        refreshTableSyncStatus()
        Log.d(TAG, "Retried failed outbox entries for $tableName")
    }

    override suspend fun discardFailedRecords(tableName: String) {
        outboxDao.discardAllFailed(tableName)
        refreshFailedCount()
        refreshTableSyncStatus()
        Log.d(TAG, "Discarded failed outbox entries for $tableName")
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

        // Refresh failed count and table status on start
        scope.launch {
            refreshFailedCount()
            refreshTableSyncStatus()
        }

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
                    cancelCatchUp()
                    realtimeManager.stop()
                }
            }
        }
    }

    override suspend fun stop() {
        Log.d(TAG, "Stopping SyncEngine")
        cancelCatchUp()

        // Cancel any scheduled FK retries (bounded + lifecycle-tied).
        fkRetryJobs.values.forEach { it.cancel() }
        fkRetryJobs.clear()

        // Stop realtime before cancelling the scope that owns the collection job.
        realtimeManager.stop()

        livePushJob?.cancel()
        livePushJob = null

        networkJob?.cancel()
        networkJob = null
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
        // syncMutex inside reconcileAndApplyBatch ensures no overlap with catch-up
        executeSyncCycle()
    }

    // ── Catch-up ─────────────────────────────────────────────────────────

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

    /** Thrown by the per-batch callback when the circuit breaker fires. */
    private class CatchUpCircuitBreakerException : Exception()

    /**
     * Execute the full CATCHING_UP cycle per spec Section 3:
     * 1. Subscribe to Realtime (buffer events, don't apply yet)
     * 2. Pull remote changes (streaming, per-batch transactions)
     * 3. Resolve conflicts per batch (outbox vs pulled)
     * 4. Push remaining outbox
     * 5. Apply remote changes to Room (inside per-batch transaction)
     * 6. Drain Realtime buffer
     * 7. Update last_synced_at per batch (inside per-batch transaction)
     * 8. Transition to LIVE
     *
     * Uses iterative retry (max [config.maxCatchUpRetries]) on buffer overflow.
     * On overflow retry, cursors are rolled back to pre-catch-up values to
     * guarantee no events are missed.
     */
    private suspend fun executeCatchUp(scope: CoroutineScope) {
        val tableNames = registeredTables.map { it.tableName }
        val catchUpStartMs = System.currentTimeMillis()

        // Snapshot pre-catch-up cursors for overflow retry rollback.
        val preCatchUpCursors = registeredTables
            .filterNot { it.fullPull }
            .associate { it.tableName to pullCoordinator.getLastSyncedAt(it.tableName) }

        var transitionedToLive = false

        // Step 1: Subscribe to Realtime once; each attempt resets the in-memory buffer.
        realtimeManager.start(realtimeChannel, tableNames, scope)

        try {
            for (attempt in 1..config.maxCatchUpRetries) {
                realtimeBuffer.reset()

                // On overflow retry, roll back cursors so we re-pull from the original
                // start point. This guarantees no events are missed even if the buffer
                // dropped events whose timestamps fall within already-pulled ranges.
                if (attempt > 1) {
                    Log.w(TAG, "Overflow retry attempt $attempt — rolling back cursors to pre-catch-up state")
                    syncMutex.withLock {
                        database.withTransaction {
                            for ((tableName, cursor) in preCatchUpCursors) {
                                pullCoordinator.setLastSyncedAt(tableName, cursor)
                            }
                        }
                    }
                }

                var forcedLive = false
                var tablesCompleted = 0
                var totalPulled = 0

                // Steps 2 + 3 + 5 + 7: stream pull → reconcile → filter local winners → apply → advance cursor
                for (tableConfig in registeredTables) {
                    var recordsProcessed = 0
                    _progress.value = SyncProgress(
                        phase = SyncPhase.PULLING,
                        currentTable = tableConfig.tableName,
                        tablesCompleted = tablesCompleted,
                        tablesTotal = registeredTables.size,
                        recordsProcessed = 0,
                    )

                    try {
                        pullCoordinator.pullStreaming(remoteClient, tableConfig) { batch, batchMaxTs ->
                            if (batch.isEmpty()) return@pullStreaming

                            // Circuit breaker: abort if catch-up is taking too long
                            if (config.maxCatchUpDurationMs > 0
                                && System.currentTimeMillis() - catchUpStartMs > config.maxCatchUpDurationMs
                            ) {
                                forcedLive = true
                                throw CatchUpCircuitBreakerException()
                            }

                            syncMutex.withLock {
                                database.withTransaction {
                                    reconcileAndApplyBatch(batch, tableConfig, batchMaxTs)
                                }
                            }

                            recordsProcessed += batch.size
                            totalPulled += batch.size
                            _progress.value = SyncProgress(
                                phase = SyncPhase.PULLING,
                                currentTable = tableConfig.tableName,
                                tablesCompleted = tablesCompleted,
                                tablesTotal = registeredTables.size,
                                recordsProcessed = recordsProcessed,
                            )
                        }
                    } catch (_: CatchUpCircuitBreakerException) {
                        break
                    }

                    tablesCompleted++
                    if (forcedLive) break
                }

                if (forcedLive) {
                    Log.w(TAG, "Catch-up exceeded max duration (${config.maxCatchUpDurationMs}ms), forcing LIVE")
                    if (!realtimeBuffer.overflowed) {
                        _progress.value = SyncProgress(SyncPhase.DRAINING_BUFFER, null, tablesCompleted, registeredTables.size, 0)
                        drainRealtimeBuffer()
                    }
                    _progress.value = null
                    transitionedToLive = true
                    stateMachine.onEvent(SyncEvent.CatchUpCompleted)
                    return
                }

                // Step 4: Push remaining outbox entries
                _progress.value = SyncProgress(SyncPhase.PUSHING, null, tablesCompleted, registeredTables.size, 0)
                val catchUpPushResult = pushCoordinator.pushPending(remoteClient, registeredTables)
                handlePushResult(catchUpPushResult)
                refreshFailedCount()
                refreshTableSyncStatus()

                // Step 6: Drain realtime buffer (deduplicated against Room state)
                if (!realtimeBuffer.overflowed) {
                    _progress.value = SyncProgress(SyncPhase.DRAINING_BUFFER, null, tablesCompleted, registeredTables.size, 0)
                    drainRealtimeBuffer()
                } else {
                    Log.w(TAG, "Buffer overflowed during catch-up — skipping buffer drain")
                }

                // If buffer didn't overflow, we're done — go LIVE
                if (!realtimeBuffer.overflowed) {
                    val durationMs = System.currentTimeMillis() - catchUpStartMs
                    config.onSyncComplete?.invoke(durationMs, totalPulled, catchUpPushResult.successCount)
                    _progress.value = null
                    transitionedToLive = true
                    stateMachine.onEvent(SyncEvent.CatchUpCompleted)
                    Log.d(TAG, "Catch-up completed on attempt $attempt, now LIVE")
                    return
                }

                val backoffMs = catchUpOverflowBackoffMs(attempt)
                Log.w(TAG, "Buffer overflow on attempt $attempt/${config.maxCatchUpRetries} — retrying after ${backoffMs}ms")
                delay(backoffMs)
            }

            // Exhausted retries — go LIVE anyway with safety sync as backstop
            Log.e(TAG, "Catch-up failed to drain buffer after ${config.maxCatchUpRetries} attempts, going LIVE (safety sync will recover)")
            val overflowed = realtimeBuffer.overflowed
            _progress.value = null
            transitionedToLive = true
            stateMachine.onEvent(SyncEvent.CatchUpCompleted)

            // If we overflowed repeatedly, run an immediate safety sync once LIVE to
            // reduce time-to-consistency for any missed realtime events.
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

    // ── Safety sync ──────────────────────────────────────────────────────

    /**
     * Execute a safety sync (spec Section 9).
     * Pull → reconcile → filter local winners → apply (per batch) → push → prune.
     * Guarded by [syncMutex] inside [reconcileAndApplyBatch] to prevent overlap with catch-up.
     */
    private suspend fun executeSyncCycle() {
        Log.d(TAG, "Executing safety sync cycle")
        stateMachine.onEvent(SyncEvent.SafetySync)

        val startMs = System.currentTimeMillis()
        var totalPulled = 0
        var tablesCompleted = 0

        // Pull all tables using streaming (same per-batch processing as catch-up)
        for (tableConfig in registeredTables) {
            var recordsProcessed = 0
            _progress.value = SyncProgress(
                phase = SyncPhase.PULLING,
                currentTable = tableConfig.tableName,
                tablesCompleted = tablesCompleted,
                tablesTotal = registeredTables.size,
                recordsProcessed = 0,
            )

            try {
                pullCoordinator.pullStreaming(remoteClient, tableConfig) { batch, batchMaxTs ->
                    if (batch.isEmpty()) return@pullStreaming

                    syncMutex.withLock {
                        database.withTransaction {
                            reconcileAndApplyBatch(batch, tableConfig, batchMaxTs)
                        }
                    }

                    recordsProcessed += batch.size
                    totalPulled += batch.size
                    _progress.value = SyncProgress(
                        phase = SyncPhase.PULLING,
                        currentTable = tableConfig.tableName,
                        tablesCompleted = tablesCompleted,
                        tablesTotal = registeredTables.size,
                        recordsProcessed = recordsProcessed,
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Safety sync failed for ${tableConfig.tableName}: ${e.message}")
            }

            tablesCompleted++
        }

        // Prune old synced outbox entries
        syncMutex.withLock {
            pruneOutbox()
        }

        // Push any pending outbox entries (losers already marked synced by reconciler)
        _progress.value = SyncProgress(SyncPhase.PUSHING, null, tablesCompleted, registeredTables.size, 0)
        var pushResult = PushResult(0, emptyList())
        try {
            pushResult = pushCoordinator.pushPending(remoteClient, registeredTables)
            handlePushResult(pushResult)
            refreshFailedCount()
            refreshTableSyncStatus()
        } catch (e: Exception) {
            Log.e(TAG, "Safety sync push failed: ${e.message}")
        }

        val durationMs = System.currentTimeMillis() - startMs
        config.onSyncComplete?.invoke(durationMs, totalPulled, pushResult.successCount)
        _progress.value = null
    }

    // ── Shared batch processing ──────────────────────────────────────────

    /**
     * Reconcile conflicts, filter out local winners, apply remaining records to Room,
     * and advance the sync cursor.
     *
     * MUST be called inside `syncMutex.withLock { database.withTransaction { ... } }`.
     *
     * This is the single code path for both catch-up and safety sync to avoid
     * duplication of the most bug-prone logic in the engine.
     */
    private suspend fun reconcileAndApplyBatch(
        batch: List<Record>,
        config: SyncTableConfig,
        batchMaxTimestamp: Long,
    ) {
        // Step 3: Resolve conflicts — marks outbox losers as synced
        conflictReconciler.reconcileBatch(batch, config)

        // Filter out records where local won (pending outbox entry still exists).
        // Without this, the older remote record would overwrite the newer local
        // change in Room, causing a visible UI flicker until the push cycle.
        val batchPks = batch.mapNotNull { it[config.primaryKey]?.toString() }
        val localWinnerPks = if (batchPks.isNotEmpty()) {
            batchPks.chunked(IN_CLAUSE_CHUNK_SIZE).flatMap { chunk ->
                outboxDao.findPendingForRecords(config.tableName, chunk)
            }.map { it.recordId }.toSet()
        } else {
            emptySet()
        }

        val safeBatch = if (localWinnerPks.isEmpty()) {
            batch
        } else {
            batch.filter { record ->
                val pk = record[config.primaryKey]?.toString()
                pk == null || pk !in localWinnerPks
            }
        }

        // Step 5: Apply remote changes (chunked to manage WAL pressure)
        applyRecordsToRoom(config, safeBatch)

        // Step 7: Advance cursor (skip fullPull tables — they always fetch everything)
        if (!config.fullPull) {
            pullCoordinator.updateLastSyncedAt(config.tableName, batchMaxTimestamp)
        }
    }

    // ── Realtime buffer drain ────────────────────────────────────────────

    /**
     * Drain the realtime buffer, applying events that are newer than what's
     * currently in Room and don't conflict with pending local changes.
     */
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
                        } catch (_: android.database.sqlite.SQLiteConstraintException) {
                            fkFailures.add(tableConfig to event)
                        }
                    } else {
                        stateMachine.onEvent(SyncEvent.RealtimeEventSkipped(event.table, pk))
                    }
                }
            }
        }

        // Schedule FK retries outside the transaction
        fkFailures.forEach { (cfg, event) ->
            scheduleFkRetry(cfg, event)
        }
    }

    /**
     * Determine whether a buffered realtime event should be applied to Room.
     *
     * Skips events where:
     * - A pending local change exists for the same record (prevents state reversion),
     *   UNLESS the server event is significantly newer (>5 min) — indicating stale local data.
     * - The event is older than what's already in Room (stale)
     *
     * DELETE events are always applied (idempotent).
     */
    private suspend fun shouldApplyBufferEvent(
        event: RealtimeChangeEvent,
        config: SyncTableConfig,
    ): Boolean {
        if (event.operation == ChangeOperation.DELETE) return true

        val pk = event.record[config.primaryKey]?.toString() ?: return false

        val pendingEntries = outboxDao.findPendingForRecords(config.tableName, listOf(pk))
        if (pendingEntries.isNotEmpty()) {
            // Check if the server event is significantly newer than the oldest pending outbox entry
            val eventTs = parseTimestampSafe(event.record[config.timestampColumn])
            val oldestOutboxTs = pendingEntries.minOf { it.createdAt }
            val ageGapMs = eventTs - oldestOutboxTs

            if (eventTs > 0L && ageGapMs > STALE_LOCAL_THRESHOLD_MS) {
                Log.w(TAG, "Server update for ${config.tableName}:$pk is ${ageGapMs}ms newer than " +
                        "pending local change — applying server version to resolve drift")
                return isBufferEventNewer(event, config)
            }

            Log.w(TAG, "Server update ignored for ${config.tableName}:$pk due to " +
                    "${pendingEntries.size} pending local change(s). Local-wins until next push cycle.")
            return false
        }

        return isBufferEventNewer(event, config)
    }

    /**
     * Check if a buffered event is newer than the record currently in Room.
     * Uses a raw PK lookup (indexed, instant) to avoid loading full entities.
     */
    private fun isBufferEventNewer(
        event: RealtimeChangeEvent,
        config: SyncTableConfig,
    ): Boolean {
        val pk = event.record[config.primaryKey]?.toString() ?: return true
        val eventTs = parseTimestampSafe(event.record[config.timestampColumn])
        if (eventTs == 0L) return true // can't compare, apply to be safe

        val query = SimpleSQLiteQuery(
            "SELECT ${config.timestampColumn} FROM ${config.tableName} WHERE ${config.primaryKey} = ?",
            arrayOf(pk)
        )

        val cursor = database.query(query)
        val currentTs = cursor.use {
            if (it.moveToFirst()) parseTimestampSafe(it.getString(0)) else 0L
        }

        return eventTs > currentTs
    }

    // ── Realtime LIVE event handling ─────────────────────────────────────

    /**
     * Apply a single realtime event to Room in LIVE state (spec Section 6).
     *
     * DELETE events: delegates to [SyncTableConfig.deleteFromRoom] if present,
     * ignores for soft-delete tables (the delete arrives as an UPDATE with
     * softDeleteColumn set), warns otherwise.
     *
     * INSERT/UPDATE events: delegates to [SyncTableConfig.applyToRoom].
     *
     * On FK constraint failure, schedules a bounded retry via [scheduleFkRetry]
     * to avoid blocking the realtime event collection loop.
     */
    private suspend fun applyRealtimeEvent(event: RealtimeChangeEvent) {
        val config = registeredTables.find { it.tableName == event.table }
        if (config == null) {
            Log.w(TAG, "No config for realtime event table: ${event.table}")
            return
        }

        val pk = event.record[config.primaryKey]?.toString()
        val eventTs = event.record[config.timestampColumn] as? String

        if (pk != null && eventTs != null) {
            Log.d(TAG, "Applying realtime event: ${event.table}/$pk ts=$eventTs")
        }

        // Handle DELETE events separately — upsert would re-create the deleted record
        if (event.operation == ChangeOperation.DELETE) {
            if (config.deleteFromRoom != null && pk != null) {
                try {
                    syncMutex.withLock {
                        config.deleteFromRoom.invoke(pk)
                    }
                    Log.d(TAG, "Applied DELETE for ${event.table}/$pk")
                } catch (_: Exception) {
                    Log.e(TAG, "Failed to apply DELETE for ${event.table}/$pk")
                }
            } else if (config.softDeleteColumn != null) {
                // Table uses soft-deletes — the actual delete comes as an UPDATE
                // with softDeleteColumn set. Ignore the hard DELETE event.
                Log.d(TAG, "Ignoring hard DELETE for soft-delete table ${event.table}/$pk")
            } else {
                Log.w(TAG, "DELETE event for ${event.table}/$pk but no deleteFromRoom callback and no soft-delete column")
            }
            return
        }

        // INSERT/UPDATE events
        try {
            syncMutex.withLock {
                config.applyToRoom?.invoke(listOf(event.record))
                    ?: Log.w(TAG, "No applyToRoom callback for ${event.table}")
            }
        } catch (_: android.database.sqlite.SQLiteConstraintException) {
            // FK constraint — parent record hasn't arrived yet.
            if (pk == null) {
                Log.w(TAG, "FK constraint applying ${event.table} but primary key missing — skipping retry")
                return
            }
            Log.w(TAG, "FK constraint applying ${event.table}/$pk, scheduling retry")
            scheduleFkRetry(config, event)
        }
    }

    /**
     * Apply a realtime event inside an existing transaction (used by buffer drain).
     * Caller handles FK constraint exceptions.
     */
    private suspend fun applyRealtimeEventInternal(
        config: SyncTableConfig,
        event: RealtimeChangeEvent,
    ) {
        when (event.operation) {
            ChangeOperation.DELETE -> {
                val pk = event.record[config.primaryKey]?.toString()
                when {
                    config.deleteFromRoom != null && pk != null -> config.deleteFromRoom.invoke(pk)
                    config.softDeleteColumn != null -> {
                        Log.d(TAG, "Ignoring DELETE for soft-delete table ${config.tableName}/$pk (buffer drain)")
                    }
                    else -> Log.w(TAG, "No deleteFromRoom for ${config.tableName}/$pk (buffer drain)")
                }
            }
            else -> {
                config.applyToRoom?.invoke(listOf(event.record))
                    ?: Log.w(TAG, "No applyToRoom callback for ${event.table}")
            }
        }
    }

    // ── FK retry ─────────────────────────────────────────────────────────

    /**
     * Schedule a bounded retry for a realtime event that failed due to FK constraint.
     * Deduplicates by "table:pk" — at most one retry job per record.
     * Jobs are children of [engineScope] and cancelled on [stop].
     */
    private fun scheduleFkRetry(config: SyncTableConfig, event: RealtimeChangeEvent) {
        val scope = engineScope ?: return
        val pk = event.record[config.primaryKey]?.toString() ?: return

        val key = "${config.tableName}:$pk"
        val existing = fkRetryJobs[key]
        if (existing?.isActive == true) return

        Log.w(TAG, "Scheduling FK retry for ${config.tableName}/$pk")

        val job = scope.launch {
            var attempt = 0
            while (attempt < this@SyncEngineImpl.config.fkRetryMaxAttempts && isActive) {
                attempt++
                delay(this@SyncEngineImpl.config.fkRetryDelayMs * attempt)
                try {
                    syncMutex.withLock {
                        config.applyToRoom?.invoke(listOf(event.record))
                            ?: Log.w(TAG, "No applyToRoom callback for ${config.tableName}")
                    }
                    Log.d(TAG, "FK retry succeeded for ${config.tableName}/$pk (attempt $attempt)")
                    return@launch
                } catch (_: android.database.sqlite.SQLiteConstraintException) {
                    Log.w(TAG, "FK retry $attempt/${this@SyncEngineImpl.config.fkRetryMaxAttempts} failed for ${config.tableName}/$pk")
                }
            }
            Log.e(TAG, "FK retries exhausted for ${config.tableName}/$pk — will be caught by safety sync")
        }

        // Atomic insert — if another coroutine won the race, cancel ours
        val previous = fkRetryJobs.putIfAbsent(key, job)
        if (previous != null) {
            job.cancel()
            return
        }

        job.invokeOnCompletion { fkRetryJobs.remove(key, job) }
    }

    // ── Helpers ──────────────────────────────────────────────────────────

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

    private fun parseTimestampSafe(value: Any?): Long {
        return when (value) {
            is String -> try { Instant.parse(value).toEpochMilli() } catch (_: Exception) {
                value.toLongOrNull() ?: 0L
            }
            is Long -> value
            is Number -> value.toLong()
            else -> 0L
        }
    }

    private fun catchUpOverflowBackoffMs(attempt: Int): Long {
        val base = config.catchUpOverflowBackoffBaseMs
        val max = config.catchUpOverflowBackoffMaxMs
        val exp = 1L shl (attempt - 1).coerceAtLeast(0)
        return (base * exp).coerceAtMost(max)
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

    /** Refresh the failed count StateFlow from the outbox. */
    private suspend fun refreshFailedCount() {
        _failedCount.value = outboxDao.countFailed()
    }

    /** Refresh per-table sync status from metadata + outbox. */
    private suspend fun refreshTableSyncStatus() {
        val metadata = syncMetadataDao.getAll().associateBy { it.tableName }
        val pendingByTable = outboxDao.countPendingByTable().associateBy { it.tableName }
        val failedByTable = outboxDao.countFailedByTable().associateBy { it.tableName }

        val allTables = (metadata.keys + pendingByTable.keys + failedByTable.keys)
        _tableSyncStatus.value = allTables.map { table ->
            TableSyncStatus(
                tableName = table,
                lastSyncedAt = metadata[table]?.lastSyncedAt ?: 0L,
                pendingCount = pendingByTable[table]?.count ?: 0,
                failedCount = failedByTable[table]?.count ?: 0,
            )
        }
    }

    /** Invoke onPushFailed callback for each failure in the push result. */
    private fun handlePushResult(result: PushResult) {
        result.failedEntries.forEach { failure ->
            config.onPushFailed?.invoke(failure.tableName, failure.recordId, failure.reason)
        }
    }
}
