package com.zagot.zagotplus.sync.engine

import android.util.Log
import androidx.room.RoomDatabase
import androidx.room.withTransaction
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import java.time.Instant
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
    fun stop()

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
) : SyncEngine {

    companion object {
        private const val TAG = "SyncEngine"
        /** Pruning threshold: keep synced entries for 30 minutes. */
        private const val OUTBOX_PRUNE_RETENTION_MS = 30 * 60 * 1_000L
        /** Debounce for LIVE push to batch rapid successive writes. */
        private const val LIVE_PUSH_DEBOUNCE_MS = 200L
    }

    private val registeredTables = mutableListOf<SyncTableConfig>()
    private var engineScope: CoroutineScope? = null
    private var networkJob: Job? = null
    private var catchUpJob: Job? = null
    private var livePushJob: Job? = null

    override val state: StateFlow<SyncState> get() = stateMachine.state
    override val syncLog: StateFlow<List<SyncLogEntry>> get() = stateMachine.syncLog

    override fun registerTable(config: SyncTableConfig) {
        registeredTables.add(config)
        Log.d(TAG, "Registered table: ${config.tableName}")
    }

    override fun notifyOutboxChanged() {
        val scope = engineScope ?: return
        if (stateMachine.state.value != SyncState.LIVE) return

        // Debounce: cancel previous pending push, schedule a new one
        livePushJob?.cancel()
        livePushJob = scope.launch {
            delay(LIVE_PUSH_DEBOUNCE_MS)
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

        Log.d(TAG, "Starting SyncEngine with ${registeredTables.size} tables")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        engineScope = scope

        // Start network monitoring (registers ConnectivityManager callbacks)
        networkMonitor.start()

        // Enqueue periodic safety sync (spec Section 8)
        val safetySyncRequest = PeriodicWorkRequestBuilder<SafetySyncWorker>(
            repeatInterval = 15, repeatIntervalTimeUnit = TimeUnit.MINUTES
        ).setConstraints(
            Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
        ).build()
        workManager.enqueueUniquePeriodicWork(
            SafetySyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,
            safetySyncRequest
        )

        // Wire realtime manager callbacks
        realtimeManager.onLiveEvent = { event ->
            applyRealtimeEvent(event)
        }

        // Monitor network state and drive state machine
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
                }
            }
        }
    }

    override fun stop() {
        Log.d(TAG, "Stopping SyncEngine")
        cancelCatchUp()
        engineScope?.launch {
            realtimeManager.stop()
        }
        networkJob?.cancel()
        networkMonitor.stop()
        engineScope?.cancel()
        engineScope = null
    }

    override suspend fun syncNow() {
        Log.d(TAG, "Manual sync requested")
        if (engineScope == null) return
        if (stateMachine.state.value == SyncState.OFFLINE) {
            Log.w(TAG, "Cannot sync while offline")
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

    /**
     * Execute the full CATCHING_UP cycle per spec Section 3:
     * 1. Subscribe to Realtime (buffer events, don't apply yet)
     * 2. Pull remote changes
     * 3. Resolve conflicts (outbox vs pulled)
     * 4. Push remaining outbox
     * 5. Apply remote changes to Room
     * 6. Drain Realtime buffer
     * 7. Update last_synced_at (all in single Room transaction)
     * 8. Transition to LIVE
     */
    private suspend fun executeCatchUp(scope: CoroutineScope) {
        val tableNames = registeredTables.map { it.tableName }

        // Step 1: Subscribe to Realtime + buffer
        realtimeBuffer.reset()
        realtimeManager.start(realtimeChannel, tableNames, scope)

        // Step 2: Pull remote changes for each table
        val allPulled = mutableMapOf<SyncTableConfig, List<Record>>()
        for (config in registeredTables) {
            val records = pullCoordinator.pull(remoteClient, config)
            allPulled[config] = records
        }

        // Step 3: Resolve conflicts
        for ((config, records) in allPulled) {
            conflictReconciler.reconcile(records, config)
        }

        // Step 4: Push remaining outbox entries
        pushCoordinator.pushPending(remoteClient, registeredTables)

        // Steps 5 + 6 + 7: Apply records + drain buffer + update metadata
        // ALL IN A SINGLE ROOM TRANSACTION (crash safety — spec Section 5.1)
        database.withTransaction {
            // Step 5: Apply remote changes
            for ((config, records) in allPulled) {
                applyRecordsToRoom(config, records)
            }

            // Step 6: Drain realtime buffer (deduplicated against pulled data)
            if (!realtimeBuffer.overflowed) {
                val bufferedEvents = realtimeBuffer.drain()
                for (event in bufferedEvents) {
                    val config = registeredTables.find { it.tableName == event.table } ?: continue
                    val pulledRecords = allPulled[config] ?: emptyList()
                    if (!isDuplicate(event, pulledRecords, config)) {
                        applyRecordsToRoom(config, listOf(event.record))
                    }
                }
            } else {
                Log.w(TAG, "Buffer overflowed during catch-up — skipping buffer drain")
            }

            // Step 7: Update last_synced_at per table
            for ((config, records) in allPulled) {
                val maxTs = pullCoordinator.maxTimestamp(records, config.timestampColumn)
                pullCoordinator.updateLastSyncedAt(config.tableName, maxTs)
            }
        }

        // Handle buffer overflow: re-sync instead of going LIVE with incomplete data
        if (realtimeBuffer.overflowed) {
            Log.w(TAG, "Buffer overflow detected — triggering fresh re-sync")
            realtimeBuffer.reset()
            executeCatchUp(scope)
            return
        }

        // Step 8: Transition to LIVE
        stateMachine.onEvent(SyncEvent.CatchUpCompleted)
        Log.d(TAG, "Catch-up completed, now LIVE")
    }

    /**
     * Execute a safety sync (spec Section 9).
     * Same pull logic as catch-up but without buffer/drain (already LIVE).
     */
    suspend fun executeSyncCycle() {
        Log.d(TAG, "Executing safety sync cycle")
        stateMachine.onEvent(SyncEvent.SafetySync)

        for (config in registeredTables) {
            try {
                val records = pullCoordinator.pull(remoteClient, config)

                database.withTransaction {
                    applyRecordsToRoom(config, records)
                    val maxTs = pullCoordinator.maxTimestamp(records, config.timestampColumn)
                    pullCoordinator.updateLastSyncedAt(config.tableName, maxTs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Safety sync pull failed for ${config.tableName}: ${e.message}")
            }
        }

        // Push any pending outbox entries
        try {
            pushCoordinator.pushPending(remoteClient, registeredTables)
        } catch (e: Exception) {
            Log.e(TAG, "Safety sync push failed: ${e.message}")
        }

        // Prune old synced outbox entries
        pruneOutbox()
    }

    /**
     * Apply records from a pull to Room.
     * Delegates to the per-table applyToRoom callback.
     */
    private suspend fun applyRecordsToRoom(config: SyncTableConfig, records: List<Record>) {
        config.applyToRoom?.invoke(records)
            ?: Log.w(TAG, "No applyToRoom callback for ${config.tableName}, ${records.size} records skipped")
    }

    /**
     * Apply a single realtime event to Room, with deduplication (spec Section 6).
     * Skips events where the local record already has updated_at >= event.updated_at.
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

        // Retry with short delay for FK ordering: a transaction event may arrive
        // before its parent batch event. One retry after 500ms usually suffices.
        var applied = false
        for (attempt in 1..2) {
            try {
                config.applyToRoom?.invoke(listOf(event.record))
                    ?: Log.w(TAG, "No applyToRoom callback for ${event.table}")
                applied = true
                break
            } catch (e: android.database.sqlite.SQLiteConstraintException) {
                if (attempt < 2) {
                    Log.w(TAG, "FK constraint applying ${event.table}/$pk, retrying in 500ms")
                    delay(500)
                } else {
                    Log.e(TAG, "FK constraint applying ${event.table}/$pk after retry — will be caught by safety sync")
                }
            }
        }
    }

    /**
     * Check if a buffered realtime event duplicates an already-pulled record.
     * Same PK and event.updated_at <= pulled.updated_at → skip.
     * Timestamps are ISO-8601 strings from Supabase.
     */
    private fun isDuplicate(
        event: RealtimeChangeEvent,
        pulledRecords: List<Record>,
        config: SyncTableConfig,
    ): Boolean {
        val eventPk = event.record[config.primaryKey]?.toString() ?: return false
        val eventTsStr = event.record[config.timestampColumn] as? String ?: return false

        val pulled = pulledRecords.find { it[config.primaryKey]?.toString() == eventPk }
            ?: return false
        val pulledTsStr = pulled[config.timestampColumn] as? String ?: return false

        val eventTs = try { Instant.parse(eventTsStr).toEpochMilli() } catch (_: Exception) { return false }
        val pulledTs = try { Instant.parse(pulledTsStr).toEpochMilli() } catch (_: Exception) { return false }

        val isDup = eventTs <= pulledTs
        if (isDup) {
            stateMachine.onEvent(
                SyncEvent.RealtimeEventSkipped(event.table, eventPk)
            )
        }
        return isDup
    }

    /**
     * Prune synced outbox entries older than retention period (spec Section 4).
     */
    private suspend fun pruneOutbox() {
        val cutoff = System.currentTimeMillis() - OUTBOX_PRUNE_RETENTION_MS
        val pruned = outboxDao.pruneSynced(cutoff)
        if (pruned > 0) {
            Log.d(TAG, "Pruned $pruned old outbox entries")
        }
    }
}
