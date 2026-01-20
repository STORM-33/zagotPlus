# Audit Report: UI & ViewModels

**Session:** phase-2/audit-ui-and-viewmodels
**Date:** 2026-01-19
**Status:** Complete

---

## Summary

Audited UI layer: 13 ViewModels, 14 UI components, 2 navigation files. Found **4 critical**, **5 high**, **14 medium**, and **6 low** severity issues.

---

## Findings

### 🔴 CRITICAL (Must Fix Before Release)

#### 1. Memory Leak - PurchaseEntryViewModel:187-202
```kotlin
productRepository.getActiveProducts().collect() // Never completes!
```
**Issue:** `.collect()` creates infinite Flow subscription in `loadProductsAndBatch()`.
**Fix:** Use `.first()` for single emission.

#### 2. Suspend Function Never Executes - HistoryViewModel:465
```kotlin
loadBatchTransactions(batchId) // Called directly without launch
```
**Issue:** `loadBatchTransactions()` is `suspend` but called synchronously from non-suspend context.
**Fix:** Wrap in `viewModelScope.launch { }`

#### 3. Main Thread Blocking - ReportsViewModel:198-201
```kotlin
getFilteredTransactions() with limit: 10000
```
**Issue:** Large dataset loaded on main thread, causes ANR risk.
**Fix:** Use `withContext(Dispatchers.IO)` or inject `@IoDispatcher`.

#### 4. Race Condition - CashViewModel:290-326
```kotlin
loadMoreOperations() // No state validation for rapid calls
```
**Issue:** Concurrent calls cause data inconsistency.
**Fix:** Add atomic state check / debounce.

---

### 🟠 HIGH (Should Fix, Workaround Exists)

#### 5. Null Safety - PurchaseEntryViewModel:506-507
**Issue:** `locationId` can be null from preferences, throws `IllegalStateException`.
**Fix:** Validate `locationId != null` before `finalizeBatch()`.

#### 6. Silent Failure - SaleEntryViewModel:597-602
**Issue:** Invalid UUID conversion caught silently, batch fails to load without user feedback.
**Fix:** Surface error to UI state.

#### 7. Job Cancellation Race - CashViewModel:168-281
**Issue:** `balanceCollectionJob` cancellation can race with active flow.
**Fix:** Use structured concurrency pattern.

#### 8. Race Condition - InventoryViewModel:240-292
**Issue:** Multiple independent coroutines in `loadInitialData()` complete in any order.
**Fix:** Use `async/await` or sequential loading.

#### 9. Memory Leak - TransferViewModel:230-252
**Issue:** `loadInventoryForLocation()` creates new job without cancelling previous.
**Fix:** Cancel previous job before launching new one.

---

### 🟡 MEDIUM

| # | File | Issue |
|---|------|-------|
| 10 | HistoryViewModel:188 | SQL injection risk - searchQuery not sanitized |
| 11 | CashViewModel:460 | Amount regex too permissive (allows ".5") |
| 12 | ProductsViewModel:158 | No MAX_AMOUNT validation unlike CashViewModel |
| 13 | PinViewModel:29-46 | No try-catch for corrupted preferences |
| 14 | ReportsViewModel:286-288 | Exception swallowed silently |
| 15 | CurrencyFormat.kt:19 | DecimalFormat not thread-safe |
| 16 | DateRangePicker.kt:40-47 | Hardcoded Ukrainian strings |
| 17 | DateRangePicker.kt:118 | DateTimeFormatter missing locale |
| 18 | AdminPinDialog.kt:42 | Hardcoded error message |
| 19 | LocationSelectionDialog.kt:44 | Hardcoded strings |
| 20 | ReorderableProductGrid.kt:87 | Hardcoded empty state text |
| 21 | ReorderableProductGrid.kt:145 | DecimalFormat recreated each render |
| 22 | NavGraph.kt:15,38 | Duplicate import |
| 23 | No deep links | Consider if external navigation needed |

---

### ⚪ LOW

| # | File | Issue |
|---|------|-------|
| 24 | CashViewModel:236 | Debounce TODO left in code |
| 25 | SaleViewModel:62 | Generic error messages |
| 26 | InventoryViewModel:204 | Null profit returns logged |
| 27 | SyncStatusIcon.kt:105 | Hardcoded content descriptions |
| 28 | LocationSelectionDialog.kt:105 | Enum display text hardcoded |
| 29 | ReorderableProductGrid.kt:221 | Uses toPlainString() not CurrencyFormat |

---

## Verified OK ✓

| Component | Status |
|-----------|--------|
| Navigation back stack handling | ✓ Correct (popUpTo with saveState) |
| Navigation type safety | ✓ All args typed with NavType |
| Route naming consistency | ✓ snake_case throughout |
| Double-tap exit prevention | ✓ Implemented |
| Timezone handling in DateRangePicker | ✓ Uses LocalDate + systemDefault |
| Null safety in composables | ✓ Good ?.let usage |

---

## Priority Fixes

1. **IMMEDIATE:** Fix 4 critical issues (memory leaks, thread safety)
2. **Before Release:** Fix 5 high issues (null safety, race conditions)
3. **Post-Release:** Extract hardcoded strings to resources
4. **Cleanup:** Thread-safe DecimalFormat, remove dead code

---

## Files Reviewed
- ViewModels: 13 files
- UI Components: 14 files
- Navigation: 2 files
- Screens: 13 files

## Issue Count
| Severity | Count |
|----------|-------|
| Critical | 4 |
| High | 5 |
| Medium | 14 |
| Low | 6 |
