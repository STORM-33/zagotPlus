package com.zagot.zagotplus.sync

import android.content.Context
import androidx.work.*
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicLong
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages WorkManager-based sync scheduling.
 * Provides methods to start periodic sync and trigger one-time sync.
 */
@Singleton
class SyncManager @Inject constructor(
    @ApplicationContext private val context: Context,
    private val syncStatusRepository: SyncStatusRepository
) {
    private val workManager = WorkManager.getInstance(context)
    
    // Rate limiting: prevent sync spam
    private val lastManualSyncTime = AtomicLong(0L)

    companion object {
        /** Minimum interval between manual syncs in milliseconds (5 seconds) */
        private const val MIN_SYNC_INTERVAL_MS = 5_000L
    }

    /**
     * Initialize periodic sync. Should be called once from Application.onCreate().
     * Schedules sync every 15 minutes when network is available.
     */
    fun initializePeriodicSync() {
        val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
            repeatInterval = 15,
            repeatIntervalTimeUnit = TimeUnit.MINUTES
        )
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .setBackoffCriteria(
                BackoffPolicy.EXPONENTIAL,
                WorkRequest.MIN_BACKOFF_MILLIS,
                TimeUnit.MILLISECONDS
            )
            .build()

        workManager.enqueueUniquePeriodicWork(
            SyncWorker.WORK_NAME,
            ExistingPeriodicWorkPolicy.KEEP,  // Don't reschedule if already exists
            syncRequest
        )
    }

    /**
     * Trigger immediate one-time sync (e.g., from "Sync Now" button).
     * Uses same constraints as periodic sync.
     * Rate-limited to prevent spam (minimum 30 seconds between syncs),
     * unless last sync failed - then immediate retry is allowed.
     * 
     * @return true if sync was enqueued, false if rate-limited
     */
    fun triggerManualSync(): Boolean {
        val now = System.currentTimeMillis()
        val lastSync = lastManualSyncTime.get()
        
        // Allow immediate retry if last sync failed or had warning
        val currentState = syncStatusRepository.currentState
        val isErrorState = currentState == SyncStatus.State.ERROR || 
                          currentState == SyncStatus.State.WARNING
        
        if (!isErrorState && now - lastSync < MIN_SYNC_INTERVAL_MS) {
            // Rate limited - too soon since last sync
            return false
        }
        
        if (!lastManualSyncTime.compareAndSet(lastSync, now)) {
            // Another thread triggered sync, skip
            return false
        }
        
        val syncRequest = OneTimeWorkRequestBuilder<SyncWorker>()
            .setConstraints(
                Constraints.Builder()
                    .setRequiredNetworkType(NetworkType.CONNECTED)
                    .build()
            )
            .build()

        workManager.enqueue(syncRequest)
        return true
    }

    /**
     * Cancel all scheduled sync work (for testing or settings).
     */
    fun cancelSync() {
        workManager.cancelUniqueWork(SyncWorker.WORK_NAME)
    }
}
