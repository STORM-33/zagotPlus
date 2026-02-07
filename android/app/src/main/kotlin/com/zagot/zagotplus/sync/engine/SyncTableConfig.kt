package com.zagot.zagotplus.sync.engine

/**
 * Configuration for a table registered for sync (spec Section 11).
 */
data class SyncTableConfig(
    val tableName: String,
    val primaryKey: String = "id",
    val timestampColumn: String = "updated_at",
    val softDeleteColumn: String? = "deleted_at",
    val conflictResolver: ConflictResolver = LastWriteWins,
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
 * Last-Write-Wins: higher updated_at wins, tie → server wins.
 */
object LastWriteWins : ConflictResolver {
    override fun resolve(local: Map<String, Any?>, remote: Map<String, Any?>): Map<String, Any?> {
        val localTs = local["updated_at"] as? Long ?: 0L
        val remoteTs = remote["updated_at"] as? Long ?: 0L
        // Tie → server (remote) wins per spec
        return if (localTs > remoteTs) local else remote
    }
}
