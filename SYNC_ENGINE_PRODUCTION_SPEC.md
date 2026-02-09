# Sync Engine Production Readiness Spec

## Overview

Five changes to make the sync engine portable across Zagot+, BerryHarvest, and SchoolKitchenUkraine.

Implementation order matters — each phase builds on the previous.

---

## Phase 1: Compound Cursor Pagination

### Problem

When 1000+ records share the same `server_updated_at` timestamp (bulk imports, batch operations, migrations), the cursor gets stuck and pagination breaks. Records beyond the first page at that timestamp are silently lost until safety sync.

### Solution

Add a secondary cursor on PK. When the timestamp cursor doesn't advance, use `(timestamp, pk)` compound ordering to guarantee forward progress.

### Changes

**SyncRemoteClient — add `afterPk` parameter:**

```kotlin
interface SyncRemoteClient {
    suspend fun pull(
        table: String,
        timestampColumn: String,
        since: Long,
        overlapWindowMs: Long = 0L,
        limit: Int = 1000,
        primaryKey: String = "id",    // NEW
        afterPk: String? = null,      // NEW — secondary cursor for same-timestamp pages
    ): List<Record>
}
```

**SupabaseSyncRemoteClient — implement compound cursor:**

When `afterPk` is provided, the query becomes:
```
WHERE (timestamp > since) OR (timestamp = since AND pk > afterPk)
ORDER BY timestamp ASC, pk ASC
LIMIT pageSize
```

In Supabase postgrest-kt:
```kotlin
override suspend fun pull(
    table: String,
    timestampColumn: String,
    since: Long,
    overlapWindowMs: Long,
    limit: Int,
    primaryKey: String,
    afterPk: String?,
): List<Record> {
    val isoTimestamp = Instant.ofEpochMilli(since).toString()

    val jsonRecords: List<Map<String, JsonElement>> = supabaseClient.postgrest[table]
        .select(Columns.ALL) {
            filter {
                if (afterPk != null) {
                    // Compound cursor: (ts > since) OR (ts = since AND pk > afterPk)
                    or {
                        gt(timestampColumn, isoTimestamp)
                        and {
                            gte(timestampColumn, isoTimestamp)  // gte not eq — handles overlap
                            gt(primaryKey, afterPk)
                        }
                    }
                } else {
                    gte(timestampColumn, isoTimestamp)
                }
            }
            order(timestampColumn, Order.ASCENDING)
            order(primaryKey, Order.ASCENDING)  // Secondary sort for deterministic pagination
            limit(count = limit.toLong())
        }
        .decodeList()

    return jsonRecords.map { jsonMap ->
        jsonMap.mapValues { (_, v) -> JsonUtil.jsonElementToAny(v) }
    }
}
```

**PullCoordinator.pullStreaming — track `lastPk` alongside `since`:**

```kotlin
suspend fun pullStreaming(
    remoteClient: SyncRemoteClient,
    config: SyncTableConfig,
    onBatch: suspend (batch: List<Record>, batchMaxTimestamp: Long) -> Unit,
): PullStreamingResult {
    // ... existing setup ...
    
    val pageSize = config.pullPageSize ?: pullPageSize
    val seenPks = mutableSetOf<String>()
    var page = 0
    var totalRecords = 0
    var lastPk: String? = null   // NEW — secondary cursor

    do {
        val batch = remoteClient.pull(
            table = config.tableName,
            timestampColumn = config.timestampColumn,
            since = since,
            overlapWindowMs = 0L,
            limit = pageSize,
            primaryKey = config.primaryKey,  // NEW
            afterPk = lastPk,               // NEW
        )

        // ... existing dedup logic ...

        val batchMaxTs = maxTimestamp(batch, config.timestampColumn)

        if (newRecords.isNotEmpty()) {
            totalRecords += newRecords.size
            onBatch(newRecords, batchMaxTs)
        }

        if (batch.size == pageSize) {
            if (batchMaxTs > since) {
                since = batchMaxTs
                lastPk = null  // NEW — reset PK cursor when timestamp advances
            } else {
                // Timestamp didn't advance — use compound cursor
                val batchLastPk = batch.lastOrNull()?.get(config.primaryKey)?.toString()
                if (batchLastPk != null && batchLastPk != lastPk) {
                    lastPk = batchLastPk  // NEW — advance PK cursor
                } else if (newInBatch == 0) {
                    Log.w(TAG, "Cursor stuck for ${config.tableName}, breaking")
                    break
                }
            }
        }

        // ...
    } while (batch.size == pageSize)
    // ...
}
```

