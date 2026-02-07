package com.zagot.zagotplus.sync.engine

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Tracks last_synced_at per table for incremental pull (spec Section 5).
 */
@Entity(tableName = "sync_metadata")
data class SyncMetadataEntity(
    @PrimaryKey
    @ColumnInfo(name = "table_name")
    val tableName: String,

    /** Epoch millis of the max updated_at received in the last successful pull. */
    @ColumnInfo(name = "last_synced_at")
    val lastSyncedAt: Long = 0L,
)
