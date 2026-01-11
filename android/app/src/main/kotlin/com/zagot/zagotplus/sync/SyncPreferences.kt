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
     * Get last sync timestamp. Returns epoch (1970-01-01) if never synced.
     */
    fun getLastSyncTimestamp(): Instant {
        val millis = prefs.getLong(KEY_LAST_SYNC, 0L)
        return if (millis > 0) Instant.ofEpochMilli(millis) else Instant.EPOCH
    }

    /**
     * Update last sync timestamp to now.
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
    }
}
