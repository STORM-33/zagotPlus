/**
 * Core sync engine API and implementation.
 *
 * This package contains the main entry points for the sync engine:
 * - [SyncEngine] - Public API interface for sync operations
 * - [SyncEngineImpl] - Main implementation orchestrating the sync process
 * - [SyncContracts] - Core interfaces (SyncRemoteClient, RealtimeChannelContract, etc.)
 * - [ZagotSyncRegistrar] - Registers all Zagot+ tables for sync
 * - [SafetySyncWorker] - Periodic safety sync WorkManager worker
 * - [SyncEngineModule] - Hilt DI module for the sync engine
 *
 * To use the sync engine:
 * 1. Register tables with [ZagotSyncRegistrar.registerAll]
 * 2. Call [SyncEngine.start] on app startup
 * 3. Observe [SyncEngine.state] for connectivity changes
 */
package com.zagot.zagotplus.sync.engine.api
