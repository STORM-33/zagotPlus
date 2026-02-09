package com.zagot.zagotplus.sync.engine

import com.google.common.truth.Truth.assertThat
import com.zagot.syncengine.testing.FakeSupabaseClient
import com.zagot.syncengine.api.Record
import com.zagot.syncengine.dao.PullCoordinator
import com.zagot.syncengine.db.SyncMetadataDao
import com.zagot.syncengine.db.SyncMetadataEntity
import com.zagot.syncengine.state.SyncStateMachine
import com.zagot.syncengine.util.SyncTableConfig
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Before
import org.junit.Test

/**
 * Tests for PullCoordinator (spec Section 14.3 — Pull & Deduplication).
 */
class PullCoordinatorTest {

    private lateinit var fakeClient: FakeSupabaseClient
    private lateinit var stateMachine: SyncStateMachine

    @Before
    fun setup() {
        fakeClient = FakeSupabaseClient()
        stateMachine = SyncStateMachine()
    }

    @Test
    fun `pull returns records from remote`() {
        fakeClient.injectRemoteRecord("products", mapOf(
            "id" to "p1", "updated_at" to 10_000L, "name" to "Cashew"
        ))
        // FakeSupabaseClient.pull filters by since; since=0 means fetch all
        val records = runBlocking { fakeClient.pull("products", "updated_at", 0L) }
        assertThat(records).hasSize(1)
        assertThat(records[0]["name"]).isEqualTo("Cashew")
    }

    @Test
    fun `pull filters by overlap window`() {
        fakeClient.injectRemoteRecord("products", mapOf(
            "id" to "p1", "updated_at" to 5_000L, "name" to "Old"
        ))
        fakeClient.injectRemoteRecord("products", mapOf(
            "id" to "p2", "updated_at" to 15_000L, "name" to "New"
        ))
        // since=10_000 means fetch records with updated_at > 10_000
        val records = runBlocking { fakeClient.pull("products", "updated_at", 10_000L) }
        assertThat(records).hasSize(1)
        assertThat(records[0]["id"]).isEqualTo("p2")
    }

    @Test
    fun `maxTimestamp extracts max updated_at from records`() {
        val coordinator = PullCoordinator(FakeSyncMetadataDao(), stateMachine)
        val records = listOf(
            mapOf("id" to "a", "updated_at" to "1970-01-01T00:00:00.100Z"),
            mapOf("id" to "b", "updated_at" to "1970-01-01T00:00:00.300Z"),
            mapOf("id" to "c", "updated_at" to "1970-01-01T00:00:00.200Z"),
        )
        assertThat(coordinator.maxTimestamp(records, "updated_at")).isEqualTo(300L)
    }

    @Test
    fun `maxTimestamp returns 0 for empty list`() {
        val coordinator = PullCoordinator(FakeSyncMetadataDao(), stateMachine)
        assertThat(coordinator.maxTimestamp(emptyList(), "updated_at")).isEqualTo(0L)
    }

    @Test
    fun `pull with network failure propagates exception`() {
        fakeClient.failNextCalls(1)
        val exception = runCatching {
            runBlocking { fakeClient.pull("products", "updated_at", 0L) }
        }.exceptionOrNull()
        assertThat(exception).isNotNull()
    }

    @Test
    fun `pull collects paginated results`() = runBlocking {
        val coordinator = PullCoordinator(FakeSyncMetadataDao(), stateMachine).apply {
            pullPageSize = 2
        }
        val config = SyncTableConfig(tableName = "products", timestampColumn = "updated_at")

        fakeClient.injectRemoteRecord("products", mapOf("id" to "p1", "updated_at" to "1970-01-01T00:00:00.100Z"))
        fakeClient.injectRemoteRecord("products", mapOf("id" to "p2", "updated_at" to "1970-01-01T00:00:00.200Z"))
        fakeClient.injectRemoteRecord("products", mapOf("id" to "p3", "updated_at" to "1970-01-01T00:00:00.300Z"))

        val records = coordinator.pull(fakeClient, config).toList().flatten()
        assertThat(records.mapNotNull { it["id"]?.toString() }).containsExactly("p1", "p2", "p3").inOrder()
    }

    @Test
    fun `pullStreaming invokes onBatch per page`() = runBlocking {
        val coordinator = PullCoordinator(FakeSyncMetadataDao(), stateMachine).apply {
            pullPageSize = 2
        }
        val config = SyncTableConfig(tableName = "products", timestampColumn = "updated_at")

        fakeClient.injectRemoteRecord("products", mapOf("id" to "p1", "updated_at" to 100L))
        fakeClient.injectRemoteRecord("products", mapOf("id" to "p2", "updated_at" to 200L))
        fakeClient.injectRemoteRecord("products", mapOf("id" to "p3", "updated_at" to 300L))

        val batches = mutableListOf<List<Map<String, Any?>>>()
        val maxTimestamps = mutableListOf<Long>()

        val result = coordinator.pullStreaming(fakeClient, config) { batch, batchMaxTs ->
            batches.add(batch)
            maxTimestamps.add(batchMaxTs)
        }

        assertThat(result.totalRecords).isEqualTo(3)
        assertThat(result.pagesProcessed).isGreaterThan(1)
        assertThat(batches.flatMap { it }.mapNotNull { it["id"]?.toString() })
            .containsExactly("p1", "p2", "p3").inOrder()
    }

