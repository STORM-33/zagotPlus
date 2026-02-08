# Realtime Sync Engine — Specification

> Reusable local-first sync module with Supabase Realtime integration.
> First target: **Zagot+**. Designed to be project-agnostic.

---

## 1. Goals

- **Offline-first** — local DB (Room) is always the source of truth
- **Transparent reconnection** — catch up on missed changes automatically
- **Low-latency cross-device sync** — Realtime for instant propagation when online
- **Resilient** — no data loss on flaky connections, missed events, or partial syncs
- **Reusable** — extract as a module usable across Android/Kotlin projects with Supabase

---

## 2. Architecture Overview

```
┌─────────────┐       ┌──────────────┐       ┌───────────────┐
│  Local DB   │◄─────►│  Sync Engine │◄─────►│   Supabase    │
│   (Room)    │       │              │       │  (REST + RT)  │
└─────────────┘       └──────────────┘       └───────────────┘
                            │
                     ┌──────┴──────┐
                     │ State Machine│
                     └─────────────┘
```

Three layers:
1. **Change Tracker** — records local mutations in an outbox queue
2. **Sync Coordinator** — manages pull/push cycles and state transitions
3. **Realtime Listener** — WebSocket subscription for live remote changes

---

## 3. Sync States

```
┌──────────┐  connectivity restored   ┌─────────────┐  catch-up done   ┌──────────┐
│ OFFLINE  │ ──────────────────────► │ CATCHING_UP │ ────────────────► │  LIVE    │
└──────────┘                          └─────────────┘                   └──────────┘
     ▲                                       │                              │
     │          connectivity lost             │    connectivity lost         │
     └───────────────────────────────────────┴──────────────────────────────┘
```

### OFFLINE
- All reads/writes go to Room
- **Change Tracker** logs every local mutation to an outbox table
- No network calls attempted
- Triggered by: app start without connectivity, network loss

