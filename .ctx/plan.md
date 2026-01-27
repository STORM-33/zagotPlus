# Plan: Tablet UI Adaptation

Created: 2026-01-27
Status: in_progress

## Problem
Current screens use single-column layouts even on tablets. Adaptive helpers exist for **sizing** (button height, padding, text scale) but layouts don't utilize the increased **horizontal space** tablets provide.

## Target User
- Elderly worker at kiosk
- Needs prominent, easy-to-tap elements
- Purchase flow is most critical
- Phone UI should remain unchanged

## Design Approach
**Phone (< 600dp)**: Keep current vertical stacked layouts
**Tablet (>= 600dp)**: Use two-column layouts where content naturally splits into input/output or list/actions

---

## Progress
- Total sessions: 5
- Completed: 0
- In Progress: 0
- Remaining: 5

## Phases

### Phase 1: Layout Infrastructure
Status: pending

| # | Session | Complexity | Status |
|---|---------|------------|--------|
| 1 | add-two-column-helpers | low | pending |

### Phase 2: Purchase Flow (CRITICAL)
Status: pending

| # | Session | Complexity | Status |
|---|---------|------------|--------|
| 2 | tablet-purchase-entry | high | pending |

### Phase 3: Sale Flow
Status: pending

| # | Session | Complexity | Status |
|---|---------|------------|--------|
| 3 | tablet-sale-entry | high | pending |

### Phase 4: Other Screens
Status: pending

| # | Session | Complexity | Status |
|---|---------|------------|--------|
| 4 | tablet-inventory | medium | pending |
| 5 | tablet-history | low | pending |

---

## Session Details

### 1. add-two-column-helpers
**Complexity:** low
**File:** `ui/components/AdaptiveLayout.kt`

Add composables for tablet two-column layouts:

```kotlin
/**
 * Two-column layout for tablets, single column for phones.
 * Used for input/output split (e.g., weight entry + total display).
 */
@Composable
fun AdaptiveTwoColumn(
    modifier: Modifier = Modifier,
    leftWeight: Float = 0.55f,
    spacing: Dp = 24.dp,
    leftContent: @Composable ColumnScope.() -> Unit,
    rightContent: @Composable ColumnScope.() -> Unit
) {
    if (isTablet()) {
        Row(modifier, horizontalArrangement = Arrangement.spacedBy(spacing)) {
            Column(Modifier.weight(leftWeight)) { leftContent() }
            Column(Modifier.weight(1f - leftWeight)) { rightContent() }
        }
    } else {
        Column(modifier) {
            leftContent()
            rightContent()
        }
    }
}
```

---

### 2. tablet-purchase-entry
**Complexity:** high
**File:** `ui/screens/purchase/PurchaseEntryScreen.kt`

#### 2a. WeightEntry - Two-Column on Tablet

**Phone (current):**
```
[Weight Input    ]
[Price Input     ]
[  Total Card    ]
[ ADD POSITION   ]
```

**Tablet (new):**
```
+---------------------+-----------------+
| [Weight Input     ] |                 |
| [Price Input      ] |   TOTAL CARD    |
|                     |   1,234.00      |
|                     |                 |
|                     | [ADD POSITION]  |
+---------------------+-----------------+
```

- Left panel (55%): Weight + Price inputs (stacked)
- Right panel (45%): Total card + Add button (centered, prominent)
- Total text should be VERY large on tablets (use displayLarge)
- Add button should fill right panel width

#### 2b. PositionsList - Master-Detail on Tablet

**Phone (current):**
```
[Position 1]
[Position 2]
[Notes field]
---footer---
[Totals]
[Cancel] [Finalize]
```

**Tablet (new):**
```
+------------------------+----------------------+
| [Position 1          ] | [Notes field       ] |
| [Position 2          ] | [Location dropdown ] |
| [Position 3          ] |                      |
|                        | -------------------- |
| [+ Dodaty sche tovar]  | Vsogo:    12.5 kg    |
|                        |           $1,234     |
|                        |                      |
|                        | [Skasuvaty]          |
|                        | [ROZRAKHUVATY]       |
+------------------------+----------------------+
```

- Left panel (60%): Scrollable positions list + Add button at bottom
- Right panel (40%): Notes + Location + Totals + Action buttons (stacked vertically)
- Action buttons full-width, stacked for larger touch targets

---

### 3. tablet-sale-entry
**Complexity:** high
**File:** `ui/screens/sale/SaleEntryScreen.kt`

#### 3a. WeighingScreen - Two-Column on Tablet

```
+---------------------+---------------------+
|   PRODUCT NAME      | Zvazhyvannya:       |
|   25.50 kg (brutto) | [Batch 1: 10.2 kg]  |
|                     | [Batch 2: 15.3 kg]  |
| [Weight Input     ] |                     |
| [Tare Count Input ] |                     |
| [DODATY ZVAZHYV]    |     [DALI ->]       |
+---------------------+---------------------+
```

- Left: Product card + inputs + add batch button
- Right: Batches list + proceed button (at bottom right)

#### 3b. PositionReviewScreen - Two-Column on Tablet

```
+---------------------+---------------------+
| PRODUCT NAME        |                     |
| +------------------+|    SUMA:            |
| | Brutto: 25.5 kg ||    $1,234           |
| | Tara: -2.0 kg   ||                     |
| | Netto: 23.5 kg  ||                     |
| +------------------+|                     |
| [Tare Weight Input] |                     |
| [Price Input      ] | [DODATY POZYCIYU]   |
+---------------------+---------------------+
```

#### 3c. PositionsListScreen - Same as Purchase PositionsList

---

### 4. tablet-inventory
**Complexity:** medium
**File:** `ui/screens/inventory/InventoryScreen.kt`

Two-Panel Layout on Tablet:

```
+------------------------+----------------------+
| [Product 1: 50kg     ] |                      |
| [Product 2: 30kg     ] |    SUMMARY PANEL     |
| [Product 3: 25kg     ] |    Total: 105 kg     |
| [Product 4: 0kg !!   ] |    Value: $12,345    |
|                        |    Profit: $2,345    |
+------------------------+----------------------+
```

- Inventory list on left (scrollable)
- Summary panel fixed on right
- Summary panel content centered, prominent typography

---

### 5. tablet-history
**Complexity:** low
**File:** `ui/screens/history/HistoryScreen.kt`

Optional improvements:
- Consider showing expanded batch details inline on tablets
- Two-column: list on left, selected batch details on right
- Lower priority - current expandable cards work fine

---

## Files to Modify

1. `ui/components/AdaptiveLayout.kt` - Add two-column composables
2. `ui/screens/purchase/PurchaseEntryScreen.kt` - WeightEntry + PositionsList
3. `ui/screens/sale/SaleEntryScreen.kt` - All sub-screens
4. `ui/screens/inventory/InventoryScreen.kt` - Two-panel layout
5. `ui/screens/history/HistoryScreen.kt` - Optional

---

## Key Principles

1. **Phone layouts unchanged** - `isTablet()` check gates all changes
2. **Larger touch targets on tablets** - Use existing `adaptiveButtonHeight()` and `adaptivePrimaryButtonHeight()`
3. **Prominent totals/actions** - Right panel should have bold, centered content
4. **Natural content split** - Input/output, list/actions, data/summary
5. **Consistent patterns** - Same two-column approach across all entry screens
6. **Elderly-friendly** - Large text, clear labels, obvious buttons
