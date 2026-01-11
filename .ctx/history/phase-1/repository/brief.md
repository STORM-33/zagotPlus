# Session Brief: repository

Type: feature
Phase: phase-1
Complexity: medium
Created: 2026-01-11

## Objective

Implement repository pattern providing a clean offline-first API for UI layer to access data.

## Background

Repositories abstract data sources from UI. The UI calls repository methods without knowing if data comes from Room or Supabase. For offline-first design, repositories always write to Room first and mark records for sync.

## Requirements

- [ ] Create `LocationRepository` interface and implementation
- [ ] Create `ProductRepository` interface and implementation
- [ ] Create `TransactionRepository` interface and implementation
- [ ] Implement computed inventory query in TransactionRepository
- [ ] Generate `localId` (UUID) for new transactions
- [ ] Set `syncedAt = null` for new records (pending sync)
- [ ] Expose reactive Flows for UI observation
- [ ] Configure Hilt bindings for repositories

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `data/local/entity/*.kt` (from session 1)
- `data/local/dao/*.kt` (from session 1)
- `data/local/ZagotDatabase.kt`

## Implementation Notes

**Repository structure:**
```kotlin
interface TransactionRepository {
    fun getAllTransactions(): Flow<List<Transaction>>
    fun getTransactionsByLocation(locationId: String): Flow<List<Transaction>>
    suspend fun createPurchase(locationId: String, productId: String, weightKg: BigDecimal, pricePerKg: BigDecimal): Transaction
    suspend fun createSale(...)
    suspend fun createTransfer(fromLocationId: String, toLocationId: String, ...)
    fun getInventory(locationId: String): Flow<List<InventoryItem>>
}
```

**Domain models:**
- Create domain models separate from entities
- Map entity ↔ domain in repository
- Domain models don't have Room annotations

**File locations:**
- Interfaces: `domain/repository/`
- Implementations: `data/repository/`
- Domain models: `domain/model/`
- Hilt module: `data/repository/RepositoryModule.kt`

**Key considerations:**
- Generate UUID with `java.util.UUID.randomUUID().toString()`
- Transfer creates two linked transactions atomically
- Inventory query: GROUP BY location_id, product_id with SUM

## TDD

Mode: encouraged

### Test Plan
- [ ] Test: createPurchase generates UUID and sets syncedAt=null
- [ ] Test: createTransfer creates two linked transactions
- [ ] Test: getInventory sums transactions correctly
- [ ] Test: Repository flows emit on database changes

### Test Command
```
./gradlew :app:testDebugUnitTest
```

## Success Criteria

- [ ] All three repositories implemented
- [ ] Domain models defined and mapped
- [ ] Inventory computation works correctly
- [ ] Transfer creates atomic pair of transactions
- [ ] Repositories injectable via Hilt
- [ ] Build succeeds: `./gradlew assembleDebug`

## Out of Scope

- Supabase client (session 3)
- Actual sync execution (session 4)
- UI consumption of repositories (Phase 2)

## Dependencies

- Requires: room-schema
- Blocks: sync-worker
