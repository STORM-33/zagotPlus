# Sale Flow Refactoring: Mode Selection + Tablet Sliding Panel

Refactor the Sale Entry flow to support two modes: **Regular** (simple single-weight) and **Wholesale** (batch weighing with tare tracking). For tablet wholesale mode, implement a sliding panel layout that locks product selection during weighing.

## User Review Required

> [!IMPORTANT]
> **Navigation Design Decision**: Mode selection will be embedded in `SaleScreen.kt` itself instead of creating a separate screen. This keeps navigation simpler and follows the pattern of Purchase where the entry point is direct.

> [!WARNING]
> **Breaking Change**: The existing `SaleEntry` destination will accept a new `mode` query parameter. This affects `NavGraph.kt`, `Destinations.kt`, `SaleScreen.kt`, and potentially deep links.

---

## Proposed Changes

### Navigation Layer

#### [MODIFY] [Destinations.kt](file:///c:/Users/vovod/Documents/zagotPlus/android/app/src/main/kotlin/com/zagot/zagotplus/ui/navigation/Destinations.kt)
- Add `SaleMode` enum: `REGULAR`, `WHOLESALE` 
- Update `SaleEntry` route pattern: `sale_entry?batchId={batchId}&mode={mode}`
- Add `ARG_MODE` constant and update `createRoute()` function

#### [MODIFY] [NavGraph.kt](file:///c:/Users/vovod/Documents/zagotPlus/android/app/src/main/kotlin/com/zagot/zagotplus/ui/navigation/NavGraph.kt)
- Add `mode` argument to SaleEntry composable route (line ~284-302)
- Parse mode from arguments and pass to `SaleEntryScreen`

---

### Sale Module - ViewModel Layer

#### [MODIFY] [SaleEntryViewModel.kt](file:///c:/Users/vovod/Documents/zagotPlus/android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/sale/SaleEntryViewModel.kt)
- Add `saleMode` to `SaleEntryUiState` (stored from navigation arg)
- Add Regular mode position data class or reuse `SalePosition` with single batch
- Add `canAddRegularPosition` computed property (requires weight + price, no batches)
- Add `addRegularPositionAndContinue()` function for regular mode
- Modify `setTabletMode()` to consider mode when setting screen state

---

### Sale Module - Screen Layer

#### [MODIFY] [SaleScreen.kt](file:///c:/Users/vovod/Documents/zagotPlus/android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/sale/SaleScreen.kt)
- Replace direct "НОВИЙ ПРОДАЖ" button with mode selection UI
- Add two large buttons: "Звичайний" and "Оптовий" 
- Add description text under each button
- Update `onNavigateToNewSale` callback to accept `SaleMode` parameter

#### [MODIFY] [SaleEntryScreen.kt](file:///c:/Users/vovod/Documents/zagotPlus/android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/sale/SaleEntryScreen.kt)
- Accept `mode: SaleMode` parameter
- Pass mode to ViewModel during initialization
- Route to appropriate tablet content based on mode (Regular vs Wholesale)

---

### Sale Module - Regular Mode Tablet

#### [NEW] [SaleEntryRegularTabletContent.kt](file:///c:/Users/vovod/Documents/zagotPlus/android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/sale/SaleEntryRegularTabletContent.kt)
- Copy structure from `PurchaseEntryTabletContent.kt`
- Three-column layout: Products (40%) | Positions (30%) | Data Entry (30%)
- Simple weight + price inputs (no batches, no tare)
- Uses `defaultSellPrice` instead of `defaultBuyPrice`
- Total = Weight × Price directly

---

### Sale Module - Wholesale Mode Sliding Panel

#### [MODIFY] [SaleEntryTabletContent.kt](file:///c:/Users/vovod/Documents/zagotPlus/android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/sale/SaleEntryTabletContent.kt)
- Refactor from `Row` to `Box` with layered children
- Add sliding animation state derived from `selectedProduct != null`
- Implement `animateFloatAsState` for panel translation (150-200ms, FastOutSlowIn)
- When product selected:
  - Positions panel slides left 75% of Products panel width
  - Products panel: `alpha = 0.3`, `clickable(enabled = false)`
  - Positions panel: `alpha = 0.5`, greyed overlay
  - Weightings panel revealed underneath
- Add "СКАСУВАТИ ТОВАР" button to cancel weighing mode
- State transitions: IDLE → BATCH_ENTRY → FINALIZATION → IDLE

---

## Verification Plan

### Existing Tests

#### [SaleEntryViewModelTest.kt](file:///c:/Users/vovod/Documents/zagotPlus/android/app/src/test/kotlin/com/zagot/zagotplus/ui/screens/sale/SaleEntryViewModelTest.kt)
Run existing 37 tests to ensure wholesale mode logic is preserved:
```bash
./gradlew :android:app:testDebugUnitTest --tests "com.zagot.zagotplus.ui.screens.sale.SaleEntryViewModelTest"
```

### New Tests to Add

1. **Mode initialization test**: Verify `SaleEntryViewModel` correctly stores mode from nav args
2. **Regular mode add position test**: Verify position is created with weight × price (no tare)
3. **Mode immutability test**: Verify mode cannot change after initialization

### Build Verification
```bash
./gradlew :android:app:assembleDebug
```

### Manual Verification

#### Mode Selection (Phone/Tablet)
1. Launch app → Navigate to Sale tab → Tap "НОВИЙ ПРОДАЖ"
2. **Expected**: Mode selection UI appears with two buttons
3. Tap "Звичайний" → **Expected**: Navigates to Sale Entry in Regular mode
4. Press back → Tap "Оптовий" → **Expected**: Navigates to Sale Entry in Wholesale mode

#### Regular Mode (Tablet)
1. Enter Regular mode on tablet
2. **Expected**: Three-column layout (Products | Positions | Data Entry)
3. Tap a product → **Expected**: Product name appears in data entry panel
4. Enter weight: 25.5 → Enter price: 18.50
5. **Expected**: Total shows ₴471.75 (25.5 × 18.50)
6. Tap Add → **Expected**: Position appears in middle panel
7. Tap product in positions → **Expected**: Inline edit with numpad

#### Wholesale Mode Sliding Panel (Tablet)
1. Enter Wholesale mode on tablet
2. **Expected**: Three-column layout in idle state
3. Tap a product → **Expected**: Sliding animation (150-200ms)
   - Products panel greyed out (~30% opacity), not clickable
   - Positions panel slid left, greyed out
   - Weightings panel revealed
4. Add batches (e.g., 25.3 kg / 2 sacks, 24.8 kg / 2 sacks)
5. Tap "ПЕРЕГЛЯНУТИ" → **Expected**: Finalization panel appears
6. Enter tare weight + price → Tap "ADD POSITION"
7. **Expected**: Slides back to idle, position added
8. Tap "СКАСУВАТИ ТОВАР" during weighing → **Expected**: Discards batches, returns to idle
