# Session: fix-decimal-precision

## Type
bugfix

## Complexity
medium

## Objective
Fix BigDecimal→Double conversion that loses precision in DTOs and UI formatting.

## Problem
1. ProductDto uses Double for defaultBuyPrice/defaultSellPrice
2. TransactionDto uses Double for weightKg/pricePerKg/totalAmount
3. UI screens call .toDouble() on BigDecimal for DecimalFormat

This causes precision loss when syncing with Supabase and displaying amounts.

## Solution
1. Change DTOs to use String for decimal values (JSON-safe, preserves precision)
2. Update DTO conversion functions to use String↔BigDecimal
3. Replace .toDouble() in UI with BigDecimal-aware formatting

## Files to Change
- data/remote/dto/ProductDto.kt
- data/remote/dto/TransactionDto.kt
- ui/screens/purchase/PurchaseScreen.kt
- ui/screens/sale/SaleScreen.kt

## Success Criteria
- [ ] DTOs use String for all decimal fields
- [ ] No .toDouble() calls for financial data
- [ ] Build passes
- [ ] Existing tests pass

## TDD Mode
encouraged
