# Session: purchase-summary

Type: feature
Complexity: medium
Status: pending
Depends on: purchase-entry-flow

## Objective
Implement summary display and database save for completed purchase batches.

## Context
After user presses "Розрахувати", show summary overlay until tapped, then save to DB and return to main screen.

## Requirements

### Summary Display
Full-screen overlay showing:
```
┌─────────────────────────────┐
│         ПІДСУМОК            │
├─────────────────────────────┤
│ Горіх білий                 │
│   5.2 кг × 45 грн = 234 грн │
│ Насіння біле                │
│   3.0 кг × 50 грн = 150 грн │
├─────────────────────────────┤
│ Всього: 8.2 кг              │
│ Сума:   384 грн             │
├─────────────────────────────┤
│ Примітки: Постійний клієнт  │
└─────────────────────────────┘
      (торкніться для продовження)
```

### Save Flow
1. User taps summary
2. Create `PurchaseBatch` with totals
3. Create all `Transaction` records linked to batch
4. All in single DB transaction (atomic)
5. Show success feedback
6. Navigate back to main purchase screen
7. Main screen refreshes to show new batch

### Database Save
```kotlin
suspend fun savePurchaseBatch(
    locationId: UUID,
    positions: List<PurchasePosition>,
    notes: String?
): PurchaseBatch
```

Uses `@Transaction` annotation for atomicity.

### TODO: Receipt Printing
Add placeholder/comment for future receipt printing:
```kotlin
// TODO: Print receipt here (Phase 6 - hardware integration)
// printReceipt(batch, positions)
```

## Success Criteria
- [ ] Summary displays all positions correctly
- [ ] Totals calculated correctly
- [ ] Notes displayed
- [ ] Tap dismisses and saves
- [ ] Batch + transactions saved atomically
- [ ] Returns to main screen after save
- [ ] New batch appears in today's list
- [ ] Build passes

## Files to Create/Modify
- `ui/screens/purchase/PurchaseSummaryScreen.kt` (create)
- `ui/screens/purchase/PurchaseEntryViewModel.kt` (modify - trigger summary)
- `domain/repository/PurchaseBatchRepository.kt` (modify - add save method)
- `data/repository/PurchaseBatchRepositoryImpl.kt` (modify - implement save)
- `ui/navigation/NavGraph.kt` (modify - add route)

## Notes
- Summary is an overlay/dialog, not a separate navigation destination
- Could use Dialog or full-screen Composable
- Success feedback: brief snackbar or animation
- Receipt printing is explicitly deferred (add TODO comment)