**Remove `seenPks` dedup set** — with compound cursor `(ts, pk)`, records are never re-fetched. The `seenPks` set was only needed because the old cursor used `gte` on timestamp alone, causing boundary re-fetches. With compound cursor ordering, each page is strictly after the previous one. This also eliminates the `seenPks` memory concern.

**FakeSupabaseClient — update to handle `afterPk`:**

```kotlin
override suspend fun pull(
    table: String,
    timestampColumn: String,
    since: Long,
    overlapWindowMs: Long,
    limit: Int,
    primaryKey: String,
    afterPk: String?,
): List<Record> {
    maybeFail()
    return remoteTables[table]
        ?.filter { record ->
            val ts = parseTs(record[timestampColumn])
            val pk = record[primaryKey]?.toString() ?: ""
            if (afterPk != null) {
                ts > since || (ts == since && pk > afterPk)
            } else {
                ts >= since
            }
        }
        ?.sortedWith(compareBy<Record> { parseTs(it[timestampColumn]) }.thenBy { it[primaryKey]?.toString() ?: "" })
        ?.take(limit)
        ?: emptyList()
}
```

**Update `PullCoordinator.pull()` convenience wrapper** — no changes needed, it calls `pullStreaming` which handles everything.

**Update all existing tests** — the `pull()` method signature on `SyncRemoteClient` changed (added `primaryKey` and `afterPk` params with defaults). Existing callers compile without changes due to default values.

**Add new test: `pullStreaming handles same-timestamp records with compound cursor`:**

```kotlin
@Test
fun `pullStreaming handles 1000+ records with same timestamp`() = runBlocking {
    val coordinator = PullCoordinator(FakeSyncMetadataDao(), stateMachine).apply {
        pullPageSize = 3
    }
    val config = SyncTableConfig(tableName = "products", timestampColumn = "updated_at")

    // 7 records all with same timestamp — more than 2 pages
    repeat(7) { i ->
        fakeClient.injectRemoteRecord("products", mapOf(
            "id" to "p${String.format("%03d", i)}", // p000..p006 for sort order
            "updated_at" to 100L,
        ))
    }

    val allRecords = mutableListOf<Record>()
    coordinator.pullStreaming(fakeClient, config) { batch, _ ->
        allRecords.addAll(batch)
    }

    assertThat(allRecords.map { it["id"] }).containsExactly(
        "p000", "p001", "p002", "p003", "p004", "p005", "p006"
    ).inOrder()
}
```

---

## Phase 2: FAILED Status for Terminal Push Errors

### Problem

Terminal push errors (400, 404) silently mark outbox entries as `synced = 1`. User data is effectively dropped without any indication.

### Solution

Add a FAILED state to outbox entries. Expose failed entries for UI display.

### Changes

**SyncOutboxEntity — add status column + fail reason:**

Do NOT change the existing `synced` Int column or its values. Add new columns alongside:

```kotlin
@Entity(
    tableName = "sync_outbox",
    indices = [
        Index(value = ["table_name", "record_id"]),
        Index(value = ["synced"]),
        Index(value = ["created_at"]),
    ]
)
data class SyncOutboxEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    @ColumnInfo(name = "table_name")
    val tableName: String,

    @ColumnInfo(name = "record_id")
    val recordId: String,

    @ColumnInfo(name = "operation")
    val operation: String,

    @ColumnInfo(name = "payload")
    val payload: String,

    @ColumnInfo(name = "created_at")
    val createdAt: Long,

    /** 0 = pending, 1 = synced, 2 = failed. */
    @ColumnInfo(name = "synced")
    val synced: Int = 0,

    /** Reason for failure (only set when synced = 2). */
    @ColumnInfo(name = "fail_reason", defaultValue = "NULL")
    val failReason: String? = null,

    /** Number of push attempts (for retry tracking). */
    @ColumnInfo(name = "push_attempts", defaultValue = "0")
    val pushAttempts: Int = 0,
)
```

