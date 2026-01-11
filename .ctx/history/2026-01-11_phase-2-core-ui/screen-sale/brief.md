# Session Brief: screen-sale

Type: feature
Phase: phase-2
Complexity: high
Created: 2026-01-11

## Objective

Build the sale screen for selling goods wholesale to buyers with product selection, weight input, price calculation, and transaction creation.

## Background

Sales are wholesale transactions where goods leave inventory. Similar to purchase screen but with different business logic: uses sell price, checks available inventory, transaction type is `sale` (subtracts from inventory).

## Requirements

- [ ] Product selector with current inventory shown
- [ ] Weight input field (numeric keyboard, kg)
- [ ] Warning if selling more than available inventory
- [ ] Price per kg display (from product default sell price, editable)
- [ ] Total calculation (weight × price)
- [ ] Current location display
- [ ] Buyer notes field (optional)
- [ ] Confirm button creates transaction
- [ ] Success feedback
- [ ] Clear form after successful transaction

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/model/InventoryItem.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/repository/TransactionRepository.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/ui/screens/purchase/PurchaseScreen.kt` (pattern)

## Implementation Notes

- Use `@HiltViewModel` for ViewModel
- Collect inventory from `TransactionRepository.getInventory(locationId)`
- Show inventory per product in selector
- Create transaction via `TransactionRepository.createSale()`
- Transaction type: `sale` (stored as negative weight internally)
- Warn but allow negative inventory (business decision, not technical block)

### State Management

```kotlin
data class SaleUiState(
    val inventory: List<InventoryItem> = emptyList(),
    val selectedProduct: Product? = null,
    val availableWeight: BigDecimal = BigDecimal.ZERO,
    val weight: String = "",
    val pricePerKg: String = "",        // Defaults to product.defaultSellPrice
    val notes: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val showSuccess: Boolean = false,
    val showInventoryWarning: Boolean = false  // Selling more than available
)
```

### UI Layout

```
┌────────────────────────────┐
│ Продаж                     │
├────────────────────────────┤
│ Локація: Кіоск №1          │
├────────────────────────────┤
│ Товар: [▼ Горіх білий    ] │
│        В наявності: 45.2 кг│
├────────────────────────────┤
│ Вага: [ 30.0 ] кг          │
├────────────────────────────┤
│ ⚠️ Перевищує залишок!      │ ← shown if weight > available
├────────────────────────────┤
│ Ціна: [ 55.00 ] грн/кг     │
├────────────────────────────┤
│ Сума: 1650.00 грн          │
├────────────────────────────┤
│ Покупець: [              ] │
├────────────────────────────┤
│    [ ПРОДАТИ ]             │
└────────────────────────────┘
```

## TDD

Mode: encouraged

### Test Plan
- [ ] Test: total calculated correctly (weight × price)
- [ ] Test: transaction created with type=sale
- [ ] Test: warning shown when weight > inventory
- [ ] Test: sale allowed even with inventory warning
- [ ] Test: price defaults to product.defaultSellPrice

### Test Command
```
./gradlew testDebugUnitTest
```

## Success Criteria

- [ ] Can select product from list with inventory shown
- [ ] Can enter weight manually
- [ ] Warning appears if selling more than available
- [ ] Can still proceed despite warning
- [ ] Price defaults to product sell price
- [ ] Total calculates correctly
- [ ] Transaction saved to Room database
- [ ] Success feedback shown
- [ ] Form clears after success
- [ ] Build succeeds

## Out of Scope

- Invoice generation (future)
- Buyer database/selection
- Payment tracking (cash only per spec)
- Batch sales (multiple products)

## Dependencies

- Requires: navigation
- Blocks: none

## Historical Notes

- Decision: Negative inventory allowed (from Phase 0 decisions)
- Pattern: Sales stored as negative weight (from repository session)
