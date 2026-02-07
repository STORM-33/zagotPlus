package com.zagot.zagotplus.sync.engine.dao

import android.util.Log
import com.zagot.zagotplus.sync.engine.api.Record
import com.zagot.zagotplus.sync.engine.api.SyncRemoteClient
import com.zagot.zagotplus.sync.engine.db.SyncMetadataDao
import com.zagot.zagotplus.sync.engine.db.SyncMetadataEntity
import com.zagot.zagotplus.sync.engine.state.SyncEvent
import com.zagot.zagotplus.sync.engine.state.SyncStateMachine
import com.zagot.zagotplus.sync.engine.util.SyncTableConfig
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
    }

    /**
     * Execute an incremental pull for a single table.
     *
     * @param remoteClient the remote data source
     * @param config table configuration
     * @return list of pulled records (caller applies to Room)
     */
    suspend fun pull(
        remoteClient: SyncRemoteClient,
        config: SyncTableConfig,
    ): List<Record> {
        val since: Long
        if (config.fullPull) {
            // Full pull: always fetch all records (e.g., locations with no server_updated_at)
            since = 0L
            Log.d(TAG, "Full-pulling ${config.tableName} (no incremental filter)")
        } else {
            val lastSyncedAt = syncMetadataDao.getLastSyncedAt(config.tableName) ?: 0L
            since = maxOf(0L, lastSyncedAt - OVERLAP_WINDOW_MS)
            Log.d(TAG, "Pulling ${config.tableName}: lastSyncedAt=$lastSyncedAt, effectiveSince=$since")
        }

        val records = remoteClient.pull(
            table = config.tableName,
            timestampColumn = config.timestampColumn,
            since = since,
            overlapWindowMs = 0L, // we already subtracted the overlap
        )

        Log.d(TAG, "Pulled ${records.size} records from ${config.tableName}")
        stateMachine.onEvent(SyncEvent.PullComplete(config.tableName, records.size))

        return records
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
