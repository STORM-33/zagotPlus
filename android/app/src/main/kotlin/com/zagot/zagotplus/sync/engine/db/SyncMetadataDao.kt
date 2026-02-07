package com.zagot.zagotplus.sync.engine.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

/**
 * DAO for sync metadata (last_synced_at per table).
 */
@Dao
interface SyncMetadataDao {

    @Query("SELECT * FROM sync_metadata WHERE table_name = :tableName")
    suspend fun get(tableName: String): SyncMetadataEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: SyncMetadataEntity)

    @Query("SELECT last_synced_at FROM sync_metadata WHERE table_name = :tableName")
    suspend fun getLastSyncedAt(tableName: String): Long?

    @Query("UPDATE sync_metadata SET last_synced_at = :lastSyncedAt WHERE table_name = :tableName")
    suspend fun updateLastSyncedAt(tableName: String, lastSyncedAt: Long)

    @Query("SELECT * FROM sync_metadata")
    suspend fun getAll(): List<SyncMetadataEntity>
}
