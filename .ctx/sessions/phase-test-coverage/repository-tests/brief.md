# Session Brief: repository-tests

Type: feature
Phase: test-coverage
Complexity: medium
Created: 2026-01-12

## Objective

Add unit tests for repository implementations to cover entity-domain mapping and data access logic.

## Background

Current test coverage is ~20%. Repositories are critical for offline-first architecture but only TransactionRepositoryImpl has tests. LocationRepositoryImpl, ProductRepositoryImpl, and PurchaseBatchRepositoryImpl have no test coverage.

## Requirements

- [ ] Create LocationRepositoryImplTest
- [ ] Create ProductRepositoryImplTest
- [ ] Create PurchaseBatchRepositoryImplTest
- [ ] Test entity-to-domain mapping logic
- [ ] Test domain-to-entity mapping logic
- [ ] Test Flow emissions for list queries

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `android/app/src/test/kotlin/com/zagot/zagotplus/data/repository/TransactionRepositoryImplTest.kt` (reference)
- `android/app/src/main/kotlin/com/zagot/zagotplus/data/repository/LocationRepositoryImpl.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/data/repository/ProductRepositoryImpl.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/data/repository/PurchaseBatchRepositoryImpl.kt`

## Implementation Notes

- Use MockK for mocking DAOs
- Follow existing TransactionRepositoryImplTest patterns
- Use runTest {} for coroutine tests
- Use turbine library if Flow testing needed (or first() extension)

## TDD

Mode: encouraged

### Test Plan
- [ ] LocationRepository: getAll returns mapped locations
- [ ] LocationRepository: getById returns single mapped location
- [ ] ProductRepository: getAll returns mapped products (active/inactive)
- [ ] ProductRepository: insert/update maps domain to entity correctly
- [ ] PurchaseBatchRepository: getTodayBatches returns today's batches
- [ ] PurchaseBatchRepository: createBatchWithTransactions is atomic

### Test Command
```
./gradlew :app:testDebugUnitTest --tests "*RepositoryImpl*"
```

## Success Criteria

- [ ] All new tests pass
- [ ] No regressions in existing tests
- [ ] At least 3 tests per repository (9+ total new tests)

## Out of Scope

- Integration tests with real Room database
- Supabase sync testing
- ViewModel tests (separate session)

## Dependencies

- Requires: none
- Blocks: none