**SyncOutboxDao — add failure queries:**

```kotlin
/** Mark entries as failed with a reason. */
@Query("UPDATE sync_outbox SET synced = 2, fail_reason = :reason WHERE id IN (:ids)")
suspend fun markFailedBatch(ids: List<Long>, reason: String)

/** Get all failed entries. */
@Query("SELECT * FROM sync_outbox WHERE synced = 2 ORDER BY created_at ASC")
suspend fun getFailed(): List<SyncOutboxEntity>

/** Get failed entry count. */
@Query("SELECT COUNT(*) FROM sync_outbox WHERE synced = 2")
suspend fun countFailed(): Int

/** Retry a failed entry (move back to pending). */
@Query("UPDATE sync_outbox SET synced = 0, fail_reason = NULL WHERE id = :id AND synced = 2")
suspend fun retryFailed(id: Long)

/** Retry all failed entries for a table. */
@Query("UPDATE sync_outbox SET synced = 0, fail_reason = NULL WHERE synced = 2 AND table_name = :tableName")
suspend fun retryAllFailed(tableName: String)

/** Discard (delete) a failed entry permanently. */
@Query("DELETE FROM sync_outbox WHERE id = :id AND synced = 2")
suspend fun discardFailed(id: Long)
```

**Room migration** — add `fail_reason` and `push_attempts` columns:

Create a `SyncOutboxMigration` helper in the sync-engine module that apps call:

```kotlin
// In sync-engine module
object SyncOutboxMigrations {
    /**
     * Migration to add fail_reason and push_attempts columns to sync_outbox.
     * Apps must include this in their Room migration list.
     *
     * @param fromVersion the database version before this migration
     * @param toVersion the database version after this migration
     */
    fun addFailureTracking(fromVersion: Int, toVersion: Int): Migration {
        return object : Migration(fromVersion, toVersion) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE sync_outbox ADD COLUMN fail_reason TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE sync_outbox ADD COLUMN push_attempts INTEGER NOT NULL DEFAULT 0")
            }
        }
    }
}
```

**PushCoordinator — mark TERMINAL as failed instead of synced:**

In `pushPending`, change the TERMINAL error handling:

```kotlin
PushErrorCategory.TERMINAL -> {
    Log.e(TAG, "Terminal error, marking entries as FAILED for ${config.tableName}")
    val ids = entries.map { it.id }
    outboxDao.markFailedBatch(ids, e.message ?: "Terminal error (${category})")
}
```

**SyncEngine interface — expose failed count:**

```kotlin
interface SyncEngine {
    // ... existing ...
    
    /** Number of outbox entries that failed to push (terminal errors). */
    val failedCount: StateFlow<Int>
}
```

SyncEngineImpl: emit `failedCount` updates after each push cycle and on start.

**Update FakeSyncOutboxDao** with the new methods.

---

## Phase 3: Progress Reporting

### Problem

For large syncs (BerryHarvest worker data after a day offline), users see nothing during a multi-minute catch-up.

### Solution

Add `StateFlow<SyncProgress?>` to the `SyncEngine` interface.

### Changes

**New data class:**

```kotlin
data class SyncProgress(
    val phase: SyncPhase,
    val currentTable: String?,
    val tablesCompleted: Int,
    val tablesTotal: Int,
    val recordsProcessed: Int,
    /** Null if unknown (streaming pull doesn't know total upfront). */
    val totalRecords: Int? = null,
)

enum class SyncPhase {
    PULLING,
    PUSHING,
    DRAINING_BUFFER,
    IDLE,
}
```

**SyncEngine interface:**

```kotlin
interface SyncEngine {
    // ... existing ...
    val progress: StateFlow<SyncProgress?>
}
```

**SyncEngineImpl — update progress during catch-up and safety sync:**

Add a `MutableStateFlow<SyncProgress?>` and update it:
- At the start of each table's pull: `PULLING, currentTable, tablesCompleted, tablesTotal, 0`
- In `reconcileAndApplyBatch`: increment `recordsProcessed` by `batch.size`
- During push: `PUSHING`
- During buffer drain: `DRAINING_BUFFER`
- After completion: `null` (idle)

