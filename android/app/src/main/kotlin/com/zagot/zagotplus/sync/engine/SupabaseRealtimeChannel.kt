package com.zagot.zagotplus.sync.engine

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.realtime.PostgresAction
import io.github.jan.supabase.realtime.Realtime
import io.github.jan.supabase.realtime.channel
import io.github.jan.supabase.realtime.postgresChangeFlow
import io.github.jan.supabase.realtime.RealtimeChannel
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonPrimitive
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

    override suspend fun subscribe(tables: List<String>) {
        Log.d(TAG, "Subscribing to realtime for tables: $tables")

        val ch = supabaseClient.channel("sync-engine")
        this.channel = ch

        // Subscribe to postgres changes for each table
        for (table in tables) {
            ch.postgresChangeFlow<PostgresAction>(schema = "public") {
                this.table = table
            }.onEach { action ->
                val event = mapAction(table, action)
                if (event != null) {
                    _events.tryEmit(event)
                }
            }.launchIn(CoroutineScope(kotlinx.coroutines.Dispatchers.IO))
        }

        ch.subscribe()
        Log.d(TAG, "Subscribed to realtime channel")
    }

    override suspend fun unsubscribe() {
        Log.d(TAG, "Unsubscribing from realtime")
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
        return jsonMap.mapValues { (_, v) -> jsonElementToAny(v) }
    }

    private fun jsonElementToAny(element: JsonElement): Any? {
        return when (element) {
            is JsonPrimitive -> {
                when {
                    element.isString -> element.content
                    element.content == "null" -> null
                    element.content == "true" -> true
                    element.content == "false" -> false
                    element.content.contains(".") -> element.content.toDoubleOrNull()
                    else -> element.content.toLongOrNull() ?: element.content
                }
            }
            is JsonNull -> null
            else -> element.toString()
        }
    }
}
