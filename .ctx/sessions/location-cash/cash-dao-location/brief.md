# Session: cash-dao-location

Type: feature
Complexity: medium

## Objective
Update CashDao to support location-specific queries and include location info in history.

## Context
- CashDao.kt has location-based methods but history query doesn't support filtering by location
- CashHistoryProjection lacks location_id for totals view display
- Need to filter history by location OR show all with location indicators

## Success Criteria
- [ ] CashHistoryProjection includes location_id and location_name
- [ ] getCashHistoryByLocationPaged(locationId, limit, offset) returns location-filtered history
- [ ] getCashHistoryPaged includes location info for totals view
- [ ] getTotalHistoryCountByLocation(locationId) returns count for location
- [ ] Build passes

## Files to Modify
- android/app/src/main/kotlin/com/zagot/zagotplus/data/local/entity/CashEntity.kt
- android/app/src/main/kotlin/com/zagot/zagotplus/data/local/dao/CashDao.kt

## Approach
1. Add location_id and location_name to CashHistoryProjection
2. Update getCashHistoryPaged UNION query to JOIN with locations for name
3. Add getCashHistoryByLocationPaged with WHERE location_id filter
4. Add getTotalHistoryCountByLocation