The progress updates happen naturally inside the existing per-batch callback.

---

## Phase 4: Integration Tests

### Problem

Component tests exist but the full orchestration flow (`executeCatchUp`) is untested.

### Architecture

The integration test needs a real Room database (in-memory) because:
- `isBufferEventNewer` uses raw SQL queries against data tables
- `database.withTransaction` needs real Room
- Cursor advancement through `sync_metadata` table needs real storage

Write the test in the `:app` module's test source set (it already has access to real Room entities and DAOs). Use Robolectric for in-memory Room.

### Test database setup

Create a minimal test Room database with:
- `sync_outbox` table (from SyncOutboxEntity)
- `sync_metadata` table (from SyncMetadataEntity)
- A `test_products` table (simple entity for testing)

```kotlin
@Entity(tableName = "test_products")
data class TestProductEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "server_updated_at") val serverUpdatedAt: String,
)

@Dao
interface TestProductDao {
    @Upsert
    suspend fun upsertAll(entities: List<TestProductEntity>)
    
    @Query("SELECT * FROM test_products WHERE id = :id")
    suspend fun getById(id: String): TestProductEntity?
    
    @Query("SELECT * FROM test_products")
    suspend fun getAll(): List<TestProductEntity>
    
    @Query("DELETE FROM test_products WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(
    entities = [SyncOutboxEntity::class, SyncMetadataEntity::class, TestProductEntity::class],
    version = 1,
)
abstract class TestSyncDatabase : RoomDatabase() {
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    abstract fun testProductDao(): TestProductDao
}
```

### Test scenarios

**1. Full catch-up: OFFLINE → CATCHING_UP → LIVE**
- Inject remote records into FakeSupabaseClient
- Simulate network connect
- Verify: state transitions, records in Room, last_synced_at advanced

**2. Conflict resolution — local wins**
- Create local outbox entry (newer timestamp)
- Inject conflicting remote record (older timestamp)
- Run catch-up
- Verify: outbox entry still pending, remote record NOT in Room (state reversion fix)

**3. Conflict resolution — remote wins**
- Create local outbox entry (older timestamp)
- Inject conflicting remote record (newer timestamp)
- Run catch-up
- Verify: outbox entry marked synced, remote record in Room

**4. Buffer drain with dedup**
- Start catch-up, emit realtime event during pull
- Verify: event applied only if newer than pulled record

**5. Buffer drain skips records with pending outbox**
- Create local outbox entry
- Emit realtime event for same record during catch-up
- Verify: buffer event skipped, local version preserved

**6. Overflow retry with cursor rollback**
- Set small buffer (maxBufferSize = 1)
- Inject many remote records + emit realtime events to cause overflow
- Verify: retry happens, cursors rolled back, eventually goes LIVE

**7. Compound cursor pagination**
- Inject 10+ records with same timestamp, set pageSize = 3
- Verify: all records fetched, none lost

**8. Safety sync**
- While LIVE, inject new remote records
- Trigger syncNow()
- Verify: records applied to Room

**9. FAILED push status**
- Create outbox entry
- Configure FakeSupabaseClient to fail with 400
- Run push
- Verify: entry marked failed with reason, not pending

**10. FK retry**
- Register two tables (parent + child) 
- Emit realtime event for child before parent arrives
- Verify: FK retry succeeds after parent arrives

---

## Phase 5: Module README

Create `android/sync-engine/README.md` with:

1. **Architecture overview** — state machine diagram, component map
2. **Integration guide** — step-by-step for adding to a new app:
   - Add dependency
   - Add Room entities (sync_outbox, sync_metadata) to your database
   - Add Room migration
   - Create Hilt module (bindings for SyncRemoteClient, RealtimeChannelContract, NetworkMonitor, RoomDatabase, WorkManager)
   - Write SyncAware DAO wrappers (or document the @RawDao pattern)
   - Write a Registrar (register tables in FK order)
   - Call `engine.start()` on app launch
