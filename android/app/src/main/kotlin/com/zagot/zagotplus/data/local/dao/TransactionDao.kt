package com.zagot.zagotplus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

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
     */
    @Query("SELECT * FROM transactions ORDER BY created_at DESC")
    fun getAllFlow(): Flow<List<TransactionEntity>>

    /**
     * Get all transactions (one-time read).
     */
    @Query("SELECT * FROM transactions ORDER BY created_at DESC")
    suspend fun getAll(): List<TransactionEntity>

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
}
