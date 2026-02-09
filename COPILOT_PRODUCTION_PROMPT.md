# Task: Sync Engine Production Readiness

Read `SYNC_ENGINE_PRODUCTION_SPEC.md` for the full specification. Implement all 5 phases in order.

## Context

The sync engine in `android/sync-engine/` needs to become a portable, production-grade module usable by multiple apps (Zagot+, BerryHarvest, SchoolKitchenUkraine). Five changes are needed: compound cursor pagination, FAILED push status, progress reporting, integration tests, and module documentation.

## Files to read first

```
android/sync-engine/src/main/kotlin/com/zagot/syncengine/api/SyncContracts.kt
android/sync-engine/src/main/kotlin/com/zagot/syncengine/api/SyncEngineImpl.kt
android/sync-engine/src/main/kotlin/com/zagot/syncengine/api/SyncEngineConfig.kt
android/sync-engine/src/main/kotlin/com/zagot/syncengine/dao/PullCoordinator.kt
android/sync-engine/src/main/kotlin/com/zagot/syncengine/dao/PushCoordinator.kt
android/sync-engine/src/main/kotlin/com/zagot/syncengine/db/SyncOutboxEntity.kt
android/sync-engine/src/main/kotlin/com/zagot/syncengine/db/SyncOutboxDao.kt
android/sync-engine/src/main/kotlin/com/zagot/syncengine/remote/SupabaseSyncRemoteClient.kt
android/sync-engine/src/testFixtures/kotlin/com/zagot/syncengine/testing/SyncTestFakes.kt
android/app/src/test/kotlin/com/zagot/zagotplus/sync/engine/PullCoordinatorTest.kt
android/app/src/test/kotlin/com/zagot/zagotplus/sync/engine/FakeSyncOutboxDao.kt
```

## Implementation order

### Phase 1: Compound Cursor Pagination
1. Add `primaryKey: String = "id"` and `afterPk: String? = null` to `SyncRemoteClient.pull()` in SyncContracts.kt
2. Implement compound cursor in `SupabaseSyncRemoteClient.pull()` — when afterPk is not null, filter by `(ts > since) OR (ts = since AND pk > afterPk)`; always order by timestamp ASC then pk ASC
3. Update `PullCoordinator.pullStreaming()` — track `lastPk`, use it for compound cursor advancement, remove `seenPks` set (compound cursor guarantees no re-fetches)
4. Update `PullCoordinator.pull()` wrapper — same changes flow through from pullStreaming
5. Update `FakeSupabaseClient.pull()` — handle `afterPk` filtering and compound sorting
6. Add test: `pullStreaming handles 1000+ records with same timestamp` (see spec for details)
7. Update existing tests that call `SyncRemoteClient.pull()` if they break (default params should keep them compiling)

### Phase 2: FAILED Push Status
1. Add `failReason: String? = null` and `pushAttempts: Int = 0` to `SyncOutboxEntity` (with Room `defaultValue` annotations)
2. Add `markFailedBatch`, `getFailed`, `countFailed`, `retryFailed`, `retryAllFailed`, `discardFailed` to `SyncOutboxDao`
3. Create `SyncOutboxMigrations.kt` in `com.zagot.syncengine.db` with a helper method that returns a `Migration` object
4. Update `PushCoordinator.pushPending()` — change TERMINAL error handling to call `markFailedBatch` instead of `markSyncedBatch`
5. Add `failedCount: StateFlow<Int>` to `SyncEngine` interface and `SyncEngineImpl`
6. Update `SyncEngineImpl` to refresh `failedCount` after push cycles and on start
7. Update `FakeSyncOutboxDao` with new methods
8. Add tests for FAILED flow in PushCoordinatorTest

### Phase 3: Progress Reporting
1. Create `SyncProgress.kt` in `com.zagot.syncengine.state` with `SyncProgress` data class and `SyncPhase` enum
2. Add `progress: StateFlow<SyncProgress?>` to `SyncEngine` interface
3. Add `MutableStateFlow<SyncProgress?>` to `SyncEngineImpl`
4. Update `executeCatchUp` and `executeSyncCycle` to emit progress at: table start (PULLING), batch completion (increment recordsProcessed), push start (PUSHING), buffer drain (DRAINING_BUFFER), completion (null)
5. Emit progress sparingly — per batch, not per record

