package com.zagot.syncengine.state

/**
 * Per-table sync status exposed via [SyncEngine.tableSyncStatus].
 * Updated after each sync cycle completion.
 */
data class TableSyncStatus(
    val tableName: String,
    /** Epoch millis of the last successfully synced record's timestamp. 0 = never synced. */
    val lastSyncedAt: Long,
    /** Number of pending outbox entries for this table. */
    val pendingCount: Int,
    /** Number of failed outbox entries for this table. */
    val failedCount: Int,
)
