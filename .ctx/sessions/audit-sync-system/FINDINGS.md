# FINDINGS: audit-sync-system

Audit Date: 2026-01-19
Status: complete
Audited Files: 10

## Summary

Sync system audit complete. Found **2 high** priority issues, **3 medium** issues, and **1 low** issue. Overall architecture is solid with good error handling and crash recovery patterns.

---

## 🟠 HIGH Priority Issues

### 1. No Pagination on Pull Operations (Potential OOM)
**Location:** `sync/SupabaseSyncDataSource.kt:35-38`

**Issue:** All pull operations fetch ALL records matching the filter without pagination. A TODO comment exists acknowledging this.

**Code:**
```kotlin
// TODO: Add pagination to pull operations to prevent memory exhaustion on large datasets
// Requires upgrade to supabase-kt version with .limit() and .order() support
// Target limit: 1000 records per pull operation
```

**Impact:**
- As data grows, pulling thousands of records at once could exhaust memory (OOM crash)
- Network timeouts more likely with large payloads
- Currently safe with expected small dataset, but risky as business scales

**Affected Methods:**
- `pullTransactions(since)` - lines 79-87
- `pullBatches(since)` - lines 89-97
- `pullSaleBatches(since)` - lines 99-107
- `pullExpenseCategories(since)` - lines 109-117
- `pullCashOperations(since)` - lines 119-127

**Fix:** Implement paginated pull with batching:
```kotlin
override suspend fun pullTransactions(since: Instant): List<TransactionDto> {
    val allResults = mutableListOf<TransactionDto>()
    var offset = 0
    val limit = 1000

    do {
        val batch = supabaseClient.postgrest[TABLE_TRANSACTIONS]
            .select(Columns.ALL) {
                filter { gt("server_updated_at", since.toString()) }
                order("server_updated_at", Order.ASCENDING)
                range(offset.toLong(), (offset + limit - 1).toLong())
            }
            .decodeList<TransactionDto>()

        allResults.addAll(batch)
        offset += batch.size
    } while (batch.size == limit)

    return allResults
}
```

---

### 2. Reference Data Pulls ALL Records Every Sync
**Location:** `sync/SyncService.kt:585-627`, `sync/SupabaseSyncDataSource.kt:129-139`

**Issue:** `pullLocations()` and `pullProducts()` fetch ALL records on every sync instead of only changes since last sync.

**Code:**
```kotlin
// SupabaseSyncDataSource.kt:129-139
override suspend fun pullLocations(): List<LocationDto> {
    return supabaseClient.postgrest[TABLE_LOCATIONS]
        .select(Columns.ALL)  // No filter - pulls ALL
        .decodeList()
}

override suspend fun pullProducts(): List<ProductDto> {
    return supabaseClient.postgrest[TABLE_PRODUCTS]
        .select(Columns.ALL)  // No filter - pulls ALL
        .decodeList()
}
```

**Impact:**
- Inefficient with large product catalogs (hundreds of products)
- Wastes bandwidth pulling unchanged data
- Scales poorly as reference data grows

**Note:** The code comment says "locations are not created/modified on devices, only pulled from server" which is only partially true - products CAN be created on devices and synced.

**Fix:** Add `server_updated_at` filter to reference data pulls:
```kotlin
override suspend fun pullLocations(since: Instant): List<LocationDto> {
    return supabaseClient.postgrest[TABLE_LOCATIONS]
        .select(Columns.ALL) {
            filter { gt("server_updated_at", since.toString()) }
        }
        .decodeList()
}
```

---

## 🟡 MEDIUM Priority Issues

### 3. markAsSynced Operations Not Batched
**Location:** `sync/SyncService.kt:378-382`, `444-446`, `470-473`, `534-536`, `648-650`, `678-680`

**Issue:** After successful batch push, each entity is marked as synced individually in a loop with separate database operations.

**Code:**
```kotlin
// SyncService.kt:378-382
val now = Instant.now()
pending.forEach { entity ->
    transactionDao.markAsSynced(entity.localId, now)  // N database calls!
}
```

**Impact:**
- N+1 database operations instead of single batch update
- Performance degrades with many pending records
- Not wrapped in transaction - crash could leave some marked, some not

**Fix:** Add batch markSynced method to DAOs:
```kotlin
// In TransactionDao
@Query("UPDATE transactions SET synced_at = :syncedAt WHERE local_id IN (:localIds)")
suspend fun markAllSynced(localIds: List<String>, syncedAt: Instant)

// In SyncService
val localIds = pending.map { it.localId }
transactionDao.markAllSynced(localIds, Instant.now())
```

---

### 4. SyncManager Uses System.currentTimeMillis() for Rate Limiting
**Location:** `sync/SyncManager.kt:67`

**Issue:** Rate limiting uses `System.currentTimeMillis()` which can be bypassed by changing device clock backward.

**Code:**
```kotlin
fun triggerManualSync(): Boolean {
    val now = System.currentTimeMillis()  // Clock-based
    val lastSync = lastManualSyncTime.get()

    if (!isErrorState && now - lastSync < MIN_SYNC_INTERVAL_MS) {
        return false  // Rate limited
    }
    // ...
}
```

**Impact:**
- User can spam sync button by setting device time back
- Minor impact since sync is already rate-limited to 5 seconds
- Inconsistent with other timing-sensitive code

