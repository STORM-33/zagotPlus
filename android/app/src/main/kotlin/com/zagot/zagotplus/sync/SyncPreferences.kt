package com.zagot.zagotplus.sync

import android.content.Context
import android.content.SharedPreferences
import dagger.hilt.android.qualifiers.ApplicationContext
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Manages sync-related preferences using SharedPreferences.
 * Tracks last sync timestamp for incremental pull operations.
 * 
 * Note on precision: We store timestamps in milliseconds, but Supabase's server_updated_at
 * may use microsecond precision. To avoid missing records that were updated within the same
 * millisecond, we subtract 1ms when returning the timestamp for queries. This may cause
 * some records to be re-pulled, but deduplication handles this safely.
 */
@Singleton
class SyncPreferences @Inject constructor(
    @ApplicationContext context: Context
) {
    private val prefs: SharedPreferences = context.getSharedPreferences(
        PREFS_NAME,
        Context.MODE_PRIVATE
    )

    /**
     * Get last sync timestamp for querying. Returns epoch (1970-01-01) if never synced.
     * 
     * Subtracts 1ms buffer to handle precision differences between local storage (ms)
     * and server timestamps (potentially microseconds). This ensures we don't miss
     * records updated within the same millisecond window.
     */
    fun getLastSyncTimestamp(): Instant {
        val millis = prefs.getLong(KEY_LAST_SYNC, 0L)
        return if (millis > 0) {
            // Subtract 1ms buffer to handle precision differences
            Instant.ofEpochMilli(millis - TIMESTAMP_BUFFER_MS)
        } else {
            Instant.EPOCH
        }
    }

    /**
     * Update last sync timestamp.
     */
    fun setLastSyncTimestamp(timestamp: Instant) {
        prefs.edit().putLong(KEY_LAST_SYNC, timestamp.toEpochMilli()).apply()
    }

    /**
     * Clear sync timestamp (for testing or reset).
     */
    fun clearLastSyncTimestamp() {
        prefs.edit().remove(KEY_LAST_SYNC).apply()
    }

    companion object {
        private const val PREFS_NAME = "zagot_sync_prefs"
        private const val KEY_LAST_SYNC = "last_sync_timestamp"
        
        /** Buffer to subtract from timestamp to handle ms/microsecond precision differences */
        private const val TIMESTAMP_BUFFER_MS = 1L
    }
}
