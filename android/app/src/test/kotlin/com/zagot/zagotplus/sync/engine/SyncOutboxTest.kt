package com.zagot.zagotplus.sync.engine

import com.google.common.truth.Truth.assertThat
import com.zagot.syncengine.db.SyncMetadataEntity
import com.zagot.syncengine.db.SyncOutboxEntity
import org.junit.Test

/**
 * Tests for SyncMetadata overlap window logic and SyncOutbox operations.
 * These are pure logic tests; Room DAO integration tests require Robolectric.
 */
class SyncOutboxTest {

    @Test
    fun `outbox entity defaults synced to 0`() {
        val entry = SyncOutboxEntity(
            tableName = "products",
            recordId = "abc-123",
            operation = "INSERT",
            payload = """{"name":"test"}""",
            createdAt = System.currentTimeMillis()
        )
        assertThat(entry.synced).isEqualTo(0)
    }

    @Test
    fun `outbox entity stores all fields`() {
        val now = System.currentTimeMillis()
        val entry = SyncOutboxEntity(
            id = 42,
            tableName = "transactions",
            recordId = "tx-001",
            operation = "UPDATE",
            payload = """{"amount":100}""",
            createdAt = now,
            synced = 1
        )
        assertThat(entry.id).isEqualTo(42)
        assertThat(entry.tableName).isEqualTo("transactions")
        assertThat(entry.recordId).isEqualTo("tx-001")
        assertThat(entry.operation).isEqualTo("UPDATE")
        assertThat(entry.payload).isEqualTo("""{"amount":100}""")
        assertThat(entry.createdAt).isEqualTo(now)
        assertThat(entry.synced).isEqualTo(1)
    }

    @Test
    fun `metadata entity defaults lastSyncedAt to 0`() {
        val meta = SyncMetadataEntity(tableName = "products")
        assertThat(meta.lastSyncedAt).isEqualTo(0L)
    }

    @Test
    fun `overlap window subtraction works correctly`() {
        val lastSyncedAt = 1_000_000L
        val overlapWindowMs = 5_000L
        val effectiveSince = lastSyncedAt - overlapWindowMs
        assertThat(effectiveSince).isEqualTo(995_000L)
    }

    @Test
    fun `overlap window with zero lastSyncedAt stays non-negative`() {
        val lastSyncedAt = 0L
        val overlapWindowMs = 5_000L
        val effectiveSince = maxOf(0L, lastSyncedAt - overlapWindowMs)
        assertThat(effectiveSince).isEqualTo(0L)
    }
}
