# Session Brief: screen-history

Type: feature
Phase: phase-4-supporting-ui
Complexity: medium
Created: 2026-01-11

## Objective

Enhance the existing History screen with filtering and search capabilities.

## Background

History screen exists with pagination but lacks the filtering/search features specified in MASTER_PLAN.md. Users need to find specific transactions by type, date range, location, or text search.

## Requirements

- [ ] Add filter chips for transaction type (purchase, sale, transfer)
- [ ] Add date range filter (today, this week, this month, custom)
- [ ] Add location filter dropdown
- [ ] Add search by product name or notes
- [ ] Filters apply to paginated queries
- [ ] Clear filters button when any filter active
- [ ] Persist filter state during session (not across app restarts)

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/history/HistoryScreen.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/history/HistoryViewModel.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/repository/TransactionRepository.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/data/repository/TransactionRepositoryImpl.kt`

## Implementation Notes

1. Add filter UI below TopAppBar (collapsible or always visible)
2. Extend TransactionRepository with filtered query methods
3. Update DAO with parameterized queries for filters
4. SearchBar or OutlinedTextField for text search
5. FilterChip from Material3 for type filters
6. DateRangePicker or simple presets for date filter

## TDD

Mode: encouraged

### Test Plan
- [ ] Test: Filter by transaction type returns correct subset
- [ ] Test: Date range filter works correctly
- [ ] Test: Search by product name matches
- [ ] Test: Combined filters work together
- [ ] Test: Clear filters resets to all transactions

### Test Command
```
./gradlew :app:testDebugUnitTest --tests "*.HistoryViewModelTest"
```

## Success Criteria

- [ ] Can filter by transaction type
- [ ] Can filter by date range
- [ ] Can filter by location
- [ ] Can search by product name
- [ ] Pagination works with filters applied
- [ ] Clear filters resets view
- [ ] No regressions in existing history functionality

## Out of Scope

- Export functionality (belongs in screen-reports)
- Transaction details/edit screen
- Pull-to-refresh (use existing refresh button pattern)

## Dependencies

- Requires: none
- Blocks: none
