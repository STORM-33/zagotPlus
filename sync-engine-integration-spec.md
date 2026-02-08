# Sync Engine Integration Spec — Wiring to Zagot+ Entities

> Connects the generic sync engine (`sync/engine/`) to the actual Zagot+ tables,
> DAOs, DTOs, and entities. Replaces the legacy `SyncService` + `SyncWorker` + `SyncManager`.

---

## 1. Goal

Replace the monolithic `SyncService` with the new sync engine while preserving all existing behavior:
- Offline-first (Room is source of truth)
- FK-ordered push/pull
- Server-wins conflict resolution via `server_updated_at`
- Typed entity ↔ DTO conversion
- Outbox-based change tracking (replacing `synced_at IS NULL` queries)
- Realtime for instant cross-device sync (new capability)

---

## 2. Tables to Register

Six syncable tables, registered in **FK dependency order**:

| # | Supabase Table | Room Entity | DAO | FK Dependencies |
|---|---|---|---|---|
| 1 | `locations` | `LocationEntity` | `LocationDao` | None (reference data) |
| 2 | `products` | `ProductEntity` | `ProductDao` | None (reference data) |
| 3 | `expense_categories` | `ExpenseCategoryEntity` | `ExpenseCategoryDao` | None |
| 4 | `purchase_batches` | `PurchaseBatchEntity` | `PurchaseBatchDao` | `locations` |
| 5 | `sale_batches` | `SaleBatchEntity` | `SaleBatchDao` | `locations` |
| 6 | `transactions` | `TransactionEntity` | `TransactionDao` | `locations`, `products`, `purchase_batches`, `sale_batches` |
| 7 | `cash_operations` | `CashOperationEntity` | `CashOperationDao` | `locations`, `expense_categories`, `purchase_batches` |

### Registration order matters
Pull must apply parents before children. Push must push parents before children.
The engine processes tables in registration order — register in the order above.

---

## 3. Timestamp Handling

### The Problem
- Supabase stores `server_updated_at` as ISO-8601 string (`"2026-02-07T18:00:00.123456Z"`)
- Room stores it as `Instant` (converted via `Converters`)
- The sync engine currently uses `Long` epoch millis throughout
- `SyncMetadataEntity.lastSyncedAt` is `Long`

### The Solution
The **integration layer** converts between formats. The engine stays generic.

- `SyncRemoteClient.pull()` returns `Record` (`Map<String, Any?>`) — timestamps arrive as strings from Postgrest
- `applyToRoom` callbacks receive `Record`, parse the ISO string → `Instant`, build the entity
- `lastSyncedAt` stored as epoch millis in `sync_metadata` — converted to/from ISO when querying Supabase
- `PullCoordinator` overlap window works on the epoch millis side

Conversion flow:
```
Supabase (ISO string) → Record (String) → applyToRoom → Entity (Instant)
Entity → DTO (String) → Supabase push

lastSyncedAt (Long epoch) → pull query uses ISO string filter → Supabase
```

### Changes to SupabaseSyncRemoteClient
The `pull()` method currently filters with `gt(timestampColumn, effectiveSince.toString())` where `effectiveSince` is a `Long`. This won't work — Supabase expects ISO-8601 for timestamp columns.

Fix: `pull()` must convert the `Long` to ISO-8601 before filtering:
```kotlin
val isoTimestamp = Instant.ofEpochMilli(effectiveSince).toString()
filter { gt(timestampColumn, isoTimestamp) }
```

Similarly, `maxTimestamp` in `PullCoordinator` must parse ISO strings from records:
```kotlin
val ts = record[timestampColumn] as? String
val epochMs = Instant.parse(ts).toEpochMilli()
```

---

## 4. Integration Layer — `ZagotSyncRegistrar`

New class: `sync/engine/ZagotSyncRegistrar.kt`

Responsibilities:
- Register all 7 tables with the sync engine on app startup
- Provide `applyToRoom` callbacks that convert `Record` → Entity → DAO upsert
- Provide `recordFromEntity` helpers for outbox payload generation

### applyToRoom Callback Pattern

Each table gets an `applyToRoom` lambda that:
1. Receives `List<Record>` (generic maps from Supabase)
2. Converts each `Record` to the corresponding DTO (reuse existing `@Serializable` DTOs)
3. Calls `dto.toEntity()` (reuse existing conversion)
4. Calls the appropriate DAO upsert method

```kotlin
// Example for transactions
SyncTableConfig(
    tableName = "transactions",
    primaryKey = "id",
    timestampColumn = "server_updated_at",
    applyToRoom = { records ->
        val entities = records.map { record ->
            TransactionDto.fromRecord(record).toEntity()
        }
        transactionDao.insertAll(entities)
    }
)
```

### Record ↔ DTO Conversion

Add a `fromRecord(record: Record)` factory method to each DTO. This converts the generic `Map<String, Any?>` into the typed DTO.

