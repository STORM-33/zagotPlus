package com.zagot.syncengine.db

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

    /** Find pending outbox entries whose record_id is in the given list. */
    @Query(
        """
        SELECT * FROM sync_outbox
        WHERE synced = 0 AND table_name = :tableName AND record_id IN (:recordIds)
        """
    )
    suspend fun findPendingForRecords(tableName: String, recordIds: List<String>): List<SyncOutboxEntity>

    /** Check if a pending DELETE exists for a specific record. */
    @Query(
        """
        SELECT COUNT(*) > 0 FROM sync_outbox
        WHERE synced = 0 AND table_name = :tableName AND record_id = :recordId AND operation = 'DELETE'
        """
    )
    suspend fun hasPendingDelete(tableName: String, recordId: String): Boolean

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

    /** Mark entries as failed with a reason. */
    @Query("UPDATE sync_outbox SET synced = 2, fail_reason = :reason WHERE id IN (:ids)")
    suspend fun markFailedBatch(ids: List<Long>, reason: String)

    /** Get all failed entries. */
    @Query("SELECT * FROM sync_outbox WHERE synced = 2 ORDER BY created_at ASC")
    suspend fun getFailed(): List<SyncOutboxEntity>

    /** Get failed entry count. */
    @Query("SELECT COUNT(*) FROM sync_outbox WHERE synced = 2")
    suspend fun countFailed(): Int

    /** Retry a failed entry (move back to pending). */
    @Query("UPDATE sync_outbox SET synced = 0, fail_reason = NULL WHERE id = :id AND synced = 2")
    suspend fun retryFailed(id: Long)

    /** Retry all failed entries for a table. */
    @Query("UPDATE sync_outbox SET synced = 0, fail_reason = NULL WHERE synced = 2 AND table_name = :tableName")
    suspend fun retryAllFailed(tableName: String)

    /** Discard (delete) a failed entry permanently. */
    @Query("DELETE FROM sync_outbox WHERE id = :id AND synced = 2")
    suspend fun discardFailed(id: Long)

    /** Discard (delete) all failed entries for a table. */
    @Query("DELETE FROM sync_outbox WHERE synced = 2 AND table_name = :tableName")
    suspend fun discardAllFailed(tableName: String)

    /** Count pending entries per table. */
    @Query("SELECT table_name, COUNT(*) as cnt FROM sync_outbox WHERE synced = 0 GROUP BY table_name")
    suspend fun countPendingByTable(): List<TablePendingCount>

    /** Count failed entries per table. */
    @Query("SELECT table_name, COUNT(*) as cnt FROM sync_outbox WHERE synced = 2 GROUP BY table_name")
    suspend fun countFailedByTable(): List<TablePendingCount>
}
