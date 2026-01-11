# Session: test-inventory-computation

Phase: 3.2 - Data Integrity
Complexity: medium
TDD Mode: encouraged

## Goal

Add unit tests for the `computeInventory` function in `TransactionRepositoryImpl` to ensure correct inventory aggregation across transaction types.

## Context

The `computeInventory` function (lines 147-160 of TransactionRepositoryImpl.kt):
- Groups transactions by locationId + productId
- Sums weightKg (purchases positive, sales/transfers-out negative)
- Returns list of InventoryItem

Currently untested - critical business logic.

## Success Criteria

- [ ] Tests cover empty transactions case
- [ ] Tests cover single purchase (positive weight)
- [ ] Tests cover purchase + sale (net calculation)
- [ ] Tests cover multiple products at same location
- [ ] Tests cover same product at multiple locations
- [ ] Tests cover transfer pairs (out negative, in positive)
- [ ] Tests verify null locationId/productId filtered out
- [ ] All tests pass

## Files to Create

- `app/src/test/kotlin/com/zagot/zagotplus/data/repository/TransactionRepositoryImplTest.kt`

## Notes

- computeInventory is private - test via getInventory() or extract to testable helper
- Use mock TransactionDao returning canned data
- No Supabase dependency - pure unit test
