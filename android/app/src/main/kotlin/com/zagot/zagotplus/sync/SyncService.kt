package com.zagot.zagotplus.sync

import android.util.Log
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for bidirectional sync between Room and Supabase.
 *
 * Push: Local unsynced transactions and batches → Supabase (upsert by local_id)
 * Pull: New remote transactions and batches → Room (since last sync timestamp)
 *
 * Deduplication is handled by UNIQUE constraint on local_id in Supabase.
 */
@Singleton
class SyncService @Inject constructor(
    private val syncDataSource: SyncDataSource,
    private val transactionDao: TransactionDao,
    private val purchaseBatchDao: PurchaseBatchDao,
    private val locationDao: LocationDao,
    private val productDao: ProductDao,
    private val syncPreferences: SyncPreferences
) {
    companion object {
        private const val TAG = "SyncService"
    }

    /**
     * Perform full sync: push local changes, then pull remote changes.
     *
     * Returns:
     * - Success: Both push and pull succeeded
     * - Partial: Push succeeded, pull failed (data is safe on server)
     * - Failure: Push failed (no data sent)
     */
    suspend fun sync(): SyncResult {
        Log.d(TAG, "Starting sync...")

        // Step 1: Push pending products (before other entities due to FK)
        val productPushResult = try {
            pushPendingProducts()
        } catch (e: Exception) {
            Log.e(TAG, "Product push failed", e)
            return SyncResult.Failure(
                error = e.message ?: "Product push failed",
                phase = SyncPhase.PUSH
            )
        }
        Log.d(TAG, "Pushed ${productPushResult.successCount} products (${productPushResult.failedCount} failed)")

        // Step 2: Pull reference data (non-critical, log and continue on failure)
        pullReferenceData()

        // Step 3: Push pending batches (before transactions due to FK)
        val batchPushResult = try {
            pushPendingBatches()
        } catch (e: Exception) {
            Log.e(TAG, "Batch push failed", e)
            return SyncResult.Failure(
                error = e.message ?: "Batch push failed",
                phase = SyncPhase.PUSH
            )
        }
        Log.d(TAG, "Pushed ${batchPushResult.successCount} batches (${batchPushResult.failedCount} failed)")

        // Step 4: Push pending transactions
        val pushResult = try {
            pushPendingTransactions()
        } catch (e: Exception) {
            Log.e(TAG, "Push failed", e)
            return SyncResult.Failure(
                error = e.message ?: "Push failed",
                phase = SyncPhase.PUSH
            )
        }
        Log.d(TAG, "Pushed ${pushResult.successCount} transactions (${pushResult.failedCount} failed)")

        // Step 5: Pull new batches
        val batchPullResult = try {
            pullNewBatches()
        } catch (e: Exception) {
            Log.e(TAG, "Batch pull failed after successful push", e)
            // Continue to transaction pull
            0
        }
        Log.d(TAG, "Pulled $batchPullResult batches")

        // Step 6: Pull new transactions
        val pullResult = try {
            pullNewTransactions()
        } catch (e: Exception) {
            Log.e(TAG, "Pull failed after successful push", e)
            // Push succeeded but pull failed - return Partial
            return SyncResult.Partial(
                pushed = pushResult.successCount + batchPushResult.successCount + productPushResult.successCount,
                pullError = e.message ?: "Pull failed"
            )
        }
        Log.d(TAG, "Pulled $pullResult transactions")

        return SyncResult.Success(
            pushed = pushResult.successCount + batchPushResult.successCount + productPushResult.successCount,
            pulled = pullResult + batchPullResult
        )
    }

    /**
     * Result of push operation tracking both successes and failures.
     */
    private data class PushResult(val successCount: Int, val failedCount: Int)

    /**
     * Push all unsynced local transactions to Supabase.
     * Uses upsert with local_id as conflict key to handle duplicates.
     *
     * @throws Exception if network or critical error occurs
     */
    private suspend fun pushPendingTransactions(): PushResult {
        val pending = transactionDao.getUnsynced()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending transactions to push")
            return PushResult(0, 0)
        }

        Log.d(TAG, "Pushing ${pending.size} pending transactions")

        var successCount = 0
        var failedCount = 0

        for (entity in pending) {
            try {
                val dto = TransactionDto.fromEntity(entity)
                syncDataSource.pushTransaction(dto)
                // Mark as synced locally
                transactionDao.markAsSynced(entity.localId, Instant.now())
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push transaction ${entity.localId}", e)
                failedCount++
                // Continue with next transaction - individual failures don't stop sync
            }
        }

        return PushResult(successCount, failedCount)
    }

    /**
     * Pull new transactions from Supabase that were created after last sync.
     * Inserts or updates local Room database.
     *
     * Note: Uses created_at filter which has a known edge case - if device A creates
     * a transaction at T1, device B syncs at T2 (setting lastSync=T2), then device A
     * syncs at T3, device B won't see A's transaction on next sync because created_at=T1 < T2.
     * For this app's use case (single user, few devices), this is acceptable.
     * A more robust solution would use a monotonic sequence number or updated_at timestamp.
     *
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewTransactions(): Int {
        val lastSync = syncPreferences.getLastSyncTimestamp()
        Log.d(TAG, "Pulling transactions created after $lastSync")

        val remoteDtos = syncDataSource.pullTransactions(lastSync)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new remote transactions")
            syncPreferences.setLastSyncTimestamp(Instant.now())
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new remote transactions")

        var insertCount = 0
        for (dto in remoteDtos) {
            try {
                // Check if we already have this transaction
                val existing = transactionDao.getByLocalId(dto.localId)
                if (existing == null) {
                    // New transaction from another device
                    val entity = dto.toEntity()
                    transactionDao.insert(entity)
                    insertCount++
                }
                // If exists, it's our own transaction - skip
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert remote transaction ${dto.localId}", e)
            }
        }

        syncPreferences.setLastSyncTimestamp(Instant.now())
        return insertCount
    }

    /**
     * Push all unsynced local batches to Supabase.
     * Uses upsert with local_id as conflict key to handle duplicates.
     *
     * @throws Exception if network or critical error occurs
     */
    private suspend fun pushPendingBatches(): PushResult {
        val pending = purchaseBatchDao.getUnsynced()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending batches to push")
            return PushResult(0, 0)
        }

        Log.d(TAG, "Pushing ${pending.size} pending batches")

        var successCount = 0
        var failedCount = 0

        for (entity in pending) {
            try {
                val dto = PurchaseBatchDto.fromEntity(entity)
                syncDataSource.pushBatch(dto)
                // Mark as synced locally
                purchaseBatchDao.markSynced(entity.id, Instant.now())
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push batch ${entity.localId}", e)
                failedCount++
                // Continue with next batch - individual failures don't stop sync
            }
        }

        return PushResult(successCount, failedCount)
    }

    /**
     * Push all unsynced local products to Supabase.
     * Uses upsert with local_id as conflict key to handle duplicates.
     *
     * @throws Exception if network or critical error occurs
     */
    private suspend fun pushPendingProducts(): PushResult {
        val pending = productDao.getUnsynced()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending products to push")
            return PushResult(0, 0)
        }

        Log.d(TAG, "Pushing ${pending.size} pending products")

        var successCount = 0
        var failedCount = 0

        for (entity in pending) {
            try {
                val dto = ProductDto.fromEntity(entity)
                syncDataSource.pushProduct(dto)
                // Mark as synced locally
                productDao.markSynced(entity.id, Instant.now())
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push product ${entity.localId}", e)
                failedCount++
                // Continue with next product - individual failures don't stop sync
            }
        }

        return PushResult(successCount, failedCount)
    }

    /**
     * Pull new batches from Supabase that were created after last sync.
     * Inserts or updates local Room database.
     *
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewBatches(): Int {
        val lastSync = syncPreferences.getLastSyncTimestamp()
        Log.d(TAG, "Pulling batches created after $lastSync")

        val remoteDtos = syncDataSource.pullBatches(lastSync)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new remote batches")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new remote batches")

        var insertCount = 0
        for (dto in remoteDtos) {
            try {
                // Check if we already have this batch
                val existing = purchaseBatchDao.getByLocalId(dto.localId)
                if (existing == null) {
                    // New batch from another device
                    val entity = dto.toEntity()
                    purchaseBatchDao.insert(entity)
                    insertCount++
                }
                // If exists, it's our own batch - skip
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert remote batch ${dto.localId}", e)
            }
        }

        return insertCount
    }

    /**
     * Pull reference data (locations and products) from Supabase.
     * These are master data managed on server, pulled to local DB.
     * Uses upsert logic to handle existing records with child FK references.
     * Each type is pulled independently so one failure doesn't block the other.
     */
    private suspend fun pullReferenceData() {
        // Pull locations
        try {
            val locations = syncDataSource.pullLocations()

            locations.forEach { dto ->
                val entity = dto.toEntity()
                val existing = locationDao.getById(entity.id)
                if (existing == null) {
                    locationDao.insert(entity)
                } else {
                    locationDao.update(entity)
                }
            }
            Log.d(TAG, "Pulled ${locations.size} locations")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull locations", e)
        }

        // Pull products (independent of locations)
        try {
            val products = syncDataSource.pullProducts()

            products.forEach { dto ->
                val entity = dto.toEntity()
                val existing = productDao.getById(entity.id)
                if (existing == null) {
                    productDao.insert(entity)
                } else {
                    productDao.update(entity)
                }
            }
            Log.d(TAG, "Pulled ${products.size} products")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull products", e)
        }
    }
}
