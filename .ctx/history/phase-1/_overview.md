# Phase 1: Data Layer

## Purpose

Implement the complete data persistence and synchronization layer that enables offline-first operation with Supabase cloud sync.

## Sessions

| # | Session | Complexity | Description |
|---|---------|------------|-------------|
| 1 | room-schema | medium | Room entities, DAOs, type converters |
| 2 | repository | medium | Repository pattern, offline-first logic |
| 3 | supabase-sync | high | Sync service, push/pull, deduplication |
| 4 | sync-worker | medium | WorkManager, retry logic, sync status |

## Architecture

```
UI Layer
    ↓
Repository (single interface)
    ↓
┌───────────────────────────────────────┐
│           Room Database               │ ← Source of Truth
│  Entities: Location, Product,         │
│            Transaction                 │
│  DAOs: Read/write operations          │
└───────────────────────────────────────┘
    ↓                     ↑
┌───────────┐    ┌───────────────────────┐
│ SyncWorker│───→│   SupabaseSync        │
│ (periodic)│    │   - Push pending      │
└───────────┘    │   - Pull new          │
                 └───────────────────────┘
                           ↓
                 ┌───────────────────────┐
                 │      Supabase         │
                 │   (PostgreSQL)        │
                 └───────────────────────┘
```

## Key Design Points

1. **Room is source of truth** - All UI reads from Room, never directly from Supabase
2. **local_id for deduplication** - UUID generated on device prevents duplicate syncs
3. **synced_at tracking** - Null means pending sync, timestamp means synced
4. **Inventory computed** - Sum transactions in DAO query, don't store inventory state

## Dependencies

- Phase 0 complete (Android project with Hilt, Supabase schema defined)

## Success Criteria

- [ ] Room database stores all entity types
- [ ] Repository provides clean API for UI layer
- [ ] Sync pushes local changes to Supabase
- [ ] Sync pulls remote changes to Room
- [ ] WorkManager handles background sync
- [ ] Sync survives app restart
