# Phase 2: Core UI

## Purpose

Build the foundational user interface for Zagot+: navigation structure, simple PIN authentication, and the three core workflow screens (purchase, sale, inventory).

## Sessions

| # | Session | Complexity | Description |
|---|---------|------------|-------------|
| 1 | navigation | medium | Nav graph, bottom bar, screen scaffolds |
| 2 | auth-pin | low | PIN entry, simple session management |
| 3 | screen-purchase | high | Buy flow with mock weight input |
| 4 | screen-sale | high | Sell flow with manual weight entry |
| 5 | screen-inventory | medium | Stock view computed from transactions |

## Dependencies

- Requires: Phase 1 complete (data layer ready)
- Enables: Phase 3 (supporting UI screens)

## Key Decisions

- Bottom navigation for main screens (Purchase, Sale, Inventory, History)
- PIN stored in SharedPreferences (consistent with existing sync prefs)
- Mock weight input (real scales in Phase 4)
- Material 3 design system
- UI text in Ukrainian

## Entry Points After Completion

- `ui/navigation/NavGraph.kt` - Navigation host
- `ui/screens/*Screen.kt` - Main screens
- `ui/components/*.kt` - Reusable components

## Success Criteria

- [ ] App has working bottom navigation
- [ ] PIN entry gates access to app
- [ ] Can create purchase transactions
- [ ] Can create sale transactions
- [ ] Can view inventory per location
- [ ] All screens follow Material 3 theming
