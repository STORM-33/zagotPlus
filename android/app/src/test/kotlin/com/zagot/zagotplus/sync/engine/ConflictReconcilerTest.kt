package com.zagot.zagotplus.sync.engine

import com.google.common.truth.Truth.assertThat
import com.zagot.syncengine.dao.ConflictReconciler
import com.zagot.syncengine.db.SyncOutboxEntity
import com.zagot.syncengine.util.ConflictResolver
import com.zagot.syncengine.util.SyncTableConfig
import org.junit.Before
import org.junit.Test

/**
 * Tests for conflict resolution (spec Section 14.3 — Conflict Resolution).
 */
class ConflictReconcilerTest {

    private lateinit var reconciler: ConflictReconciler
    private lateinit var outboxDao: FakeSyncOutboxDao

    @Before
    fun setup() {
        outboxDao = FakeSyncOutboxDao()
        reconciler = ConflictReconciler(outboxDao)
    }

    @Test
    fun `no conflict when outbox is empty`() = runBlocking {
        val remote = listOf(mapOf("id" to "p1", "server_updated_at" to 200L))
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
        val remote = listOf(mapOf("id" to "p2", "server_updated_at" to 200L))
        val losers = reconciler.reconcile(remote, productsConfig())
        assertThat(losers).isEmpty()
        assertThat(outboxDao.countPending()).isEqualTo(1) // still pending
    }

    @Test
    fun `LWW remote wins when remote timestamp is newer`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", 100L))
        val remote = listOf(mapOf("id" to "p1", "server_updated_at" to 200L))
        val losers = reconciler.reconcile(remote, productsConfig())
        assertThat(losers).hasSize(1)
        assertThat(outboxDao.countPending()).isEqualTo(0) // marked synced
    }

    @Test
    fun `LWW local wins when local timestamp is newer`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", 300L))
        val remote = listOf(mapOf("id" to "p1", "server_updated_at" to 200L))
        val losers = reconciler.reconcile(remote, productsConfig())
        assertThat(losers).isEmpty()
        assertThat(outboxDao.countPending()).isEqualTo(1) // still pending, will be pushed
    }

    @Test
    fun `LWW tie goes to server (remote wins)`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", 200L))
        val remote = listOf(mapOf("id" to "p1", "server_updated_at" to 200L))
        val losers = reconciler.reconcile(remote, productsConfig())
        assertThat(losers).hasSize(1) // tie → server wins
        assertThat(outboxDao.countPending()).isEqualTo(0)
    }

    @Test
    fun `custom conflict resolver is used when registered`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", 100L))
        val remote = listOf(mapOf("id" to "p1", "server_updated_at" to 200L, "name" to "Remote"))

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
            mapOf("id" to "p1", "server_updated_at" to 200L),
            mapOf("id" to "p2", "server_updated_at" to 200L),
            mapOf("id" to "p3", "server_updated_at" to 300L),
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
        payload = """{"id":"$id","server_updated_at":$updatedAt,"name":"local"}""",
        createdAt = System.currentTimeMillis()
    )

    private fun <T> runBlocking(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
