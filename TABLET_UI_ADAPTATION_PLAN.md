# Zagot Plus — Tablet UI Adaptation Plan

## Executive Summary

**Goal:** Transform the 3-step wizard flow (Product → Weight → Positions) into a single-screen kiosk layout for tablets, matching the reference design.

**Current Status:** ✅ **Purchase Entry COMPLETED** | ⏳ Sale Entry pending  
**Estimated Remaining Effort:** 2-3 development days (Sale Entry + Testing)

---

## ✅ COMPLETED: Purchase Entry Tablet Adaptation

### Implementation Date: 2026-01-27

### What Was Built

#### 1. Core Components

| Component | Location | Purpose |
|-----------|----------|---------|
| `CustomNumpad.kt` | `ui/components/` | Custom kiosk-style numpad with dynamic sizing |
| `InputDisplayBox.kt` | `ui/components/` | Fake text field for numpad input (no system keyboard) |
| `PurchaseEntryTabletContent.kt` | `ui/screens/purchase/` | Three-column tablet layout |

#### 2. ViewModel Enhancements

Added to `PurchaseEntryViewModel.kt`:
- `InputField` enum (WEIGHT, PRICE) for numpad focus tracking
- `activeInputField` state
- `tabletEditingPositionId` for inline position editing
- `isTabletEditMode` computed property
- `onKeypadInput()`, `onKeypadDecimal()`, `onKeypadBackspace()` methods
- `onNextInputField()` for cycling focus
- `selectInputFieldAndClear()` for auto-clear on tap
- `selectTabletPosition()` for tap-to-edit workflow
- Modified `addPosition()` to handle edit mode updates

#### 3. Screen State Changes

Added `UNIFIED_ENTRY` state for tablet mode in `PurchaseEntryScreenState` enum.

#### 4. Layout Architecture

```
┌────────────────────┬─────────────────────┬─────────────────────────────┐
│                    │                     │  [Скасувати] [РОЗРАХУВАТИ]  │ ← Top Bar
├────────────────────┼─────────────────────┼─────────────────────────────┤
│                    │                     │      Введення даних         │
│  ┌────┐ ┌────┐    │  Позиції (N)        │  ┌───────────────────────┐  │
│  │Prod│ │Prod│    │                     │  │   [Product Name]       │  │
│  │ 1  │ │ 2  │    │  Item 1... ✕        │  └───────────────────────┘  │
│  └────┘ └────┘    │  Item 2... ✕        │                             │
│  ┌────┐ ┌────┐    │                     │  ┌──────────┐ ┌───────────┐ │
│  │Prod│ │Prod│    │                     │  │ Вага(кг) │ │ Ціна(₴/кг)│ │
│  │ 3  │ │ 4  │    │  ┌─────────────┐    │  │  2.87    │ │   35.0    │ │
│  └────┘ └────┘    │  │  Примітки   │    │  └──────────┘ └───────────┘ │
│        ...        │  └─────────────┘    │                             │
│                   │                     │  ┌───────────────────────┐  │
│                   │  ┌─────────────────┐│  │   Сума: ₴100.45       │  │ ← Item Total
│                   │  │  Всього         ││  └───────────────────────┘  │
│                   │  │  25 кг  ₴5000   ││                             │
│                   │  └─────────────────┘│  ┌─┬─┬─┬───┐                │
│                   │   ↑ Grand Total     │  │1│2│3│ ⌫ │                │
│                   │                     │  │4│5│6│→│ │                │
│                   │                     │  │7│8│9│   │                │
│                   │                     │  │0│ . │ + │ ← Action       │
│                   │                     │  └─┴───┴───┘                │
└───────────────────┴─────────────────────┴─────────────────────────────┘
      ~40%                  ~30%                     ~30%
```

#### 5. UX Decisions Made

| Decision | Implementation |
|----------|----------------|
| **System keyboard** | Replaced with custom numpad |
| **Position editing** | Tap to select → edit inline → tap action button to confirm |
| **Flow buttons** | Moved to top bar for clear separation from data entry |
| **Grand Total** | Moved to Positions panel (Receipt context) |
| **Item Subtotal** | Made large and prominent in data entry (instant math feedback) |
| **Action button** | Uses icons (+ for add, ✓ for edit) instead of text |
| **Ripple clipping** | Fixed with clip modifier before clickable |

#### 6. Key Files Modified

| File | Changes |
|------|---------|
| `PurchaseEntryScreen.kt` | Added UNIFIED_ENTRY branch, top bar action buttons |
| `PurchaseEntryViewModel.kt` | Numpad input handling, tablet edit mode |
| `PurchaseEntryTabletContent.kt` | Complete rewrite with 3-column layout + numpad |