### Phase 4: Integration Tests
1. Create `TestSyncDatabase.kt` in `android/app/src/test/kotlin/com/zagot/zagotplus/integration/` with a simple test Room database (sync_outbox + sync_metadata + test_products table)
2. Create `TestProductEntity.kt` and `TestProductDao.kt` in same package
3. Create `SyncFlowIntegrationTest.kt` with test scenarios from the spec (see SYNC_ENGINE_PRODUCTION_SPEC.md Phase 4 for full list)
4. Tests use Robolectric (`@RunWith(RobolectricTestRunner::class)`) with in-memory Room
5. Each test creates a fresh SyncEngineImpl with real Room + all fakes, registers a test table, and exercises the full flow

### Phase 5: Module README
1. Create `android/sync-engine/README.md` with architecture overview, integration guide, configuration reference, testing guide, and troubleshooting section (see spec for outline)

### Phase 6: Per-Table Sync Status
1. Create `TableSyncStatus.kt` in `com.zagot.syncengine.state` — data class with `tableName`, `lastSyncedAt`, `pendingCount`, `failedCount`
2. Create `TablePendingCount.kt` in `com.zagot.syncengine.db` — Room query result POJO with `tableName` + `count`
3. Add `countPendingByTable()` and `countFailedByTable()` to `SyncOutboxDao` (GROUP BY queries)
4. Add `tableSyncStatus: StateFlow<List<TableSyncStatus>>` to `SyncEngine` interface
5. Add `MutableStateFlow<List<TableSyncStatus>>` to `SyncEngineImpl`, initialize to empty
6. Create `private suspend fun refreshTableSyncStatus()` that queries `SyncMetadataDao.getAll()` + `outboxDao.countPendingByTable()` + `outboxDao.countFailedByTable()`, builds `TableSyncStatus` list, emits to the flow
7. Call `refreshTableSyncStatus()` after each catch-up completion, safety sync completion, and push completion
8. Update `FakeSyncOutboxDao` with new GROUP BY methods

### Phase 7: Error Callback + Observability
1. Create `PushResult.kt` in `com.zagot.syncengine.dao` — `PushResult(successCount, failedEntries: List<PushFailure>)` and `PushFailure(tableName, recordId, reason)`
2. Change `PushCoordinator.pushPending` return type from `Int` to `PushResult` — collect failures from TERMINAL errors instead of just logging
3. Add `onPushFailed: ((tableName, recordId, reason) -> Unit)?` and `onSyncComplete: ((durationMs, recordsPulled, recordsPushed) -> Unit)?` to `SyncEngineConfig` with null defaults
4. In `SyncEngineImpl`, after `pushCoordinator.pushPending()` calls: invoke `onPushFailed` for each failure, invoke `onSyncComplete` with timing + counts
5. Track `totalPulled` counter in `executeCatchUp` and `executeSyncCycle` by counting records in `reconcileAndApplyBatch`
6. Update all callers of `pushPending` (SyncEngineImpl uses the return value in 3 places) to handle `PushResult` instead of `Int`
7. Update tests that assert on pushPending return value

## Critical constraints

- `SyncRemoteClient.pull()` signature change uses DEFAULT parameter values — all existing callers must compile without changes
- Room migration is a HELPER method, not auto-applied — apps control their own version numbers
- Removing `seenPks` from PullCoordinator is ONLY safe because compound cursor guarantees strict forward progress. The test in Phase 1 step 6 must pass before removing seenPks.
- Progress StateFlow emits per batch, not per record
- Integration tests use `@RunWith(RobolectricTestRunner::class)` + `Room.inMemoryDatabaseBuilder`, not instrumented tests
- All changes are backward-compatible — Zagot+ app code should require zero changes
- `SyncEngineImpl.executeSyncCycle()` must remain private — the `syncNow()` public method wraps it
- Follow existing code style: KDoc, companion object for TAG/constants, Log.d/w/e patterns
- Do NOT change ConflictReconciler, RealtimeBuffer, RealtimeManager, SyncStateMachine, NetworkMonitor
- The compound cursor in SupabaseSyncRemoteClient uses Supabase postgrest-kt `or { }` filter builder, not raw strings
- `PushCoordinator.pushPending` return type changes from `Int` to `PushResult` — update ALL callers (SyncEngineImpl calls it in executeCatchUp, executeSyncCycle, and notifyOutboxChanged)
- `onPushFailed` and `onSyncComplete` callbacks are invoked on Dispatchers.IO — document that they must not block
- `refreshTableSyncStatus()` should be cheap — just 3 small queries. Call it after completions, not after every batch.
- `TablePendingCount` needs `@ColumnInfo` annotations matching the SQL aliases
