# Plan: Memory & Scalability Fixes

Created: 2026-01-16
Status: active

## Overview
Fix critical memory management risks and business logic fragility identified in audit. Remove deprecated methods that can cause OOM errors on high-volume data, optimize expensive queries, and fix fragile transfer detection logic.

## Progress
- Total sessions: 3
- Completed: 0
- Blocked: 0
- Remaining: 3

## Phases

### Phase: Core Fixes
Status: pending
Remove deprecated memory-unsafe methods, optimize queries, fix transfer detection.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 1 | remove-deprecated-dao-methods | high | pending | none |
| 2 | optimize-cash-history-query | medium | pending | remove-deprecated-dao-methods |
| 3 | fix-transfer-detection | medium | pending | optimize-cash-history-query |

## Dependencies Graph
```
remove-deprecated-dao-methods -> optimize-cash-history-query -> fix-transfer-detection
```

## Session Details

### 1. remove-deprecated-dao-methods
**Complexity:** high

Remove all deprecated OOM-prone methods from DAOs and their usages:
- Remove `CashOperationDao.getAllOperations()` - replace with paginated alternatives
- Remove `TransactionDao.getAllFlow()` and `TransactionDao.getAll()` - already have paginated versions
- Remove `ProductDao.getAllFlow()` and `ProductDao.getAll()` if they load unrestricted data
- Remove `LocationDao.getAllFlow()` and `LocationDao.getAll()` if they load unrestricted data
- Update all repository implementations to use paginated methods
- Fix all test usages to use paginated alternatives

**Files:** 
- CashDao.kt, TransactionDao.kt, ProductDao.kt, LocationDao.kt
- CashRepository.kt, CashRepositoryImpl.kt, TransactionRepository.kt, TransactionRepositoryImpl.kt
- ProductRepositoryImpl.kt, LocationRepositoryImpl.kt, PurchaseBatchRepositoryImpl.kt
- All test files using deprecated methods

**Success Criteria:**
- No @Deprecated methods remain in DAO interfaces
- All production code uses paginated queries
- All tests pass with paginated alternatives
- No Flow<List<All...>> patterns that load unbounded datasets

### 2. optimize-cash-history-query
**Complexity:** medium

Optimize the expensive getCashHistoryPaged query:
- Remove date formatting from SQL (`date(pb.created_at / 1000, 'unixepoch', 'localtime')`)
- Pre-compute date groups in application layer or use indexed timestamp ranges
- Add database index on `purchase_batches(created_at)` if not exists
- Consider caching daily aggregates in a materialized view table
- Measure query performance before/after

**Files:**
- CashDao.kt (getCashHistoryPaged, getCashHistoryByLocationPaged, getTotalHistoryCount)
- Database migration file (add index)
- CashRepositoryImpl.kt (may need date grouping logic)

**Success Criteria:**
- Query time reduced by >50% on datasets with 1000+ purchase batches
- No string-based date formatting in SQL
- Proper indexes exist for temporal queries
- Tests verify correctness of optimized query

### 3. fix-transfer-detection
**Complexity:** medium

Replace fragile pattern-match transfer detection with explicit flag:
- Add `is_transfer` boolean column to `cash_operations` table (default false)
- Update transfer creation logic to set `is_transfer = true`
- Replace `notes NOT LIKE 'Переказ%'` with `is_transfer = 0` in all queries
- Add database migration with data migration for existing transfers
- Update tests to verify transfer exclusion logic

**Files:**
- Database migration (add column, migrate existing data)
- CashOperationEntity.kt (add isTransfer field)
- CashDao.kt (update queries to use is_transfer instead of pattern match)
- CashRepository/RepositoryImpl (set is_transfer when creating transfers)
- Transfer creation UI/ViewModel (ensure flag is set)
- Tests for transfer logic

**Success Criteria:**
- Transfer detection no longer relies on text pattern matching
- All transfers have `is_transfer = true`
- Balance calculations correctly exclude transfers
- Tests verify notes starting with "Переказ" don't affect balance if is_transfer=false
- Migration preserves existing transfer semantics

## Notes
- High priority: These are production stability risks
- Session 1 is high complexity due to widespread test changes
- Consider adding performance benchmarks for query optimization validation
- Ensure backward compatibility during migrations
