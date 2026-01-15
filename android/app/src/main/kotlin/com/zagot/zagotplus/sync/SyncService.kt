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
        
        // Track non-fatal warnings for reference data pull failures
        val warnings = mutableListOf<String>()
        
        // Track max server_updated_at from all pulled records
        val maxServerUpdatedAt = MaxTimestampTracker()

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
        val refDataWarnings = pullReferenceData()
        warnings.addAll(refDataWarnings)

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
            pullNewExpenseCategories(syncStartTimestamp, maxServerUpdatedAt)
        } catch (e: Exception) {
            Log.e(TAG, "Expense category pull failed after successful push", e)
            0
        }
        Log.d(TAG, "Pulled $categoryPullResult expense categories")

        // Step 8: Pull new batches
        val batchPullResult = try {
            pullNewBatches(syncStartTimestamp, maxServerUpdatedAt)
        } catch (e: Exception) {
            Log.e(TAG, "Batch pull failed after successful push", e)
            // Continue to transaction pull
            0
        }
        Log.d(TAG, "Pulled $batchPullResult batches")

        // Step 8b: Pull new sale batches
        val saleBatchPullResult = try {
            pullNewSaleBatches(syncStartTimestamp, maxServerUpdatedAt)
        } catch (e: Exception) {
            Log.e(TAG, "Sale batch pull failed after successful push", e)
            0
        }
        Log.d(TAG, "Pulled $saleBatchPullResult sale batches")

        // Step 9: Pull new transactions
        val pullResult = try {
            pullNewTransactions(syncStartTimestamp, maxServerUpdatedAt)
        } catch (e: Exception) {
            Log.e(TAG, "Pull failed after successful push", e)
            // Push succeeded but pull failed - return Partial
            return SyncResult.Partial(
                pushed = pushResult.successCount + batchPushResult.successCount + saleBatchPushResult.successCount + productPushResult.successCount + categoryPushResult.successCount + cashPushResult.successCount,
                pullError = e.message ?: "Pull failed",
                warnings = warnings
            )
        }
        Log.d(TAG, "Pulled $pullResult transactions")

        // Step 10: Pull new cash operations
        val cashPullResult = try {
            pullNewCashOperations(syncStartTimestamp, maxServerUpdatedAt)
        } catch (e: Exception) {
            Log.e(TAG, "Cash operations pull failed after successful push", e)
            0
        }
        Log.d(TAG, "Pulled $cashPullResult cash operations")

        // Update last sync timestamp using the maximum server_updated_at from pulled records.
        // We store the exact max timestamp (no subtraction needed) because:
        // 1. Supabase uses strict `gt` filter, so same-timestamp records will be re-pulled
        // 2. Before inserting, we check if local_id already exists (deduplication)
        // 3. OnConflictStrategy.REPLACE handles any edge cases safely
        // This approach trades a small amount of redundant re-pulls for correctness.
        //
        // IMPORTANT: Only update the timestamp if we actually pulled records with server_updated_at.
        // Using Instant.now() as a fallback is unsafe because device clocks may be skewed,
        // which could cause missed records if the device clock is ahead of the server.
        val maxTimestamp = maxServerUpdatedAt.getMaxTimestamp()
        if (maxTimestamp != null) {
            syncPreferences.setLastSyncTimestamp(maxTimestamp)
            Log.d(TAG, "Updated last sync timestamp to $maxTimestamp")
        } else {
            Log.d(TAG, "No new records pulled, keeping existing sync timestamp")
        }

        return SyncResult.Success(
            pushed = pushResult.successCount + batchPushResult.successCount + saleBatchPushResult.successCount + productPushResult.successCount + categoryPushResult.successCount + cashPushResult.successCount,
            pulled = pullResult + batchPullResult + saleBatchPullResult + categoryPullResult + cashPullResult,
            warnings = warnings
        )
    }
    
    /**
     * Helper class to track the maximum server_updated_at timestamp across all pulled records.
     */
    private class MaxTimestampTracker {
        private var maxTimestamp: Instant? = null
        
        fun update(timestamp: String?) {
            if (timestamp == null) return
            try {
                val instant = Instant.parse(timestamp)
                val current = maxTimestamp
                if (current == null || instant.isAfter(current)) {
                    maxTimestamp = instant
                }
            } catch (e: Exception) {
                Log.w(TAG, "Failed to parse server_updated_at: $timestamp", e)
            }
        }
        
        fun getMaxTimestamp(): Instant? = maxTimestamp
    }

    /**
     * Result of push operation tracking both successes and failures.
     */
    private data class PushResult(val successCount: Int, val failedCount: Int)

    /**
     * Push all unsynced local transactions to Supabase.
     * Uses upsert with local_id as conflict key to handle duplicates.
     *
     * Atomicity note: Each transaction is pushed and marked synced individually.
     * This is intentional - network calls can fail independently, and the upsert
     * on Supabase (onConflict="local_id") ensures idempotency if a transaction
     * is pushed again after app crash. This is an eventually-consistent design.
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
     * Pull new transactions from Supabase that were updated after last sync.
     * Uses batch insert for new records and individual updates for batch ID fixes.
     *
     * Uses server_updated_at filter which is set by a Postgres trigger when the
     * server receives the record. This ensures no gaps even if devices sync at
     * different times - we always query based on when the server received data,
     * not when the client created it.
     *
     * @param since Timestamp to filter transactions updated after
     * @param timestampTracker Tracker to record max server_updated_at for next sync
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewTransactions(since: Instant, timestampTracker: MaxTimestampTracker): Int {
        Log.d(TAG, "Pulling transactions created after $since")

        val remoteDtos = syncDataSource.pullTransactions(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new remote transactions")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new remote transactions")

        // Track max timestamp from all pulled records
        remoteDtos.forEach { timestampTracker.update(it.serverUpdatedAt) }

        // Get existing local_ids in one query for efficient deduplication
        val existingLocalIds = transactionDao.getAllLocalIds().toSet()
        
        // Separate new records from existing ones
        val (existingDtos, newDtos) = remoteDtos.partition { it.localId in existingLocalIds }
        
        // Batch insert all new records
        if (newDtos.isNotEmpty()) {
            val newEntities = newDtos.map { it.toEntity() }
            transactionDao.insertAll(newEntities)
            Log.d(TAG, "Inserted ${newEntities.size} new transactions")
        }

        // Handle batch ID updates for existing transactions
        // This is rare (only for v1 migrations or out-of-order sync) so individual updates are acceptable
        for (dto in existingDtos) {
            try {
                val existing = transactionDao.getByLocalId(dto.localId) ?: continue
                
                val remoteBatchId = dto.batchId?.let { java.util.UUID.fromString(it) }
                val remoteSaleBatchId = dto.saleBatchId?.let { java.util.UUID.fromString(it) }
                
                val needsBatchUpdate = (existing.batchId == null && remoteBatchId != null) ||
                                      (existing.saleBatchId == null && remoteSaleBatchId != null)
                
                if (needsBatchUpdate) {
                    transactionDao.updateBatchIds(
                        localId = dto.localId,
                        batchId = remoteBatchId ?: existing.batchId,
                        saleBatchId = remoteSaleBatchId ?: existing.saleBatchId
                    )
                    Log.d(TAG, "Updated batch IDs for transaction ${dto.localId}")
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to update batch IDs for transaction ${dto.localId}", e)
            }
        }

        return newDtos.size
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
     * Uses batch insert for efficiency after filtering out existing records.
     *
     * @param since Timestamp to filter batches created after
     * @param timestampTracker Tracker to record max server_updated_at for next sync
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewBatches(since: Instant, timestampTracker: MaxTimestampTracker): Int {
        Log.d(TAG, "Pulling batches created after $since")

        val remoteDtos = syncDataSource.pullBatches(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new remote batches")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new remote batches")

        // Track max timestamp from all pulled records
        remoteDtos.forEach { timestampTracker.update(it.serverUpdatedAt) }

        // Get existing local_ids in one query for efficient deduplication
        val existingLocalIds = purchaseBatchDao.getAllLocalIds().toSet()
        
        // Filter to only new records and convert to entities
        val newEntities = remoteDtos
            .filter { it.localId !in existingLocalIds }
            .map { it.toEntity() }

        if (newEntities.isEmpty()) {
            Log.d(TAG, "All batches already exist locally")
            return 0
        }

        // Batch insert all new records
        purchaseBatchDao.insertAll(newEntities)
        Log.d(TAG, "Inserted ${newEntities.size} new batches")

        return newEntities.size
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
     * Uses batch insert for efficiency after filtering out existing records.
     *
     * @param since Timestamp to filter sale batches created after
     * @param timestampTracker Tracker to record max server_updated_at for next sync
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewSaleBatches(since: Instant, timestampTracker: MaxTimestampTracker): Int {
        Log.d(TAG, "Pulling sale batches created after $since")

        val remoteDtos = syncDataSource.pullSaleBatches(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new remote sale batches")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new remote sale batches")

        // Track max timestamp from all pulled records
        remoteDtos.forEach { timestampTracker.update(it.serverUpdatedAt) }

        // Get existing local_ids in one query for efficient deduplication
        val existingLocalIds = saleBatchDao.getAllLocalIds().toSet()
        
        // Filter to only new records and convert to entities
        val newEntities = remoteDtos
            .filter { it.localId !in existingLocalIds }
            .map { it.toEntity() }

        if (newEntities.isEmpty()) {
            Log.d(TAG, "All sale batches already exist locally")
            return 0
        }

        // Batch insert all new records
        saleBatchDao.insertAll(newEntities)
        Log.d(TAG, "Inserted ${newEntities.size} new sale batches")

        return newEntities.size
    }

    /**
     * Pull reference data (locations and products) from Supabase.
     * These are master data managed on server, pulled to local DB.
     * Uses upsert logic to handle existing records with child FK references.
     * Each type is pulled independently so one failure doesn't block the other.
     * 
     * @return List of warning messages for failed pulls (empty if all succeeded)
     */
    private suspend fun pullReferenceData(): List<String> {
        val warnings = mutableListOf<String>()
        
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
            warnings.add("Failed to update locations: ${e.message}")
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
            warnings.add("Failed to update products: ${e.message}")
        }
        
        return warnings
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
     * Uses batch insert for efficiency after filtering out existing records.
     *
     * @param since Timestamp to filter expense categories created after
     * @param timestampTracker Tracker to record max server_updated_at for next sync
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewExpenseCategories(since: Instant, timestampTracker: MaxTimestampTracker): Int {
        Log.d(TAG, "Pulling expense categories created after $since")

        val remoteDtos = syncDataSource.pullExpenseCategories(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new remote expense categories")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new remote expense categories")

        // Track max timestamp from all pulled records
        remoteDtos.forEach { timestampTracker.update(it.serverUpdatedAt) }

        // Get existing local_ids in one query for efficient deduplication
        val existingLocalIds = expenseCategoryDao.getAllLocalIds().toSet()
        
        // Filter to only new records and convert to entities
        val newEntities = remoteDtos
            .filter { it.localId !in existingLocalIds }
            .map { it.toEntity() }

        if (newEntities.isEmpty()) {
            Log.d(TAG, "All expense categories already exist locally")
            return 0
        }

        // Batch insert all new records
        expenseCategoryDao.insertAll(newEntities)
        Log.d(TAG, "Inserted ${newEntities.size} new expense categories")

        return newEntities.size
    }

    /**
     * Pull new cash operations from Supabase that were created after last sync.
     * Uses batch insert for efficiency after filtering out existing records.
     *
     * @param since Timestamp to filter cash operations created after
     * @param timestampTracker Tracker to record max server_updated_at for next sync
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewCashOperations(since: Instant, timestampTracker: MaxTimestampTracker): Int {
        Log.d(TAG, "Pulling cash operations created after $since")

        val remoteDtos = syncDataSource.pullCashOperations(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new remote cash operations")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new remote cash operations")

        // Track max timestamp from all pulled records
        remoteDtos.forEach { timestampTracker.update(it.serverUpdatedAt) }

        // Get existing local_ids in one query for efficient deduplication
        val existingLocalIds = cashOperationDao.getAllLocalIds().toSet()
        
        // Filter to only new records and convert to entities
        val newEntities = remoteDtos
            .filter { it.localId !in existingLocalIds }
            .map { it.toEntity() }

        if (newEntities.isEmpty()) {
            Log.d(TAG, "All cash operations already exist locally")
            return 0
        }

        // Batch insert all new records
        cashOperationDao.insertAll(newEntities)
        Log.d(TAG, "Inserted ${newEntities.size} new cash operations")

        return newEntities.size
    }
}
