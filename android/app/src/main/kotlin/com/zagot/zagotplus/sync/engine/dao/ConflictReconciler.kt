package com.zagot.zagotplus.sync.engine.dao

import android.util.Log
import com.zagot.zagotplus.sync.engine.api.Record
import com.zagot.zagotplus.sync.engine.db.SyncOutboxDao
import com.zagot.zagotplus.sync.engine.util.JsonUtil
import com.zagot.zagotplus.sync.engine.util.SyncTableConfig
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
    }

    /**
     * Reconcile pulled remote records against pending outbox entries.
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

            // Run conflict resolution
            val winner = config.conflictResolver.resolve(localRecord, remoteRecord)

            val localWins = winner == localRecord
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

    private fun parsePayload(json: String): Record = JsonUtil.parsePayload(json)
}
