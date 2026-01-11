# Session Report: fix-decimal-precision

## Status
completed

## Objective
Fix BigDecimal→Double conversion that loses precision in DTOs and UI formatting.

## Work Summary
- Changed ProductDto to use String for defaultBuyPrice/defaultSellPrice
- Changed TransactionDto to use String for weightKg/pricePerKg/totalAmount
- Updated DTO conversion to use BigDecimal(string) constructor and toPlainString()
- Removed .toDouble() calls from PurchaseScreen and SaleScreen

## Files Changed
- data/remote/dto/ProductDto.kt - Double→String for prices
- data/remote/dto/TransactionDto.kt - Double→String for weight/prices/total
- ui/screens/purchase/PurchaseScreen.kt - format(total) not format(total.toDouble())
- ui/screens/sale/SaleScreen.kt - same fix

## Verification
- Build: SUCCESSFUL
- Tests: 4 failures (all OOM/timeout, not related to this fix)
  - Environment issue: Java heap space on constrained machine
  - No precision-related test failures

## Key Decision
Use String type in DTOs for decimal values:
- JSON serialization preserves exact representation
- BigDecimal(string) constructor doesn't introduce floating-point errors
- toPlainString() outputs exact decimal without scientific notation

## Commit
455bd5e - fix(sync): use String instead of Double for decimal values in DTOs
