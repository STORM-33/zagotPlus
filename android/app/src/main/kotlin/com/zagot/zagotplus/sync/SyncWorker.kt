package com.zagot.zagotplus.sync

import android.content.Context
import android.util.Log
import androidx.hilt.work.HiltWorker
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import dagger.assisted.Assisted
import dagger.assisted.AssistedInject

/**
 * Background worker for periodic synchronization with Supabase.
 *
 * Executes push-then-pull sync strategy:
 * 1. Push local unsynced transactions to Supabase
 * 2. Pull new remote transactions from Supabase
 *
 * Retries with exponential backoff on failure.
 */
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncService: SyncService,
    private val syncStatusRepository: SyncStatusRepository
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "SyncWorker"
        const val WORK_NAME = "sync_periodic"
        const val MAX_RETRY_ATTEMPTS = 3
    }

    override suspend fun doWork(): Result {
        Log.d(TAG, "Starting sync work (attempt ${runAttemptCount + 1})")

        return try {
            // Update status to syncing
            syncStatusRepository.setSyncing()

            // Perform sync
            when (val result = syncService.sync()) {
                is SyncResult.Success -> {
                    syncStatusRepository.setIdle()
                    Log.d(TAG, "Sync succeeded: pushed=${result.pushed}, pulled=${result.pulled}")
                    Result.success()
                }

                is SyncResult.Partial -> {
                    // Partial success - data was pushed, but pull failed
                    // Set warning status so user knows data might be incomplete
                    syncStatusRepository.setWarning(
                        "Дані збережено, але не всі оновлення завантажені"
                    )
                    Log.w(TAG, "Sync partial: pushed=${result.pushed}, pull failed: ${result.pullError}")
                    // Return success - we'll try pull again on next sync
                    Result.success()
                }

                is SyncResult.Failure -> {
                    handleFailure(result.error)
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Sync work failed", e)
            handleFailure(e.message ?: "Unknown error")
        }
    }

    private suspend fun handleFailure(error: String): Result {
        return if (runAttemptCount < MAX_RETRY_ATTEMPTS) {
            syncStatusRepository.setError(error)
            Log.w(TAG, "Sync failed, will retry: $error")
            Result.retry()
        } else {
            syncStatusRepository.setError("Failed after $MAX_RETRY_ATTEMPTS attempts: $error")
            Log.e(TAG, "Sync failed after max retries: $error")
            Result.failure()
        }
    }
}
