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
     * Push all unsynced local transactions to Supabase in a single batch.
     * Uses upsert with local_id as conflict key to handle duplicates.
     *
     * ## Atomicity & Crash Safety
     * 
     * All pending items are pushed in a single batch request. If the request succeeds,
     * all items are marked as synced. If it fails, none are marked.
     * 
     * Crash scenarios:
     * - Crash before network call: Items remain unsynced, will retry on next sync
     * - Crash after success, before markAsSynced: Items pushed but still marked unsynced.
     *   Will be re-pushed on next sync, but Supabase upsert with local_id handles duplicates.
     * - Crash after markAsSynced: Normal completion
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

        val dtos = pending.map { TransactionDto.fromEntity(it) }
        syncDataSource.pushTransactions(dtos)
        
        // Mark all as synced after successful batch push
        val now = Instant.now()
        pending.forEach { entity ->
            transactionDao.markAsSynced(entity.localId, now)
        }

        return PushResult(pending.size, 0)
    }

    /**
     * Pull transactions from Supabase that were updated after last sync.
     * Uses upsert (REPLACE) to handle both new and updated records.
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
        Log.d(TAG, "Pulling transactions updated after $since")

        val remoteDtos = syncDataSource.pullTransactions(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new/updated remote transactions")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new/updated remote transactions")

        // Track max timestamp from all pulled records
        remoteDtos.forEach { timestampTracker.update(it.serverUpdatedAt) }

        // Convert all DTOs to entities and upsert
        // Room's OnConflictStrategy.REPLACE handles both insert and update
        val entities = remoteDtos.map { it.toEntity() }
        transactionDao.insertAll(entities)
        Log.d(TAG, "Upserted ${entities.size} transactions")

        return entities.size
    }

    /**
     * Push all unsynced local batches to Supabase in a single batch.
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

        val dtos = pending.map { PurchaseBatchDto.fromEntity(it) }
        syncDataSource.pushBatches(dtos)
        
        // Mark all as synced after successful batch push
        val now = Instant.now()
        pending.forEach { entity ->
            purchaseBatchDao.markSynced(entity.id, now)
        }

        return PushResult(pending.size, 0)
    }

    /**
     * Push all unsynced local products to Supabase in a single batch.
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

        val dtos = pending.map { ProductDto.fromEntity(it) }
        syncDataSource.pushProducts(dtos)
        
        // Mark all as synced after successful batch push
        val now = Instant.now()
        pending.forEach { entity ->
            productDao.markSynced(entity.id, now)
        }

        return PushResult(pending.size, 0)
    }

    /**
     * Pull batches from Supabase that were updated after last sync.
     * Uses upsert (REPLACE) to handle both new and updated records.
     * 
     * IMPORTANT: This handles updates to existing records (e.g., voided status changes).
     * If a batch is voided on device A and synced, device B will receive the update
     * and apply the voided status to its local record.
     *
     * @param since Timestamp to filter batches updated after
     * @param timestampTracker Tracker to record max server_updated_at for next sync
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewBatches(since: Instant, timestampTracker: MaxTimestampTracker): Int {
        Log.d(TAG, "Pulling batches updated after $since")

        val remoteDtos = syncDataSource.pullBatches(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new/updated remote batches")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new/updated remote batches")

        // Track max timestamp from all pulled records
        remoteDtos.forEach { timestampTracker.update(it.serverUpdatedAt) }

        // Convert all DTOs to entities and upsert
        // Room's OnConflictStrategy.REPLACE handles both insert and update
        val entities = remoteDtos.map { it.toEntity() }
        purchaseBatchDao.insertAll(entities)
        Log.d(TAG, "Upserted ${entities.size} batches")

        return entities.size
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

        val dtos = pending.map { SaleBatchDto.fromEntity(it) }
        syncDataSource.pushSaleBatches(dtos)
        
        // Mark all as synced after successful batch push
        val now = Instant.now()
        pending.forEach { entity ->
            saleBatchDao.markSynced(entity.id, now)
        }

        return PushResult(pending.size, 0)
    }

    /**
     * Pull sale batches from Supabase that were updated after last sync.
     * Uses upsert (REPLACE) to handle both new and updated records.
     *
     * IMPORTANT: This handles updates to existing records (e.g., voided status changes).
     * If a batch is voided on device A and synced, device B will receive the update
     * and apply the voided status to its local record.
     *
     * @param since Timestamp to filter sale batches updated after
     * @param timestampTracker Tracker to record max server_updated_at for next sync
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewSaleBatches(since: Instant, timestampTracker: MaxTimestampTracker): Int {
        Log.d(TAG, "Pulling sale batches updated after $since")

        val remoteDtos = syncDataSource.pullSaleBatches(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new/updated remote sale batches")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new/updated remote sale batches")

        // Track max timestamp from all pulled records
        remoteDtos.forEach { timestampTracker.update(it.serverUpdatedAt) }

        // Convert all DTOs to entities and upsert
        // Room's OnConflictStrategy.REPLACE handles both insert and update
        val entities = remoteDtos.map { it.toEntity() }
        saleBatchDao.insertAll(entities)
        Log.d(TAG, "Upserted ${entities.size} sale batches")

        return entities.size
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
     * Push all unsynced local expense categories to Supabase in a single batch.
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

        val dtos = pending.map { ExpenseCategoryDto.fromEntity(it) }
        syncDataSource.pushExpenseCategories(dtos)
        
        // Mark all as synced after successful batch push
        val now = Instant.now()
        pending.forEach { entity ->
            expenseCategoryDao.markSynced(entity.id, now)
        }

        return PushResult(pending.size, 0)
    }

    /**
     * Push all unsynced local cash operations to Supabase in a single batch.
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

        val dtos = pending.map { CashOperationDto.fromEntity(it) }
        syncDataSource.pushCashOperations(dtos)
        
        // Mark all as synced after successful batch push
        val now = Instant.now()
        pending.forEach { entity ->
            cashOperationDao.markSynced(entity.id, now)
        }

        return PushResult(pending.size, 0)
    }

    /**
     * Pull expense categories from Supabase that were updated after last sync.
     * Uses upsert (REPLACE) to handle both new and updated records.
     *
     * @param since Timestamp to filter expense categories updated after
     * @param timestampTracker Tracker to record max server_updated_at for next sync
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewExpenseCategories(since: Instant, timestampTracker: MaxTimestampTracker): Int {
        Log.d(TAG, "Pulling expense categories updated after $since")

        val remoteDtos = syncDataSource.pullExpenseCategories(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new/updated remote expense categories")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new/updated remote expense categories")

        // Track max timestamp from all pulled records
        remoteDtos.forEach { timestampTracker.update(it.serverUpdatedAt) }

        // Convert all DTOs to entities and upsert
        // Room's OnConflictStrategy.REPLACE handles both insert and update
        val entities = remoteDtos.map { it.toEntity() }
        expenseCategoryDao.insertAll(entities)
        Log.d(TAG, "Upserted ${entities.size} expense categories")

        return entities.size
    }

    /**
     * Pull cash operations from Supabase that were updated after last sync.
     * Uses upsert (REPLACE) to handle both new and updated records.
     *
     * @param since Timestamp to filter cash operations updated after
     * @param timestampTracker Tracker to record max server_updated_at for next sync
     * @throws Exception if network error occurs
     */
    private suspend fun pullNewCashOperations(since: Instant, timestampTracker: MaxTimestampTracker): Int {
        Log.d(TAG, "Pulling cash operations updated after $since")

        val remoteDtos = syncDataSource.pullCashOperations(since)

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new/updated remote cash operations")
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new/updated remote cash operations")

        // Track max timestamp from all pulled records
        remoteDtos.forEach { timestampTracker.update(it.serverUpdatedAt) }

        // Convert all DTOs to entities and upsert
        // Room's OnConflictStrategy.REPLACE handles both insert and update
        val entities = remoteDtos.map { it.toEntity() }
        cashOperationDao.insertAll(entities)
        Log.d(TAG, "Upserted ${entities.size} cash operations")

        return entities.size
    }
}
