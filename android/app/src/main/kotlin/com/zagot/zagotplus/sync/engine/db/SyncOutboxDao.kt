package com.zagot.zagotplus.sync.engine.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

/**
 * DAO for sync outbox (change tracker) — spec Section 4.
 */
@Dao
interface SyncOutboxDao {

    @Insert
    suspend fun insert(entry: SyncOutboxEntity): Long

    @Insert
    suspend fun insertAll(entries: List<SyncOutboxEntity>)

    /** Get all pending (unsynced) entries, FIFO by created_at. */
    @Query("SELECT * FROM sync_outbox WHERE synced = 0 ORDER BY created_at ASC")
    suspend fun getPending(): List<SyncOutboxEntity>

    /** Get pending entries for a specific table. */
    @Query("SELECT * FROM sync_outbox WHERE synced = 0 AND table_name = :tableName ORDER BY created_at ASC")
    suspend fun getPendingForTable(tableName: String): List<SyncOutboxEntity>

    /** Mark an entry as synced. */
    @Query("UPDATE sync_outbox SET synced = 1 WHERE id = :id")
    suspend fun markSynced(id: Long)

    /** Mark multiple entries as synced. */
    @Query("UPDATE sync_outbox SET synced = 1 WHERE id IN (:ids)")
    suspend fun markSyncedBatch(ids: List<Long>)

    /** Count pending entries. */
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE synced = 0")
    suspend fun countPending(): Int

    /**
     * Prune synced entries older than [olderThan] epoch millis.
     * Per spec: synced entries retained for at least one safety sync cycle.
     */
    @Query("DELETE FROM sync_outbox WHERE synced = 1 AND created_at < :olderThan")
    suspend fun pruneSynced(olderThan: Long): Int

    /** Get pending entries for a specific record (for conflict resolution). */
    @Query("SELECT * FROM sync_outbox WHERE synced = 0 AND table_name = :tableName AND record_id = :recordId ORDER BY created_at ASC")
    suspend fun getPendingForRecord(tableName: String, recordId: String): List<SyncOutboxEntity>

    /** Mark outbox entries as synced for a given record (used after conflict resolution). */
    @Query("UPDATE sync_outbox SET synced = 1 WHERE synced = 0 AND table_name = :tableName AND record_id = :recordId")
    suspend fun markSyncedForRecord(tableName: String, recordId: String)

    /** Delete all entries (for testing). */
    @Query("DELETE FROM sync_outbox")
    suspend fun deleteAll()
}
