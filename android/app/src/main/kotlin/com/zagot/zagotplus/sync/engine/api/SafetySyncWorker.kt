package com.zagot.zagotplus.sync.engine.api

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic safety sync worker (spec Section 9).
 *
 * Runs every 15 minutes to catch any records missed by Realtime.
 * Same pull logic as CATCHING_UP but without buffer/drain (already LIVE).
 * Also prunes old synced outbox entries.
 */
@HiltWorker
class SafetySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngineImpl,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SafetySyncWorker"
        const val WORK_NAME = "safety_sync_periodic"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting safety sync")
        return try {
            syncEngine.executeSyncCycle()
            Log.d(TAG, "Safety sync completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Safety sync failed: ${e.message}", e)
            Result.retry()
        }
    }
}