**Fix:** Use `SystemClock.elapsedRealtime()`:
```kotlin
val now = SystemClock.elapsedRealtime()
```

---

### 5. Partial Pull Failure Blocks All Entity Types
**Location:** `sync/SyncService.kt:265-267`

**Issue:** In `pullAllInTransaction()`, if ANY entity type fails to fetch, the entire pull operation fails and throws an exception.

**Code:**
```kotlin
// If ANY entity type failed to pull, throw an exception to prevent timestamp advancement
if (pullFailures.isNotEmpty()) {
    throw Exception("Partial pull failure: ${pullFailures.joinToString("; ")}")
}
```

**Impact:**
- One flaky endpoint (e.g., expense_categories 500 error) blocks pulling transactions, batches, etc.
- Data integrity preserved (timestamp not advanced) but availability reduced
- Could cause repeated sync failures if one endpoint is down

**Trade-off:** Current behavior prioritizes data integrity over availability. This is correct for financial data but could be refined with per-entity-type timestamps.

**Mitigation:** Consider separate last sync timestamps per entity type for more granular retry.

---

## ⚪ LOW Priority Issues

### 6. Unused Individual Pull Methods
**Location:** `sync/SyncService.kt:400-745`

**Issue:** Several private methods for pulling individual entity types exist but are never called:
- `pullNewTransactions()`
- `pullNewBatches()`
- `pullNewSaleBatches()`
- `pullNewExpenseCategories()`
- `pullNewCashOperations()`

They were superseded by `pullAllInTransaction()` which wraps all pulls in a single database transaction.

**Impact:** Dead code, minor code maintenance issue

**Fix:** Remove unused methods to reduce confusion.

---

## ✅ Good Patterns Observed

### 1. Push-then-Pull Strategy
Correctly implements offline-first sync:
1. Push local changes first (ensures data reaches server)
2. Pull remote changes after (brings in updates from other devices)
3. Partial success handling when push succeeds but pull fails

### 2. Atomic Transaction for Pulls
```kotlin
database.withTransaction {
    // Insert all pulled records atomically
}
```
Prevents UI flicker by ensuring Room Flow observers only emit once.

### 3. MaxTimestampTracker for Sync Timing
Uses server's `server_updated_at` timestamp (set by Postgres trigger) rather than client time. This prevents clock skew issues.

### 4. Upsert with local_id Deduplication
Uses Supabase upsert with `onConflict = "local_id"` to handle duplicate pushes safely. If app crashes after push but before marking as synced, re-push is safe.

### 5. Error State Handling
- `SyncResult.Success` / `Partial` / `Failure` with phase tracking
- Exponential backoff with 3 retry attempts
- Warning state for partial success
- Rate limiting to prevent sync spam

### 6. Good Test Coverage
Unit tests cover:
- Push/pull success and failure scenarios
- Partial sync behavior
- Voided batch sync
- Batch IDs propagation
- Idempotent sync behavior

Integration tests cover:
- Full sync flow
- Data consistency between local and remote
- Mixed push/pull scenarios

---

## Verification Checklist

Based on plan.md session checklist:

| Check | Status | Notes |
|-------|--------|-------|
| Conflict resolution is deterministic | ✅ | Uses server_updated_at, local_id upsert |
| Partial sync failures don't corrupt data | ✅ | Timestamp only advances on successful pull |
| Network errors handled gracefully | ✅ | Retry with exponential backoff |
| Duplicate records prevented | ✅ | local_id UNIQUE constraint |
| Sync doesn't run during critical ops | ⚠️ | No explicit lock, relies on WorkManager |
| Background sync respects battery/network | ✅ | WorkManager constraints |
| Timestamps use consistent timezone | ✅ | UTC via Instant |
| Large batch sync doesn't OOM | 🔴 | No pagination implemented |

---

## Recommendations

### Immediate (Before Release)
None critical - system is functional and safe for production with current dataset size.

### Should Fix Soon
1. **Add pagination to pull operations** - Prevents OOM as data grows
2. **Batch markAsSynced operations** - Performance improvement

### Can Fix Later
3. **Incremental reference data sync** - Efficiency improvement
4. **Per-entity-type sync timestamps** - Better partial failure handling
5. **Remove dead code** - Code cleanup
6. **Use elapsedRealtime for rate limiting** - Minor security improvement

---

## Files Audited

### Sync Core
- [x] SyncService.kt - Main sync orchestration (746 lines)
- [x] SyncManager.kt - WorkManager scheduling (103 lines)
- [x] SyncWorker.kt - Background execution (81 lines)
- [x] SupabaseSyncDataSource.kt - Supabase API implementation (140 lines)
- [x] SyncDataSource.kt - Interface definition (167 lines)

### Sync State
- [x] SyncResult.kt - Success/Partial/Failure states (63 lines)
- [x] SyncStatus.kt - Current sync state model (29 lines)
- [x] SyncStatusRepository.kt - Status observation (54 lines)
- [x] SyncPreferences.kt - Timestamp storage (64 lines)
- [x] SyncModule.kt - Hilt DI setup

### Tests Reviewed
- [x] SyncServiceTest.kt - 97 lines, comprehensive unit tests
- [x] SyncFlowIntegrationTest.kt - 812 lines, full integration tests with FakeSyncDataSource
