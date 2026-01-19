# Plan: Production Readiness Audit

Created: 2026-01-19
Status: active

## Overview
Comprehensive audit of Zagot+ Android app before production deployment (2 days). Focus on identifying bugs, security issues, data integrity risks, and test coverage gaps.

## Progress
- Total sessions: 7
- Completed: 4
- Blocked: 0
- Remaining: 3

## Codebase Summary
- **Production Code:** 138 files (~27,110 LOC)
- **Test Code:** 66 files (~20,917 LOC)
- **Test Coverage:** 100% ViewModels, 100% Repositories, 100% DAOs
- **Architecture:** Clean Architecture (data/domain/ui), MVVM, Hilt DI

## Phases

### Phase 1: Critical Path Audit
Status: pending
Audit the most critical paths: data persistence, sync, and financial calculations.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 1 | audit-data-layer | high | completed | none |
| 2 | audit-sync-system | high | completed | none |

### Phase 2: Business Logic Audit
Status: pending
Audit domain models, business rules, and financial calculations.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 3 | audit-domain-and-business-logic | medium | completed | phase-1 |
| 4 | audit-ui-and-viewmodels | high | pending | phase-1 |

### Phase 3: Infrastructure Audit
Status: pending
Audit database migrations, build config, and security settings.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 5 | audit-migrations-and-schema | medium | completed | none |
| 6 | audit-build-and-dependencies | medium | pending | none |

### Phase 4: Test Coverage Verification
Status: pending
Run all tests and identify gaps in coverage.

Sessions:
| # | Session | Complexity | Status | Depends On |
|---|---------|------------|--------|------------|
| 7 | verify-test-coverage | high | pending | phase-1, phase-2 |

---

## Session Details

### 1. audit-data-layer
**Complexity:** high
**Estimated Files:** 40

Audit all data layer components:

**Room Database (8 files):**
- `ZagotDatabase.kt` - Schema version, migrations, initialization
- `Converters.kt` - Type converters (dates, enums, JSON)
- DAOs: `CashDao`, `LocationDao`, `ProductDao`, `PurchaseBatchDao`, `SaleBatchDao`, `TransactionDao`
- `TransactionQueryBuilder.kt` - Dynamic query construction

**Entities (6 files):**
- `CashEntity.kt` - Cash operations structure
- `LocationEntity.kt` - Location tracking
- `ProductEntity.kt` - Product catalog
- `PurchaseBatchEntity.kt` - Purchase batches
- `SaleBatchEntity.kt` - Sale batches
- `TransactionEntity.kt` - All transactions

**DTOs (7 files):**
- Verify Supabase DTO mappings match schema
- Check nullable vs non-null field alignment
- Verify serialization annotations

**Repositories (7 files):**
- Verify offline-first behavior
- Check error handling
- Validate data transformations

**Preferences (4 files):**
- `AuthPreferences.kt` - PIN storage, lockout
- `DevicePreferences.kt` - Device ID persistence
- `SyncPreferences.kt` - Last sync timestamps
- `ProductOrderPreferences.kt` - UI preferences

**Checklist:**
- [ ] Room schema version is correct
- [ ] All migrations are idempotent and tested
- [ ] Type converters handle edge cases (null, empty)
- [ ] DAOs handle concurrent access
- [ ] DTOs match Supabase schema exactly
- [ ] Repository error handling is comprehensive
- [ ] Preferences handle corruption gracefully

---

### 2. audit-sync-system
**Complexity:** high
**Estimated Files:** 10

Audit sync infrastructure:

**Sync Core (6 files):**
- `SyncService.kt` - Push/pull logic
- `SyncManager.kt` - WorkManager scheduling
- `SyncWorker.kt` - Background execution
- `SyncDataSource.kt` - Interface
- `SupabaseSyncDataSource.kt` - Supabase implementation
- `SyncPreferences.kt` - Timestamps tracking

**Sync State (4 files):**
- `SyncResult.kt` - Success/Partial/Failure states
- `SyncStatus.kt` - Current sync state
- `SyncStatusRepository.kt` - Status observation

**Checklist:**
- [ ] Conflict resolution is deterministic
- [ ] Partial sync failures don't corrupt data
- [ ] Network errors are handled gracefully
- [ ] Duplicate records are prevented (local_id UNIQUE)
- [ ] Sync doesn't run during critical operations
- [ ] Background sync respects battery/network
- [ ] Timestamps use consistent timezone (UTC)
- [ ] Large batch sync doesn't OOM

---

### 3. audit-domain-and-business-logic
**Complexity:** medium
**Estimated Files:** 15

Audit domain layer and business rules:

**Domain Models (8 files):**
- `Transaction.kt` - Transaction types, validation
- `PurchaseBatch.kt` - Batch calculations
- `SaleBatch.kt` - Sale aggregation
- `InventoryItem.kt` - Computed inventory
- `CashModels.kt` - Cash flow tracking
- `Location.kt` - Location model
- `Product.kt` - Product model
- `TransactionFilter.kt` - Filtering logic

**Repository Interfaces (6 files):**
- Verify interface contracts are complete
- Check method signatures match implementations

**Validation (1 file):**
- `InputValidation.kt` - User input validation

**Business Rules to Verify:**
- [ ] Inventory is computed correctly (sum of transactions)
- [ ] Negative inventory is flagged but allowed
- [ ] Transfer creates linked transactions atomically
- [ ] Purchase batches aggregate correctly
- [ ] Sale batches compute totals correctly
- [ ] Price calculations are precise (no floating point errors)
- [ ] Weight handling uses appropriate precision
- [ ] Void operations create proper reversals

