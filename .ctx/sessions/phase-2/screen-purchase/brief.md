# Session Brief: screen-purchase

Type: feature
Phase: phase-2
Complexity: high
Created: 2026-01-11

## Objective

Build the purchase screen for buying goods from population with product selection, weight input (mock), price calculation, and transaction creation.

## Background

This is the primary workflow screen for the kiosk. Operator selects product, enters/reads weight, confirms price, and creates a purchase transaction. For now, weight is entered manually (scales integration is Phase 4).

## Requirements

- [ ] Product selector (dropdown or list)
- [ ] Weight input field (numeric keyboard, kg)
- [ ] Price per kg display (from product default, editable)
- [ ] Total calculation (weight × price)
- [ ] Current location display
- [ ] Notes field (optional)
- [ ] Confirm button creates transaction
- [ ] Success feedback (snackbar or dialog)
- [ ] Clear form after successful transaction

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/model/Product.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/model/Transaction.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/repository/ProductRepository.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/repository/TransactionRepository.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/repository/LocationRepository.kt`

## Implementation Notes

- Use `@HiltViewModel` for ViewModel
- Collect products from `ProductRepository.getActiveProducts()`
- Get current location from `LocationRepository` (for now, use first kiosk)
- Create transaction via `TransactionRepository.createPurchase()`
- Transaction type: `purchase` (adds to inventory)
- Weight stored as positive value in kg (BigDecimal)
- Price per kg and total in UAH (BigDecimal)

### State Management

```kotlin
data class PurchaseUiState(
    val products: List<Product> = emptyList(),
    val selectedProduct: Product? = null,
    val weight: String = "",            // User input
    val pricePerKg: String = "",        // Editable, defaults to product.defaultBuyPrice
    val notes: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val showSuccess: Boolean = false
)
```

### UI Layout

```
┌────────────────────────────┐
│ Закупівля                  │ ← TopAppBar (from scaffold)
├────────────────────────────┤
│ Локація: Кіоск №1          │
├────────────────────────────┤
│ Товар: [▼ Горіх білий    ] │
├────────────────────────────┤
│ Вага: [ 24.5 ] кг          │
├────────────────────────────┤
│ Ціна: [ 45.00 ] грн/кг     │
├────────────────────────────┤
│ Сума: 1102.50 грн          │
├────────────────────────────┤
│ Примітки: [              ] │
├────────────────────────────┤
│    [ ЗБЕРЕГТИ ]            │
└────────────────────────────┘
```

## TDD

Mode: encouraged

### Test Plan
- [ ] Test: total calculated correctly (weight × price)
- [ ] Test: transaction created with correct type (purchase)
- [ ] Test: form validation (weight > 0, product selected)
- [ ] Test: price defaults to product.defaultBuyPrice

### Test Command
```
./gradlew testDebugUnitTest
```

## Success Criteria

- [ ] Can select product from list
- [ ] Can enter weight manually
- [ ] Price defaults to product buy price
- [ ] Total calculates correctly
- [ ] Transaction saved to Room database
- [ ] Success feedback shown
- [ ] Form clears after success
- [ ] Build succeeds

## Out of Scope

- Scales integration (Phase 4)
- Receipt printing (Phase 4)
- Multiple items per receipt (future enhancement)
- Seller information capture (not required per spec)

## Dependencies

- Requires: navigation
- Blocks: none

## Historical Notes

- Pattern: Use StateFlow in ViewModel (from sync-worker session)
- Pattern: Domain models separate from entities (from repository session)
