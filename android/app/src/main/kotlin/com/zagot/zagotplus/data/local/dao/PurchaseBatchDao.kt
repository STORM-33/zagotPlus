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

    @Query("""
        SELECT * FROM purchase_batches 
        WHERE date(created_at / 1000, 'unixepoch', 'localtime') = date('now', 'localtime')
        ORDER BY created_at DESC
    """)
    fun observeTodaysBatches(): Flow<List<PurchaseBatchEntity>>

    @Query("""
        SELECT * FROM purchase_batches 
        WHERE date(created_at / 1000, 'unixepoch', 'localtime') = date('now', 'localtime')
        ORDER BY created_at DESC
    """)
    suspend fun getTodaysBatches(): List<PurchaseBatchEntity>

    @Query("SELECT * FROM purchase_batches WHERE synced_at IS NULL")
    suspend fun getUnsynced(): List<PurchaseBatchEntity>

    @Query("UPDATE purchase_batches SET synced_at = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: UUID, syncedAt: Instant)

    @Query("DELETE FROM purchase_batches WHERE id = :id")
    suspend fun delete(id: UUID)
}