Why not skip DTOs and go directly to entities? Because:
- DTOs already handle all the string-to-type conversions (UUID, BigDecimal, Instant)
- DTOs have `@Serializable` annotations needed for push serialization
- The existing `toEntity()` and `fromEntity()` methods are battle-tested

---

## 5. Outbox Instrumentation

### Current State
The old sync finds unsynced records via `getUnsynced()` queries (`WHERE synced_at IS NULL`).

### New State
Every local write must create an outbox entry. Two approaches:

**Option A: DAO wrapper (recommended)**
Create a `SyncAwareDao` wrapper that intercepts insert/update/delete calls and creates outbox entries automatically:

```kotlin
class SyncAwareTransactionDao(
    private val dao: TransactionDao,
    private val outboxDao: SyncOutboxDao,
) {
    suspend fun insert(entity: TransactionEntity) {
        dao.insert(entity)
        outboxDao.insert(SyncOutboxEntity(
            tableName = "transactions",
            recordId = entity.id.toString(),
            operation = "INSERT",
            payload = TransactionDto.fromEntity(entity).toJsonString(),
            createdAt = System.currentTimeMillis(),
        ))
    }
    // ... same for update, delete
}
```

**Option B: Room callback**
Use `RoomDatabase.Callback` or `InvalidationTracker` to detect writes. Less precise, harder to get the full record payload.

**Go with Option A.** Create `SyncAware*Dao` wrappers for all 7 tables. The rest of the app uses these wrappers instead of raw DAOs.

### Where writes happen
Audit all places in the codebase that call DAO insert/update methods and route them through the `SyncAware` wrappers. Key locations:
- ViewModels (purchase flow, sale flow, transfer flow)
- Batch creation/voiding
- Product CRUD
- Cash operations
- Expense category CRUD
- The sync engine's own `applyToRoom` callbacks (these should NOT create outbox entries — they're applying remote data)

