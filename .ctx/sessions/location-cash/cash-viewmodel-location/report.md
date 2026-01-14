# Report: cash-viewmodel-location

Status: completed
Complexity: medium
Duration: ~5 minutes

## Summary
Added location filtering support to CashViewModel and CashRepository for location-specific cash history and balance.

## Changes Made

### CashModels.kt
- Extended `CashHistoryItem` with `locationId: UUID?` and `locationName: String?` fields

### CashRepository.kt (interface)
- Added `getCashHistoryByLocationPaged(locationId, limit, offset)`
- Added `getTotalHistoryCountByLocation(locationId)`

### CashRepositoryImpl.kt
- Implemented location-filtered history methods
- Updated `toDomain()` mapping to include locationId and locationName

### CashViewModel.kt
- Injected `LocationRepository` for locations list
- Added `locations: List<Location>` and `selectedLocationId: UUID?` to `CashUiState`
- Added computed properties: `isTotalsView`, `selectedLocationName`
- Refactored `loadData()` to first load locations, then call new `loadHistoryAndBalance()`
- New `loadHistoryAndBalance()` method handles location-based vs global data loading
- Updated `loadMoreOperations()` to respect selected location
- Updated `refreshOperations()` to respect selected location
- Added `selectLocation(locationId)` method
- Added `selectTotalView()` method
- Cash operations (deposit/withdraw/payment) now use selected location instead of null

## Verification
- Build passes (compileDebugKotlin)

## Notes
- ViewModel now supports switching between totals view and location-specific view
- Operations are created with the currently selected location
- Balance and history react to location selection changes
