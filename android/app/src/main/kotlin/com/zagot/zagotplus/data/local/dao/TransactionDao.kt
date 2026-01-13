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
     * Get all transactions as Flow (reactive for UI).
     * WARNING: Loads all transactions into memory. Use getAllPaginatedFlow for large datasets.
     */
    @Query("SELECT * FROM transactions ORDER BY created_at DESC")
    fun getAllFlow(): Flow<List<TransactionEntity>>

    /**
     * Get paginated transactions as Flow (memory-efficient for UI).
     * @param limit Number of transactions to load per page
     * @param offset Number of transactions to skip
     */
    @Query("SELECT * FROM transactions ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    fun getAllPaginatedFlow(limit: Int, offset: Int): Flow<List<TransactionEntity>>

    /**
     * Get all transactions (one-time read).
     */
    @Query("SELECT * FROM transactions ORDER BY created_at DESC")
    suspend fun getAll(): List<TransactionEntity>

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
     */
    @Query("""
        SELECT location_id AS locationId, product_id AS productId, SUM(weight_kg) AS totalWeightKg
        FROM transactions
        WHERE location_id IS NOT NULL AND product_id IS NOT NULL
        GROUP BY location_id, product_id
    """)
    fun getInventoryAggregatedFlow(): Flow<List<InventoryAggregateResult>>

    /**
     * Get aggregated inventory for a specific location using SQL SUM.
     * Memory-efficient: doesn't load all transactions into memory.
     */
    @Query("""
        SELECT location_id AS locationId, product_id AS productId, SUM(weight_kg) AS totalWeightKg
        FROM transactions
        WHERE location_id = :locationId AND product_id IS NOT NULL
        GROUP BY location_id, product_id
    """)
    fun getInventoryByLocationAggregatedFlow(locationId: UUID): Flow<List<InventoryAggregateResult>>
}
