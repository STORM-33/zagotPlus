# Session: purchase-main-screen

Type: feature
Complexity: medium
Status: pending
Depends on: db-batches

## Objective
Redesign the main purchase screen to show today's batch history, weight placeholder, and "new client" entry point.

## Context
Current PurchaseScreen is a single-transaction form. New design:
- Top: Weight placeholder (for future scales)
- Middle: Today's batches list (scrollable, lazy loading)
- Bottom: "Новий клієнт" button

## Requirements

### UI Layout
```
┌─────────────────────────────┐
│    Вага: -- кг              │  ← Placeholder for scales
├─────────────────────────────┤
│ Закупівлі сьогодні          │
├─────────────────────────────┤
│ 14:32  15.5 кг  ₴350  3 поз │  ← Batch item
│ 12:15  8.2 кг   ₴180  2 поз │
│ 10:45  22.0 кг  ₴500  4 поз │
│ ...                         │  ← LazyColumn
├─────────────────────────────┤
│    [ НОВИЙ КЛІЄНТ ]         │  ← Button
└─────────────────────────────┘
```

### ViewModel
- Load today's batches (from midnight)
- Gradual loading (pagination)
- Batch item data: time, total_weight, total_amount, item_count
- Navigation state for "new client" flow

### Batch Item Display
- Time: HH:mm format
- Weight: X.X кг
- Sum: ₴XXX (or XXX грн)
- Positions: N поз

## Success Criteria
- [ ] Today's batches displayed in scrollable list
- [ ] Lazy loading works (pagination)
- [ ] Weight placeholder visible
- [ ] "Новий клієнт" button navigates to entry flow
- [ ] Empty state when no batches today
- [ ] Build passes

## Files to Create/Modify
- `ui/screens/purchase/PurchaseScreen.kt` (rewrite)
- `ui/screens/purchase/PurchaseViewModel.kt` (rewrite)
- `ui/navigation/NavGraph.kt` (modify - add nested route)

## Notes
- Keep old screen backup or use git for rollback
- Navigation to entry flow will be added in next session
- Consider pulling batch list on return from entry flow
