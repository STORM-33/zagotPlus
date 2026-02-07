/**
 * Sync engine utilities and configuration.
 *
 * This package contains shared utilities and configuration classes:
 * - [SyncTableConfig] - Configuration for a table registered for sync
 * - [JsonUtil] - JSON ↔ Record conversion utilities
 * - [SyncJson] - Shared Json instance with encodeDefaults = true
 * - [RawDao] - Qualifier for raw (unwrapped) Room DAOs
 * - [ConflictResolver] / [LastWriteWins] - Conflict resolution strategies
 */
package com.zagot.zagotplus.sync.engine.util
