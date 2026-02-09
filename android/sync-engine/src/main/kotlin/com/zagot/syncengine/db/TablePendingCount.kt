package com.zagot.syncengine.db

import androidx.room.ColumnInfo

/**
 * Room query result POJO for GROUP BY count queries on sync_outbox.
 */
data class TablePendingCount(
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "cnt") val count: Int,
)
