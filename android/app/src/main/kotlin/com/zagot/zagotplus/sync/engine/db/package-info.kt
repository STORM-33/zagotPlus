/**
 * Sync engine database layer.
 *
 * This package contains Room entities and DAOs for sync metadata:
 * - [SyncOutboxEntity] / [SyncOutboxDao] - Tracks pending local changes
 * - [SyncMetadataEntity] / [SyncMetadataDao] - Tracks last_synced_at per table
 * - [SyncMigrationHelper] - One-time migration from old sync system
 *
 * The outbox pattern ensures all local writes are captured and eventually synced.
 */
package com.zagot.zagotplus.sync.engine.db
