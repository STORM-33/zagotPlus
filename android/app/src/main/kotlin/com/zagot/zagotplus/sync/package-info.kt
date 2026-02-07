/**
 * Synchronization system for offline-first data.
 *
 * This package provides the bridge between the app's data layer and the
 * realtime sync engine:
 * - [SyncStatus] - UI-facing sync state representation (IDLE, SYNCING, SUCCESS, ERROR)
 * - [SyncManager] - Legacy API bridge for manual sync triggers (rate-limited)
 * - [SyncStatusRepository] - Maps engine state to UI-friendly [SyncStatus]
 *
 * The [engine] sub-package contains the full realtime sync implementation:
 * - [engine.api.SyncEngine] - Main engine interface
 * - [engine.state.SyncState] - OFFLINE, CATCHING_UP, LIVE states
 * - [engine.db.SyncOutbox] - Pending local changes queue
 * - [engine.realtime.RealtimeManager] - Live Supabase subscriptions
 *
 * Migration note: The legacy sync system has been replaced by the new engine.
 * [SyncManager] now delegates to [engine.api.SyncEngine].
 */
package com.zagot.zagotplus.sync
