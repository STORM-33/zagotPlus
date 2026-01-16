package com.zagot.zagotplus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.RawQuery
import androidx.room.Update
import androidx.sqlite.db.SupportSQLiteQuery
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Raw result from inventory aggregation query.
 * Used internally by DAO; converted to InventoryItem in repository.
 */
data class InventoryAggregateResult(
    val locationId: String,
    val productId: String,
    val totalWeightKg: String
)

/**
 * Raw result from daily product purchase totals query.
 * Used internally by DAO; converted to ProductDailyTotal in repository.
 */
data class ProductDailyTotalResult(
    val productId: String,
    val totalWeightKg: String,
    val totalAmount: String
)

/**
 * Data Access Object for transactions table.
 * Provides CRUD operations, sync queries, and reactive queries via Flow.
 */
@Dao
interface TransactionDao {

    /**
     * Insert a new transaction. Replaces on conflict based on local_id (for sync upsert).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    /**
     * Insert multiple transactions (for sync pull).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(transactions: List<TransactionEntity>)

    /**
     * Update existing transaction (primarily for marking as synced).
     */
    @Update
    suspend fun update(transaction: TransactionEntity)



    /**
     * Get paginated transactions as Flow (memory-efficient for UI).
     * @param limit Number of transactions to load per page
     * @param offset Number of transactions to skip
     */
    @Query("SELECT * FROM transactions ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    fun getAllPaginatedFlow(limit: Int, offset: Int): Flow<List<TransactionEntity>>



    /**
     * Get paginated transactions (one-time read).
     * @param limit Number of transactions to load per page
     * @param offset Number of transactions to skip
     */
    @Query("SELECT * FROM transactions ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getAllPaginated(limit: Int, offset: Int): List<TransactionEntity>

    /**
     * Get total transaction count (for pagination UI).
     */
    @Query("SELECT COUNT(*) FROM transactions")
    fun getTotalCountFlow(): Flow<Int>

    /**
     * Get total transaction count (one-time read).
     */
    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun getTotalCount(): Int

    /**
     * Get transaction by ID.
     */
    @Query("SELECT * FROM transactions WHERE id = :id")
    suspend fun getById(id: UUID): TransactionEntity?

    /**
     * Get transaction by local_id (for conflict resolution).
     */
    @Query("SELECT * FROM transactions WHERE local_id = :localId")
    suspend fun getByLocalId(localId: String): TransactionEntity?

    /**
     * Get all existing local_ids for efficient batch deduplication during sync.
     */
    @Query("SELECT local_id FROM transactions")
    suspend fun getAllLocalIds(): List<String>

    /**
     * Get transactions by location as Flow.
     */
    @Query("SELECT * FROM transactions WHERE location_id = :locationId ORDER BY created_at DESC")
    fun getByLocationFlow(locationId: UUID): Flow<List<TransactionEntity>>

    /**
     * Get transactions by product as Flow.
     */
    @Query("SELECT * FROM transactions WHERE product_id = :productId ORDER BY created_at DESC")
    fun getByProductFlow(productId: UUID): Flow<List<TransactionEntity>>

    /**
     * Get unsynced transactions (synced_at IS NULL) for sync push.
     */
    @Query("SELECT * FROM transactions WHERE synced_at IS NULL ORDER BY created_at ASC")
    suspend fun getUnsynced(): List<TransactionEntity>

    /**
     * Get unsynced transactions count (for sync status display).
     */
    @Query("SELECT COUNT(*) FROM transactions WHERE synced_at IS NULL")
    fun getUnsyncedCountFlow(): Flow<Int>

    /**
     * Get transactions created after a timestamp (for sync pull).
     */
    @Query("SELECT * FROM transactions WHERE created_at > :afterTimestamp ORDER BY created_at ASC")
    suspend fun getCreatedAfter(afterTimestamp: Instant): List<TransactionEntity>

    /**
     * Mark transaction as synced by local_id.
     */
    @Query("UPDATE transactions SET synced_at = :syncedAt WHERE local_id = :localId")
    suspend fun markAsSynced(localId: String, syncedAt: Instant)

    /**
     * Update batch IDs for an existing transaction by local_id.
     * Used during sync to link transactions to their batches when the transaction
     * was created before the batch was synced.
     */
    @Query("UPDATE transactions SET batch_id = :batchId, sale_batch_id = :saleBatchId WHERE local_id = :localId")
    suspend fun updateBatchIds(localId: String, batchId: UUID?, saleBatchId: UUID?)

    /**
     * Delete all transactions (for testing/reset).
     */
    @Query("DELETE FROM transactions")
    suspend fun deleteAll()

    /**
     * Get transactions belonging to a specific batch.
     */
    @Query("SELECT * FROM transactions WHERE batch_id = :batchId ORDER BY created_at DESC")
    suspend fun getByBatchId(batchId: UUID): List<TransactionEntity>

    /**
     * Get transactions belonging to a specific sale batch.
     */
    @Query("SELECT * FROM transactions WHERE sale_batch_id = :saleBatchId ORDER BY created_at DESC")
    suspend fun getBySaleBatchId(saleBatchId: UUID): List<TransactionEntity>

    /**
     * Get transactions by their IDs.
     */
    @Query("SELECT * FROM transactions WHERE id IN (:ids)")
    suspend fun getByIds(ids: List<UUID>): List<TransactionEntity>

    /**
     * Get transactions for multiple purchase batch IDs in a single query.
     * Avoids N+1 query pattern when loading batch transactions.
     */
    @Query("SELECT * FROM transactions WHERE batch_id IN (:batchIds) ORDER BY created_at DESC")
    suspend fun getByPurchaseBatchIds(batchIds: List<UUID>): List<TransactionEntity>

    /**
     * Get transactions for multiple sale batch IDs in a single query.
     * Avoids N+1 query pattern when loading batch transactions.
     */
    @Query("SELECT * FROM transactions WHERE sale_batch_id IN (:saleBatchIds) ORDER BY created_at DESC")
    suspend fun getBySaleBatchIds(saleBatchIds: List<UUID>): List<TransactionEntity>

    /**
     * Get filtered transactions with dynamic query.
     * Use TransactionQueryBuilder to construct the query.
     */
    @RawQuery
    suspend fun getFiltered(query: SupportSQLiteQuery): List<TransactionEntity>

    /**
     * Get filtered transaction count with dynamic query.
     * Use TransactionQueryBuilder to construct the count query.
     */
    @RawQuery
    suspend fun getFilteredCount(query: SupportSQLiteQuery): Int

    /**
     * Get aggregated inventory using SQL SUM.
     * Memory-efficient: doesn't load all transactions into memory.
     * Excludes transactions from voided batches.
     */
    @Query("""
        SELECT t.location_id AS locationId, t.product_id AS productId, SUM(t.weight_kg) AS totalWeightKg
        FROM transactions t
        LEFT JOIN purchase_batches pb ON t.batch_id = pb.id
        LEFT JOIN sale_batches sb ON t.sale_batch_id = sb.id
        WHERE t.location_id IS NOT NULL 
          AND t.product_id IS NOT NULL
          AND (t.batch_id IS NULL OR pb.is_voided = 0)
          AND (t.sale_batch_id IS NULL OR sb.is_voided = 0)
        GROUP BY t.location_id, t.product_id
    """)
    fun getInventoryAggregatedFlow(): Flow<List<InventoryAggregateResult>>

    /**
     * Get aggregated inventory for a specific location using SQL SUM.
     * Memory-efficient: doesn't load all transactions into memory.
     * Excludes transactions from voided batches.
     */
    @Query("""
        SELECT t.location_id AS locationId, t.product_id AS productId, SUM(t.weight_kg) AS totalWeightKg
        FROM transactions t
        LEFT JOIN purchase_batches pb ON t.batch_id = pb.id
        LEFT JOIN sale_batches sb ON t.sale_batch_id = sb.id
        WHERE t.location_id = :locationId 
          AND t.product_id IS NOT NULL
          AND (t.batch_id IS NULL OR pb.is_voided = 0)
          AND (t.sale_batch_id IS NULL OR sb.is_voided = 0)
        GROUP BY t.location_id, t.product_id
    """)
    fun getInventoryByLocationAggregatedFlow(locationId: UUID): Flow<List<InventoryAggregateResult>>

    /**
     * Get today's purchase totals grouped by product.
     * Excludes transactions from voided batches.
     */
    @Query("""
        SELECT t.product_id AS productId, 
               SUM(t.weight_kg) AS totalWeightKg,
               SUM(t.total_amount) AS totalAmount
        FROM transactions t
        LEFT JOIN purchase_batches pb ON t.batch_id = pb.id
        WHERE t.type = 'purchase'
          AND t.product_id IS NOT NULL
          AND t.created_at >= :startMillis AND t.created_at < :endMillis
          AND (t.batch_id IS NULL OR pb.is_voided = 0)
        GROUP BY t.product_id
    """)
    fun observeTodaysPurchaseTotals(startMillis: Long, endMillis: Long): Flow<List<ProductDailyTotalResult>>
}
