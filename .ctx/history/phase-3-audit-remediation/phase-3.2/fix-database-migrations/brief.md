# Session Brief: fix-database-migrations

Type: bugfix
Phase: phase-3.2
Complexity: medium
Created: 2026-01-11

## Objective

Fix: Remove destructive migration fallback and implement proper Room migration strategy.

## Bug Description

**Observed behavior**: DatabaseModule uses `fallbackToDestructiveMigration()` which will DELETE ALL DATA if schema version changes.
**Expected behavior**: Schema changes should migrate data safely, never lose user transactions.
**Reproduction steps**:
1. User accumulates transaction data
2. App update changes database schema (version 1 → 2)
3. On app launch, Room destroys and recreates database
4. All transaction data is permanently lost

## Impact

- Severity: critical (data loss risk)
- Affected users/features: All users with synced/unsynced transactions

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `android/app/src/main/kotlin/com/zagot/zagotplus/data/local/DatabaseModule.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/data/local/ZagotDatabase.kt`

## Investigation Notes

Room database is currently at version 1. The TODO comment acknowledges the risk. Since we're still pre-production, we can:
1. Keep version at 1 (no migration needed yet)
2. Remove destructive fallback now
3. Prepare migration infrastructure for future changes
4. Add empty migration callback to prevent crashes if version unchanged

## TDD

Mode: encouraged (infrastructure change, manual verification)

## Success Criteria

- [ ] `fallbackToDestructiveMigration()` removed
- [ ] App builds successfully
- [ ] Database opens without crashes (existing data preserved)
- [ ] Migration callback in place for future schema changes

## Constraints

- Keep database version at 1 (no actual migration needed)
- No changes to entity definitions
- Must not lose data if user already has database

## Dependencies

- Requires: none
- Blocks: none
