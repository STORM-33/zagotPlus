package com.zagot.zagotplus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
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

    /**
     * Upsert purchase batches using Room's @Upsert annotation.
     * 
     * IMPORTANT: Use this instead of insertAll for sync operations!
     * 
     * Unlike OnConflictStrategy.REPLACE which does DELETE + INSERT,
     * @Upsert does INSERT or UPDATE. This is critical because:
     * - TransactionEntity has FK to purchase_batches with onDelete = SET_NULL
     * - REPLACE triggers DELETE, which sets all transaction.batch_id to NULL
     * - This breaks inventory calculation and batch detail views
     * 
     * @Upsert performs an UPDATE when a conflict occurs, preserving FK relationships.
     */
    @Upsert
    suspend fun upsertAll(batches: List<PurchaseBatchEntity>)

    @Update
    suspend fun update(batch: PurchaseBatchEntity)

    @Query("SELECT * FROM purchase_batches WHERE id = :id")
    suspend fun getById(id: UUID): PurchaseBatchEntity?

    @Query("SELECT * FROM purchase_batches WHERE local_id = :localId")
    suspend fun getByLocalId(localId: String): PurchaseBatchEntity?

    /**
     * Get all existing local_ids for efficient batch deduplication during sync.
     */
    @Query("SELECT local_id FROM purchase_batches")
    suspend fun getAllLocalIds(): List<String>

    @Query("SELECT * FROM purchase_batches WHERE is_voided = 0 ORDER BY created_at DESC")
    fun observeAll(): Flow<List<PurchaseBatchEntity>>

    /**
     * Observe batches created within a date range (index-friendly).
     * Excludes voided batches.
     */
    @Query("""
        SELECT * FROM purchase_batches 
        WHERE created_at >= :startMillis AND created_at < :endMillis
          AND is_voided = 0
        ORDER BY created_at DESC
    """)
    fun observeBatchesInRange(startMillis: Long, endMillis: Long): Flow<List<PurchaseBatchEntity>>

    /**
     * Get batches created within a date range (index-friendly).
     * Excludes voided batches.
     */
    @Query("""
        SELECT * FROM purchase_batches 
        WHERE created_at >= :startMillis AND created_at < :endMillis
          AND is_voided = 0
        ORDER BY created_at DESC
    """)
    suspend fun getBatchesInRange(startMillis: Long, endMillis: Long): List<PurchaseBatchEntity>

    /**
     * Observe batches for a specific location within a date range (index-friendly).
     * Excludes voided batches.
     */
    @Query("""
        SELECT * FROM purchase_batches 
        WHERE created_at >= :startMillis AND created_at < :endMillis
          AND location_id = :locationId
          AND is_voided = 0
        ORDER BY created_at DESC
    """)
    fun observeBatchesInRangeByLocation(startMillis: Long, endMillis: Long, locationId: UUID): Flow<List<PurchaseBatchEntity>>

    @Query("SELECT * FROM purchase_batches WHERE synced_at IS NULL")
    suspend fun getUnsynced(): List<PurchaseBatchEntity>

    @Query("UPDATE purchase_batches SET synced_at = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: UUID, syncedAt: Instant)

    @Query("DELETE FROM purchase_batches WHERE id = :id")
    suspend fun delete(id: UUID)

    /**
     * Get paginated batches ordered by creation date (newest first).
     * Excludes voided batches.
     */
    @Query("""
        SELECT * FROM purchase_batches
        WHERE is_voided = 0
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getAllPaginated(limit: Int, offset: Int): List<PurchaseBatchEntity>

    /**
     * Get paginated batches including voided ones (for "show deleted" filter).
     * Returns all batches ordered by creation date (newest first).
     */
    @Query("""
        SELECT * FROM purchase_batches
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getAllPaginatedIncludingVoided(limit: Int, offset: Int): List<PurchaseBatchEntity>

    /**
     * Get total count of non-voided batches.
     */
    @Query("SELECT COUNT(*) FROM purchase_batches WHERE is_voided = 0")
    suspend fun getTotalCount(): Int

    /**
     * Observe total count of non-voided batches (reactive).
     */
    @Query("SELECT COUNT(*) FROM purchase_batches WHERE is_voided = 0")
    fun observeTotalCount(): Flow<Int>

    /**
     * Observe max server_updated_at to detect content changes (voids, corrections).
     * Returns null when table is empty or all values are null.
     */
    @Query("SELECT MAX(server_updated_at) FROM purchase_batches")
    fun observeLatestUpdate(): Flow<Long?>

    /**
     * Mark a batch as voided (for correction workflow).
     * Sets voided_at timestamp and voided_by_device_id for audit trail.
     * @return Number of rows affected (should be 1 if batch exists, 0 if not found)
     */
    @Query("UPDATE purchase_batches SET is_voided = 1, synced_at = NULL, voided_at = :voidedAt, voided_by_device_id = :deviceId WHERE id = :id")
    suspend fun markVoided(id: UUID, voidedAt: Long, deviceId: String): Int
    
    /**
     * Mark a batch as voided (legacy, without audit trail).
     * @deprecated Use markVoided(id, voidedAt, deviceId) instead
     * @return Number of rows affected (should be 1 if batch exists, 0 if not found)
     */
    @Deprecated("Use markVoided(id, voidedAt, deviceId) instead for full audit trail")
    @Query("UPDATE purchase_batches SET is_voided = 1, synced_at = NULL WHERE id = :id")
    suspend fun markVoidedLegacy(id: UUID): Int

    /**
     * Get paginated non-voided batches (for inventory and history display).
     */
    @Query("""
        SELECT * FROM purchase_batches
        WHERE is_voided = 0
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getActiveAllPaginated(limit: Int, offset: Int): List<PurchaseBatchEntity>

    /**
     * Get count of non-voided batches.
     */
    @Query("SELECT COUNT(*) FROM purchase_batches WHERE is_voided = 0")
    suspend fun getActiveCount(): Int
}
