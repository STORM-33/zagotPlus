package com.zagot.zagotplus.sync.engine

import android.util.Log
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.serialization.json.JsonElement
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Real Supabase implementation of [SyncRemoteClient] (spec Section 5 + 10).
 *
 * Uses Postgrest-kt for REST operations. Records use `server_updated_at`
 * as the timestamp column for incremental pulls.
 */
@Singleton
class SupabaseSyncRemoteClient @Inject constructor(
    private val supabaseClient: SupabaseClient,
) : SyncRemoteClient {

    companion object {
        private const val TAG = "SupabaseSyncRemoteClient"
    }

    override suspend fun pull(
        table: String,
        timestampColumn: String,
        since: Long,
        overlapWindowMs: Long,
    ): List<Record> {
        val effectiveSince = since - overlapWindowMs
        Log.d(TAG, "Pulling $table where $timestampColumn > $effectiveSince")

        val jsonRecords: List<Map<String, JsonElement>> = supabaseClient.postgrest[table]
            .select(Columns.ALL) {
                filter {
                    gt(timestampColumn, effectiveSince.toString())
                }
                order(timestampColumn, Order.ASCENDING)
            }
            .decodeList()

        val records = jsonRecords.map { jsonMap ->
            jsonMap.mapValues { (_, v) -> JsonUtil.jsonElementToAny(v) }
        }

        Log.d(TAG, "Pulled ${records.size} records from $table")
        return records
    }

    override suspend fun push(table: String, primaryKey: String, records: List<Record>) {
        if (records.isEmpty()) return
        Log.d(TAG, "Pushing ${records.size} records to $table")

        val jsonRecords = records.map { record ->
            record.mapValues { (_, v) -> JsonUtil.anyToJsonElement(v) }
        }

        supabaseClient.postgrest[table].upsert(jsonRecords, onConflict = primaryKey)

        Log.d(TAG, "Pushed ${records.size} records to $table")
    }
}
