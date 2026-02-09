package com.zagot.syncengine.testing

import com.zagot.syncengine.api.ChangeOperation
import com.zagot.syncengine.api.RealtimeChangeEvent
import com.zagot.syncengine.api.RealtimeChannelContract
import com.zagot.syncengine.api.Record
import com.zagot.syncengine.api.SyncRemoteClient
import com.zagot.syncengine.network.NetworkMonitor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow

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
        limit: Int,
        primaryKey: String,
        afterPk: String?,
    ): List<Record> {
        maybeFail()
        return remoteTables[table]
            ?.filter { record ->
                val ts = parseTs(record[timestampColumn])
                val pk = record[primaryKey]?.toString() ?: ""
                if (afterPk != null) {
                    ts > since || (ts == since && pk > afterPk)
                } else {
                    ts >= since
                }
            }
            ?.sortedWith(compareBy<Record> { parseTs(it[timestampColumn]) }.thenBy { it[primaryKey]?.toString() ?: "" })
            ?.take(limit)
            ?: emptyList()
    }

    /** Parse timestamp from Long, Number, or ISO-8601 String. */
    private fun parseTs(value: Any?): Long = when (value) {
        is Long -> value
        is Number -> value.toLong()
        is String -> try {
            java.time.Instant.parse(value).toEpochMilli()
        } catch (_: Exception) {
            value.toLongOrNull() ?: 0L
        }
        else -> 0L
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

/**
 * Controllable network monitor for tests.
 */
class FakeNetworkMonitor : NetworkMonitor {
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected.asStateFlow()

    override fun start() { /* no-op for fake */ }
    override fun stop() { /* no-op for fake */ }

    fun simulateOnline() { _isConnected.value = true }
    fun simulateOffline() { _isConnected.value = false }
}
