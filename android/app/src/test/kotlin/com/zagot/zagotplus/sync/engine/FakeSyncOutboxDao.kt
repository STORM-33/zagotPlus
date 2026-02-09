package com.zagot.zagotplus.sync.engine

import com.zagot.syncengine.db.SyncOutboxDao
import com.zagot.syncengine.db.SyncOutboxEntity
import com.zagot.syncengine.db.TablePendingCount

/**
 * Shared in-memory fake for [SyncOutboxDao] used across sync engine tests.
 */
class FakeSyncOutboxDao : SyncOutboxDao {
    private var autoId = 1L
    private val entries = mutableListOf<SyncOutboxEntity>()

    val findPendingForRecordsCallSizes = mutableListOf<Int>()

    override suspend fun insert(entry: SyncOutboxEntity): Long {
        val id = autoId++
        entries.add(entry.copy(id = id))
        return id
    }
    override suspend fun insertAll(entries: List<SyncOutboxEntity>) {
        entries.forEach { insert(it) }
    }
    override suspend fun getPending() = entries.filter { it.synced == 0 }.sortedBy { it.createdAt }
    override suspend fun getPendingForTable(tableName: String) =
        entries.filter { it.synced == 0 && it.tableName == tableName }.sortedBy { it.createdAt }

    override suspend fun findPendingForRecords(tableName: String, recordIds: List<String>): List<SyncOutboxEntity> {
        findPendingForRecordsCallSizes.add(recordIds.size)
        val recordIdSet = recordIds.toHashSet()
        return entries.filter { it.synced == 0 && it.tableName == tableName && recordIdSet.contains(it.recordId) }
    }

    override suspend fun hasPendingDelete(tableName: String, recordId: String): Boolean {
        return entries.any {
            it.synced == 0 && it.tableName == tableName && it.recordId == recordId && it.operation == "DELETE"
        }
    }

    override suspend fun markSynced(id: Long) {
        val idx = entries.indexOfFirst { it.id == id }
        if (idx >= 0) entries[idx] = entries[idx].copy(synced = 1)
    }
    override suspend fun markSyncedBatch(ids: List<Long>) {
        ids.forEach { markSynced(it) }
    }
    override suspend fun countPending() = entries.count { it.synced == 0 }
    override suspend fun pruneSynced(olderThan: Long): Int {
        val toRemove = entries.filter { it.synced == 1 && it.createdAt < olderThan }
        entries.removeAll(toRemove)
        return toRemove.size
    }
    override suspend fun getPendingForRecord(tableName: String, recordId: String) =
        entries.filter { it.synced == 0 && it.tableName == tableName && it.recordId == recordId }
    override suspend fun markSyncedForRecord(tableName: String, recordId: String) {
        entries.forEachIndexed { idx, e ->
            if (e.synced == 0 && e.tableName == tableName && e.recordId == recordId) {
                entries[idx] = e.copy(synced = 1)
            }
        }
    }
    override suspend fun deleteAll() { entries.clear() }

    override suspend fun markFailedBatch(ids: List<Long>, reason: String) {
        entries.forEachIndexed { idx, e ->
            if (e.id in ids) {
                entries[idx] = e.copy(synced = 2, failReason = reason)
            }
        }
    }
    override suspend fun getFailed() = entries.filter { it.synced == 2 }.sortedBy { it.createdAt }
    override suspend fun countFailed() = entries.count { it.synced == 2 }
    override suspend fun retryFailed(id: Long) {
        val idx = entries.indexOfFirst { it.id == id && it.synced == 2 }
        if (idx >= 0) entries[idx] = entries[idx].copy(synced = 0, failReason = null)
    }
    override suspend fun retryAllFailed(tableName: String) {
        entries.forEachIndexed { idx, e ->
            if (e.synced == 2 && e.tableName == tableName) {
                entries[idx] = e.copy(synced = 0, failReason = null)
            }
        }
    }
    override suspend fun discardFailed(id: Long) {
        entries.removeAll { it.id == id && it.synced == 2 }
    }
    override suspend fun discardAllFailed(tableName: String) {
        entries.removeAll { it.synced == 2 && it.tableName == tableName }
    }
    override suspend fun countPendingByTable(): List<TablePendingCount> {
        return entries.filter { it.synced == 0 }
            .groupBy { it.tableName }
            .map { (table, list) -> TablePendingCount(table, list.size) }
    }
    override suspend fun countFailedByTable(): List<TablePendingCount> {
        return entries.filter { it.synced == 2 }
            .groupBy { it.tableName }
            .map { (table, list) -> TablePendingCount(table, list.size) }
    }
}
