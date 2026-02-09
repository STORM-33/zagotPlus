package com.zagot.syncengine.api

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.zagot.syncengine.dao.PushErrorCategory
import com.zagot.syncengine.dao.PushCoordinator
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Periodic safety sync worker (spec Section 9).
 *
 * Runs at the configured interval to catch any records missed by Realtime.
 * Same pull logic as CATCHING_UP but without buffer/drain (already LIVE).
 * Also prunes old synced outbox entries.
 *
 * Error handling:
 * - Auth errors (401) → [Result.failure] (retrying won't help, saves battery)
 * - Terminal errors (400/404) → [Result.failure] (server rejected the data)
 * - Transient errors (network, 5xx) → [Result.retry] (will succeed later)
 */
@HiltWorker
class SafetySyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncEngine: SyncEngineImpl,
    private val pushCoordinator: PushCoordinator,
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SafetySyncWorker"
        const val WORK_NAME = "safety_sync_periodic"
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting safety sync")
        return try {
            syncEngine.syncNow()
            Log.d(TAG, "Safety sync completed")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "Safety sync failed: ${e.message}", e)
            val category = pushCoordinator.classifyError(e)
            when (category) {
                PushErrorCategory.AUTH, PushErrorCategory.TERMINAL -> {
                    Log.e(TAG, "Non-retryable error ($category), failing permanently")
                    Result.failure()
                }
                else -> Result.retry()
            }
        }
    }
}
