/**
 * Data layer for the Zagot+ application.
 *
 * This package and its sub-packages implement the offline-first data architecture:
 * - [local] - Room database with SQLite for on-device persistence
 * - [remote] - Supabase client for cloud synchronization
 * - [repository] - Repository pattern implementations as single source of truth
 * - [preferences] - User preferences and settings storage
 * - [connectivity] - Network connectivity monitoring utilities
 *
 * The data flow follows:
 * UI → ViewModel → Repository → (Local DB + Remote API) → Sync Engine
 *
 * Key DI modules:
 * - [DispatchersModule] - Provides coroutine dispatchers for IO/Default operations
 * - [DatabaseModule] - Room database and DAO providers
 * - [SupabaseModule] - Supabase client configuration
 * - [RepositoryModule] - Repository interface bindings
 */
package com.zagot.zagotplus.data
