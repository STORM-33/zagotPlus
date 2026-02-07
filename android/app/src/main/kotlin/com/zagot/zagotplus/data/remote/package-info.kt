/**
 * Remote data layer for Supabase integration.
 *
 * This package provides:
 * - [SupabaseModule] - Hilt module configuring the Supabase client
 * - [SupabaseAuthManager] - Authentication state management
 * - [SupabaseStorageHelper] - Image upload/download operations
 *
 * The Supabase client is configured with:
 * - Postgrest - REST API for database operations
 * - Realtime - WebSocket subscriptions for live updates
 * - Storage - File storage for product images
 *
 * Note: Row Level Security (RLS) is planned but not yet enforced.
 * Currently using anon key for all operations.
 */
package com.zagot.zagotplus.data.remote
