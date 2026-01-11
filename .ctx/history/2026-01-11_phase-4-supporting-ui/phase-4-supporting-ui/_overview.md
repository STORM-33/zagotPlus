# Phase 4: Supporting UI

## Purpose
Complete the UI layer with supporting screens for power users and operational needs.

## Sessions

| # | Session | Complexity | Description |
|---|---------|------------|-------------|
| 1 | screen-history | medium | Add filters (type, date, location) and search to existing history screen |
| 2 | screen-products | medium | Product CRUD screen with activation toggle |
| 3 | screen-reports | medium | Daily summary view with totals and basic export |
| 4 | screen-settings | low | Sync status display, device info, location selection |

## Key Patterns
- ViewModel + StateFlow + Compose (established in Phase 2)
- collectAsStateWithLifecycle for lifecycle-aware collection
- Refresh button in TopAppBar
- Ukrainian UI text

## Success Criteria
- All 4 screens functional and accessible
- Filters/search working on History
- Product CRUD saves to Room (syncs via existing infrastructure)
- Reports show accurate daily summaries
- Settings displays sync status and device configuration