    @Test
    fun `pullStreaming respects per-table pullPageSize`() = runBlocking {
        val coordinator = PullCoordinator(FakeSyncMetadataDao(), stateMachine).apply {
            pullPageSize = 100 // engine default
        }
        // Table overrides to page size 1
        val config = SyncTableConfig(tableName = "products", timestampColumn = "updated_at", pullPageSize = 1)

        fakeClient.injectRemoteRecord("products", mapOf("id" to "p1", "updated_at" to 100L))
        fakeClient.injectRemoteRecord("products", mapOf("id" to "p2", "updated_at" to 200L))

        var batchCount = 0
        coordinator.pullStreaming(fakeClient, config) { _, _ -> batchCount++ }
        assertThat(batchCount).isEqualTo(2) // page size 1 = 2 batches for 2 records
    }

    @Test
    fun `pullStreaming handles same-timestamp records with compound cursor`() = runBlocking {
        val coordinator = PullCoordinator(FakeSyncMetadataDao(), stateMachine).apply {
            pullPageSize = 3
        }
        val config = SyncTableConfig(tableName = "products", timestampColumn = "updated_at")

        // 7 records all with same timestamp — more than 2 pages
        repeat(7) { i ->
            fakeClient.injectRemoteRecord("products", mapOf(
                "id" to "p${String.format("%03d", i)}",
                "updated_at" to 100L,
            ))
        }

        val allRecords = mutableListOf<Record>()
        coordinator.pullStreaming(fakeClient, config) { batch, _ ->
            allRecords.addAll(batch)
        }

        assertThat(allRecords.map { it["id"] }).containsExactly(
            "p000", "p001", "p002", "p003", "p004", "p005", "p006"
        ).inOrder()
    }

    @Test
    fun `pullStreaming breaks on stuck cursor`() = runBlocking {
        val coordinator = PullCoordinator(FakeSyncMetadataDao(), stateMachine).apply {
            pullPageSize = 2
        }
        val config = SyncTableConfig(tableName = "products", timestampColumn = "updated_at")

        // 3 records share same timestamp — compound cursor handles this now
        fakeClient.injectRemoteRecord("products", mapOf("id" to "p1", "updated_at" to 100L))
        fakeClient.injectRemoteRecord("products", mapOf("id" to "p2", "updated_at" to 100L))
        fakeClient.injectRemoteRecord("products", mapOf("id" to "p3", "updated_at" to 100L))

        val result = coordinator.pullStreaming(fakeClient, config) { _, _ -> }
        // Compound cursor fetches all same-timestamp records
        assertThat(result.totalRecords).isEqualTo(3)
    }

    @Test
    fun `maxTimestamp handles numeric timestamps`() {
        val coordinator = PullCoordinator(FakeSyncMetadataDao(), stateMachine)
        val records = listOf(
            mapOf("id" to "a", "updated_at" to 100L),
            mapOf("id" to "b", "updated_at" to 300L),
            mapOf("id" to "c", "updated_at" to 200L),
        )
        assertThat(coordinator.maxTimestamp(records, "updated_at")).isEqualTo(300L)
    }

    @Test
    fun `getLastSyncedAt and setLastSyncedAt round-trip`() = runBlocking {
        val dao = FakeSyncMetadataDao()
        val coordinator = PullCoordinator(dao, stateMachine)

        assertThat(coordinator.getLastSyncedAt("products")).isEqualTo(0L)

        coordinator.setLastSyncedAt("products", 12345L)
        assertThat(coordinator.getLastSyncedAt("products")).isEqualTo(12345L)

        // setLastSyncedAt can move backwards (for overflow retry rollback)
        coordinator.setLastSyncedAt("products", 100L)
        assertThat(coordinator.getLastSyncedAt("products")).isEqualTo(100L)
    }

    @Test
    fun `updateLastSyncedAt only advances forward`() = runBlocking {
        val dao = FakeSyncMetadataDao()
        val coordinator = PullCoordinator(dao, stateMachine)

        coordinator.updateLastSyncedAt("products", 500L)
        assertThat(coordinator.getLastSyncedAt("products")).isEqualTo(500L)

        // Should NOT go backwards
        coordinator.updateLastSyncedAt("products", 300L)
        assertThat(coordinator.getLastSyncedAt("products")).isEqualTo(500L)

        // Should go forward
        coordinator.updateLastSyncedAt("products", 700L)
        assertThat(coordinator.getLastSyncedAt("products")).isEqualTo(700L)
    }

    // Minimal in-memory SyncMetadataDao for unit tests
    private class FakeSyncMetadataDao : SyncMetadataDao {
        private val data = mutableMapOf<String, SyncMetadataEntity>()

        override suspend fun get(tableName: String) = data[tableName]
        override suspend fun upsert(entity: SyncMetadataEntity) { data[entity.tableName] = entity }
        override suspend fun getLastSyncedAt(tableName: String) = data[tableName]?.lastSyncedAt
        override suspend fun updateLastSyncedAt(tableName: String, lastSyncedAt: Long) {
            data[tableName] = SyncMetadataEntity(tableName, lastSyncedAt)
        }
        override suspend fun getAll() = data.values.toList()
    }

    // Helper: run blocking for test (since we're in JUnit, not Android)
    private fun <T> runBlocking(block: suspend () -> T): T =
        kotlinx.coroutines.runBlocking { block() }
}
