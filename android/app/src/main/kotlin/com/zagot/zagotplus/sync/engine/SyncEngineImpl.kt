package com.zagot.zagotplus.sync.engine

import android.util.Log
import androidx.room.RoomDatabase
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
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
) : SyncEngine {

    companion object {
        private const val TAG = "SyncEngine"
        /** Pruning threshold: keep synced entries for 30 minutes. */
        private const val OUTBOX_PRUNE_RETENTION_MS = 30 * 60 * 1_000L
    }

    private val registeredTables = mutableListOf<SyncTableConfig>()
    private var engineScope: CoroutineScope? = null
    private var networkJob: Job? = null
    private var catchUpJob: Job? = null

    override val state: StateFlow<SyncState> get() = stateMachine.state
    override val syncLog: StateFlow<List<SyncLogEntry>> get() = stateMachine.syncLog

    override fun registerTable(config: SyncTableConfig) {
        registeredTables.add(config)
        Log.d(TAG, "Registered table: ${config.tableName}")
    }

    override fun start() {
        if (engineScope != null) {
            Log.w(TAG, "SyncEngine already started")
            return
        }

        Log.d(TAG, "Starting SyncEngine with ${registeredTables.size} tables")
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
        engineScope = scope

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
        database.runInTransaction {
            runBlocking {
                // Step 5: Apply remote changes
                for ((config, records) in allPulled) {
                    applyRecordsToRoom(config, records)
                }

                // Step 6: Drain realtime buffer (deduplicated)
                val bufferedEvents = realtimeBuffer.drain()
                for (event in bufferedEvents) {
                    applyRealtimeEvent(event)
                }

                // Step 7: Update last_synced_at per table
                for ((config, records) in allPulled) {
                    val maxTs = pullCoordinator.maxTimestamp(records, config.timestampColumn)
                    pullCoordinator.updateLastSyncedAt(config.tableName, maxTs)
                }
            }
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

                database.runInTransaction {
                    runBlocking {
                        applyRecordsToRoom(config, records)
                        val maxTs = pullCoordinator.maxTimestamp(records, config.timestampColumn)
                        pullCoordinator.updateLastSyncedAt(config.tableName, maxTs)
                    }
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
     * This is a placeholder — the actual Room UPSERT must be done per-entity.
     * The sync engine provides the data; a per-table callback handles the Room write.
     */
    private suspend fun applyRecordsToRoom(config: SyncTableConfig, records: List<Record>) {
        // Each SyncTableConfig could have an applyToRoom callback,
        // but for now we log the intent — actual Room writes happen
        // through the table-specific callbacks registered via registerTable
        config.applyToRoom?.invoke(records)
            ?: Log.w(TAG, "No applyToRoom callback for ${config.tableName}, ${records.size} records skipped")
    }

    /**
     * Apply a single realtime event to Room.
     */
    private suspend fun applyRealtimeEvent(event: RealtimeChangeEvent) {
        val config = registeredTables.find { it.tableName == event.table }
        if (config == null) {
            Log.w(TAG, "No config for realtime event table: ${event.table}")
            return
        }

        config.applyToRoom?.invoke(listOf(event.record))
            ?: Log.w(TAG, "No applyToRoom callback for ${event.table}")
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