3. **Configuration reference** — all `SyncEngineConfig` fields with descriptions
4. **SyncTableConfig reference** — all fields, when to use fullPull, custom conflict resolvers
5. **Testing guide** — how to use test fakes, what to test in app-level tests
6. **Troubleshooting** — common issues (outbox loops with wrong DAO, FK ordering, stuck cursor)

---

## Phase 6: Per-Table Sync Status

### Problem

Apps need to show which tables are synced, which are stale, and when each was last synced. Currently only `SyncState` (OFFLINE/CATCHING_UP/LIVE) is exposed — no per-table granularity.

### Solution

Expose per-table sync metadata through the `SyncEngine` interface.

### Changes

**New data class in `com.zagot.syncengine.state`:**

```kotlin
data class TableSyncStatus(
    val tableName: String,
    /** Epoch millis of the last successfully synced record's timestamp. 0 = never synced. */
    val lastSyncedAt: Long,
    /** Number of pending outbox entries for this table. */
    val pendingCount: Int,
    /** Number of failed outbox entries for this table. */
    val failedCount: Int,
)
```

**SyncEngine interface:**

```kotlin
interface SyncEngine {
    // ... existing ...

    /** Per-table sync status. Updated after each sync cycle. */
    val tableSyncStatus: StateFlow<List<TableSyncStatus>>
}
```

**SyncOutboxDao — add per-table counts:**

```kotlin
/** Count pending entries per table. */
@Query("SELECT table_name, COUNT(*) as cnt FROM sync_outbox WHERE synced = 0 GROUP BY table_name")
suspend fun countPendingByTable(): List<TablePendingCount>

/** Count failed entries per table. */
@Query("SELECT table_name, COUNT(*) as cnt FROM sync_outbox WHERE synced = 2 GROUP BY table_name")
suspend fun countFailedByTable(): List<TablePendingCount>
```

Room requires a POJO for the result:

```kotlin
data class TablePendingCount(
    @ColumnInfo(name = "table_name") val tableName: String,
    @ColumnInfo(name = "cnt") val count: Int,
)
```

Put `TablePendingCount` in `com.zagot.syncengine.db`.

**SyncEngineImpl:**

- Add `MutableStateFlow<List<TableSyncStatus>>` initialized to empty
- After each `executeSyncCycle`, `executeCatchUp` completion, and `pushPending`, refresh the status by querying `SyncMetadataDao.getAll()` + `outboxDao.countPendingByTable()` + `outboxDao.countFailedByTable()`
- Extract a `private suspend fun refreshTableSyncStatus()` helper

---

## Phase 7: Error Callback + Observability

### Problem

Apps need a way to react to sync errors without polling. Also useful for analytics/telemetry.

### Solution

Add optional callbacks to `SyncEngineConfig` for error and event observation.

### Changes

**SyncEngineConfig — add callbacks:**

```kotlin
data class SyncEngineConfig(
    // ... existing fields ...

    /**
     * Called when a push fails terminally (400/404).
     * Apps can use this to show a toast, log to analytics, etc.
     * Called on the sync engine's IO dispatcher — don't block.
     */
    val onPushFailed: ((tableName: String, recordId: String, reason: String) -> Unit)? = null,

    /**
     * Called when a full sync cycle completes (catch-up or safety sync).
     * Includes timing for observability.
     */
    val onSyncComplete: ((durationMs: Long, recordsPulled: Int, recordsPushed: Int) -> Unit)? = null,
)
```

**SyncEngineImpl — invoke callbacks:**

In `PushCoordinator.pushPending` TERMINAL handler, after marking as failed:
```kotlin
entries.forEach { entry ->
    config.onPushFailed?.invoke(config.tableName, entry.recordId, e.message ?: "Terminal error")
}
```

Wait — `PushCoordinator` doesn't have access to `SyncEngineConfig`. Two options:
1. Pass the callback through to PushCoordinator
2. Invoke the callback in SyncEngineImpl after pushPending returns

Option 2 is cleaner. SyncEngineImpl already wraps push calls. But it doesn't know which specific entries failed.

**Better approach:** Make `PushCoordinator.pushPending` return a result object:

