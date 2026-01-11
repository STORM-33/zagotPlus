# Plan: Phase 5 - Purchase Flow Redesign

Created: 2026-01-11
Status: active

## Overview
Redesign purchase flow: main screen with today's batches history + weight placeholder + "new client" button → product grid → multi-position entry → summary → DB save.

## Progress
- Total sessions: 5
- Completed: 2
- Blocked: 0
- Remaining: 3

## Phases

### Phase 5: Purchase Flow Redesign
Status: pending
Complete overhaul of purchase screen to support batch transactions (multiple positions per client), product images, adaptive grid layout, and a proper flow from client start to summary.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 1 | db-batches | medium | completed | none |
| 2 | product-images | medium | completed | none |
| 3 | purchase-main-screen | medium | pending | db-batches |
| 4 | purchase-entry-flow | high | pending | db-batches, product-images |
| 5 | purchase-summary | medium | pending | purchase-entry-flow |

## Dependencies Graph

```
db-batches ─────────────────┬──→ purchase-main-screen
                            │
product-images ─────────────┼──→ purchase-entry-flow
                            │
db-batches ─────────────────┘
                            
purchase-entry-flow ────────────→ purchase-summary
```

## Session Details

### 1. db-batches (medium)
Add `purchase_batches` table to group transactions by client session.
- New table: `purchase_batches` (id, notes, total_weight, total_amount, item_count, created_at, synced_at)
- Add `batch_id` FK to transactions table
- Room entity + DAO
- Domain model
- Repository methods
- Migration (Room + Supabase SQL)

### 2. product-images (medium)
Add image support to products.
- Add `image_uri` column to products table
- Update ProductEntity, Product domain model
- Update ProductsScreen to show/edit images
- Image picker (gallery) in edit dialog
- Room + Supabase migration

### 3. purchase-main-screen (medium)
Redesign main purchase screen.
- Today's batches list (LazyColumn with gradual load)
- Each item shows: time, total weight, total sum, # positions
- Weight placeholder display (for future scales)
- "Новий клієнт" button
- Navigation to entry flow

### 4. purchase-entry-flow (high)
Multi-step purchase entry.
- Product selection grid (adaptive columns based on screen width)
- Product tiles with images
- Weight input (placeholder + manual fallback)
- Price per kg input (editable, default from product)
- "Додати позицію" button
- List of added positions
- Notes field (applies to batch)
- "Розрахувати" and "Скасувати" buttons

### 5. purchase-summary (medium)
Summary display and save.
- Full-screen summary overlay
- List of items with weights/prices
- Totals (weight, sum)
- Notes display
- Tap anywhere to dismiss
- Save batch + transactions to DB
- Reset to main screen
- TODO: receipt printing (Phase 6)

## Historical Context

**Similar past work:**
- Phase 2 screen-purchase: Basic purchase entry
  - Pattern: ViewModel + UiState + form inputs
  - Will be replaced, but patterns reused

**Relevant decisions:**
- BigDecimal for calculations
- collectAsStateWithLifecycle for state
- Ukrainian UI text

**Lessons to apply:**
- Adaptive grid: use `LazyVerticalGrid` with `GridCells.Adaptive`
- Image loading: consider Coil library for async image loading

## Open Questions
- none (all clarified with user)

## Notes
- Existing PurchaseScreen.kt will be completely replaced
- Need Coil dependency for image loading
- Weight placeholder will show "--" until scales integration (Phase 6)
- Receipt printing deferred to Phase 6 (hardware integration)
