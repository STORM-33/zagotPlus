# Plan: Phase 1 - Data Layer

Created: 2026-01-11
Status: active

## Overview

Implement the local data layer (Room) and Supabase sync infrastructure for offline-first operation.

## Progress

- Total sessions: 4
- Completed: 4
- Blocked: 0
- Remaining: 0

## Historical Context

**Relevant decisions from Phase 0:**
- Conflict-free sync: UNIQUE constraint on `local_id` (UUID generated client-side)
- Computed inventory: Database VIEW summing transactions
- Hilt setup: `@HiltAndroidApp` on App class, `@AndroidEntryPoint` on Activity

**Patterns to reuse:**
- Gradle version catalog for dependency management

## Phases

### Phase 1: Data Layer
Status: pending
Implements the complete data layer: Room database for local storage, repositories for data access, Supabase client for remote operations, and WorkManager for background sync.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 1 | room-schema | medium | completed | none |
| 2 | repository | medium | completed | room-schema |
| 3 | supabase-sync | high | completed | room-schema |
| 4 | sync-worker | medium | completed | repository, supabase-sync |

## Dependencies Graph

```
room-schema -> repository
room-schema -> supabase-sync
repository -> sync-worker
supabase-sync -> sync-worker
```

Execution order: room-schema → (repository || supabase-sync) → sync-worker

## Open Questions

- none

## Notes

- Room entities must match Supabase schema exactly for sync to work
- Repository provides single source of truth interface to UI
- Supabase-sync is highest complexity due to bidirectional sync logic
- Sessions 2 and 3 can run in parallel after session 1
