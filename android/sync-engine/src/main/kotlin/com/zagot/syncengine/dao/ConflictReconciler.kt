package com.zagot.syncengine.dao

import android.util.Log
import com.zagot.syncengine.api.Record
import com.zagot.syncengine.db.SyncOutboxDao
import com.zagot.syncengine.util.JsonUtil
import com.zagot.syncengine.util.SyncTableConfig
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Conflict reconciler — runs during CATCHING_UP between pull and push (spec Section 7).
 *
 * For each record that exists in both the outbox (local pending) and the pulled
 * remote data, applies conflict resolution:
 * - LWW by default (higher updated_at wins, tie → server/remote wins)
 * - Custom ConflictResolver if registered for the table
 *
 * Losing outbox entries are marked as synced (don't push them).
 */
@Singleton
class ConflictReconciler @Inject constructor(
    private val outboxDao: SyncOutboxDao,
) {

    companion object {
        private const val TAG = "ConflictReconciler"
        /** SQLite IN-clause limit is 999; use 900 for safety margin. */
        private const val IN_CLAUSE_CHUNK_SIZE = 900
    }

    /**
     * Reconcile pulled remote records against ALL pending outbox entries for the table.
     *
     * Used when the full set of pulled records is available in memory (small tables, tests).
     *
     * @param pulledRecords records fetched from the server
     * @param config table configuration (includes conflict resolver)
     * @return list of outbox entry IDs that lost conflict (marked synced, won't be pushed)
     */
    suspend fun reconcile(
        pulledRecords: List<Record>,
        config: SyncTableConfig,
    ): List<Long> {
        val pendingEntries = outboxDao.getPendingForTable(config.tableName)
        if (pendingEntries.isEmpty() || pulledRecords.isEmpty()) return emptyList()

        // Index pulled records by PK for O(1) lookup
        val pulledByPk = pulledRecords.associateBy { it[config.primaryKey]?.toString() }

        val losers = mutableListOf<Long>()

        for (entry in pendingEntries) {
            val remoteRecord = pulledByPk[entry.recordId] ?: continue // no conflict

            // Parse local record from outbox payload
            val localRecord = parsePayload(entry.payload)

            // Run conflict resolution.
            // Compare by identity (===) since resolvers return one of the input instances.
            // Structural equality (==) would fail for custom resolvers that construct new maps.
            val winner = config.conflictResolver.resolve(localRecord, remoteRecord)

            val localWins = winner === localRecord
            val remoteWins = winner === remoteRecord
            if (!localWins && !remoteWins) {
                Log.w(TAG, "ConflictResolver for ${config.tableName} returned a new instance for ${entry.recordId}; treating as remote-wins")
            }

            if (!localWins) {
                // Remote wins — mark outbox entry as synced (don't push)
                losers.add(entry.id)
                Log.d(TAG, "Conflict on ${config.tableName}/${entry.recordId}: remote wins")
            } else {
                Log.d(TAG, "Conflict on ${config.tableName}/${entry.recordId}: local wins")
            }
        }

        if (losers.isNotEmpty()) {
            outboxDao.markSyncedBatch(losers)
            Log.d(TAG, "Marked ${losers.size} outbox entries as resolved (remote won)")
        }

        return losers
    }

    /**
     * Reconcile a single pulled batch against pending outbox entries.
     *
     * Only queries outbox entries for PKs in the given batch (not ALL pending entries
     * for the table). Chunks the IN-clause to avoid SQLite parameter limits.
     *
     * Used by the streaming pull path where records are processed per-page.
     *
     * @param batchRecords records in the current batch
     * @param config table configuration (includes conflict resolver)
     * @return list of outbox entry IDs that lost conflict (marked synced, won't be pushed)
     */
    suspend fun reconcileBatch(
        batchRecords: List<Record>,
        config: SyncTableConfig,
    ): List<Long> {
        if (batchRecords.isEmpty()) return emptyList()

        val pks = batchRecords.mapNotNull { it[config.primaryKey]?.toString() }.distinct()
        if (pks.isEmpty()) return emptyList()

        val pulledByPk = batchRecords.associateBy { it[config.primaryKey]?.toString() }
        val losers = mutableListOf<Long>()

        for (chunk in pks.chunked(IN_CLAUSE_CHUNK_SIZE)) {
            val pendingEntries = outboxDao.findPendingForRecords(config.tableName, chunk)
            if (pendingEntries.isEmpty()) continue

            for (entry in pendingEntries) {
                val remoteRecord = pulledByPk[entry.recordId] ?: continue
                val localRecord = parsePayload(entry.payload)
                val winner = config.conflictResolver.resolve(localRecord, remoteRecord)

                val localWins = winner === localRecord
                val remoteWins = winner === remoteRecord
                if (!localWins && !remoteWins) {
                    Log.w(TAG, "ConflictResolver for ${config.tableName} returned a new instance for ${entry.recordId}; treating as remote-wins")
                }

                if (!localWins) {
                    losers.add(entry.id)
                    Log.d(TAG, "Conflict on ${config.tableName}/${entry.recordId}: remote wins")
                } else {
                    Log.d(TAG, "Conflict on ${config.tableName}/${entry.recordId}: local wins")
                }
            }
        }

        if (losers.isNotEmpty()) {
            outboxDao.markSyncedBatch(losers)
            Log.d(TAG, "Marked ${losers.size} outbox entries as resolved (remote won)")
        }

        return losers
    }

    private fun parsePayload(json: String): Record = JsonUtil.parsePayload(json)
}