### CATCHING_UP
- Entered on connectivity restored (after debounce — see Section 8)
- Steps (in order):
  1. **Subscribe to Realtime** — open WebSocket, start buffering incoming events (do NOT apply yet)
  2. **Pull remote changes** — fetch all changes where `updated_at > last_synced_at - OVERLAP_WINDOW` (REST). See Section 5 for overlap window rationale.
  3. **Resolve conflicts** — compare pulled remote records against local outbox entries. For each conflict (same PK exists in both outbox and pull), run conflict resolution (LWW by default). Mark losing outbox entries as resolved (don't push them).
  4. **Push local outbox** — send remaining (non-conflicting + winning) outbox entries to Supabase (REST)
  5. **Apply remote changes** to Room (UPSERT, deduplicate by PK + `updated_at`)
  6. **Drain Realtime buffer** — apply buffered events, deduplicated against already-pulled data
  7. **Update `last_synced_at`** + clear buffer — **in a single Room transaction** (see Section 5.1 for crash safety)
  8. Transition to **LIVE**

> **Why pull-before-push?** If two devices edit the same record offline and we push first, our version overwrites the server before we even know theirs exists. Pulling first lets us detect and resolve conflicts before pushing — critical for the reusable module goal. For append-only tables (like Zagot+ transactions), the order doesn't matter since conflicts can't occur.

### LIVE
- Realtime events applied immediately (deduplicated, idempotent)
- Local changes pushed to Supabase immediately (or near-immediately with small debounce)
- **Periodic safety sync** every 15–30 minutes — full pull cycle to catch any missed Realtime events
- On connectivity loss → transition to **OFFLINE**

---

## 4. Change Tracker (Outbox)

### Outbox Table Schema

```sql
-- Room entity
sync_outbox (
    id          INTEGER PRIMARY KEY AUTOINCREMENT,
    table_name  TEXT NOT NULL,
    record_id   TEXT NOT NULL,       -- PK of the changed record (UUID)
    operation   TEXT NOT NULL,       -- INSERT | UPDATE | DELETE
    payload     TEXT NOT NULL,       -- JSON snapshot of the record
    created_at  INTEGER NOT NULL,    -- epoch ms
    synced      INTEGER DEFAULT 0    -- 0 = pending, 1 = synced
)
```

### Rules
- Every local write (insert, update, delete) creates an outbox entry
- Outbox entries are consumed during CATCHING_UP (step 4) and LIVE (immediate push)
- Entries marked `synced = 1` after successful push
- **Pruning:** synced entries retained for at least **one full safety sync cycle** (15–30 min). After a safety sync confirms the record exists on the server (pulled back successfully), the outbox entry can be pruned. This guards against phantom pushes (HTTP 200 returned but server didn't actually commit, or response was lost mid-flight).
- On push failure → retry with exponential backoff, stay in queue

---

## 5. Pull Strategy

### Incremental with Overlap Window
- Track `last_synced_at` per table (stored in a Room metadata table)
- Pull query: `SELECT * FROM {table} WHERE updated_at > {last_synced_at - OVERLAP_WINDOW} ORDER BY updated_at ASC`
- **OVERLAP_WINDOW = 5 seconds** — guards against clock skew and same-timestamp races. Two records committed at the same server timestamp could arrive in different batches; the overlap ensures we re-fetch the boundary and rely on dedup (UPSERT by PK) to handle duplicates cheaply.
- Paginate if needed (Supabase default limit is 1000 rows)
- After successful pull, update `last_synced_at` to the max `updated_at` received

> **Why not server-side cursors?** A monotonic sequence ID would be more reliable than timestamps, but requires a custom Supabase function or trigger to maintain. The overlap window + idempotent UPSERT achieves the same correctness with less infrastructure. If a project needs stronger guarantees, swap the pull strategy to use a sequence column.

### 5.1 Crash Safety — Transaction Boundaries
During CATCHING_UP, multiple steps write to Room. Critical invariant: **`last_synced_at` must not advance past data that hasn't been fully applied.**

- Steps 5 (apply remote changes) and 6 (drain buffer) write records into Room
- Step 7 updates `last_synced_at` and clears the buffer
- **All of step 5 + 6 + 7 must execute in a single Room transaction** (or step 7 must be in the same transaction as step 6)
- If the app crashes mid-apply: `last_synced_at` hasn't moved → next catch-up re-pulls from the same point → safe
- If the app crashes after apply but before `last_synced_at` update: same re-pull → dedup handles duplicates → safe
- If `last_synced_at` updates but buffer isn't drained: those events are lost forever. **This is why they share a transaction.**

```kotlin
// Pseudocode for the critical transaction
db.withTransaction {
    applyRemoteChanges(pulledRecords)     // step 5
    applyBufferedEvents(realtimeBuffer)   // step 6
    updateLastSyncedAt(maxUpdatedAt)      // step 7
}
realtimeBuffer.clear()
```

### Soft Deletes
- Remote deletions must be trackable — use `deleted_at` column instead of hard deletes
- Pull includes soft-deleted records so local DB can mark them accordingly

---

## 6. Realtime Integration

### Subscription Setup
- Subscribe to Postgres Changes on all synced tables
- Filter: `INSERT`, `UPDATE`, `DELETE` events
- Use Supabase Kotlin client's `Realtime` module

### Event Handling

```
Realtime Event Received
    │
    ├─ State == CATCHING_UP?
    │       └─ YES → buffer event (in-memory list)
    │
    └─ State == LIVE?
            └─ YES → apply immediately
                      ├─ Deduplicate (PK + updated_at already exists? skip)
                      └─ UPSERT into Room
```

### Buffering During Catch-Up
- Buffer is an in-memory list of `(table, operation, record)` tuples
- **Maximum buffer size: 1000 events.** If exceeded, discard buffer and fall back to a full re-sync from `last_synced_at` after the current pull completes. This prevents unbounded memory growth when a device has been offline for a long time on an active multi-device setup.
- Drained in step 6 of CATCHING_UP
- Deduplicated: if the pull already fetched a record with same PK and `updated_at >= event.updated_at`, skip the buffered event
- Buffer cleared after drain (within the same Room transaction as `last_synced_at` update — see Section 5.1)

---

## 7. Conflict Resolution

Zagot+ uses **immutable transactions** (reversals, not edits), so true conflicts are rare. For the general module:

### Strategy: Last-Write-Wins (LWW)
- Compare `updated_at` timestamps
- Higher `updated_at` wins
- On tie: server version wins (Supabase is the tiebreaker)

### Zagot+ Specifics
- Transactions are append-only — no conflict possible on transaction records
- Supplier/product metadata could conflict — LWW is acceptable (edits are infrequent)
- Inventory is computed, not stored — no sync needed

### Default vs Custom Resolution
- **LWW is baked into the UPSERT logic** — no interface overhead for the common case. During bulk pulls, every record is applied via `INSERT OR REPLACE` with an `updated_at` guard (`WHERE new.updated_at > existing.updated_at`). This is the fast path.
- **`ConflictResolver` interface** is available for tables that need merge strategies (e.g., combining fields from both versions). Only invoked when explicitly registered for a table — not on the hot path for bulk operations.
- For most projects (including Zagot+), the default UPSERT-with-timestamp-guard is sufficient.

---

## 8. Network Detection

### Android Implementation
- `ConnectivityManager.NetworkCallback` for real-time network state
- **Flapping debounce: 3 seconds** — after `onAvailable()`, wait 3s of stable connectivity before transitioning to CATCHING_UP. Resets if `onLost()` fires during the window. Prevents thrashing the state machine on unstable connections.
- On `onLost()` → transition to OFFLINE immediately (no debounce — fail fast)
- **Validation ping** before trusting connectivity — captive portals and flaky WiFi can report connected without actual internet access
- Ping target: Supabase health endpoint or a lightweight REST call (e.g., `HEAD` on the PostgREST URL)
- WiFi → mobile data handoff: Android fires `onLost` then `onAvailable` — treated as reconnect, debounce applies

---

## 9. Periodic Safety Sync

Even in LIVE state, run a background pull every **15–30 minutes**:

- Supabase Realtime does not guarantee delivery of all Postgres Changes under load
- Safety sync closes any gaps from missed events
- Implemented via WorkManager `PeriodicWorkRequest` (existing infrastructure)
- Same pull logic as CATCHING_UP step 3, but without the buffer/drain (already in LIVE)

---

## 10. Push Strategy

### During LIVE
- Local change → outbox entry → immediate push attempt
- Optional debounce (100–300ms) to batch rapid successive changes
- On failure → entry stays in outbox, retried on next push cycle or safety sync

### During CATCHING_UP
- All pending outbox entries pushed in batch (step 2)
- Order: FIFO by `created_at`
- Conflicts resolved server-side (Supabase RLS + `updated_at` comparison)

### Idempotency
- Every push is an UPSERT keyed on the record's PK
- Safe to retry — duplicate pushes produce the same result

---

## 11. Module API Surface

```kotlin
interface SyncEngine {
    /** Current sync state */
    val state: StateFlow<SyncState>

    /** Start the engine (call on app startup) */
    fun start()

    /** Stop the engine (call on app shutdown) */
    fun stop()

    /** Force an immediate full sync cycle */
    suspend fun syncNow()

    /** Register a table for syncing */
    fun registerTable(config: SyncTableConfig)
}

enum class SyncState {
    OFFLINE,
    CATCHING_UP,
    LIVE
}

data class SyncTableConfig(
    val tableName: String,
    val primaryKey: String = "id",
    val timestampColumn: String = "updated_at",
    val softDeleteColumn: String? = "deleted_at",
    val conflictResolver: ConflictResolver = LastWriteWins,
)

interface ConflictResolver {
    fun resolve(local: Record, remote: Record): Record
}
```

---

## 12. Error Handling

### Push Error Classification

| HTTP Status | Category | Behavior |
|---|---|---|
| Network error / timeout | Transient | Retry with exponential backoff (1s, 2s, 4s... max 5min) |
| 401 Unauthorized | Auth | Refresh token, retry once. If still 401 → pause sync, notify user |
| 409 Conflict | Conflict | Pull latest server version, run conflict resolution, re-push winner |
| 429 Too Many Requests | Rate limit | Respect `Retry-After` header, back off accordingly |
| 400 Bad Request | Terminal | Log error, flag entry for manual review, skip |
| 404 Not Found | Terminal | Log error (table/endpoint missing), skip, alert developer |
| 500+ Server Error | Transient | Retry with backoff, same as network error |

### General Scenarios

| Scenario | Behavior |
|---|---|
| Pull fails | Retry whole catch-up cycle, do not transition to LIVE |
| Realtime disconnects | Re-subscribe, trigger safety sync immediately |
| Realtime misses events | Covered by periodic safety sync |
| App killed during CATCHING_UP | On next start, restart catch-up from last `last_synced_at` (safe — see Section 5.1) |
| Buffer overflow during CATCHING_UP | Discard buffer, complete pull, trigger fresh re-sync (see Section 6) |

---

## 13. Data Flow Summary

```
LOCAL CHANGE (user action)
  → Room write
  → Outbox entry
  → If LIVE: push to Supabase immediately
  → If OFFLINE: queued until CATCHING_UP

REMOTE CHANGE (another device)
  → Supabase Realtime event (if LIVE)
      → Deduplicate → UPSERT into Room
  → OR: pulled during catch-up / safety sync
      → Deduplicate → UPSERT into Room
```

---

## 14. Testing Infrastructure

### 14.1 Test Layers

| Layer | What | Tools |
|---|---|---|
| Unit | State machine transitions, conflict resolution, outbox logic, dedup | JUnit 5, Turbine (Flow testing), MockK |
| Integration | Room ↔ SyncEngine, Realtime event processing, pull/push cycles | Robolectric, Room in-memory DB |
| End-to-End | Full offline → reconnect → live flow across two simulated devices | Android Instrumented Tests (Espresso), real Supabase test project |

### 14.2 Fakes & Test Doubles

```kotlin
/** In-memory Supabase replacement for unit/integration tests */
class FakeSupabaseClient : SupabaseClientContract {
    val pushedRecords = mutableListOf<Record>()
    val remoteTables = mutableMapOf<String, MutableList<Record>>()
    
    /** Simulate remote changes appearing */
    fun injectRemoteChange(table: String, record: Record)
    
    /** Simulate network failure on next N calls */
    fun failNextCalls(count: Int, error: Exception)
}

/** Controllable network state for tests */
class FakeNetworkMonitor : NetworkMonitor {
    private val _isConnected = MutableStateFlow(false)
    override val isConnected: StateFlow<Boolean> = _isConnected
    
    fun simulateOnline() { _isConnected.value = true }
    fun simulateOffline() { _isConnected.value = false }
}

/** Fake Realtime channel that lets tests emit events manually */
class FakeRealtimeChannel : RealtimeChannelContract {
    private val _events = MutableSharedFlow<RealtimeEvent>()
    
    fun emitEvent(event: RealtimeEvent)
    fun simulateDisconnect()
    fun simulateReconnect()
}
```

### 14.3 Critical Test Scenarios

#### State Machine
- `OFFLINE → connectivity restored → CATCHING_UP → sync completes → LIVE`
- `LIVE → connectivity lost → OFFLINE`
- `CATCHING_UP → connectivity lost before sync completes → OFFLINE` (must not transition to LIVE)
- `CATCHING_UP → app killed → restart → resumes from last_synced_at`

#### Outbox
- Local write while OFFLINE creates outbox entry
- Outbox entries pushed in FIFO order during catch-up
- Failed push retries with backoff, entry stays in queue
- Successful push marks entry as synced
- Duplicate push (retry) is idempotent — no duplicate records server-side

#### Pull & Deduplication
- Incremental pull fetches only records newer than `last_synced_at`
- Pulled records UPSERT into Room (no duplicates)
- Soft-deleted remote records reflected locally
- `last_synced_at` updated only after successful pull

#### Realtime Buffer
- Events received during CATCHING_UP are buffered, not applied
- Buffer is drained after pull completes
- Buffered event for a record already pulled (same PK, same or older `updated_at`) is skipped
- Buffered event for a record with newer `updated_at` than pulled version is applied
- Buffer cleared after drain

#### Conflict Resolution
- LWW: remote `updated_at` > local → remote wins
- LWW: local `updated_at` > remote → local wins
- Tie: server version wins
- Custom `ConflictResolver` is called when registered

#### Realtime in LIVE
- Incoming event applied immediately
- Duplicate event (same PK + `updated_at`) is no-op
- Realtime disconnect triggers re-subscribe + immediate safety sync
- Missed event caught by periodic safety sync

#### Safety Sync
- Runs on schedule even when LIVE
- Catches records missed by Realtime
- Does not create duplicates (idempotent pull)

#### Network Edge Cases
- Rapid online/offline toggling (flapping) — debounce, don't thrash state machine
- Captive portal (reports connected but no actual internet) — validation ping fails → stay OFFLINE
- WiFi → mobile data handoff — treat as reconnect, trigger catch-up

### 14.4 Test Supabase Project

- Dedicated Supabase project for testing (separate from production)
- Seeded via SQL fixtures before each E2E test run
- Tables mirror production schema
- RLS policies replicated
- Realtime enabled on all synced tables
- **CI**: use Supabase CLI local dev (`supabase start`) for isolated, reproducible test environments — no network dependency in CI

### 14.5 CI Integration

```yaml
# Conceptual — adapt to actual CI (GitHub Actions)
test-sync-engine:
  steps:
    - name: Start local Supabase
      run: supabase start
    
    - name: Unit + Integration tests
      run: ./gradlew :sync-engine:test
    
    - name: Instrumented tests (emulator)
      run: ./gradlew :app:connectedAndroidTest
      # Uses local Supabase — no flaky network deps
    
    - name: Stop Supabase
      run: supabase stop
```

### 14.6 Observability for Testing

- `SyncEngine` exposes a `SyncLog` (list of recent sync events with timestamps)
- Useful for both tests (assertions) and debug builds (on-screen sync status)
- Log entries: `PUSH_SUCCESS`, `PUSH_FAIL`, `PULL_COMPLETE`, `RT_EVENT_APPLIED`, `RT_EVENT_BUFFERED`, `RT_EVENT_SKIPPED`, `SAFETY_SYNC`, `STATE_CHANGE`

```kotlin
data class SyncLogEntry(
    val timestamp: Long,
    val event: SyncEvent,
    val table: String? = null,
    val recordId: String? = null,
    val details: String? = null,
)

interface SyncEngine {
    // ... existing API ...
    val syncLog: StateFlow<List<SyncLogEntry>>
}
```

---

## 15. Implementation Order

1. **State machine** — OFFLINE / CATCHING_UP / LIVE transitions (the backbone everything hangs off of)
2. **Fakes + test harness** — `FakeSupabaseClient`, `FakeNetworkMonitor`, `FakeRealtimeChannel` + state machine tests
3. **Sync metadata table** — `last_synced_at` per table, overlap window logic
4. **Outbox table + Change Tracker** — instrument all Room DAOs to write outbox entries
5. **Pull logic** — incremental fetch with overlap window, UPSERT into Room, crash-safe transactions + unit tests
6. **Push logic** — drain outbox to Supabase, error classification + unit tests
7. **Conflict resolution** — pull-before-push reconciliation during CATCHING_UP + conflict tests
8. **Network monitor** — ConnectivityManager integration, flapping debounce, validation ping + edge case tests
9. **Realtime subscription** — Supabase Kotlin client, event buffering with cap + buffer tests
10. **Safety sync** — periodic WorkManager job, outbox pruning verification
11. **Integration tests** — full offline → reconnect → live flow
12. **CI pipeline** — local Supabase, emulator, automated runs
13. **E2E tests** — two-device simulation
14. **Extract as module** — decouple from Zagot+ specifics

---

## 16. Dependencies

- `io.github.jan-tennert.supabase:realtime-kt` — Realtime client
- `io.github.jan-tennert.supabase:postgrest-kt` — REST client (existing)
- `androidx.room:room-runtime` — local DB (existing)
- `androidx.work:work-runtime-ktx` — periodic safety sync (existing)

---

## 17. Open Questions

- [ ] Exact debounce window for LIVE pushes — needs testing with real usage patterns (100–300ms range)
- [ ] Should safety sync interval be configurable per project? (leaning yes, default 15min)
- [ ] Row-level security (RLS) implications for Realtime subscriptions — need to verify Supabase Realtime respects RLS policies
- [ ] Should the module handle schema migrations, or leave that to the host project?
- [ ] Overlap window (currently 5s) — may need tuning based on observed Supabase commit latency in production
