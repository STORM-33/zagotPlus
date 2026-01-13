package com.zagot.zagotplus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zagot.zagotplus.data.local.entity.PurchaseBatchEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * Room DAO for purchase_batches table.
 */
@Dao
interface PurchaseBatchDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(batch: PurchaseBatchEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(batches: List<PurchaseBatchEntity>)

    @Update
    suspend fun update(batch: PurchaseBatchEntity)

    @Query("SELECT * FROM purchase_batches WHERE id = :id")
    suspend fun getById(id: UUID): PurchaseBatchEntity?

    @Query("SELECT * FROM purchase_batches WHERE local_id = :localId")
    suspend fun getByLocalId(localId: String): PurchaseBatchEntity?

    @Query("SELECT * FROM purchase_batches ORDER BY created_at DESC")
    fun observeAll(): Flow<List<PurchaseBatchEntity>>

    /**
     * Observe batches created within a date range (index-friendly).
     */
    @Query("""
        SELECT * FROM purchase_batches 
        WHERE created_at >= :startMillis AND created_at < :endMillis
        ORDER BY created_at DESC
    """)
    fun observeBatchesInRange(startMillis: Long, endMillis: Long): Flow<List<PurchaseBatchEntity>>

    /**
     * Get batches created within a date range (index-friendly).
     */
    @Query("""
        SELECT * FROM purchase_batches 
        WHERE created_at >= :startMillis AND created_at < :endMillis
        ORDER BY created_at DESC
    """)
    suspend fun getBatchesInRange(startMillis: Long, endMillis: Long): List<PurchaseBatchEntity>

    @Query("SELECT * FROM purchase_batches WHERE synced_at IS NULL")
    suspend fun getUnsynced(): List<PurchaseBatchEntity>

    @Query("UPDATE purchase_batches SET synced_at = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: UUID, syncedAt: Instant)

    @Query("DELETE FROM purchase_batches WHERE id = :id")
    suspend fun delete(id: UUID)

    /**
     * Get paginated batches ordered by creation date (newest first).
     */
    @Query("""
        SELECT * FROM purchase_batches
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getAllPaginated(limit: Int, offset: Int): List<PurchaseBatchEntity>

    /**
     * Get total count of batches.
     */
    @Query("SELECT COUNT(*) FROM purchase_batches")
    suspend fun getTotalCount(): Int

    /**
     * Observe total count of batches (reactive).
     */
    @Query("SELECT COUNT(*) FROM purchase_batches")
    fun observeTotalCount(): Flow<Int>
}
