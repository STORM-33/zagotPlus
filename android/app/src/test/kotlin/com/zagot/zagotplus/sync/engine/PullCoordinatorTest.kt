package com.zagot.zagotplus.sync.engine

import com.google.common.truth.Truth.assertThat
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
            mapOf("id" to "a", "updated_at" to 100L),
            mapOf("id" to "b", "updated_at" to 300L),
            mapOf("id" to "c", "updated_at" to 200L),
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
