package com.zagot.zagotplus.domain.repository

import com.zagot.zagotplus.domain.model.ProductDailyTotal
import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository interface for purchase batch operations.
 */
interface PurchaseBatchRepository {

    /**
     * Observe all batches ordered by creation date (newest first).
     */
    fun observeAll(): Flow<List<PurchaseBatch>>

    /**
     * Observe today's batches ordered by creation date (newest first).
     */
    fun observeTodaysBatches(): Flow<List<PurchaseBatch>>

    /**
     * Observe today's batches for a specific location ordered by creation date (newest first).
     */
    fun observeTodaysBatches(locationId: UUID): Flow<List<PurchaseBatch>>

    /**
     * Observe today's purchase totals grouped by product.
     * Returns list with product name resolved.
     */
    fun observeTodaysProductTotals(): Flow<List<ProductDailyTotal>>

    /**
     * Observe today's purchase totals for a specific location grouped by product.
     * Returns list with product name resolved.
     */
    fun observeTodaysProductTotals(locationId: UUID): Flow<List<ProductDailyTotal>>

    /**
     * Get today's batches.
     */
    suspend fun getTodaysBatches(): List<PurchaseBatch>

    /**
     * Get a batch by its ID.
     */
    suspend fun getById(id: UUID): PurchaseBatch?

    /**
     * Get a batch by its local ID.
     */
    suspend fun getByLocalId(localId: String): PurchaseBatch?

    /**
     * Create a new batch with its transactions atomically.
     * @param batch The batch to create
     * @param transactions The transactions belonging to this batch
     */
    suspend fun createBatchWithTransactions(
        batch: PurchaseBatch,
        transactions: List<Transaction>
    )

    /**
     * Get unsynced batches for sync.
     */
    suspend fun getUnsynced(): List<PurchaseBatch>

    /**
     * Mark a batch as synced.
     */
    suspend fun markSynced(id: UUID)

    /**
     * Delete a batch by its ID.
     */
    suspend fun delete(id: UUID)

    /**
     * Mark a batch as voided (soft delete).
     * The batch remains in the database but is marked as voided.
     */
    suspend fun markVoided(id: UUID)

    /**
     * Get transactions belonging to a specific batch.
     */
    suspend fun getTransactionsForBatch(batchId: UUID): List<Transaction>

    /**
     * Get paginated batches ordered by creation date (newest first).
     */
    suspend fun getAllBatchesPaginated(limit: Int, offset: Int): List<PurchaseBatch>

    /**
     * Get total count of batches.
     */
    suspend fun getTotalBatchCount(): Int

    /**
     * Observe total count of batches (reactive).
     */
    fun observeTotalBatchCount(): Flow<Int>

    /**
     * Correct a batch by voiding the original and creating a new corrected batch.
     * This is an atomic operation that:
     * 1. Marks the original batch as voided (is_voided = true)
     * 2. Creates a new correction batch with corrects_batch_id pointing to original
     * 3. Creates new transactions for the correction batch
     * 
     * @param originalBatchId ID of the batch to correct
     * @param correctedBatch The new corrected batch data
     * @param correctedTransactions The new transactions for the correction batch
     * @param reason User-provided reason for the correction
     * @return The newly created correction batch
     */
    suspend fun correctBatch(
        originalBatchId: UUID,
        correctedBatch: PurchaseBatch,
        correctedTransactions: List<Transaction>,
        reason: String
    ): PurchaseBatch
}