```kotlin
data class PushResult(
    val successCount: Int,
    val failedEntries: List<PushFailure>,
)

data class PushFailure(
    val tableName: String,
    val recordId: String,
    val reason: String,
)
```

PushCoordinator returns this instead of just `Int`. SyncEngineImpl processes the failures:

```kotlin
val result = pushCoordinator.pushPending(remoteClient, registeredTables)
result.failedEntries.forEach { failure ->
    config.onPushFailed?.invoke(failure.tableName, failure.recordId, failure.reason)
}
```

**SyncEngineImpl — track sync cycle timing:**

In `executeCatchUp` and `executeSyncCycle`, measure wall-clock time and total records:

```kotlin
private suspend fun executeSyncCycle() {
    val startMs = System.currentTimeMillis()
    var totalPulled = 0
    // ... existing pull logic, count records in reconcileAndApplyBatch ...
    val pushResult = pushCoordinator.pushPending(remoteClient, registeredTables)
    val durationMs = System.currentTimeMillis() - startMs
    config.onSyncComplete?.invoke(durationMs, totalPulled, pushResult.successCount)
}
```

**Update FakeSyncOutboxDao and tests** as needed for new return types.

---

## File change summary

| File | Phase | Change |
|---|---|---|
| `SyncRemoteClient` (SyncContracts.kt) | 1 | Add `primaryKey` + `afterPk` params to `pull()` |
| `SupabaseSyncRemoteClient.kt` | 1 | Implement compound cursor query |
| `PullCoordinator.kt` | 1 | Track `lastPk`, remove `seenPks`, use compound cursor |
| `SyncTestFakes.kt` | 1 | Update FakeSupabaseClient.pull() |
| `PullCoordinatorTest.kt` | 1 | Add same-timestamp pagination test |
| `SyncOutboxEntity.kt` | 2 | Add `failReason`, `pushAttempts` columns |
| `SyncOutboxDao.kt` | 2 | Add `markFailedBatch`, `getFailed`, `retryFailed`, etc. |
| `PushCoordinator.kt` | 2 | Mark TERMINAL as failed, not synced |
| `SyncEngineImpl.kt` | 2+3 | Expose `failedCount` + `progress` StateFlows |
| `SyncEngine` interface (SyncEngineImpl.kt) | 2+3 | Add `failedCount` + `progress` to interface |
| `SyncOutboxMigrations.kt` (NEW) | 2 | Room migration helper |
| `SyncProgress.kt` (NEW) | 3 | Data class + enum |
| `FakeSyncOutboxDao.kt` | 2 | Add new failure-related methods |
| `SyncFlowIntegrationTest.kt` | 4 | Full integration test suite |
| `TestSyncDatabase.kt` (NEW) | 4 | Test Room database |
| `README.md` (NEW) | 5 | Module documentation |
| `TableSyncStatus.kt` (NEW) | 6 | Per-table status data class |
| `TablePendingCount.kt` (NEW) | 6 | Room query result POJO |
| `SyncOutboxDao.kt` | 6 | Add `countPendingByTable`, `countFailedByTable` |
| `SyncEngineImpl.kt` | 6 | Add `tableSyncStatus` StateFlow + `refreshTableSyncStatus()` |
| `SyncEngine` interface | 6 | Add `tableSyncStatus` |
| `SyncEngineConfig.kt` | 7 | Add `onPushFailed`, `onSyncComplete` callbacks |
| `PushCoordinator.kt` | 7 | Return `PushResult` instead of `Int` from `pushPending` |
| `PushResult.kt` (NEW) | 7 | Push result + failure data classes |
| `SyncEngineImpl.kt` | 7 | Invoke callbacks, track timing |

## Critical constraints

- `SyncRemoteClient.pull()` signature change uses default parameter values — existing callers compile without changes
- Room migration is provided as a helper, NOT auto-applied — apps control their own migration versions
- `seenPks` removal in PullCoordinator is ONLY safe because compound cursor guarantees no re-fetches. Verify this in tests before removing.
- Progress StateFlow must not cause memory pressure — emit sparingly (per batch, not per record)
- Integration tests use Robolectric + in-memory Room, NOT Android instrumented tests (faster CI)
- All changes backward-compatible — existing Zagot+ code continues to work without modification
