# Report: cash-ui-tabs

Status: completed
Complexity: medium
Duration: ~4 minutes

## Summary
Added location tabs to CashScreen UI following InventoryScreen pattern.

## Changes Made

### CashScreen.kt
- Added `Tab` and `TabRow` imports
- Added TabRow with location tabs + "Всього" (Totals) tab after loading check
- Tab selection logic: totals tab is last, location tabs by index
- Clicking location tab calls `viewModel.selectLocation(location.id)`
- Clicking totals tab calls `viewModel.selectTotalView()`
- Added `enabled` parameter to ActionButtons (disabled in totals view)
- Updated HistoryItem to accept `showLocationName` parameter
- In totals view, history items show location name with bullet separator (date • location)

## Success Criteria
- [x] TabRow with location tabs + "Всього" tab
- [x] Clicking tab calls viewModel.selectLocation or selectTotalView
- [x] Balance displays per-location or total
- [x] History items show location indicator in totals view
- [x] Deposit/Withdraw/Payment buttons disabled in totals view
- [x] UI builds correctly

## Verification
- Build passes (compileDebugKotlin)

## Notes
- Pattern follows InventoryScreen implementation
- Operations require location selection - totals view is display-only
- Location name shown in primary color for visibility
