# Animated Counter Refactor — Spec & Task

## Context

Zagot+ has realtime sync. When values change on another device, numeric displays should animate smoothly — like the cash balance card already does (spring counter that rolls from old value to new value).

## What Already Exists

In `ui/components/AnimationModifiers.kt`:
- `AnimatedValueText` — slide+fade text swap for string values (already used in BalanceCard for daily change)

In `ui/screens/cash/CashScreen.kt` → `BalanceCard`:
- `Animatable(0f)` + `LaunchedEffect` + `spring(stiffness = 300f)` pattern for the rolling number counter
- This is the target animation style

## Task

### Step 1: Create reusable `AnimatedCounter` composable

Add to `ui/components/AnimationModifiers.kt`:

```kotlin
/**
 * Rolling number counter that springs from current value to target.
 * Used for numeric displays (weights, amounts, totals) that change via sync.
 *
 * @param targetValue the number to animate toward
 * @param formatter converts the animated BigDecimal to display string (e.g., "1,234.56 кг")
 * @param modifier standard modifier
 * @param style text style
 * @param color text color
 * @param fontWeight optional font weight
 * @param textAlign optional text alignment
 * @param stiffness spring stiffness — lower = slower/smoother, higher = snappier. Default 300f.
 */
@Composable
fun AnimatedCounter(
    targetValue: BigDecimal,
    formatter: (BigDecimal) -> String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    textAlign: TextAlign? = null,
    stiffness: Float = 300f,
) {
    val animatable = remember { Animatable(targetValue.toFloat()) }

    LaunchedEffect(targetValue) {
        animatable.animateTo(
            targetValue = targetValue.toFloat(),
            animationSpec = spring(stiffness = stiffness)
        )
    }

    Text(
        text = formatter(BigDecimal(animatable.value.toDouble())),
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        textAlign = textAlign,
    )
}
```

Required imports: `androidx.compose.animation.core.Animatable`, `androidx.compose.animation.core.spring`, `java.math.BigDecimal`.

### Step 2: Refactor BalanceCard to use AnimatedCounter

Replace the manual `Animatable` + `LaunchedEffect` + `Text` pattern in `BalanceCard` (CashScreen.kt ~line 617-652) with the new `AnimatedCounter`. This validates the composable works correctly before mass-applying it.

### Step 3: Apply AnimatedCounter to all changing numeric displays

**Target pattern — find and replace:**

BEFORE (typical):
```kotlin
Text(
    text = "${weightFormat.format(someValue)} кг",
    style = ...,
    color = ...
)
```

AFTER:
```kotlin
AnimatedCounter(
    targetValue = someValue,
    formatter = { "${weightFormat.format(it)} кг" },
    style = ...,
    color = ...
)
```

### Screens and what to animate:

**InventoryScreen.kt** (`ui/screens/inventory/InventoryScreen.kt`):
- `InventoryItemCard` composable:
  - Weight display: `"${weightFormat.format(item.weightKg)} кг"` → AnimatedCounter
  - Purchase price: `"₴${priceFormat.format(it)}"` → AnimatedCounter
  - Sale price: `"₴${priceFormat.format(it)}"` → AnimatedCounter
  - Projected profit: `"₴${currencyFormat.format(it)}"` → AnimatedCounter
- `InventorySummaryPanel` composable:
  - Total weight: `"${weightFormat.format(summary.totalWeight)} кг"` → AnimatedCounter
  - Total invested: `"₴${currencyFormat.format(summary.totalInvested)}"` → AnimatedCounter  
  - Total expected profit: `"₴${currencyFormat.format(summary.totalExpectedProfit)}"` → AnimatedCounter

**ReportsScreen.kt** (`ui/screens/reports/ReportsScreen.kt`):
- Per-product report rows: weight, amount, count
- Summary totals at bottom
- SummaryPanel composable amounts

**PurchaseScreen.kt** (`ui/screens/purchase/PurchaseScreen.kt`):
- `ProductTotalItem` composable: weight total, amount total per product today

**SaleScreen.kt** (`ui/screens/sale/SaleScreen.kt`):
- Batch weight/amount displays

**HistoryScreen.kt** (`ui/screens/history/HistoryScreen.kt`):
- Batch card: total weight, total amount, per-transaction weights/amounts
- These change less often via sync, but corrections/voids do update them

**CashScreen.kt** (`ui/screens/cash/CashScreen.kt`):
- BalanceCard: already done (refactor to use AnimatedCounter in Step 2)
- ExpandableDayCard: daily totals, per-operation amounts

### DO NOT animate:
- Static labels, titles, headers
- Date/time displays
- Product names
- Formatted text that isn't a number (notes, categories)
- Numbers in dialogs (TransactionDialogs.kt) — too transient
- Numbers in entry screens while user is typing (PurchaseEntryScreen, SaleEntryScreen)
- Numbers in transfer position items during active editing (TransferScreen position entry)

### How to identify targets:
Search for these patterns in Text() calls:
- `currencyFormat.format(`
- `weightFormat.format(`
- `priceFormat.format(`
- `decimalFormat.format(`
- `.setScale(` followed by `₴` or `кг`

Only convert if the value comes from a state/model object (item.weightKg, summary.totalWeight, etc.), NOT from user input fields.

## Rules

1. `AnimatedCounter` takes `BigDecimal` — if the source value is already BigDecimal, pass directly. If it's `Double` or `Float`, wrap with `BigDecimal(value)`
2. The `formatter` lambda receives the animated intermediate BigDecimal — apply the same formatting the original Text used
3. Keep all existing modifiers, styles, colors, fontWeights — only change Text→AnimatedCounter
4. Don't break existing layout — AnimatedCounter renders a Text internally, same sizing
5. Don't touch `AnimatedValueText` or `AnimatedListItem` — those serve different purposes
6. Import `com.zagot.zagotplus.ui.components.AnimatedCounter` in each file that uses it
7. If a value can be null (e.g., `item.avgPurchasePrice?.let { ... } ?: "-"`), keep the null check. Only wrap the non-null branch in AnimatedCounter, keep the fallback as plain Text
8. For values with prefixes/suffixes baked into the formatter (like "₴" or "кг"), include them in the formatter lambda, not separately

## Verification

After changes:
- App compiles without errors
- Cash balance still animates smoothly on tab switch
- Inventory weights animate when another device changes a purchase
- Numbers don't "jump" or flash — they roll smoothly
- Null/missing values still show "-" or fallback text without animation
- No animation on entry screens while user is actively typing
