package com.zagot.zagotplus.sync

import com.zagot.syncengine.api.SyncEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Bridge: delegates to the new SyncEngine while preserving the existing API.
 * Repositories and ViewModels call triggerManualSync() — this now triggers the engine.
 */
@Singleton
class SyncManager @Inject constructor(
    private val syncEngine: SyncEngine,
) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val lastManualSyncTime = AtomicLong(0L)

    companion object {
        private const val MIN_SYNC_INTERVAL_MS = 5_000L
    }

    /**
     * @deprecated Periodic sync is now managed by the engine (SafetySyncWorker).
     * Calling this is a no-op.
     */
    fun initializePeriodicSync() { /* no-op — engine handles scheduling */ }

    /**
     * Trigger immediate sync. Rate-limited to 5 s unless last sync errored.
     * @return true if sync was enqueued
     */
    fun triggerManualSync(): Boolean {
        val now = System.currentTimeMillis()
        val lastSync = lastManualSyncTime.get()

        if (now - lastSync < MIN_SYNC_INTERVAL_MS) return false
        if (!lastManualSyncTime.compareAndSet(lastSync, now)) return false

        scope.launch { syncEngine.syncNow() }
        return true
    }

    fun cancelSync() { /* no-op — engine lifecycle managed by ZagotApp */ }
}
