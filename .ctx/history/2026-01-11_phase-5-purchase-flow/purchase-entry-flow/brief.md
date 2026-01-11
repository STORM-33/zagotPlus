# Session: purchase-entry-flow

Type: feature
Complexity: high
Status: pending
Depends on: db-batches, product-images

## Objective
Implement the multi-step purchase entry flow: product grid → weight/price input → position list → notes.

## Context
This is the core of the new purchase experience. User selects products from visual grid, enters weight/price for each, builds up a list of positions, adds notes, then finalizes.

## Requirements

### Screen States
1. **Product Selection** - Grid of products with images
2. **Weight Entry** - Large weight display + price input + "Додати позицію"
3. **Positions List** - Added items + notes + action buttons

### Product Grid
- `LazyVerticalGrid` with `GridCells.Adaptive(minSize = 120.dp)`
- Product tile: image (or placeholder) + name
- Tap to select → transition to weight entry

### Weight Entry
- Large weight display (placeholder: "--" or manual input)
- Manual weight input field (fallback)
- Price per kg (editable, default from product)
- Calculated total
- "Додати позицію" button

### Positions List
- Shows all added positions
- Each: product name, weight, price, total
- Can remove position (swipe or X button)
- Notes text field at bottom
- "Розрахувати" button (green/primary)
- "Скасувати" button (red/secondary)

### State Management
```kotlin
data class PurchaseEntryUiState(
    val products: List<Product>,
    val selectedProduct: Product?,
    val currentWeight: String,
    val currentPrice: String,
    val positions: List<PurchasePosition>,
    val notes: String,
    val screenState: ScreenState, // GRID, WEIGHT_ENTRY, POSITIONS
    val scaleWeight: BigDecimal?, // null = not connected
)

data class PurchasePosition(
    val product: Product,
    val weightKg: BigDecimal,
    val pricePerKg: BigDecimal,
    val totalAmount: BigDecimal
)
```

## Success Criteria
- [ ] Product grid displays with images
- [ ] Grid adapts to screen width (phone vs tablet)
- [ ] Can select product → enter weight/price
- [ ] Can add multiple positions
- [ ] Can remove positions
- [ ] Can enter notes
- [ ] "Розрахувати" triggers summary (next session)
- [ ] "Скасувати" returns to main screen
- [ ] Build passes

## Files to Create/Modify
- `ui/screens/purchase/PurchaseEntryScreen.kt` (create)
- `ui/screens/purchase/PurchaseEntryViewModel.kt` (create)
- `ui/screens/purchase/components/ProductGrid.kt` (create)
- `ui/screens/purchase/components/WeightEntry.kt` (create)
- `ui/screens/purchase/components/PositionsList.kt` (create)
- `ui/navigation/Destinations.kt` (modify - add route)
- `ui/navigation/NavGraph.kt` (modify - add screen)

## Notes
- This is the most complex session - may need to split if too large
- Weight entry should be dismissable (back to grid)
- Consider keyboard handling for weight/price inputs
- GridCells.Adaptive handles phone/tablet automatically
