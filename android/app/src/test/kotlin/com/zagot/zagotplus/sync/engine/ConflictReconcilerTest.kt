package com.zagot.zagotplus.sync.engine

import com.google.common.truth.Truth.assertThat
import org.junit.Before
import org.junit.Test

/**
 * Tests for conflict resolution (spec Section 14.3 — Conflict Resolution).
 */
class ConflictReconcilerTest {

    private lateinit var reconciler: ConflictReconciler
    private lateinit var outboxDao: FakeSyncOutboxDao
    private lateinit var stateMachine: SyncStateMachine

    @Before
    fun setup() {
        outboxDao = FakeSyncOutboxDao()
        stateMachine = SyncStateMachine()
        reconciler = ConflictReconciler(outboxDao, stateMachine)
    }

    @Test
    fun `no conflict when outbox is empty`() = runBlocking {
        val remote = listOf(mapOf("id" to "p1", "updated_at" to 200L))
        val losers = reconciler.reconcile(remote, productsConfig())
        assertThat(losers).isEmpty()
    }

    @Test
    fun `no conflict when pulled records are empty`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", 100L))
        val losers = reconciler.reconcile(emptyList(), productsConfig())
        assertThat(losers).isEmpty()
    }

    @Test
    fun `no conflict when different PKs`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", 100L))
        val remote = listOf(mapOf("id" to "p2", "updated_at" to 200L))
        val losers = reconciler.reconcile(remote, productsConfig())
        assertThat(losers).isEmpty()
        assertThat(outboxDao.countPending()).isEqualTo(1) // still pending
    }

    @Test
    fun `LWW remote wins when remote timestamp is newer`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", 100L))
        val remote = listOf(mapOf("id" to "p1", "updated_at" to 200L))
        val losers = reconciler.reconcile(remote, productsConfig())
        assertThat(losers).hasSize(1)
        assertThat(outboxDao.countPending()).isEqualTo(0) // marked synced
    }

    @Test
    fun `LWW local wins when local timestamp is newer`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", 300L))
        val remote = listOf(mapOf("id" to "p1", "updated_at" to 200L))
        val losers = reconciler.reconcile(remote, productsConfig())
        assertThat(losers).isEmpty()
        assertThat(outboxDao.countPending()).isEqualTo(1) // still pending, will be pushed
    }

    @Test
    fun `LWW tie goes to server (remote wins)`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", 200L))
        val remote = listOf(mapOf("id" to "p1", "updated_at" to 200L))
        val losers = reconciler.reconcile(remote, productsConfig())
        assertThat(losers).hasSize(1) // tie → server wins
        assertThat(outboxDao.countPending()).isEqualTo(0)
    }

    @Test
    fun `custom conflict resolver is used when registered`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", 100L))
        val remote = listOf(mapOf("id" to "p1", "updated_at" to 200L, "name" to "Remote"))

        // Custom resolver: always pick local
        val alwaysLocal = object : ConflictResolver {
            override fun resolve(local: Map<String, Any?>, remote: Map<String, Any?>): Map<String, Any?> = local
        }
        val config = SyncTableConfig(tableName = "products", conflictResolver = alwaysLocal)

        val losers = reconciler.reconcile(remote, config)
        assertThat(losers).isEmpty() // local wins via custom resolver
        assertThat(outboxDao.countPending()).isEqualTo(1) // still pending
    }

    @Test
    fun `multiple conflicts resolved correctly`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", 100L)) // remote wins
        outboxDao.insert(makeOutboxEntry("products", "p2", 500L)) // local wins
        outboxDao.insert(makeOutboxEntry("products", "p3", 300L)) // remote wins (tie goes to server)

        val remote = listOf(
            mapOf("id" to "p1", "updated_at" to 200L),
            mapOf("id" to "p2", "updated_at" to 200L),
            mapOf("id" to "p3", "updated_at" to 300L),
        )

        val losers = reconciler.reconcile(remote, productsConfig())
        assertThat(losers).hasSize(2) // p1 and p3 lost
        assertThat(outboxDao.countPending()).isEqualTo(1) // only p2 still pending
    }

    // === Helpers ===

    private fun productsConfig() = SyncTableConfig(tableName = "products")

    private fun makeOutboxEntry(table: String, id: String, updatedAt: Long) = SyncOutboxEntity(
        tableName = table,
        recordId = id,
        operation = "UPDATE",
        payload = """{"id":"$id","updated_at":$updatedAt,"name":"local"}""",
        createdAt = System.currentTimeMillis()
    )

    private fun <T> runBlocking(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }

    /** In-memory outbox DAO. */
    private class FakeSyncOutboxDao : SyncOutboxDao {
        private var autoId = 1L
        private val entries = mutableListOf<SyncOutboxEntity>()

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
    }
}
