package com.zagot.zagotplus.domain.repository

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
}
