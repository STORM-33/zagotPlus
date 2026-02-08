package com.zagot.syncengine.db

/**
 * Provides outbox entries for one-time migrations into the sync engine.
 */
interface OutboxMigrationProvider {
    suspend fun buildOutboxEntries(now: Long): List<SyncOutboxEntity>
}
