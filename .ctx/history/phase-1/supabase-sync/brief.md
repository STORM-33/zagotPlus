# Session Brief: supabase-sync

Type: feature
Phase: phase-1
Complexity: high
Created: 2026-01-11

## Objective

Implement Supabase client and bidirectional sync service with push/pull operations and deduplication.

## Background

Devices operate offline and sync when connected. Sync must handle:
1. Push: Local changes (syncedAt=null) → Supabase
2. Pull: New remote changes → Room
3. Deduplication: Same record synced twice must not create duplicates

The `local_id` UNIQUE constraint in Supabase handles deduplication automatically via upsert.

## Requirements

- [ ] Add Supabase Kotlin SDK dependency
- [ ] Create `SupabaseClient` singleton with proper config
- [ ] Create `SyncService` with push/pull methods
- [ ] Implement push: query unsynced → upsert to Supabase → mark synced
- [ ] Implement pull: fetch since lastSync → insert/update Room
- [ ] Track `lastSyncTimestamp` in SharedPreferences or DataStore
- [ ] Handle network errors gracefully (don't crash, log, retry later)
- [ ] Configure Hilt module for Supabase injection

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `MASTER_PLAN.md` (Sync Strategy section)
- `supabase/migrations/` (schema reference)
- `data/local/entity/*.kt`
- `data/local/dao/*.kt`
- `android/gradle/libs.versions.toml`

## Implementation Notes

**Supabase SDK setup:**
```kotlin
val supabase = createSupabaseClient(
    supabaseUrl = BuildConfig.SUPABASE_URL,
    supabaseKey = BuildConfig.SUPABASE_ANON_KEY
) {
    install(Postgrest)
    install(Realtime) // optional for now
}
```

**Push logic:**
```kotlin
suspend fun pushPendingTransactions() {
    val pending = transactionDao.getUnsynced()
    pending.forEach { entity ->
        supabase.postgrest["transactions"]
            .upsert(entity.toSupabaseDto()) {
                onConflict = "local_id"
            }
        transactionDao.markSynced(entity.id, Instant.now())
    }
}
```

**Pull logic:**
```kotlin
suspend fun pullNewTransactions() {
    val lastSync = preferences.getLastSync()
    val remote = supabase.postgrest["transactions"]
        .select { filter { gt("created_at", lastSync) } }
        .decodeList<TransactionDto>()
    remote.forEach { dto ->
        transactionDao.upsertFromRemote(dto.toEntity())
    }
    preferences.setLastSync(Instant.now())
}
```

**File locations:**
- Client: `data/remote/SupabaseClient.kt`
- DTOs: `data/remote/dto/`
- Sync service: `sync/SyncService.kt`
- Hilt module: `data/remote/SupabaseModule.kt`

**Key considerations:**
- Store Supabase URL/key in BuildConfig (local.properties)
- Use upsert with `local_id` as conflict key for deduplication
- Pull must handle records that already exist locally
- Consider batching for large sync sets

## TDD

Mode: encouraged

### Test Plan
- [ ] Test: Push marks records as synced after success
- [ ] Test: Push handles network failure gracefully
- [ ] Test: Pull inserts new remote records
- [ ] Test: Pull updates existing records (by local_id)
- [ ] Test: Deduplication prevents double-insert

### Test Command
```
./gradlew :app:testDebugUnitTest
```

## Success Criteria

- [ ] Supabase client configured and injectable
- [ ] Push sends unsynced records to Supabase
- [ ] Pull fetches new records from Supabase
- [ ] Sync handles network errors without crashing
- [ ] local_id deduplication works
- [ ] Build succeeds: `./gradlew assembleDebug`

## Out of Scope

- WorkManager scheduling (session 4)
- Realtime subscriptions (future enhancement)
- Conflict resolution UI (not needed - append-only)
- Sync status UI (Phase 2)

## Dependencies

- Requires: room-schema
- Blocks: sync-worker
