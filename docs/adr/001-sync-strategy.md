# ADR-001: Sync Strategy

## Status
Accepted

## Context

ZagotPlus needs to synchronize data between multiple Android devices and a central server. Devices may be offline for extended periods. Data includes purchases, sales, inventory transfers, and cash operations.

Key requirements:
- Works offline with full functionality
- Syncs when network is available
- Handles concurrent edits from multiple devices
- Minimizes data loss risk

## Decision

We use a **push-then-pull sync strategy** with server-side timestamps:

1. **Push**: Local unsynced entities are pushed to Supabase using upsert with `local_id` as conflict key
2. **Pull**: Entities updated after `last_sync_timestamp` are pulled using `server_updated_at` filter

Key design choices:
- `local_id`: Device-generated UUID ensures conflict-free inserts across devices
- `server_updated_at`: Postgres trigger sets this on insert/update, ensuring no gaps in sync
- `synced_at`: Local timestamp marking when entity was pushed to server
- **Append-only ledger**: Transactions are never edited, only reversed if needed

## Consequences

### Positive
- Simple and robust - no complex conflict resolution needed
- Works reliably with intermittent connectivity
- Clear sync status visibility for users
- Easy to debug sync issues

### Negative
- Slightly higher storage (local_id + server_id)
- No real-time sync (pull-based)
- Edge case: data created between sync start and end may be missed (mitigated by using end-of-sync timestamp)

## Alternatives Considered

1. **Realtime sync (Supabase Realtime)**: More complex, battery-intensive, not needed for this use case
2. **CRDTs**: Overkill for append-only ledger with rare conflicts
3. **Firebase Firestore**: Would work but adds vendor lock-in, Supabase aligns better with existing Postgres skills
