/**
 * Sync-aware DAO wrappers and coordinators.
 *
 * This package contains DAO wrappers that instrument writes for sync:
 * - [SyncAwareLocationDao], [SyncAwareProductDao], etc. - Outbox-instrumented DAO wrappers
 * - [ConflictReconciler] - Resolves conflicts between local and remote data
 * - [PullCoordinator] - Fetches remote changes with incremental sync support
 * - [PushCoordinator] - Drains outbox to Supabase with error handling and retry
 *
 * The [SyncAwareXxxDao] wrappers intercept writes and create outbox entries
 * automatically. Views use these via DI, while the sync engine uses raw DAOs
 * (annotated with @[RawDao]) to avoid recursive outbox entries.
 */
package com.zagot.zagotplus.sync.engine.dao
