# Report: cash-dao-location

Status: completed
Complexity: medium
Duration: ~5 minutes

## Summary
Added location support to CashDao for location-specific cash history queries.

## Changes Made

### CashEntity.kt
- Extended `CashHistoryProjection` with `location_id` and `location_name` fields

### CashDao.kt
- Updated `getCashHistoryPaged()` to JOIN with locations table for location names
- Modified grouping for purchase/sale batches: now groups by location + date (not just date)
- Added `getCashHistoryByLocationPaged(locationId, limit, offset)` for filtered history
- Added `getTotalHistoryCountByLocation(locationId)` for location-specific counts
- Updated `getTotalHistoryCount()` to count distinct location+date combinations

## Verification
- Build passes (compileDebugKotlin)

## Notes
- Purchase/sale batch IDs now include location_id in the composite key (e.g., `purchase_{locationId}_{date}`)
- This enables proper separation of batches when viewing totals across locations
