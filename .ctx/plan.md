# Plan: Location-based Cash Operations

Created: 2026-01-14
Status: active

## Overview
Make cash operations location-specific. Each location has its own cash balance. Add Totals view with combined history showing location indicators.

## Progress
- Total sessions: 3
- Completed: 1
- Blocked: 0
- Remaining: 2

## Historical Context

**Similar past work:**
- Phase 2/screen-inventory: Location tabs pattern with TabRow
- Pattern: TabRow with location tabs + "Всього" (Total) tab

**Relevant decisions:**
- Location tabs (Phase 2): TabRow with selectedTabIndex for location switching
- Negative inventory display: Red color + warning icon

## Phases

### Phase: Location Cash
Status: pending
Add location awareness to cash operations with totals view.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 1 | cash-dao-location | medium | completed | none |
| 2 | cash-viewmodel-location | medium | pending | cash-dao-location |
| 3 | cash-ui-tabs | medium | pending | cash-viewmodel-location |

## Dependencies Graph
```
cash-dao-location -> cash-viewmodel-location -> cash-ui-tabs
```

## Session Details

### 1. cash-dao-location
**Complexity:** medium

Update CashDao to support location-specific queries:
- Add `getCashHistoryByLocationPaged(locationId, limit, offset)` 
- Add `getTotalHistoryCountByLocation(locationId)`
- Include location_id in CashHistoryProjection for totals view
- Update history UNION query to filter by location or include all with location info

**Files:** CashDao.kt, CashEntity.kt (if projection needs location)

### 2. cash-viewmodel-location
**Complexity:** medium

Update CashViewModel and CashRepository:
- Add location list loading (from LocationRepository)
- Add selectedLocation state (null = totals view)
- Add location-filtered history and balance loading
- Operations (deposit/withdraw/payment) require locationId (no longer nullable in UI context)

**Files:** CashViewModel.kt, CashRepository.kt, CashRepositoryImpl.kt

### 3. cash-ui-tabs
**Complexity:** medium

Update CashScreen to show location tabs:
- Add TabRow with location tabs + "Всього" (Totals) tab
- Pass selectedLocation to ViewModel methods
- In totals view: show combined balance, history items display location name
- Update CashHistoryItem to include locationName for display

**Files:** CashScreen.kt, CashModels.kt (add locationName to CashHistoryItem)

## Notes
- Pattern: Copy InventoryScreen location tabs implementation
- Purchases/sales already have location_id from transaction (no change needed)
- location_id already exists in cash_operations table (Supabase schema ready)
- deposit/withdraw/payment will require location selection in UI
