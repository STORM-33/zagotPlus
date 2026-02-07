package com.zagot.zagotplus.sync.engine

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow

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
    /** Pull records from [table] where [timestampColumn] > [since]. */
    suspend fun pull(
        table: String,
        timestampColumn: String,
        since: Long,
        overlapWindowMs: Long = 5_000L,
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

// ==================== FAKES ====================

/**
 * In-memory Supabase replacement for unit/integration tests (spec Section 14.2).
 */
class FakeSupabaseClient : SyncRemoteClient {
    val pushedRecords = mutableListOf<Pair<String, Record>>()
    private val remoteTables = mutableMapOf<String, MutableList<Record>>()

    private var failCount = 0
    private var failError: Exception? = null

    /** Simulate network failure on next N calls. */
    fun failNextCalls(count: Int, error: Exception = RuntimeException("Fake network error")) {
        failCount = count
        failError = error
    }

    /** Inject a remote record that will be returned on next pull. */
    fun injectRemoteRecord(table: String, record: Record) {
        remoteTables.getOrPut(table) { mutableListOf() }.add(record)
    }

    private fun maybeFail() {
        if (failCount > 0) {
            failCount--
            throw failError!!
        }
    }

    override suspend fun pull(
        table: String,
        timestampColumn: String,
        since: Long,
        overlapWindowMs: Long,
    ): List<Record> {
        maybeFail()
        val effectiveSince = since - overlapWindowMs
        return remoteTables[table]?.filter { record ->
            val ts = record[timestampColumn] as? Long ?: 0L
            ts > effectiveSince
        } ?: emptyList()
    }

    override suspend fun push(table: String, primaryKey: String, records: List<Record>) {
        maybeFail()
        records.forEach { record ->
            pushedRecords.add(table to record)
            val tableRecords = remoteTables.getOrPut(table) { mutableListOf() }
            val pk = record[primaryKey]
            tableRecords.removeAll { it[primaryKey] == pk }
            tableRecords.add(record)
        }
    }
}

/**
 * Fake Realtime channel that lets tests emit events manually (spec Section 14.2).
 */
class FakeRealtimeChannel : RealtimeChannelContract {
    private val _events = MutableSharedFlow<RealtimeChangeEvent>(extraBufferCapacity = 64)
    override val events: SharedFlow<RealtimeChangeEvent> = _events.asSharedFlow()

    var isSubscribed = false
        private set

    override suspend fun subscribe(tables: List<String>, scope: CoroutineScope) {
        isSubscribed = true
    }

    override suspend fun unsubscribe() {
        isSubscribed = false
    }

    fun emitEvent(event: RealtimeChangeEvent) {
        _events.tryEmit(event)
    }
}
