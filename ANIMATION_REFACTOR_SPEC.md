# AnimatedListItem Refactor — Spec & Task

## Context

Zagot+ is an offline-first Android procurement app (Kotlin, Jetpack Compose, Room, Supabase).
We added realtime sync — when data changes on another device, lists update live. Without
animations, cards pop in/out jarringly. We need smooth appear/disappear animations on all
list/grid items across the app.

## What Already Exists

`AnimatedListItem` composable in:
```
ui/components/AnimationModifiers.kt
```

It wraps content with `AnimatedVisibility` (fadeIn + expandVertically, 300ms tween).
Already applied to: **HistoryScreen**, **CashScreen**, **InventoryScreen**.

## Compose Version Constraint

**BOM 2024.02.00 (Compose 1.6.x, Kotlin 1.9.21)**. Cannot use `Modifier.animateItem()` 
(requires Compose 1.7 / Kotlin 2.0). Must use `AnimatedVisibility` wrapper approach.

## Task

Wrap every `LazyColumn` / `LazyVerticalGrid` data item across all screens with `AnimatedListItem`.

### Screens to update (files with `items()` in LazyColumn/LazyVerticalGrid):

**High priority — synced data that changes via realtime:**
1. `ui/screens/purchase/PurchaseScreen.kt` — today's purchased product list
2. `ui/screens/purchase/PurchaseSummaryScreen.kt` — batch summary items
3. `ui/screens/sale/SaleScreen.kt` — sale positions list
4. `ui/screens/reports/ReportsScreen.kt` — report data rows
5. `ui/screens/products/ProductsScreen.kt` — product management list
6. `ui/screens/transfer/TransferScreen.kt` — transfer items

**Medium priority — transaction entry (local-only but benefits from animation polish):**
7. `ui/screens/shared/TransactionPhoneComponents.kt` — phone transaction list
8. `ui/screens/shared/TransactionEntryBatchTabletContent.kt` — tablet batch entry list
9. `ui/screens/shared/TransactionEntryRegularTabletContent.kt` — tablet regular entry list
10. `ui/screens/shared/TransactionDialogs.kt` — dialog item lists
11. `ui/screens/purchase/BatchModeScreens.kt` — batch mode item lists

**Low priority / skip:**
- `ui/screens/settings/PrinterConfigDialog.kt` — static config, no sync
- `ui/screens/settings/ScalesDebugScreen.kt` — debug screen, no sync
- `ui/screens/auth/PinScreen.kt` — no lists

### Already done (DO NOT touch):
- `ui/screens/history/HistoryScreen.kt` ✅
- `ui/screens/cash/CashScreen.kt` ✅
- `ui/screens/inventory/InventoryScreen.kt` ✅

## How to Apply

For each `items(...)` block in a LazyColumn/LazyVerticalGrid:

**Before:**
```kotlin
items(dataList, key = { it.id }) { item ->
    SomeCard(
        // ... params
        modifier = Modifier.animateItemPlacement()
    )
}
```

**After:**
```kotlin
items(dataList, key = { it.id }) { item ->
    AnimatedListItem {
        SomeCard(
            // ... params
            modifier = Modifier.animateItemPlacement()
        )
    }
}
```

### Rules:
1. Import `com.zagot.zagotplus.ui.components.AnimatedListItem`
2. Wrap only DATA items, not static headers, footers, summary panels, or loading indicators
3. Keep existing `Modifier.animateItemPlacement()` — it handles reorder, `AnimatedListItem` handles appear/disappear
4. If an item doesn't have `animateItemPlacement()`, add it too (on the card's modifier)
5. Every `items()` block must have a `key` parameter. If missing, add one using the item's ID
6. Don't wrap `stickyHeader` blocks
7. Don't modify `AnimationModifiers.kt` — the composable is already correct
8. Don't change any business logic, only wrap UI items

## Verification

After changes, the app should:
- Compile without errors
- Show smooth fade+expand when new items appear in any list
- Not break existing functionality (scrolling, click handlers, expand/collapse)
- Not animate static content (headers, footers, empty states)
