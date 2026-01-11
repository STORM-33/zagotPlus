# Plan: Phase 2 - Core UI

Created: 2026-01-11
Status: active

## Overview

Build the core user interface: navigation structure, PIN authentication, and the three main workflow screens (purchase, sale, inventory).

## Progress

- Total sessions: 5
- Completed: 3
- Blocked: 0
- Remaining: 2

## Historical Context

**Relevant decisions from Phase 0-1:**
- Hilt DI: `@HiltAndroidApp` on App, `@AndroidEntryPoint` on Activity
- Compose BOM 2024.02.02 for consistent UI dependencies
- Repository layer provides domain models (Location, Product, Transaction, InventoryItem)
- Flow-based DAO queries enable reactive UI with `collectAsState()`
- Inventory is computed on-the-fly (sum of transactions)

**Patterns to reuse:**
- Material 3 theming (Theme.kt, Type.kt already exist)
- Hilt ViewModel injection with `@HiltViewModel`
- StateFlow for reactive sync status (SyncStatusRepository)

**Lessons to apply:**
- Use extension functions for clean model mapping
- Flow-based queries for reactive lists

## Phases

### Phase 2: Core UI
Status: in_progress
Implements navigation, PIN auth, and the three core screens: purchase (buy from population), sale (sell wholesale), and inventory (stock overview).

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 1 | navigation | medium | completed | none |
| 2 | auth-pin | low | completed | navigation |
| 3 | screen-purchase | high | pending | navigation |
| 4 | screen-sale | high | completed | navigation |
| 5 | screen-inventory | medium | pending | navigation |

## Dependencies Graph

```
navigation -> auth-pin
navigation -> screen-purchase
navigation -> screen-sale
navigation -> screen-inventory
```

Execution order: navigation → (auth-pin || screen-purchase || screen-sale || screen-inventory)

Note: Sessions 2-5 can run in parallel after navigation is complete.

## Open Questions

- PIN storage: SharedPreferences (simple) vs DataStore (modern)? → Default to SharedPreferences for consistency with SyncPreferences
- Bottom bar items: Which 3-4 screens go in bottom nav?

## Notes

- Navigation session creates the NavHost and bottom bar scaffold
- auth-pin is lowest complexity (simple PIN entry, no biometrics)
- screen-purchase and screen-sale are high complexity due to transaction creation logic
- screen-inventory is simpler (read-only, uses existing repository Flow)
- UI text in Ukrainian, code/comments in English
- Mock weight input for now (hardware integration is Phase 4)
