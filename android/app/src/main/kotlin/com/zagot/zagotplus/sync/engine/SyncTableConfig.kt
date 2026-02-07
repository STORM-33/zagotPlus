package com.zagot.zagotplus.sync.engine

/**
 * Configuration for a table registered for sync (spec Section 11).
 */
data class SyncTableConfig(
    val tableName: String,
    val primaryKey: String = "id",
    val timestampColumn: String = "server_updated_at",
    val softDeleteColumn: String? = "deleted_at",
    val conflictResolver: ConflictResolver = LastWriteWins,
    /** Callback to apply pulled/buffered records to Room via UPSERT. */
    val applyToRoom: (suspend (List<Record>) -> Unit)? = null,
)

/**
 * Conflict resolution strategy (spec Section 7).
 * Default is LWW baked into UPSERT logic. Custom resolvers only invoked
 * when explicitly registered — not on the hot path for bulk operations.
 */
interface ConflictResolver {
    fun resolve(local: Map<String, Any?>, remote: Map<String, Any?>): Map<String, Any?>
}

/**
 * Last-Write-Wins: higher timestamp wins, tie → server wins.
 *
 * Uses [timestampKey] to look up the comparison field — defaults to
 * "server_updated_at" matching SyncTableConfig.timestampColumn.
 */
class LastWriteWins(
    private val timestampKey: String = "server_updated_at",
) : ConflictResolver {
    override fun resolve(local: Map<String, Any?>, remote: Map<String, Any?>): Map<String, Any?> {
        val localTs = local[timestampKey] as? Long ?: 0L
        val remoteTs = remote[timestampKey] as? Long ?: 0L
        // Tie → server (remote) wins per spec
        return if (localTs > remoteTs) local else remote
    }

    companion object : ConflictResolver {
        /** Default singleton using "server_updated_at". */
        private val DEFAULT = LastWriteWins("server_updated_at")
        override fun resolve(local: Map<String, Any?>, remote: Map<String, Any?>): Map<String, Any?> =
            DEFAULT.resolve(local, remote)
    }
}
