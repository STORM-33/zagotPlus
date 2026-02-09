package com.zagot.syncengine.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Outbox entry for change tracking (spec Section 4).
 * Every local write creates an outbox entry; consumed during CATCHING_UP or LIVE push.
 */
@Entity(
    tableName = "sync_outbox",
    indices = [
        androidx.room.Index(value = ["table_name", "record_id"]),
        androidx.room.Index(value = ["synced"]),
        androidx.room.Index(value = ["created_at"]),
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "table_name")
    val tableName: String,

    /** PK of the changed record (UUID). */
    @ColumnInfo(name = "record_id")
    val recordId: String,

    /** INSERT, UPDATE, or DELETE. */
    @ColumnInfo(name = "operation")
    val operation: String,

    /** JSON snapshot of the record at time of change. */
    @ColumnInfo(name = "payload")
    val payload: String,

    /** Epoch millis when the change was recorded. */
    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** 0 = pending, 1 = synced, 2 = failed. */
    @ColumnInfo(name = "synced")
    val synced: Int = 0,

    /** Reason for failure (only set when synced = 2). */
    @ColumnInfo(name = "fail_reason", defaultValue = "NULL")
    val failReason: String? = null,

    /** Number of push attempts (for retry tracking). */
    @ColumnInfo(name = "push_attempts", defaultValue = "0")
    val pushAttempts: Int = 0,
)
