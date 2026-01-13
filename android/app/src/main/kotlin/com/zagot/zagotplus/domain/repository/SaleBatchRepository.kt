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
     * Get transactions belonging to a specific batch.
     */
    suspend fun getTransactionsForBatch(batchId: UUID): List<Transaction>

    /**
     * Get paginated batches ordered by creation date (newest first).
     */
    suspend fun getAllBatchesPaginated(limit: Int, offset: Int): List<SaleBatch>

    /**
     * Get total count of batches.
     */
    suspend fun getTotalBatchCount(): Int

    /**
     * Observe total count of batches (reactive).
     */
    fun observeTotalBatchCount(): Flow<Int>
}
