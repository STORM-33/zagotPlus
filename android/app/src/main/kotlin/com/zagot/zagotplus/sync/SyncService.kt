package com.zagot.zagotplus.sync

import android.util.Log
import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.SaleBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.remote.dto.CashOperationDto
import com.zagot.zagotplus.data.remote.dto.ExpenseCategoryDto
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import com.zagot.zagotplus.data.remote.dto.SaleBatchDto
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
    private val saleBatchDao: SaleBatchDao,
    private val locationDao: LocationDao,
    private val productDao: ProductDao,
    private val expenseCategoryDao: ExpenseCategoryDao,
    private val cashOperationDao: CashOperationDao,
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

        // Capture the sync timestamp ONCE at the start for all pull operations
        val syncStartTimestamp = syncPreferences.getLastSyncTimestamp()

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

        // Step 3: Push pending expense categories (before cash operations due to FK)
        val categoryPushResult = try {
            pushPendingExpenseCategories()
        } catch (e: Exception) {
            Log.e(TAG, "Expense category push failed", e)
            return SyncResult.Failure(
                error = e.message ?: "Expense category push failed",
                phase = SyncPhase.PUSH
            )
        }
        Log.d(TAG, "Pushed ${categoryPushResult.successCount} expense categories (${categoryPushResult.failedCount} failed)")

        // Step 4: Push pending batches (before transactions due to FK)
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

        // Step 4b: Push pending sale batches (before transactions due to FK)
        val saleBatchPushResult = try {
            pushPendingSaleBatches()
        } catch (e: Exception) {
            Log.e(TAG, "Sale batch push failed", e)
            return SyncResult.Failure(
                error = e.message ?: "Sale batch push failed",
                phase = SyncPhase.PUSH
            )
        }
        Log.d(TAG, "Pushed ${saleBatchPushResult.successCount} sale batches (${saleBatchPushResult.failedCount} failed)")

        // Step 5: Push pending transactions
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

        // Step 6: Push pending cash operations
        val cashPushResult = try {
            pushPendingCashOperations()
        } catch (e: Exception) {
            Log.e(TAG, "Cash operations push failed", e)
            return SyncResult.Failure(
                error = e.message ?: "Cash operations push failed",
                phase = SyncPhase.PUSH
            )
        }
        Log.d(TAG, "Pushed ${cashPushResult.successCount} cash operations (${cashPushResult.failedCount} failed)")

        // Step 7: Pull new expense categories
        val categoryPullResult = try {
            pullNewExpenseCategories(syncStartTimestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Expense category pull failed after successful push", e)
            0
        }
        Log.d(TAG, "Pulled $categoryPullResult expense categories")

        // Step 8: Pull new batches
        val batchPullResult = try {
            pullNewBatches(syncStartTimestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Batch pull failed after successful push", e)
            // Continue to transaction pull
            0
        }
        Log.d(TAG, "Pulled $batchPullResult batches")

        // Step 8b: Pull new sale batches
        val saleBatchPullResult = try {
            pullNewSaleBatches(syncStartTimestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Sale batch pull failed after successful push", e)
            0
        }
        Log.d(TAG, "Pulled $saleBatchPullResult sale batches")

        // Step 9: Pull new transactions
        val pullResult = try {
            pullNewTransactions(syncStartTimestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Pull failed after successful push", e)
            // Push succeeded but pull failed - return Partial
            return SyncResult.Partial(
                pushed = pushResult.successCount + batchPushResult.successCount + saleBatchPushResult.successCount + productPushResult.successCount + categoryPushResult.successCount + cashPushResult.successCount,
                pullError = e.message ?: "Pull failed"
            )
        }
        Log.d(TAG, "Pulled $pullResult transactions")

        // Step 10: Pull new cash operations
        val cashPullResult = try {
            pullNewCashOperations(syncStartTimestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Cash operations pull failed after successful push", e)
            0
        }
        Log.d(TAG, "Pulled $cashPullResult cash operations")

        // Update last sync timestamp only once at the end after all pulls complete
        syncPreferences.setLastSyncTimestamp(Instant.now())

        return SyncResult.Success(
            pushed = pushResult.successCount + batchPushResult.successCount + saleBatchPushResult.successCount + productPushResult.successCount + categoryPushResult.successCount + cashPushResult.successCount,
            pulled = pullResult + batchPullResult + saleBatchPullResult + categoryPullResult + cashPullResult
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
     * @param since Timestamp to filter transactions created after
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewTransactions(since: Instant): Int {
        Log.d(TAG, "Pulling transactions created after $since")

        val remoteDtos = syncDataSource.pullTransactions(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new remote transactions")
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
     * @param since Timestamp to filter batches created after
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewBatches(since: Instant): Int {
        Log.d(TAG, "Pulling batches created after $since")

        val remoteDtos = syncDataSource.pullBatches(since)

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
     * Push all unsynced local sale batches to Supabase.
     * Uses upsert with local_id as conflict key to handle duplicates.
     *
     * @throws Exception if network or critical error occurs
     */
    private suspend fun pushPendingSaleBatches(): PushResult {
        val pending = saleBatchDao.getUnsynced()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending sale batches to push")
            return PushResult(0, 0)
        }

        Log.d(TAG, "Pushing ${pending.size} pending sale batches")

        var successCount = 0
        var failedCount = 0

        for (entity in pending) {
            try {
                val dto = SaleBatchDto.fromEntity(entity)
                syncDataSource.pushSaleBatch(dto)
                // Mark as synced locally
                saleBatchDao.markSynced(entity.id, Instant.now())
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push sale batch ${entity.localId}", e)
                failedCount++
                // Continue with next batch - individual failures don't stop sync
            }
        }

        return PushResult(successCount, failedCount)
    }

    /**
     * Pull new sale batches from Supabase that were created after last sync.
     * Inserts or updates local Room database.
     *
     * @param since Timestamp to filter sale batches created after
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewSaleBatches(since: Instant): Int {
        Log.d(TAG, "Pulling sale batches created after $since")

        val remoteDtos = syncDataSource.pullSaleBatches(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new remote sale batches")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new remote sale batches")

        var insertCount = 0
        for (dto in remoteDtos) {
            try {
                // Check if we already have this batch
                val existing = saleBatchDao.getByLocalId(dto.localId)
                if (existing == null) {
                    // New batch from another device
                    val entity = dto.toEntity()
                    saleBatchDao.insert(entity)
                    insertCount++
                }
                // If exists, it's our own batch - skip
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert remote sale batch ${dto.localId}", e)
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

    /**
     * Push all unsynced local expense categories to Supabase.
     * Uses upsert with local_id as conflict key to handle duplicates.
     *
     * @throws Exception if network or critical error occurs
     */
    private suspend fun pushPendingExpenseCategories(): PushResult {
        val pending = expenseCategoryDao.getUnsynced()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending expense categories to push")
            return PushResult(0, 0)
        }

        Log.d(TAG, "Pushing ${pending.size} pending expense categories")

        var successCount = 0
        var failedCount = 0

        for (entity in pending) {
            try {
                val dto = ExpenseCategoryDto.fromEntity(entity)
                syncDataSource.pushExpenseCategory(dto)
                // Mark as synced locally
                expenseCategoryDao.markSynced(entity.id, Instant.now())
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push expense category ${entity.localId}", e)
                failedCount++
            }
        }

        return PushResult(successCount, failedCount)
    }

    /**
     * Push all unsynced local cash operations to Supabase.
     * Uses upsert with local_id as conflict key to handle duplicates.
     *
     * @throws Exception if network or critical error occurs
     */
    private suspend fun pushPendingCashOperations(): PushResult {
        val pending = cashOperationDao.getUnsynced()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending cash operations to push")
            return PushResult(0, 0)
        }

        Log.d(TAG, "Pushing ${pending.size} pending cash operations")

        var successCount = 0
        var failedCount = 0

        for (entity in pending) {
            try {
                val dto = CashOperationDto.fromEntity(entity)
                syncDataSource.pushCashOperation(dto)
                // Mark as synced locally
                cashOperationDao.markSynced(entity.id, Instant.now())
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push cash operation ${entity.localId}", e)
                failedCount++
            }
        }

        return PushResult(successCount, failedCount)
    }

    /**
     * Pull new expense categories from Supabase that were created after last sync.
     * Inserts or updates local Room database.
     *
     * @param since Timestamp to filter expense categories created after
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewExpenseCategories(since: Instant): Int {
        Log.d(TAG, "Pulling expense categories created after $since")

        val remoteDtos = syncDataSource.pullExpenseCategories(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new remote expense categories")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new remote expense categories")

        var insertCount = 0
        for (dto in remoteDtos) {
            try {
                val existing = expenseCategoryDao.getByLocalId(dto.localId)
                if (existing == null) {
                    val entity = dto.toEntity()
                    expenseCategoryDao.insert(entity)
                    insertCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert remote expense category ${dto.localId}", e)
            }
        }

        return insertCount
    }

    /**
     * Pull new cash operations from Supabase that were created after last sync.
     * Inserts or updates local Room database.
     *
     * @param since Timestamp to filter cash operations created after
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewCashOperations(since: Instant): Int {
        Log.d(TAG, "Pulling cash operations created after $since")

        val remoteDtos = syncDataSource.pullCashOperations(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new remote cash operations")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new remote cash operations")

        var insertCount = 0
        for (dto in remoteDtos) {
            try {
                val existing = cashOperationDao.getByLocalId(dto.localId)
                if (existing == null) {
                    val entity = dto.toEntity()
                    cashOperationDao.insert(entity)
                    insertCount++
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert remote cash operation ${dto.localId}", e)
            }
        }

        return insertCount
    }
}
