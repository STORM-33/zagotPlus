# History Index

Last updated: 2026-01-11

## Plans

| Plan | Date | Sessions | Status |
|------|------|----------|--------|
| Phase 0: Setup | 2026-01-11 | 3 | completed |
| Phase 1: Data Layer | 2026-01-11 | 4 | completed |

## Decisions Log

| Date | Plan | Decision | Reasoning | Session |
|------|------|----------|-----------|---------|
| 2026-01-11 | Phase 0 | AGP 8.3.1 (not 8.2.0) | JDK 21 compatibility requires AGP 8.3+ | init-android |
| 2026-01-11 | Phase 0 | Gradle 8.4 (not 8.2) | Required by AGP 8.3.1 | init-android |
| 2026-01-11 | Phase 0 | Single migration file | Atomicity and simplicity over multiple files | init-supabase |
| 2026-01-11 | Phase 0 | Fixed UUIDs for seed data | Consistency across environments | init-supabase |
| 2026-01-11 | Phase 0 | Permissive RLS for anon | Single-org use case; can tighten later | init-supabase |
| 2026-01-11 | Phase 0 | Defer launcher icons | Will add in Phase 4 polish | init-android |
| 2026-01-11 | Phase 1 | UUID→TEXT, BigDecimal→TEXT | Preserve precision in SQLite | room-schema |
| 2026-01-11 | Phase 1 | Instant→INTEGER (epoch millis) | Efficient storage for timestamps | room-schema |
| 2026-01-11 | Phase 1 | Index on transfer_location_id | Prevent full table scans on transfer queries | room-schema |
| 2026-01-11 | Phase 1 | Domain models separate from entities | Clean architecture boundary | repository |
| 2026-01-11 | Phase 1 | Inventory computed on-the-fly | No cached state to synchronize | repository |
| 2026-01-11 | Phase 1 | Sales stored as negative weight | Simplifies inventory sum calculation | repository |
| 2026-01-11 | Phase 1 | Transfer UUID prefix linking | -out/-in suffix pairs linked transactions | repository |
| 2026-01-11 | Phase 1 | SharedPreferences for sync timestamp | Simpler than DataStore for single value | supabase-sync |
| 2026-01-11 | Phase 1 | Individual transaction push | Partial success possible on batch failure | supabase-sync |
| 2026-01-11 | Phase 1 | @HiltWorker over custom factory | Simpler Hilt integration for WorkManager | sync-worker |
| 2026-01-11 | Phase 1 | KEEP policy for periodic work | Prevents duplicate schedule registration | sync-worker |
| 2026-01-11 | Phase 1 | fallbackToDestructiveMigration | Dev mode convenience (will add migrations for prod) | room-schema |
| 2026-01-11 | Phase 1 | OnConflictStrategy.REPLACE in DAOs | Enables sync upserts for duplicate handling | room-schema |

## Lessons Learned

| Date | Plan | Lesson | Context |
|------|------|--------|---------|
| 2026-01-11 | Phase 0 | JDK 21 requires AGP 8.3+ and Gradle 8.4+ | Windows dev machine with JDK 21 |
| 2026-01-11 | Phase 0 | LF→CRLF warnings are normal on Windows | Git autocrlf setting |
| 2026-01-11 | Phase 0 | Version catalog (libs.versions.toml) keeps deps organized | Modern Gradle pattern |
| 2026-01-11 | Phase 1 | Room KSP warns about missing FK indexes | Add indexes proactively on foreign keys |
| 2026-01-11 | Phase 1 | Flow-based DAOs enable reactive UI | Use Flow for lists, suspend for one-shot |
| 2026-01-11 | Phase 1 | BuildConfig fields for API credentials | Keeps secrets out of code |
| 2026-01-11 | Phase 1 | TypeConverters validate at compile time | Room KSP catches mapping errors early |
| 2026-01-11 | Phase 1 | Extension functions clean entity mapping | Keep repository implementations concise |
| 2026-01-11 | Phase 1 | WorkManager constraints require proper policy | KEEP prevents duplicate periodic schedules |

## Patterns & Solutions

| Problem | Solution | Used In |
|---------|----------|---------|
| Conflict-free sync | UNIQUE constraint on local_id (UUID generated client-side) | init-supabase, repository |
| Computed inventory | Database VIEW summing transactions | init-supabase |
| Hilt setup | @HiltAndroidApp on App class, @AndroidEntryPoint on Activity | init-android |
| Dependency management | Gradle version catalog (libs.versions.toml) | init-android |
| Complex type persistence | TypeConverters for UUID, Instant, BigDecimal | room-schema |
| Reactive data flow | Flow-based DAO queries + collect in UI | room-schema |
| Entity-domain mapping | Extension functions in repository impl | repository |
| Atomic linked transactions | Shared UUID prefix with -out/-in suffix | repository |
| Background sync | WorkManager + CoroutineWorker + @HiltWorker | sync-worker |
| Sync status UI | StateFlow in SyncStatusRepository | sync-worker |
| Deduplication on pull | Skip existing by local_id lookup | supabase-sync |
| Partial push success | Individual transaction upsert, not batch | supabase-sync |
| Offline-first architecture | Repository layer over Room + Supabase | repository, supabase-sync |
| Clean architecture layers | Domain models (entities) ← Repository interfaces ← UI | repository |
| Pending sync tracking | syncedAt=null for unsynced transactions | repository, supabase-sync |
| Periodic background work | WorkManager with ExistingPeriodicWorkPolicy.KEEP | sync-worker |
| Network-aware sync | Constraints.Builder().setRequiredNetworkType(CONNECTED) | sync-worker |

## Tags

#android #supabase #hilt #compose #offline-first #jdk21 #room #workmanager #sync
