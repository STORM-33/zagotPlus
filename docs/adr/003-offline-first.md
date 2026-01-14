# ADR-003: Offline-First Architecture

## Status
Accepted

## Context

Agricultural purchasing operations often occur in areas with poor or no network connectivity. The app must function fully offline and sync when connectivity returns.

## Decision

We implement an **offline-first architecture**:

```
UI Layer
    ↓ (observes)
Room Database (Source of Truth)
    ↓ (syncs to)
Supabase (Remote Storage)
```

Key components:

1. **Room Database**: Local SQLite database is the source of truth
   - All reads come from Room
   - All writes go to Room first
   - UI observes Room via Flow

2. **SyncWorker**: Background sync via WorkManager
   - Periodic sync every 15 minutes
   - Manual sync trigger available
   - Runs only when network is connected

3. **Sync Status**: Visible to users
   - Each entity has `synced_at` field
   - UI shows sync indicator per item
   - Global sync status in app bar

4. **Conflict Handling**: Last-write-wins with local_id deduplication
   - Rare conflicts due to append-only model
   - Server UNIQUE constraint on local_id prevents duplicates

## Consequences

### Positive
- App works fully offline
- Fast UI (reads from local DB)
- Battery efficient (periodic sync, not continuous)
- User has visibility into sync status

### Negative
- Stale data possible between devices
- Slightly complex architecture
- Need to handle sync errors gracefully
- Storage grows on device

## Implementation Details

### Entity Lifecycle
1. User creates entity → Room insert with `synced_at = null`
2. UI immediately shows entity (from Room)
3. SyncWorker pushes to Supabase
4. On success: `synced_at = now()`
5. UI shows sync checkmark

### Network Handling
- WorkManager constraints: `NetworkType.CONNECTED`
- Exponential backoff on failures
- Max 3 retries before marking as failed
- Manual retry available to user
