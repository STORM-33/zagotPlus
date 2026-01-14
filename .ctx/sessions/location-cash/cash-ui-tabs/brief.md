# Session: cash-ui-tabs

Type: feature
Complexity: medium

## Objective
Add location tabs to CashScreen UI following InventoryScreen pattern.

## Context
- InventoryScreen has TabRow with location tabs + "Всього" (Totals) tab
- CashScreen needs same pattern for location switching
- History items in totals view should show location name

## Success Criteria
- [ ] TabRow with location tabs + "Всього" tab
- [ ] Clicking tab calls viewModel.selectLocation or selectTotalView
- [ ] Balance displays per-location or total
- [ ] History items show location indicator in totals view
- [ ] Deposit/Withdraw/Payment dialogs disabled without location selection (in totals view)
- [ ] UI builds and renders correctly

## Files to Modify
- android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/cash/CashScreen.kt

## Approach
1. Copy TabRow pattern from InventoryScreen
2. Display tabs from uiState.locations + "Всього" tab
3. Connect tab clicks to ViewModel selection methods
4. Show location name on history items in totals view
5. Disable action buttons in totals view (user must select location for operations)
6. Test on device
