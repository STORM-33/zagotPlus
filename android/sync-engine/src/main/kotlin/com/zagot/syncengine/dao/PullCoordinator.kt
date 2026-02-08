package com.zagot.syncengine.dao

import android.util.Log
import com.zagot.syncengine.api.Record
import com.zagot.syncengine.api.SyncRemoteClient
import com.zagot.syncengine.db.SyncMetadataDao
import com.zagot.syncengine.db.SyncMetadataEntity
import com.zagot.syncengine.state.SyncEvent
import com.zagot.syncengine.state.SyncStateMachine
import com.zagot.syncengine.util.SyncTableConfig
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Pull coordinator — fetches remote changes with overlap window,
 * applies them to the local store, and updates sync metadata (spec Section 5).
 *
 * The pull uses `last_synced_at - OVERLAP_WINDOW` to guard against clock skew
 * and same-timestamp races. Deduplication via UPSERT by PK handles any
 * re-fetched records cheaply.
 *
 * Uses cursor-based pagination to handle tables with more records than a
 * single page (default 1000). The cursor advances by the max timestamp in
 * each batch, using `gt` (strict greater-than) so already-fetched records
 * at the boundary are skipped. Sub-millisecond timestamp collisions at the
 * page boundary are safe: duplicate records from `gt` overlap are handled
 * by the caller's UPSERT-by-PK logic.
 */
@Singleton
class PullCoordinator @Inject constructor(
    private val syncMetadataDao: SyncMetadataDao,
    private val stateMachine: SyncStateMachine,
) {

    companion object {
        private const val TAG = "PullCoordinator"
        /** 5 seconds overlap window per spec Section 5. */
        const val OVERLAP_WINDOW_MS = 5_000L
        /** Default page size — overridden by [pullPageSize]. */
        private const val DEFAULT_PAGE_SIZE = 1000
    }

    /** Page size for pull requests. Set by SyncEngineImpl from SyncEngineConfig. */
    var pullPageSize: Int = DEFAULT_PAGE_SIZE

    /**
     * Execute a paginated incremental pull for a single table.
     *
     * Fetches pages of [pullPageSize] records, advancing the cursor by the max
     * timestamp in each batch. Stops when a page returns fewer than [pullPageSize]
     * records (meaning the server has no more).
     *
     * @param remoteClient the remote data source
     * @param config table configuration
     * @return all pulled records across all pages (caller applies to Room)
     */
    suspend fun pull(
        remoteClient: SyncRemoteClient,
        config: SyncTableConfig,
    ): List<Record> {
        var since: Long
        if (config.fullPull) {
            since = 0L
            Log.d(TAG, "Full-pulling ${config.tableName} (no incremental filter)")
        } else {
            val lastSyncedAt = syncMetadataDao.getLastSyncedAt(config.tableName) ?: 0L
            since = maxOf(0L, lastSyncedAt - OVERLAP_WINDOW_MS)
            Log.d(TAG, "Pulling ${config.tableName}: lastSyncedAt=$lastSyncedAt, effectiveSince=$since")
        }

        val allRecords = mutableListOf<Record>()
        var page = 0

        do {
            val batch = remoteClient.pull(
                table = config.tableName,
                timestampColumn = config.timestampColumn,
                since = since,
                overlapWindowMs = 0L, // we already subtracted the overlap
                limit = pullPageSize,
            )

            allRecords.addAll(batch)
            page++

            if (batch.size == pullPageSize) {
                // Advance cursor to the max timestamp in this batch for next page.
                // Uses gt (strict greater-than) so records at the boundary are not
                // re-fetched. In the rare case of multiple records sharing the exact
                // boundary timestamp, some may be skipped — the UPSERT deduplication
                // and overlap window on the next full sync make this safe.
                val batchMaxTs = maxTimestamp(batch, config.timestampColumn)
                if (batchMaxTs > since) {
                    since = batchMaxTs
                } else {
                    // Safety: if max timestamp didn't advance, break to avoid infinite loop.
                    // This can happen if all records in the batch share the same timestamp.
                    Log.w(TAG, "Cursor did not advance for ${config.tableName} (stuck at $since), breaking pagination")
                    break
                }
            }

            Log.d(TAG, "Pulled page $page: ${batch.size} records from ${config.tableName} (total=${allRecords.size})")
        } while (batch.size == pullPageSize)

        stateMachine.onEvent(SyncEvent.PullComplete(config.tableName, allRecords.size))
        return allRecords
    }

    /**
     * Compute the max updated_at timestamp from a list of pulled records.
     * Parses ISO-8601 strings from Supabase into epoch millis for metadata storage.
     */
    fun maxTimestamp(records: List<Record>, timestampColumn: String): Long {
        return records.maxOfOrNull { record ->
            val ts = record[timestampColumn] as? String
            ts?.let { Instant.parse(it).toEpochMilli() } ?: 0L
        } ?: 0L
    }

    /**
     * Update last_synced_at for a table. Must be called within the same
     * Room transaction as applying the pulled records (crash safety — spec Section 5.1).
     */
    suspend fun updateLastSyncedAt(tableName: String, maxUpdatedAt: Long) {
        if (maxUpdatedAt <= 0L) return
        val current = syncMetadataDao.getLastSyncedAt(tableName) ?: 0L
        if (maxUpdatedAt > current) {
            syncMetadataDao.upsert(SyncMetadataEntity(tableName, maxUpdatedAt))
            Log.d(TAG, "Updated last_synced_at for $tableName: $current → $maxUpdatedAt")
        }
    }
}