---

## ✅ COMPLETED: Sale Entry Tablet Adaptation

### Implementation Date: 2026-01-27

### What Was Built

#### 1. ViewModel Enhancements

Added to `SaleEntryViewModel.kt`:
- `SaleInputField` enum (WEIGHT, TARE_COUNT, TARE_WEIGHT_UNIT, PRICE) for numpad focus tracking
- `activeInputField` state and `isInFinalizationMode` state for two-stage workflow
- `onKeypadInput()`, `onKeypadDecimal()`, `onKeypadBackspace()` methods for numpad
- `onNextInputField()` for cycling between fields
- `selectInputFieldAndClear()` for tap-to-clear UX
- `toggleFinalizationMode()` to switch between batch entry and finalization modes
- `resetTabletState()` to reset mode when product changes
- `setTabletMode()` to enable unified tablet layout

#### 2. Tablet Content Component

Created `SaleEntryTabletContent.kt` with:
- Three-column layout (40% Product Grid, 30% Positions, 30% Data Entry)
- **Batch Entry Mode** (right panel):
  - Weight + Tare Count input boxes
  - Running total card showing batch count and gross weight
  - Scrollable batches list with delete option
  - "REVIEW" button to proceed to finalization
  - Custom numpad with "+" action button for adding batches
- **Finalization Mode** (right panel):
  - Tare Weight per Unit + Price input boxes
  - Weight calculation breakdown (Brutto - Tara = Netto)
  - Large total amount display
  - "BACK TO BATCHES" button
  - Custom numpad with "✓" action button for adding position

#### 3. Screen Integration

Updated `SaleEntryScreen.kt`:
- Added `UNIFIED_ENTRY` state for tablet mode
- Top bar action buttons (Cancel / Finalize) for tablet
- Disabled step indicator for tablet mode
- BackHandler support for tablet state

### Key UX Decisions

| Decision | Implementation |
|----------|----------------|
| **Batch workflow** | Two-stage process: batch entry → finalization |
| **Mode switching** | Manual toggle via "REVIEW" button (not automatic) |
| **Batch editing** | Tap batch in list to remove, or edit full position via dialog |
| **Position editing** | Tap position in center panel → opens edit dialog with batch history |
| **Numpad behavior** | Cycles through relevant fields based on current mode |
| **Action button** | Changes from "+" (add batch) to "✓" (add position) between modes |
| **Smart zones** | Middle panel switches context: Weightings (batch entry) ↔ Positions (finalization) |

### Smart Zone Repurposing (Refinement)

**Problem:** Weightings list was cramped in right panel (30% width), causing scrolling issues with 10+ batches.

**Solution:** Middle panel becomes context-aware, switching between two modes:

#### Mode 1: Weightings View (During Product Entry)
- **When:** Product selected (both batch entry AND finalization modes)
- **Shows:** Weightings list for current product
- **Content:**
  - Product name header
  - Running total card (batch count + gross weight)
  - Scrollable batch list with numbered badges
  - Large, detailed batch items (weight + tare count + delete)
- **Space:** Full 30% width dedicated to batches
- **Why:** User needs to see weightings while setting tare and price!

#### Mode 2: Positions View (Between Products)
- **When:** No product selected, or after adding position
- **Shows:** Positions list + Notes + Grand Total
- **Content:**
  - Positions list with tap-to-edit
  - Notes input field
  - Grand total card (receipt style)
- **Space:** Same 30% width for positions

#### Automatic Switching Logic:
```
Product selected     → Show Weightings
Press "REVIEW"       → Keep Weightings (user needs to see what they're pricing!)
Add Position         → Switch to Positions
Select new product   → Show Weightings
```

#### Right Panel Optimization:
- **Numpad height:** Reduced from 75% to 60%
- **REVIEW button:** Always visible (52dp height)
  - Enabled when batches exist
  - Disabled (grayed out) when no batches
  - Text: "ПЕРЕГЛЯНУТИ ТА ВСТАНОВИТИ ЦІНУ"
- **Top section:** Compressed to 20% (removed hint card)
- **Input labels:** Shortened ("Тара (шт)" instead of "Кількість тари")

#### Benefits:
- ✅ **3x more space** for weightings (30% vs ~10% of right panel)
- ✅ **Better scalability** - handles 20+ batches without cramping
- ✅ **Clearer focus** - one task at a time in middle panel
- ✅ **Better UX** - display matches user's current context
- ✅ **Always-visible button** - clear next action, no hunting for it
- ✅ **Weightings stay visible** - user can review batches while setting price

