# Plan: Increase Test Coverage

Created: 2026-01-12
Status: active

## Overview
Increase test coverage from ~20% to higher level by adding unit tests for repositories and ViewModels.

## Progress
- Total sessions: 2
- Completed: 1
- Blocked: 0
- Remaining: 1

## Phases

### Phase: Test Coverage
Status: in_progress
Add unit tests for critical business logic components.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 1 | repository-tests | medium | completed | none |
| 2 | viewmodel-tests | medium | pending | none |

## Dependencies Graph
```
repository-tests (independent)
viewmodel-tests (independent)
```

## Session Details

### 1. repository-tests
Add unit tests for repository implementations:
- LocationRepositoryImpl
- ProductRepositoryImpl  
- PurchaseBatchRepositoryImpl

Focus on testing the mapping logic between entities and domain models.

### 2. viewmodel-tests
Add unit tests for ViewModels without test coverage:
- HistoryViewModel (filtering logic, date handling)
- InventoryViewModel (inventory computation)
- ReportsViewModel (aggregation, summary calculation)

## Notes
- Sessions can run in parallel (no dependencies)
- Pattern: MockK for repositories, runTest for coroutines (per history)
- Existing test examples: PurchaseViewModelTest, ProductsViewModelTest
