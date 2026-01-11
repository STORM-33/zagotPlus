# Session Brief: navigation

Type: feature
Phase: phase-2
Complexity: medium
Created: 2026-01-11

## Objective

Create the navigation structure with NavHost, bottom bar, and screen scaffolds for the main app screens.

## Background

The app needs a navigation framework before any screens can be built. This session creates the foundational structure that all other Phase 2 sessions depend on.

## Requirements

- [ ] Define sealed class for navigation routes (Destinations)
- [ ] Create NavHost with Compose Navigation
- [ ] Implement bottom navigation bar with 4 items (Purchase, Sale, Inventory, History)
- [ ] Create basic scaffolds for each screen (placeholder content)
- [ ] Handle navigation state (selected item, back stack)
- [ ] Add TopAppBar with title and sync status indicator

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `android/app/src/main/kotlin/com/zagot/zagotplus/MainActivity.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/ui/theme/Theme.kt`
- `android/app/build.gradle.kts` (for dependencies)

## Implementation Notes

- Use `androidx.navigation:navigation-compose` (already in dependencies)
- Bottom bar items: Закупівля (Purchase), Продаж (Sale), Залишки (Inventory), Історія (History)
- Use Material 3 `NavigationBar` and `NavigationBarItem`
- Scaffold with `TopAppBar` showing current screen title
- Sync status icon in TopAppBar (uses SyncStatusRepository)
- Keep screens as simple placeholders for now

### Suggested Structure

```
ui/
├── navigation/
│   ├── Destinations.kt      # Sealed class/object for routes
│   └── NavGraph.kt          # NavHost setup
├── screens/
│   ├── purchase/
│   │   └── PurchaseScreen.kt  # Placeholder
│   ├── sale/
│   │   └── SaleScreen.kt      # Placeholder
│   ├── inventory/
│   │   └── InventoryScreen.kt # Placeholder
│   └── history/
│       └── HistoryScreen.kt   # Placeholder
└── components/
    └── SyncStatusIcon.kt      # Sync indicator component
```

## TDD

Mode: optional

Navigation is primarily UI; manual testing is sufficient.

## Success Criteria

- [ ] App launches with bottom navigation bar
- [ ] Tapping bottom items switches screens
- [ ] TopAppBar shows screen title
- [ ] Sync status indicator visible in TopAppBar
- [ ] Back navigation works correctly
- [ ] Build succeeds with `./gradlew assembleDebug`

## Out of Scope

- Actual screen content (other sessions handle this)
- PIN authentication (auth-pin session)
- Settings screen (Phase 3)
- Deep linking

## Dependencies

- Requires: none (first session in phase)
- Blocks: auth-pin, screen-purchase, screen-sale, screen-inventory
