package com.zagot.zagotplus.domain.repository

import com.zagot.zagotplus.domain.model.SaleBatch
import com.zagot.zagotplus.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository interface for sale batch operations.
 */
interface SaleBatchRepository {

    /**
     * Observe all batches ordered by creation date (newest first).
     */
    fun observeAll(): Flow<List<SaleBatch>>

    /**
     * Observe today's batches ordered by creation date (newest first).
     */
    fun observeTodaysBatches(): Flow<List<SaleBatch>>

    /**
     * Observe today's batches for a specific location ordered by creation date (newest first).
     */
    fun observeTodaysBatches(locationId: UUID): Flow<List<SaleBatch>>

    /**
     * Get today's batches.
     */
    suspend fun getTodaysBatches(): List<SaleBatch>

    /**
     * Get a batch by its ID.
     */
    suspend fun getById(id: UUID): SaleBatch?

    /**
     * Get a batch by its local ID.
     */
    suspend fun getByLocalId(localId: String): SaleBatch?

    /**
     * Create a new batch with its transactions atomically.
     * @param batch The batch to create
     * @param transactions The transactions belonging to this batch
     */
    suspend fun createBatchWithTransactions(
        batch: SaleBatch,
        transactions: List<Transaction>
    )

    /**
     * Get unsynced batches for sync.
     */
    suspend fun getUnsynced(): List<SaleBatch>

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
     * @param includeVoided If true, includes voided batches (for "show deleted" filter)
     */
    suspend fun getAllBatchesPaginated(limit: Int, offset: Int, includeVoided: Boolean = false): List<SaleBatch>

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
        correctedBatch: SaleBatch,
        correctedTransactions: List<Transaction>,
        reason: String
    ): SaleBatch
}
