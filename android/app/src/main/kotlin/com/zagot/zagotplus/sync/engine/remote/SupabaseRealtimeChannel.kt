package com.zagot.zagotplus.sync.engine.remote

import android.util.Log
import com.zagot.zagotplus.sync.engine.api.ChangeOperation
import com.zagot.zagotplus.sync.engine.api.RealtimeChangeEvent
import com.zagot.zagotplus.sync.engine.api.RealtimeChannelContract
import com.zagot.zagotplus.sync.engine.api.Record
import com.zagot.zagotplus.sync.engine.util.JsonUtil
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.RealtimeChannel
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Supabase Realtime channel implementation (spec Section 6).
 *
 * Subscribes to Postgres Changes on all synced tables and converts
 * them to [RealtimeChangeEvent]s for the sync engine.
 */
@Singleton
class SupabaseRealtimeChannel @Inject constructor(
    private val supabaseClient: SupabaseClient,
) : RealtimeChannelContract {

    companion object {
        private const val TAG = "SupabaseRealtimeChannel"
    }

    private val _events = MutableSharedFlow<RealtimeChangeEvent>(extraBufferCapacity = 256)
    override val events: SharedFlow<RealtimeChangeEvent> = _events.asSharedFlow()

    private var channel: RealtimeChannel? = null
    private val flowJobs = mutableListOf<Job>()

    override suspend fun subscribe(tables: List<String>, scope: CoroutineScope) {
        Log.d(TAG, "Subscribing to realtime for tables: $tables")

        val ch = supabaseClient.channel("sync-engine")
        this.channel = ch

        // Wire up event collection before subscribing
        flowJobs.forEach { it.cancel() }
        flowJobs.clear()

        for (table in tables) {
            val job = ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                this.table = table
            }.onEach { action ->
                val event = mapAction(table, action)
                if (event != null) {
                    _events.tryEmit(event)
                }
            }.launchIn(scope)
            flowJobs.add(job)
        }

        ch.subscribe()
        Log.d(TAG, "Subscribed to realtime channel")
    }

    override suspend fun unsubscribe() {
        Log.d(TAG, "Unsubscribing from realtime")
        flowJobs.forEach { it.cancel() }
        flowJobs.clear()
        channel?.unsubscribe()
        channel = null
    }

    private fun mapAction(table: String, action: PostgresAction): RealtimeChangeEvent? {
        return when (action) {
            is PostgresAction.Insert -> RealtimeChangeEvent(
                table = table,
                operation = ChangeOperation.INSERT,
                record = extractRecord(action.record),
            )
            is PostgresAction.Update -> RealtimeChangeEvent(
                table = table,
                operation = ChangeOperation.UPDATE,
                record = extractRecord(action.record),
            )
            is PostgresAction.Delete -> RealtimeChangeEvent(
                table = table,
                operation = ChangeOperation.DELETE,
                record = extractRecord(action.oldRecord),
            )
            else -> {
                Log.w(TAG, "Unknown postgres action for $table: $action")
                null
            }
        }
    }

    private fun extractRecord(jsonMap: Map<String, JsonElement>): Record {
        return jsonMap.mapValues { (_, v) -> JsonUtil.jsonElementToAny(v) }
    }
}
