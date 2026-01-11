# Session Brief: screen-products

Type: feature
Phase: phase-4-supporting-ui
Complexity: medium
Created: 2026-01-11

## Objective

Create a Product management screen with CRUD operations.

## Background

Products are currently read-only (seeded in Supabase). Business needs ability to add new products, edit prices, and deactivate products from within the app.

## Requirements

- [ ] List all products with name, default prices, active status
- [ ] Add new product (name, buy price, sell price)
- [ ] Edit existing product (name, prices)
- [ ] Toggle product active/inactive
- [ ] Validation: name required, prices must be positive
- [ ] Changes save to Room and sync via existing infrastructure
- [ ] Navigation: accessible from Settings or dedicated nav item

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/repository/ProductRepository.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/data/repository/ProductRepositoryImpl.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/data/local/dao/ProductDao.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/model/Product.kt`

## Implementation Notes

1. Add CRUD methods to ProductRepository interface
2. Implement in ProductRepositoryImpl with Room operations
3. Create ProductsScreen with LazyColumn list
4. Create AddEditProductDialog or bottom sheet
5. Use Switch for active/inactive toggle
6. Add to navigation (secondary screen, not bottom nav)
7. Consider: Products need sync similar to transactions? Check if ProductEntity has syncedAt field

## TDD

Mode: encouraged

### Test Plan
- [ ] Test: Create product saves to repository
- [ ] Test: Edit product updates correctly
- [ ] Test: Toggle active changes status
- [ ] Test: Validation rejects empty name
- [ ] Test: Validation rejects negative prices

### Test Command
```
./gradlew :app:testDebugUnitTest --tests "*.ProductsViewModelTest"
```

## Success Criteria

- [ ] Products list displays all products
- [ ] Can add new product with validation
- [ ] Can edit existing product
- [ ] Can toggle product active status
- [ ] Inactive products shown differently (greyed, at bottom)
- [ ] Changes persist to Room database
- [ ] No regressions in product selection (purchase/sale screens)

## Out of Scope

- Delete product (use deactivate instead - referential integrity)
- Product categories
- Product images
- Barcode scanning

## Dependencies

- Requires: none
- Blocks: none
