# Session Brief: screen-inventory

Type: feature
Phase: phase-2
Complexity: medium
Created: 2026-01-11

## Objective

Build the inventory screen showing current stock levels per location, computed from transactions.

## Background

Inventory is a read-only view that computes current stock by summing all transactions. Users need to see what's available at each location (kiosk vs mobile). Negative inventory should be highlighted as a warning.

## Requirements

- [ ] Location selector/tabs (switch between kiosk and mobile)
- [ ] List of products with current quantities
- [ ] Show 0 kg for products with no inventory
- [ ] Highlight negative inventory in red (warning)
- [ ] Pull-to-refresh to reload data
- [ ] Sync status indicator
- [ ] Last sync timestamp display

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/model/InventoryItem.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/repository/TransactionRepository.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/repository/LocationRepository.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/sync/SyncStatusRepository.kt`

## Implementation Notes

- Use `@HiltViewModel` for ViewModel
- Collect locations from `LocationRepository.getAll()`
- Collect inventory from `TransactionRepository.getInventory(locationId)`
- Inventory is Flow-based, auto-updates when transactions change
- Use Material 3 `TabRow` for location switching
- Use `LazyColumn` for product list
- Use `SwipeRefresh` for pull-to-refresh (triggers manual sync)

### State Management

```kotlin
data class InventoryUiState(
    val locations: List<Location> = emptyList(),
    val selectedLocation: Location? = null,
    val inventory: List<InventoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastSyncTime: Instant? = null
)
```

### UI Layout

```
┌────────────────────────────┐
│ Залишки              [🔄]  │ ← sync icon
├────────────────────────────┤
│ [ Кіоск №1 ] [ Телефон ]   │ ← tabs
├────────────────────────────┤
│ Горіх білий      45.20 кг  │
│ Горіх волоський  22.50 кг  │
│ Насіння біле     12.00 кг  │
│ Насіння сіре      0.00 кг  │ ← gray for zero
├────────────────────────────┤
│ Останнє оновлення: 14:35   │
└────────────────────────────┘

Negative inventory:
│ Горіх білий     -5.20 кг  │ ← red text, warning icon
```

## TDD

Mode: optional

Read-only display; manual testing sufficient.

## Success Criteria

- [ ] Can switch between locations
- [ ] Inventory list shows all products
- [ ] Quantities update reactively after transactions
- [ ] Negative inventory highlighted in red
- [ ] Pull-to-refresh triggers sync
- [ ] Last sync time displayed
- [ ] Build succeeds

## Out of Scope

- Inventory value (price × quantity)
- Export functionality
- Historical inventory snapshots
- Transfer initiation (separate screen)

## Dependencies

- Requires: navigation
- Blocks: none

## Historical Notes

- Pattern: Flow-based DAO queries enable reactive UI (from Phase 1 lessons)
- Pattern: Inventory computed on-the-fly (from repository session)
- Decision: Negative inventory allowed (Phase 0)
