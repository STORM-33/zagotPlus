# Session: cash-viewmodel-location

Type: feature
Complexity: medium

## Objective
Update CashViewModel and CashRepository to support location selection and filtering.

## Context
- CashViewModel currently shows global totals only
- Need to load locations list and track selected location
- Balance and history should filter by location (or show all in totals view)
- Operations require location (no longer nullable in practice)

## Success Criteria
- [ ] CashRepository has location-filtered history methods
- [ ] CashUiState includes locations list and selectedLocation
- [ ] selectLocation(location) and selectTotalView() methods
- [ ] Balance and history update when location changes
- [ ] CashHistoryItem includes locationName for display
- [ ] Build passes

## Files to Modify
- android/app/src/main/kotlin/com/zagot/zagotplus/domain/repository/CashRepository.kt
- android/app/src/main/kotlin/com/zagot/zagotplus/data/repository/CashRepositoryImpl.kt
- android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/cash/CashViewModel.kt
- android/app/src/main/kotlin/com/zagot/zagotplus/domain/model/CashModels.kt

## Approach
1. Add locationName to CashHistoryItem
2. Add getCashHistoryByLocationPaged to CashRepository interface
3. Implement in CashRepositoryImpl with projection mapping
4. Add locations, selectedLocation to CashUiState
5. Inject LocationRepository into CashViewModel
6. Add selectLocation/selectTotalView methods
7. Update loadData to filter by selected location
