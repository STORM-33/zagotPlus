# Plan: Phase 4 - Supporting UI

Created: 2026-01-11
Status: active

## Overview
Complete the remaining UI screens: enhance History with filters/search, add Product management, Reports, and Settings screens.

## Progress
- Total sessions: 4
- Completed: 0
- Blocked: 0
- Remaining: 4

## Phases

### Phase 4: Supporting UI
Status: in_progress
Completes the UI layer with supporting screens for transaction history browsing, product management, daily reporting, and app configuration.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 1 | screen-history | medium | pending | none |
| 2 | screen-products | medium | pending | none |
| 3 | screen-reports | medium | pending | none |
| 4 | screen-settings | low | pending | none |

## Dependencies Graph

```
(all sessions independent - can be done in any order)
```

## Historical Context

**Similar past work:**
- Phase 2 Core UI (2026-01-11): screen-purchase, screen-sale, screen-inventory
  - Pattern: ViewModel + UiState + Compose screen
  - Outcome: Success, established UI patterns

**Relevant decisions:**
- collectAsStateWithLifecycle for state collection
- BigDecimal for calculations, String for DTOs
- Refresh button pattern (not pull-to-refresh)
- Ukrainian UI text, English code/comments

**Lessons to apply:**
- lifecycle-runtime-compose needed for collectAsStateWithLifecycle
- material-icons-extended increases APK size (already included)

## Open Questions
- none

## Notes
- History screen already exists with pagination - enhance with filters/search
- ProductRepository is read-only - need to add CRUD operations
- Reports will compute daily summaries from transactions
- Settings will show sync status and device configuration