---

### 4. audit-ui-and-viewmodels
**Complexity:** high
**Estimated Files:** 46

Audit UI layer:

**ViewModels (13 files):**
- `PinViewModel.kt` - Auth flow, lockout
- `CashViewModel.kt` - Cash operations
- `PurchaseViewModel.kt` - Purchase batches
- `PurchaseEntryViewModel.kt` - Entry flow, scales/printer
- `SaleViewModel.kt` - Sale batches
- `SaleEntryViewModel.kt` - Sale entry flow
- `InventoryViewModel.kt` - Inventory computation
- `HistoryViewModel.kt` - Transaction history
- `ReportsViewModel.kt` - Daily reports
- `ProductsViewModel.kt` - Product CRUD
- `TransferViewModel.kt` - Transfers
- `SettingsViewModel.kt` - App settings
- `LocationSelectionViewModel.kt` - Location picker

**Screens (13 screens):**
- Verify UI state handling
- Check loading/error states
- Validate user input sanitization

**Components (14 files):**
- `CurrencyFormat.kt` - Money formatting (Ukrainian hryvnia)
- `DateRangePicker.kt` - Date handling
- `AdaptiveLayout.kt` - Screen size adaptation
- Other UI components

**Navigation (2 files):**
- `NavGraph.kt` - Navigation routes
- `Destinations.kt` - Route definitions

**Checklist:**
- [ ] All ViewModels handle errors gracefully
- [ ] Loading states are shown during operations
- [ ] UI doesn't block on long operations
- [ ] Input validation prevents invalid data
- [ ] Currency formatting is consistent
- [ ] Date/time displays use correct locale
- [ ] Navigation handles back stack correctly
- [ ] No hardcoded strings (all in resources)

---

### 5. audit-migrations-and-schema
**Complexity:** medium
**Estimated Files:** 17 SQL + schema

Audit database schema and migrations:

**Initial Schema:**
- `20260111000000_initial_schema.sql` - Core tables

**Feature Migrations (16 files):**
- Purchase batches, product images, product sync
- Cash operations, sale batches
- Batch corrections, adjustment types
- Location renaming, transfer flags
- Voided batch filtering, RLS policies

**Checklist:**
- [ ] Migrations are in correct order
- [ ] Each migration is idempotent (IF NOT EXISTS)
- [ ] Indexes exist for common queries
- [ ] Foreign keys have proper ON DELETE behavior
- [ ] RLS policies are correct and tested
- [ ] Constraints prevent invalid data
- [ ] Views compute correctly
- [ ] Triggers don't cause infinite loops

---

### 6. audit-build-and-dependencies
**Complexity:** medium
**Estimated Files:** 3

Audit build configuration:

**Gradle Files:**
- `build.gradle.kts` - App configuration
- `libs.versions.toml` - Dependency versions

**Checklist:**
- [ ] All dependencies are up-to-date (no known vulnerabilities)
- [ ] ProGuard rules are configured (isMinifyEnabled = false noted!)
- [ ] Signing config is ready for release
- [ ] Version code/name are set
- [ ] targetSdk is current (34)
- [ ] minSdk is appropriate (26)
- [ ] Test configurations are correct
- [ ] No debug code in release build

**Security Concerns:**
- [ ] Supabase credentials not in source control
- [ ] BuildConfig doesn't leak secrets
- [ ] INTERNET permission is declared
- [ ] No unnecessary permissions

---

### 7. verify-test-coverage
**Complexity:** high
**Estimated Files:** 66 test files

Run and verify test coverage:

**Test Categories:**
- Unit Tests (52 files)
- Integration Tests (11 files)
- Instrumentation Tests (3 files)

**Actions:**
1. Run full test suite: `./gradlew test`
2. Run instrumentation tests: `./gradlew connectedAndroidTest`
3. Identify uncovered critical paths
4. Verify integration tests cover business flows

**Coverage Goals:**
- [ ] All tests pass
- [ ] Critical flows have integration tests
- [ ] Edge cases are covered (empty, null, overflow)
- [ ] Error conditions are tested
- [ ] Concurrent access is tested

**Known Gaps to Address:**
- Instrumentation test coverage: only 2/13 screens
- Hardware layer tests (mocks exist but verify coverage)

---

## Audit Output Format

For each session, produce:

1. **FINDINGS.md** - Categorized issues:
   - 🔴 CRITICAL: Must fix before release
   - 🟠 HIGH: Should fix, workaround exists
   - 🟡 MEDIUM: Fix after release
   - ⚪ LOW: Nice to have

2. **Issues by File** - Specific file:line references

3. **Recommendations** - Prioritized fix list

---

## Risk Areas (Pre-identified)

Based on codebase analysis:

1. **ProGuard disabled** (`isMinifyEnabled = false`) - APK not obfuscated
2. **Instrumentation coverage** - Only 15% of screens
3. **Hardware layer** - Mock-only implementations
4. **Release signing** - Using debug signing config

---

## Dependencies Graph
```
audit-data-layer ─┐
                  ├─→ audit-domain-and-business-logic ─┐
audit-sync-system ┘                                   │
                                                      ├─→ verify-test-coverage
audit-ui-and-viewmodels ──────────────────────────────┘

audit-migrations-and-schema (independent)
audit-build-and-dependencies (independent)
```

## Notes
- Hardware integration is Phase B (post-hardware arrival), not blocking release
- App will be used in real business environment in 2 days
- Focus on data integrity and financial calculations
- Ukrainian locale and hryvnia currency formatting critical
