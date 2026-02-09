package com.zagot.syncengine.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.SharedFlow

/**
 * A record from or to Supabase — generic key-value map.
 */
typealias Record = Map<String, Any?>

/**
 * A realtime event from Supabase channel.
 */
data class RealtimeChangeEvent(
    val table: String,
    val operation: ChangeOperation,
    val record: Record,
)

enum class ChangeOperation { INSERT, UPDATE, DELETE }

/**
 * Contract for Supabase remote operations used by the sync engine.
 * Implemented by the real SupabaseClient wrapper and by FakeSupabaseClient in tests.
 */
interface SyncRemoteClient {
    /**
     * Pull records from [table] where [timestampColumn] >= [since].
     *
     * NOTE: Overlap window handling is the caller's responsibility
     * (PullCoordinator subtracts the window before calling this method).
     * [overlapWindowMs] is passed for informational purposes only and
     * MUST NOT be subtracted again by implementations.
     *
     * @param limit max records to return (pagination page size). Implementations
     *   MUST respect this to enable cursor-based pagination in [PullCoordinator].
     * @param primaryKey column name used as secondary cursor for compound pagination.
     * @param afterPk when non-null, only return records where
     *   `(timestamp > since) OR (timestamp = since AND pk > afterPk)`.
     */
    suspend fun pull(
        table: String,
        timestampColumn: String,
        since: Long,
        overlapWindowMs: Long = 0L,
        limit: Int = 1000,
        primaryKey: String = "id",
        afterPk: String? = null,
    ): List<Record>

    /** Push (upsert) records to [table], keyed on [primaryKey]. */
    suspend fun push(table: String, primaryKey: String, records: List<Record>)
}

/**
 * Contract for a realtime channel subscription.
 */
interface RealtimeChannelContract {
    val events: SharedFlow<RealtimeChangeEvent>
    suspend fun subscribe(tables: List<String>, scope: CoroutineScope)
    suspend fun unsubscribe()
}

// Test fakes (FakeSupabaseClient, FakeRealtimeChannel, FakeNetworkMonitor)
// are in the testFixtures source set: com.zagot.syncengine.testing.SyncTestFakes
