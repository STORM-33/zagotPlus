package com.zagot.syncengine.api

import android.util.Log
import androidx.room.RoomDatabase
import androidx.room.withTransaction
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
    private val config: SyncEngineConfig,
) : SyncEngine {

    companion object {
        private const val TAG = "SyncEngine"
    }

    private val registeredTables = mutableListOf<SyncTableConfig>()
    @Volatile private var started = false
    private var engineScope: CoroutineScope? = null
    private var networkJob: Job? = null
    private var catchUpJob: Job? = null
    private var livePushJob: Job? = null

    /** Guards concurrent access to sync operations (catch-up vs safety sync). */
    private val syncMutex = Mutex()

    override val state: StateFlow<SyncState> get() = stateMachine.state
    override val syncLog: StateFlow<List<SyncLogEntry>> get() = stateMachine.syncLog

    override fun registerTable(config: SyncTableConfig) {
        check(!started) { "Cannot register tables after start() — call registerTable() before start()" }
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
            ExistingPeriodicWorkPolicy.KEEP,
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

    override fun stop() {
        Log.d(TAG, "Stopping SyncEngine")
        cancelCatchUp()
        // Stop realtime synchronously before cancelling scope.
        // Using runBlocking is safe here — stop() is called from lifecycle callbacks
        // (not from a coroutine), and realtimeManager.stop() just unsubscribes the channel.
        runBlocking {
            realtimeManager.stop()
        }
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
     *
     * Uses iterative retry (max [config.maxCatchUpRetries]) on buffer overflow
     * instead of recursion to prevent stack overflow under pathological write pressure.
     */
    private suspend fun executeCatchUp(scope: CoroutineScope) = syncMutex.withLock {
        val tableNames = registeredTables.map { it.tableName }

        for (attempt in 1..config.maxCatchUpRetries) {
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
                // Step 5: Apply remote changes in FK dependency order (registeredTables order)
                for (config in registeredTables) {
                    val records = allPulled[config] ?: continue
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

            // If buffer didn't overflow, we're done — go LIVE
            if (!realtimeBuffer.overflowed) {
                stateMachine.onEvent(SyncEvent.CatchUpCompleted)
                Log.d(TAG, "Catch-up completed on attempt $attempt, now LIVE")
                return
            }

            Log.w(TAG, "Buffer overflow on attempt $attempt/$config.maxCatchUpRetries — retrying")
        }

        // Exhausted retries — go LIVE anyway with safety sync as backstop
        Log.e(TAG, "Catch-up failed to drain buffer after $config.maxCatchUpRetries attempts, going LIVE (safety sync will recover)")
        stateMachine.onEvent(SyncEvent.CatchUpCompleted)
    }

    /**
     * Execute a safety sync (spec Section 9).
     * Pull → reconcile conflicts → apply → push → prune.
     * Guarded by [syncMutex] to prevent overlap with catch-up.
     */
    suspend fun executeSyncCycle() = syncMutex.withLock {
        Log.d(TAG, "Executing safety sync cycle")
        stateMachine.onEvent(SyncEvent.SafetySync)

        // Pull all tables first, then reconcile, then apply — same order as catch-up
        val allPulled = mutableMapOf<SyncTableConfig, List<Record>>()
        for (config in registeredTables) {
            try {
                val records = pullCoordinator.pull(remoteClient, config)
                allPulled[config] = records
            } catch (e: Exception) {
                Log.e(TAG, "Safety sync pull failed for ${config.tableName}: ${e.message}")
            }
        }

        // Reconcile conflicts before applying (prevents overwriting local changes)
        for ((config, records) in allPulled) {
            try {
                conflictReconciler.reconcile(records, config)
            } catch (e: Exception) {
                Log.e(TAG, "Safety sync reconcile failed for ${config.tableName}: ${e.message}")
            }
        }

        // Apply pulled records to Room
        for (config in registeredTables) {
            val records = allPulled[config] ?: continue
            try {
                database.withTransaction {
                    applyRecordsToRoom(config, records)
                    val maxTs = pullCoordinator.maxTimestamp(records, config.timestampColumn)
                    pullCoordinator.updateLastSyncedAt(config.tableName, maxTs)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Safety sync apply failed for ${config.tableName}: ${e.message}")
            }
        }

        // Push any pending outbox entries (losers already marked synced by reconciler)
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

        try {
            config.applyToRoom?.invoke(listOf(event.record))
                ?: Log.w(TAG, "No applyToRoom callback for ${event.table}")
        } catch (e: android.database.sqlite.SQLiteConstraintException) {
            // FK constraint — parent record hasn't arrived yet.
            // Retry in a separate coroutine to not block the realtime event pipeline.
            Log.w(TAG, "FK constraint applying ${event.table}/$pk, scheduling retry")
            engineScope?.launch {
                delay(config.fkRetryDelayMs)
                try {
                    config.applyToRoom?.invoke(listOf(event.record))
                    Log.d(TAG, "FK retry succeeded for ${event.table}/$pk")
                } catch (e2: android.database.sqlite.SQLiteConstraintException) {
                    Log.e(TAG, "FK retry failed for ${event.table}/$pk — will be caught by safety sync")
                }
            }
        }
    }

    /**
     * Check if a buffered realtime event duplicates an already-pulled record.
     * Same PK and event.updated_at <= pulled.updated_at → skip.
     * Timestamps are ISO-8601 strings from Supabase.
     */
    private suspend fun isDuplicate(
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
        val cutoff = System.currentTimeMillis() - config.outboxPruneRetentionMs
        val pruned = outboxDao.pruneSynced(cutoff)
        if (pruned > 0) {
            Log.d(TAG, "Pruned $pruned old outbox entries")
        }
    }
}

