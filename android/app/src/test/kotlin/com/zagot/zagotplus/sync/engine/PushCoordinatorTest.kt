package com.zagot.zagotplus.sync.engine

import com.google.common.truth.Truth.assertThat
import com.zagot.syncengine.api.FakeSupabaseClient
import com.zagot.syncengine.dao.PushCoordinator
import com.zagot.syncengine.dao.PushErrorCategory
import com.zagot.syncengine.db.SyncOutboxEntity
import com.zagot.syncengine.state.SyncStateMachine
import com.zagot.syncengine.util.SyncTableConfig
import org.junit.Before
import org.junit.Test

/**
 * Tests for PushCoordinator (spec Section 14.3 — Outbox).
 */
class PushCoordinatorTest {

    private lateinit var pushCoordinator: PushCoordinator
    private lateinit var outboxDao: FakeSyncOutboxDao
    private lateinit var fakeClient: FakeSupabaseClient
    private lateinit var stateMachine: SyncStateMachine

    @Before
    fun setup() {
        outboxDao = FakeSyncOutboxDao()
        stateMachine = SyncStateMachine()
        pushCoordinator = PushCoordinator(outboxDao, stateMachine)
        pushCoordinator.backoffMultiplier = 0L // disable delays in tests
        fakeClient = FakeSupabaseClient()
    }

    @Test
    fun `pushPending with no entries returns 0`() = runBlocking {
        val count = pushCoordinator.pushPending(fakeClient, listOf(productsConfig()))
        assertThat(count).isEqualTo(0)
    }

    @Test
    fun `pushPending pushes entries and marks synced`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", "INSERT"))
        outboxDao.insert(makeOutboxEntry("products", "p2", "INSERT"))

        val count = pushCoordinator.pushPending(fakeClient, listOf(productsConfig()))
        assertThat(count).isEqualTo(2)
        assertThat(outboxDao.countPending()).isEqualTo(0)
        assertThat(fakeClient.pushedRecords).hasSize(2)
    }

    @Test
    fun `pushPending groups by table`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", "INSERT"))
        outboxDao.insert(makeOutboxEntry("transactions", "t1", "INSERT"))

        val configs = listOf(productsConfig(), transactionsConfig())
        val count = pushCoordinator.pushPending(fakeClient, configs)
        assertThat(count).isEqualTo(2)
    }

    @Test
    fun `transient error leaves entries pending`() = runBlocking {
        outboxDao.insert(makeOutboxEntry("products", "p1", "INSERT"))
        fakeClient.failNextCalls(10) // exceed max retries

        // pushPending catches transient errors — entries stay pending, no exception thrown
        val count = pushCoordinator.pushPending(fakeClient, listOf(productsConfig()))
        assertThat(count).isEqualTo(0) // nothing succeeded
        assertThat(outboxDao.countPending()).isEqualTo(1) // still pending
    }

    @Test
    fun `classifyError identifies auth errors`() {
        val category = pushCoordinator.classifyError(RuntimeException("HTTP 401 Unauthorized"))
        assertThat(category).isEqualTo(PushErrorCategory.AUTH)
    }

    @Test
    fun `classifyError identifies terminal errors`() {
        assertThat(pushCoordinator.classifyError(RuntimeException("400 Bad Request")))
            .isEqualTo(PushErrorCategory.TERMINAL)
        assertThat(pushCoordinator.classifyError(RuntimeException("404 Not Found")))
            .isEqualTo(PushErrorCategory.TERMINAL)
    }

    @Test
    fun `classifyError identifies rate limit`() {
        val category = pushCoordinator.classifyError(RuntimeException("429 Too Many Requests"))
        assertThat(category).isEqualTo(PushErrorCategory.RATE_LIMIT)
    }

    @Test
    fun `classifyError identifies conflict`() {
        val category = pushCoordinator.classifyError(RuntimeException("409 Conflict"))
        assertThat(category).isEqualTo(PushErrorCategory.CONFLICT)
    }

    @Test
    fun `classifyError defaults to transient`() {
        val category = pushCoordinator.classifyError(RuntimeException("Connection timeout"))
        assertThat(category).isEqualTo(PushErrorCategory.TRANSIENT)
    }

    // === Helpers ===

    private fun productsConfig() = SyncTableConfig(tableName = "products")
    private fun transactionsConfig() = SyncTableConfig(tableName = "transactions")

    private fun makeOutboxEntry(table: String, id: String, op: String) = SyncOutboxEntity(
        tableName = table,
        recordId = id,
        operation = op,
        payload = """{"id":"$id","name":"test","updated_at":${System.currentTimeMillis()}}""",
        createdAt = System.currentTimeMillis()
    )

    private fun <T> runBlocking(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
