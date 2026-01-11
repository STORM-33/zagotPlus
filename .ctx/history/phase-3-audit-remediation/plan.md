# Plan: Phase 3 - Audit Remediation

Created: 2026-01-11
Status: active

## Overview

Address critical issues from code audit. Priority: stability first (sync, tests, migrations) → security → features.

## Progress

- Total sessions: 7
- Completed: 7
- Blocked: 0
- Remaining: 0

## Historical Context

**From audit:**
- Sync partial failure handling creates data inconsistency
- BigDecimal→Double conversion loses precision
- PIN security lacks salt, lockout not persisted
- `.fallbackToDestructiveMigration()` will delete data
- `deviceId = null` breaks multi-device conflict resolution
- SyncService and computeInventory lack tests

**Accepted trade-offs:**
- RLS "allow all" acceptable for single-org device auth
- Repository pattern provides testability (keep it)
- Domain layer provides type safety (keep it)
- O(n) inventory acceptable until > 1000 transactions

## Phases

### Phase 3.1: Sync Reliability
Status: completed
Fix sync partial failure handling and add comprehensive tests.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 1 | fix-sync-partial-failure | high | completed | none |
| 2 | test-sync-service | high | partial | fix-sync-partial-failure |

Note: test-sync-service is partial - SyncResultTest passes, SyncServiceTest @Ignored due to Supabase mocking issues.

### Phase 3.2: Data Integrity
Status: pending
Fix migrations, device ID, and add inventory tests.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 3 | fix-database-migrations | medium | completed | none |
| 4 | implement-device-id | low | completed | none |
| 5 | test-inventory-computation | medium | completed | none |

### Phase 3.3: Security Hardening
Status: completed
Improve PIN security and fix decimal precision.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 6 | fix-pin-security | medium | completed | none |
| 7 | fix-decimal-precision | medium | completed | none |

## Dependencies Graph

```
fix-sync-partial-failure -> test-sync-service
(3, 4, 5, 6, 7 can run in parallel)
```

## Open Questions

None - priorities clarified with user.

## Notes

- Phase 3.4 (RLS, pagination, performance) deferred to post-MVP
- All changes must pass existing tests before merge
- Manual sync test required after each sync-related change
