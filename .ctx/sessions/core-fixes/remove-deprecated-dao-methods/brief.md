# Session Brief: remove-deprecated-dao-methods

**Type:** refactor  
**Complexity:** high  
**Phase:** core-fixes

## Goal
Remove all deprecated OOM-prone DAO methods and migrate all code to use paginated/limited alternatives.

## Context
Audit identified deprecated methods that load ALL records into memory, risking OutOfMemoryErrors on high-volume production data:
- `CashOperationDao.getAllOperations()` - can load 1000s of operations
- `TransactionDao.getAllFlow()` / `getAll()` - can load 1000s of transactions  
- Similar methods in ProductDao and LocationDao (lower risk but inconsistent)

## Approach
1. Identify all production usages of deprecated methods
2. Replace with paginated or limited alternatives:
   - For reactive UI: use `getRecentOperations(limit)` or paginated flow
   - For one-time reads: use `getPaginated(limit, offset)`
   - For reference data (Product/Location): assess actual risk, may keep if small
3. Update all test usages
4. Remove @Deprecated methods from DAOs
5. Remove deprecated repository methods

## Files to Change
- CashDao.kt - remove getAllOperations()
- TransactionDao.kt - remove getAllFlow(), getAll()
- CashRepository.kt/Impl - remove getAllOperations()
- TransactionRepository.kt/Impl - remove getAllTransactions()
- ProductRepositoryImpl.kt - replace productDao.getAllFlow() if needed
- LocationRepositoryImpl.kt - replace locationDao.getAllFlow() if needed
- PurchaseBatchRepositoryImpl.kt - replace productDao.getAllFlow() usage
- All test files using deprecated methods (many)

## Success Criteria
- [ ] No @Deprecated methods in DAO interfaces
- [ ] No @Deprecated methods in Repository interfaces  
- [ ] All production code uses bounded queries
- [ ] All tests pass
- [ ] No compilation errors

## Risks
- Many test files to update (high effort)
- May need to adjust test logic for pagination
- Ensure all test scenarios still covered