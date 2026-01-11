# Session: product-images

Type: feature
Complexity: medium
Status: pending

## Objective
Add image support to products - storage in database, display in UI, and editing capability.

## Context
Products need images for the visual grid selection in the new purchase flow. Images stored as URI strings (local file path or content URI).

## Requirements

### Database Changes
Supabase migration:
```sql
ALTER TABLE products ADD COLUMN image_uri TEXT;
```

Room migration:
```sql
ALTER TABLE products ADD COLUMN image_uri TEXT;
```

### Entity/Model Updates
1. Add `imageUri: String?` to `ProductEntity`
2. Add `imageUri: String?` to `Product` domain model
3. Update DTOs if needed

### UI Changes
1. Add Coil dependency for image loading
2. Update `ProductsScreen` to show product images
3. Add image picker to `AddEditProductDialog`
4. Show placeholder when no image

## Success Criteria
- [ ] Products can have images (nullable)
- [ ] ProductsScreen shows images
- [ ] Can pick image from gallery when editing
- [ ] Placeholder shown for products without images
- [ ] Build passes
- [ ] Existing products still work (nullable column)

## Files to Create/Modify
- `supabase/migrations/20260111000002_product_images.sql` (create)
- `android/app/build.gradle.kts` (modify - add Coil)
- `data/local/entity/ProductEntity.kt` (modify)
- `domain/model/Product.kt` (modify)
- `data/remote/dto/ProductDto.kt` (modify)
- `data/local/ZagotDatabase.kt` (modify - migration)
- `ui/screens/products/ProductsScreen.kt` (modify)
- `ui/screens/products/ProductsViewModel.kt` (modify if needed)

## Notes
- Store content:// URI from picker directly
- Consider file copying to app-private storage for persistence
- Coil handles async loading and caching