### Comparison with Purchase Entry

| Feature | Purchase Entry | Sale Entry |
|---------|----------------|------------|
| **Workflow** | Single-step (weight + price) | Two-step (batches → finalization) |
| **Input fields** | 2 fields (weight, price) | 4 fields (weight, tare count, tare weight, price) |
| **Data entry modes** | One mode | Two modes (batch entry / finalization) |
| **Repeatable entry** | One item per product | Multiple batches per product |
| **Calculation display** | Item subtotal | Weight breakdown + total amount |
| **Middle panel** | Static (always positions) | Dynamic (weightings ↔ positions) |

### Phase 7: Final Testing

**Status:** Ready for testing

Manual testing required:
- [ ] Test purchase flow on tablets
- [ ] Test sale flow on tablets (batch entry → finalization → add position)
- [ ] Test sale batch editing and review
- [ ] Test phone flows unchanged
- [ ] Test landscape orientation
- [ ] Test with scale integration
- [ ] Test edit mode for existing batches

---

## Testing Checklist

### ✅ Purchase Entry (Completed)

- [x] Three-column layout renders correctly
- [x] Product selection updates data entry panel
- [x] Custom numpad works for weight/price input
- [x] Tap-to-edit positions workflow
- [x] Action button changes to edit mode (yellow, ✓ icon)
- [x] Grand Total in positions panel
- [x] Item subtotal prominent in data entry
- [x] Top bar action buttons work
- [x] Ripple effects respect card borders

### ✅ Sale Entry (Completed - Ready for Testing)

Implementation complete, pending manual device testing:
- [ ] Three-column layout renders correctly
- [ ] Product selection clears previous product state
- [ ] Batch entry mode: weight + tare count inputs work
- [ ] Custom numpad works for all input fields
- [ ] Adding batches updates running total
- [ ] Batch list displays correctly with delete buttons
- [ ] "REVIEW" button transitions to finalization mode
- [ ] Finalization mode: tare weight + price inputs work
- [ ] Weight calculation breakdown displays correctly (brutto - tara = netto)
- [ ] "BACK TO BATCHES" button returns to batch entry mode
- [ ] Action button works in both modes (+ for batch, ✓ for position)
- [ ] Adding position clears state and returns to batch entry mode
- [ ] Position editing via tap opens dialog with batch history
- [ ] Grand Total in positions panel updates correctly
- [ ] Top bar action buttons work (Cancel / Finalize)
- [ ] Notes field integrates properly

### ⏳ Device Testing (Pending)

- [ ] Small tablet (600-840dp)
- [ ] Large tablet (>840dp)
- [ ] Tablet landscape
- [ ] Phone unchanged

---

## Questions Resolved

| Question | Resolution |
|----------|------------|
| NumPad integration | ✅ Implemented custom numpad with dynamic sizing |
| Product dropdown vs tap | Tap-to-select from grid (no dropdown needed) |
| Position editing on tablet | Tap to select, inline edit, tap action to confirm (Purchase) / Dialog (Sale) |
| Flow button placement | Top bar for clear separation from work area |
| Total placement | Grand Total in Positions (Receipt), Item Total in Data Entry |
| Sale batch workflow | Two-stage: Batch Entry Mode → Finalization Mode |
| Batch editing in Sale | Remove in batch list, or edit full position via dialog |

---

## Summary

### ✅ COMPLETED: Both Purchase and Sale Entry Tablet Adaptations

**Implementation Date:** 2026-01-27

**Total Files Created:**
- `CustomNumpad.kt` - Kiosk-style numpad component (shared)
- `InputDisplayBox.kt` - Custom input display (shared)
- `PurchaseEntryTabletContent.kt` - Purchase tablet layout
- `SaleEntryTabletContent.kt` - Sale tablet layout

**Total Files Modified:**
- `PurchaseEntryViewModel.kt` - Numpad input handling
- `PurchaseEntryScreen.kt` - UNIFIED_ENTRY state handling
- `SaleEntryViewModel.kt` - Numpad input handling + two-stage workflow
- `SaleEntryScreen.kt` - UNIFIED_ENTRY state handling
- `AdaptiveLayout.kt` - Layout utilities (if modified)

**Estimated vs Actual:**
- Original estimate: 3-5 development days
- Actual time: 2 development sessions (Purchase + Sale)

**Next Steps:**
1. Manual testing on physical tablet devices
2. Test with hardware scale integration
3. Gather user feedback on workflow
4. Iterate on UX improvements if needed

