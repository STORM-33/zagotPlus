# Session Brief: viewmodel-tests

Type: feature
Phase: test-coverage
Complexity: medium
Created: 2026-01-12

## Objective

Add unit tests for ViewModels that currently lack test coverage.

## Background

Current test coverage is ~20%. Several ViewModels have tests (PurchaseViewModel, SaleViewModel, PinViewModel, ProductsViewModel) but HistoryViewModel, InventoryViewModel, and ReportsViewModel have no coverage.

## Requirements

- [ ] Create HistoryViewModelTest
- [ ] Create InventoryViewModelTest
- [ ] Create ReportsViewModelTest
- [ ] Test filtering and state management logic
- [ ] Test computed properties and aggregations

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `android/app/src/test/kotlin/com/zagot/zagotplus/ui/screens/products/ProductsViewModelTest.kt` (reference)
- `android/app/src/test/kotlin/com/zagot/zagotplus/ui/screens/purchase/PurchaseViewModelTest.kt` (reference)
- `android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/history/HistoryViewModel.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/inventory/InventoryViewModel.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/reports/ReportsViewModel.kt`

## Implementation Notes

- Use MockK for mocking repositories
- Follow existing PurchaseViewModelTest patterns
- Use runTest {} for coroutine tests
- Use savedStateHandle = SavedStateHandle() for ViewModel construction

## TDD

Mode: encouraged

### Test Plan
- [ ] HistoryViewModel: initial state loads all transactions
- [ ] HistoryViewModel: setTypeFilter filters by transaction type
- [ ] HistoryViewModel: setDateFilter filters by date range
- [ ] HistoryViewModel: search filters by product name
- [ ] InventoryViewModel: loads inventory grouped by location
- [ ] InventoryViewModel: selectLocation filters by location
- [ ] InventoryViewModel: negative inventory flagged correctly
- [ ] ReportsViewModel: date selection updates report period
- [ ] ReportsViewModel: summary calculates totals correctly
- [ ] ReportsViewModel: groupByProduct aggregates correctly

### Test Command
```
./gradlew :app:testDebugUnitTest --tests "*ViewModelTest"
```

## Success Criteria

- [ ] All new tests pass
- [ ] No regressions in existing tests
- [ ] At least 3 tests per ViewModel (9+ total new tests)

## Out of Scope

- UI testing (Compose)
- Integration tests with real repositories
- Settings/Transfer ViewModels (lower priority)

## Dependencies

- Requires: none
- Blocks: none
