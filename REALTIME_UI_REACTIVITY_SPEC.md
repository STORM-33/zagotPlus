# Realtime UI Reactivity — Spec & Task

## Context

Zagot+ has a sync engine that writes remote changes to Room via realtime events. The UI needs to react to these changes smoothly. Currently:
- ✅ New records appear with fade+expand animation (via `AnimatedListItem`)
- ❌ Deleted/voided records don't animate out — they just disappear on next reload
- ❌ Changed records don't animate (e.g., value updates)
- ❌ Cash history section doesn't update at all without reopening the app
- ❌ Corrections and transfers don't appear in history until manual pull-to-refresh

## Root Causes

### 1. ViewModels not fully reactive to Room changes
Some ViewModels load data once (or on manual refresh) instead of observing Room `Flow`s. When the sync engine writes to Room, the UI doesn't know about it.

**CashViewModel** (`ui/screens/cash/CashViewModel.kt`):
- Balance/categories: ✅ reactive (uses `combine` + `collect` on Room Flows)
- History items: ❌ NOT reactive — loads via `getCashHistoryPaged()` one-shot calls in `refreshOperations()`. No Flow observation. Only refreshes on explicit user action or after local writes.
- **Fix needed:** Observe a Room Flow for cash operation count/changes, trigger `refreshOperations()` when it changes (same pattern as HistoryViewModel's `observeBatchChanges`)

**HistoryViewModel** (`ui/screens/history/HistoryViewModel.kt`):
- Observes batch count changes ✅ (triggers reload when count changes)
- But only watches `purchase_batches` and `sale_batches` count
- **Missing:** Doesn't observe `transactions` changes (corrections/voids update existing batches) or `cash_operations` (transfers). When a batch is voided or corrected, the batch count doesn't change — only the batch content changes.
- **Fix needed:** Also observe a "content changed" signal — e.g., max `server_updated_at` across purchase_batches + sale_batches + transactions. When it changes, reload.

### 2. No disappear animation
`AnimatedListItem` only animates appearance (fadeIn + expandVertically on first composition). When an item is removed from the list, it just vanishes because it leaves the composition tree immediately.

**Fix:** Use `animateContentSize()` on item containers + animate alpha. For LazyColumn, Compose 1.6 doesn't natively support exit animations on item removal. The practical approach:
- Use `Modifier.animateContentSize()` on cards for size changes (value updates)
- For value changes (e.g., quantity update), use `AnimatedContent` or `Crossfade` on the changing text values
- For item removal: Accept the limitation on Compose 1.6 — items will disappear without exit animation. The important thing is that the LIST reacts to the change at all (which is the reactivity fix above). Exit animations on lazy lists require Compose 1.7+.

### 3. Value change animation
When a record is updated (e.g., inventory quantity changes, cash balance changes), the new value should transition smoothly. The cash screen already uses `animateContentSize()` on expandable cards. We should use `AnimatedContent` with a slide+fade for changing numeric values.

## Tasks

### Task 1: Make CashViewModel reactive to sync changes

In `CashViewModel`, add observation of cash operation changes similar to how `HistoryViewModel.observeBatchChanges()` works:

```kotlin
private fun observeCashChanges() {
    viewModelScope.launch {
        cashRepository.observeTotalOperationCount() // Need to add this to repo/DAO
            .distinctUntilChanged()
            .collect { count ->
                if (lastKnownCashCount >= 0 && count != lastKnownCashCount) {
                    refreshOperations()
                }
                lastKnownCashCount = count
            }
    }
}
```

**Files to modify:**
- `data/local/dao/CashDao.kt` — add `@Query("SELECT COUNT(*) FROM cash_operations") fun observeCount(): Flow<Int>`
- `data/repository/CashRepositoryImpl.kt` — expose the new DAO method
- `domain/repository/CashRepository.kt` (if interface exists) — add to interface
- `ui/screens/cash/CashViewModel.kt` — add `observeCashChanges()`, call in init

### Task 2: Make HistoryViewModel reactive to content changes (not just count)

The current `observeBatchChanges()` only detects NEW batches (count change). It misses:
- Voided batches (batch still exists, just marked voided)
- Corrections (transactions updated)
- Transfers (appear as special batch types)

**Fix:** Observe max `server_updated_at` across synced tables, not just count.

**Files to modify:**
- `data/local/dao/PurchaseBatchDao.kt` — add `@Query("SELECT MAX(serverUpdatedAt) FROM purchase_batches") fun observeLatestUpdate(): Flow<Long?>`
- `data/local/dao/SaleBatchDao.kt` — same
- `data/local/dao/TransactionDao.kt` — same  
- Repository layer — expose these flows
- `ui/screens/history/HistoryViewModel.kt` — combine the three flows, reload on change

### Task 3: Value change animations

Create a reusable `AnimatedValue` composable for smooth numeric transitions:

```kotlin
@Composable
fun AnimatedValueText(
    targetValue: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    AnimatedContent(
        targetState = targetValue,
        transitionSpec = {
            (fadeIn(tween(200)) + slideInVertically { -it / 2 })
                .togetherWith(fadeOut(tween(200)) + slideOutVertically { it / 2 })
        },
        label = "valueChange"
    ) { value ->
        Text(text = value, modifier = modifier, style = style, color = color)
    }
}
```

**Add to:** `ui/components/AnimationModifiers.kt`

**Apply to key numeric displays:**
- Cash balance display (CashScreen)
- Daily change display (CashScreen)  
- Inventory quantities (InventoryScreen)
- Report totals (ReportsScreen)

Look for `Text(text = currencyFormat.format(...)` or similar formatted number displays in these screens and wrap with `AnimatedValueText`.

### Task 4: animateContentSize on cards that change content

Add `Modifier.animateContentSize()` to cards whose content can change due to sync:
- `ExpandableBatchCard` in HistoryScreen (batch details may update)
- `InventoryItemCard` in InventoryScreen (quantities change)
- `ExpandableDayCard` in CashScreen (operations may be added within a day group)

This makes cards smoothly resize when their content changes rather than jumping.

## Rules

1. **Don't touch AnimatedListItem** — it's correct for appear animations
2. **Don't add animateItemPlacement()** — it's not compatible with AnimatedListItem wrapper (breaks LazyItemScope)
3. **Keep all existing business logic unchanged** — only add reactivity and animation
4. **Use existing patterns** — follow HistoryViewModel's `observeBatchChanges()` pattern for new observers
5. **Room DAO queries that return Flow must NOT use suspend** — `fun observeX(): Flow<T>` (not `suspend fun`)
6. **Test the DAO queries** — make sure the SQL is valid (no typos in column names). Room column names use camelCase in entities but the @ColumnInfo might map to snake_case.
7. **Import AnimatedContent from androidx.compose.animation** (not foundation)

## Compose Constraints
- **BOM 2024.02.00 / Compose 1.6.x / Kotlin 1.9.21**
- `AnimatedContent` is available ✅
- `Crossfade` is available ✅  
- `animateContentSize()` is available ✅
- `Modifier.animateItem()` is NOT available (requires 1.7)

## File Reference

Key files (all under `android/app/src/main/kotlin/com/zagot/zagotplus/`):

**DAOs:**
- `data/local/dao/CashOperationDao.kt` (or `CashDao.kt`)
- `data/local/dao/PurchaseBatchDao.kt`
- `data/local/dao/SaleBatchDao.kt`
- `data/local/dao/TransactionDao.kt`

**Repositories:**
- `data/repository/CashRepositoryImpl.kt`
- `data/repository/PurchaseBatchRepositoryImpl.kt`
- `data/repository/SaleBatchRepositoryImpl.kt`
- `data/repository/TransactionRepositoryImpl.kt`

**ViewModels:**
- `ui/screens/cash/CashViewModel.kt`
- `ui/screens/history/HistoryViewModel.kt`
- `ui/screens/inventory/InventoryViewModel.kt`
- `ui/screens/reports/ReportsViewModel.kt`

**UI Components:**
- `ui/components/AnimationModifiers.kt` — add AnimatedValueText here
- `ui/screens/cash/CashScreen.kt`
- `ui/screens/history/HistoryScreen.kt`
- `ui/screens/inventory/InventoryScreen.kt`
- `ui/screens/reports/ReportsScreen.kt`

## Priority Order

1. Task 1 (CashViewModel reactivity) — most broken, user has to reopen app
2. Task 2 (HistoryViewModel content reactivity) — corrections/transfers don't show
3. Task 3 (AnimatedValueText) — polish
4. Task 4 (animateContentSize) — polish
