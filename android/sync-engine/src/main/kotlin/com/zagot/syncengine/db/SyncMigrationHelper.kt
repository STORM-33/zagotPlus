package com.zagot.syncengine.db

import android.content.Context
import android.util.Log
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time migration: moves existing `synced_at IS NULL` records into the
 * sync outbox so the new engine can push them (spec Section 9, Step 6).
 *
 * Uses SharedPreferences flag to ensure this runs only once per install.
 */
@Singleton
class SyncMigrationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    private val migrationProvider: OutboxMigrationProvider,
    private val outboxDao: SyncOutboxDao,
) {
    companion object {
        private const val TAG = "SyncMigrationHelper"
        private const val PREFS_NAME = "sync_engine_migration"
        private const val KEY_OUTBOX_MIGRATED = "outbox_migration_done"
    }

    /**
     * Run migration if not already done.
     * Queries all `synced_at IS NULL` records and creates outbox entries.
     */
    suspend fun migrateIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_OUTBOX_MIGRATED, false)) {
            Log.d(TAG, "Outbox migration already completed")
            return
        }

        Log.d(TAG, "Starting outbox migration for unsynced records")
        val now = System.currentTimeMillis()
        val entries = migrationProvider.buildOutboxEntries(now)
        if (entries.isNotEmpty()) {
            outboxDao.insertAll(entries)
        }
        val total = entries.size

        // Mark migration as done
        prefs.edit().putBoolean(KEY_OUTBOX_MIGRATED, true).apply()
        Log.d(TAG, "Outbox migration complete: $total records migrated")
    }
}