### Avoiding outbox loops
When the sync engine applies remote records via `applyToRoom`, those writes must NOT generate outbox entries (or they'd be pushed back to the server in an infinite loop). Solution:
- `applyToRoom` calls the **raw DAO** directly (not the SyncAware wrapper)
- Only user-initiated local writes go through the SyncAware wrapper

---

## 6. Push via Engine vs Legacy

### Current legacy push
`SyncService.pushPending*()` methods:
1. Query `getUnsynced()` (WHERE synced_at IS NULL)
2. Fetch server timestamps for conflict check
3. Compare timestamps, skip server-newer records
4. Convert entity → DTO
5. Call `SyncDataSource.push*(dtos)` (Postgrest upsert)
6. Mark as synced (`syncedAt = Instant.now()`)

### New push via engine
`PushCoordinator.pushPending()`:
1. Query outbox for pending entries
2. Parse payload JSON → Record
3. Call `SyncRemoteClient.push()` (Postgrest upsert)
4. Mark outbox entry as synced

The conflict resolution now happens during CATCHING_UP via `ConflictReconciler` (pull-before-push), not during push. Server-wins is handled by the pull phase overwriting local data.

### Migration
- The `synced_at` column on entities can be kept for backward compatibility but is no longer the source of truth for "needs sync"
- The outbox is the new source of truth
- First sync after migration should push all existing `synced_at IS NULL` records to the outbox, then proceed with normal engine flow

---

## 7. Pull via Engine vs Legacy

### Current legacy pull
`SyncService.pullAllInTransaction()`:
1. Fetch remote records for each table via `SyncDataSource.pull*(since)` using typed DTOs
2. Convert DTO → Entity
3. Insert/upsert all in a single Room transaction
4. Track max `server_updated_at`, update `SyncPreferences.lastSyncTimestamp`

### New pull via engine
`PullCoordinator.pull()` + `SyncEngineImpl.executeCatchUp()`:
1. Fetch via `SyncRemoteClient.pull()` → generic `Record` maps
2. `applyToRoom` callback converts Record → DTO → Entity → DAO upsert
3. All in a single Room transaction (crash-safe)
4. Update `sync_metadata.last_synced_at` per table

### Key difference: per-table timestamps
Old sync used a single global `lastSyncTimestamp`. New engine tracks `last_synced_at` per table.
This is better — a failed pull of one table doesn't block others.

---

## 8. Safety Sync Worker

### Wire up WorkManager
`SafetySyncWorker` is implemented but never enqueued. Add to `SyncEngineImpl.start()`:

```kotlin
val safetySyncRequest = PeriodicWorkRequestBuilder<SafetySyncWorker>(
    repeatInterval = 15, repeatIntervalTimeUnit = TimeUnit.MINUTES
).setConstraints(
    Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
).build()

workManager.enqueueUniquePeriodicWork(
    SafetySyncWorker.WORK_NAME,
    ExistingPeriodicWorkPolicy.KEEP,
    safetySyncRequest
)
```

---

## 9. Migration Plan

### Step 1: Add `fromRecord()` to all DTOs
Add `fromRecord(record: Map<String, Any?>)` factory methods.

### Step 2: Create `ZagotSyncRegistrar`
Registers all 7 tables with `SyncTableConfig` + `applyToRoom` callbacks.
Called from `ZagotApp.onCreate()` before `syncEngine.start()`.

### Step 3: Create `SyncAware*Dao` wrappers
One per syncable table. Intercept writes, create outbox entries.

### Step 4: Route app writes through SyncAware DAOs
Update DI module to provide SyncAware wrappers. Audit all DAO injection sites.

### Step 5: Fix timestamp handling
Update `SupabaseSyncRemoteClient.pull()` to convert epoch millis → ISO-8601 for Supabase queries.
Update `PullCoordinator.maxTimestamp()` to parse ISO strings → epoch millis.

### Step 6: Migrate existing unsynced records
On first launch after update, query all `synced_at IS NULL` records across all tables and create outbox entries for them. Then clear the migration flag.

### Step 7: Wire SafetySyncWorker
Enqueue periodic work in `SyncEngineImpl.start()`.

### Step 8: Wire LIVE push
In `SyncEngineImpl`, when state is LIVE and a new outbox entry is created, trigger immediate push via `PushCoordinator.pushSingle()`.

### Step 9: Test end-to-end
- Offline write → outbox entry created
- Reconnect → CATCHING_UP → pull, conflict resolve, push, apply, drain, LIVE
- LIVE mode → local write → immediate push → other device gets Realtime event
- Safety sync catches missed Realtime events

### Step 10: Archive legacy sync
- Move `SyncService.kt`, `SyncWorker.kt`, `SyncManager.kt`, `SyncModule.kt`, `SyncPreferences.kt`, `SyncDataSource.kt`, `SupabaseSyncDataSource.kt`, `SyncResult.kt`, `SyncStatus.kt`, `SyncStatusRepository.kt` to `sync/legacy/` or delete
- Remove `SyncModule` from Hilt
- Remove any UI references to old sync status

---

## 10. Files to Create

| File | Purpose |
|---|---|
| `sync/engine/ZagotSyncRegistrar.kt` | Registers all tables, provides applyToRoom callbacks |
| `sync/engine/SyncAwareTransactionDao.kt` | Outbox-instrumented wrapper for TransactionDao |
| `sync/engine/SyncAwarePurchaseBatchDao.kt` | Outbox-instrumented wrapper for PurchaseBatchDao |
| `sync/engine/SyncAwareSaleBatchDao.kt` | Outbox-instrumented wrapper for SaleBatchDao |
| `sync/engine/SyncAwareProductDao.kt` | Outbox-instrumented wrapper for ProductDao |
| `sync/engine/SyncAwareCashOperationDao.kt` | Outbox-instrumented wrapper for CashOperationDao |
| `sync/engine/SyncAwareExpenseCategoryDao.kt` | Outbox-instrumented wrapper for ExpenseCategoryDao |
| `sync/engine/SyncAwareLocationDao.kt` | Outbox-instrumented wrapper for LocationDao |
| `sync/engine/SyncMigrationHelper.kt` | One-time migration of `synced_at IS NULL` → outbox |

## 11. Files to Modify

| File | Change |
|---|---|
| All DTOs (`*Dto.kt`) | Add `fromRecord(record: Record)` factory method |
| `SupabaseSyncRemoteClient.kt` | Fix timestamp ISO conversion in `pull()` |
| `PullCoordinator.kt` | Fix `maxTimestamp()` to parse ISO strings |
| `SyncEngineImpl.kt` | Add WorkManager enqueue, LIVE push trigger |
| `ZagotApp.kt` | Call registrar before engine start |
| `SyncEngineModule.kt` | Provide SyncAware DAOs, remove old sync module bindings |
| `DatabaseModule.kt` | Expose raw DAOs for applyToRoom (non-outbox path) |
| All ViewModels using DAOs | Switch to SyncAware DAO injections |

## 12. Files to Archive/Delete

| File | Reason |
|---|---|
| `sync/SyncService.kt` | Replaced by SyncEngine |
| `sync/SyncWorker.kt` | Replaced by SafetySyncWorker |
| `sync/SyncManager.kt` | Replaced by SyncEngine.start() |
| `sync/SyncModule.kt` | Replaced by SyncEngineModule |
| `sync/SyncPreferences.kt` | Replaced by SyncMetadataDao (per-table timestamps) |
| `sync/SyncResult.kt` | No longer needed (engine handles internally) |
| `sync/SyncStatus.kt` | Replace with SyncState from engine |
| `sync/SyncStatusRepository.kt` | Replace with SyncStateMachine.state |
| `sync/SyncDataSource.kt` | Replaced by SyncRemoteClient |
| `sync/SupabaseSyncDataSource.kt` | Replaced by SupabaseSyncRemoteClient |
