package com.zagot.syncengine.util

import com.zagot.syncengine.api.Record
import java.time.Instant

/**
 * Configuration for a table registered for sync (spec Section 11).
 */
data class SyncTableConfig(
    val tableName: String,
    val primaryKey: String = "id",
    val timestampColumn: String = "server_updated_at",
    val softDeleteColumn: String? = "deleted_at",
    /** When true, always pull ALL records (no incremental since filter). */
    val fullPull: Boolean = false,
    val conflictResolver: ConflictResolver = LastWriteWins,
    /** Callback to apply pulled/buffered records to Room via UPSERT. */
    val applyToRoom: (suspend (List<Record>) -> Unit)? = null,
)

/**
 * Conflict resolution strategy (spec Section 7).
 * Default is LWW baked into UPSERT logic. Custom resolvers only invoked
 * when explicitly registered — not on the hot path for bulk operations.
 *
 * CONTRACT: Implementations MUST return one of the two input instances
 * (either [local] or [remote]) — not a newly constructed map.
 * The caller uses identity comparison (===) to determine which side won.
 */
interface ConflictResolver {
    fun resolve(local: Map<String, Any?>, remote: Map<String, Any?>): Map<String, Any?>
}

/**
 * Last-Write-Wins: higher timestamp wins, tie → server wins.
 *
 * Uses [timestampKey] to look up the comparison field — defaults to
 * "server_updated_at" matching SyncTableConfig.timestampColumn.
 *
 * Handles both ISO-8601 strings (from Supabase pulls) and epoch millis (Long).
 */
class LastWriteWins(
    private val timestampKey: String = "server_updated_at",
) : ConflictResolver {
    override fun resolve(local: Map<String, Any?>, remote: Map<String, Any?>): Map<String, Any?> {
        val localTs = parseTimestamp(local[timestampKey])
        val remoteTs = parseTimestamp(remote[timestampKey])
        // Tie → server (remote) wins per spec
        return if (localTs > remoteTs) local else remote
    }

    companion object : ConflictResolver {
        /** Default singleton using "server_updated_at". */
        private val DEFAULT = LastWriteWins("server_updated_at")
        override fun resolve(local: Map<String, Any?>, remote: Map<String, Any?>): Map<String, Any?> =
            DEFAULT.resolve(local, remote)

        /**
         * Parse a timestamp value that may be an ISO-8601 string, a Long, or null.
         * Returns epoch millis, or 0L if unparseable.
         */
        fun parseTimestamp(value: Any?): Long = when (value) {
            is Long -> value
            is Number -> value.toLong()
            is String -> try {
                Instant.parse(value).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
            else -> 0L
        }
    }
}
