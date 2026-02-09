package com.zagot.syncengine.db

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Room migration helpers for sync_outbox schema changes.
 * Apps must include these in their Room migration list — they are NOT auto-applied.
 */
object SyncOutboxMigrations {
    /**
     * Migration to add fail_reason and push_attempts columns to sync_outbox.
     * Apps must include this in their Room migration list.
     *
     * @param fromVersion the database version before this migration
     * @param toVersion the database version after this migration
     */
    fun addFailureTracking(fromVersion: Int, toVersion: Int): Migration {
        return object : Migration(fromVersion, toVersion) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_outbox ADD COLUMN fail_reason TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sync_outbox ADD COLUMN push_attempts INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
