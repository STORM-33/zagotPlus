# File Tree

Updated: 2026-01-11 (Post-Phase 5 Complete)

## Overview

Zagot+ is organized as a monorepo with Android app, Supabase backend, and Claude Auto OS workspace.
Android follows Clean Architecture with clear separation: data, domain, sync, and UI layers.
**Phase 5 complete!** 🎉 Purchase Flow Redesign (5/5 sessions).
**Next**: Phase 6 (Hardware Integration)

## Root (/)

- CLAUDE.md - Claude Auto OS configuration
- MASTER_PLAN.md - Complete project specification (20 sessions across 5 phases)
- README.md - Project overview and setup instructions
- .gitignore - Version control exclusions (Android/Kotlin patterns)

## Claude Workspace (.ctx/)

### state and planning
- state.md - Current mode, active session, stats, deferred actions
- plan.md - Active work plan with phases and sessions
- journal.md - Append-only log of all work
- scratchpad.md - Cross-session notes, questions, discoveries

### memory/ (project knowledge)
- project.md - Architecture, conventions, tech stack
- file-tree.md - This file
- modules/android.md - Android-specific context
- modules/supabase.md - Backend schema and sync context
- modules/hardware.md - Scales and printer integration notes

### modes/ (8 workflow files)
plan.md, work.md, do.md, reflect.md, validate.md, archive.md, blocked.md, status.md

### templates/ (4 session templates)
feature.md, bugfix.md, refactor.md, research.md

### sessions/phase-5-purchase-flow/ (complete - pending archive)
- db-batches/ - Batch table and FK implementation
- product-images/ - Image column and Coil integration
- purchase-main-screen/ - Redesigned batch history screen
- purchase-entry-flow/ - Multi-step purchase entry flow
- purchase-summary/ - Summary overlay with atomic save

### history/phase-0-setup/ (archived)
### history/phase-1-data-layer/ (archived)
### history/phase-2-core-ui/ (archived)
### history/phase-3-audit-remediation/ (archived)
### history/phase-4-supporting-ui/ (archived)
### history/phase-5-purchase-flow/ (pending archive)

### history/
- index.md - Decisions, patterns, lessons learned

## Android App (android/) - IMPLEMENTED

### Project Root
- build.gradle.kts - Project-level Gradle config (AGP 8.3.1)
- settings.gradle.kts - Module configuration
- gradle.properties - Build properties (JDK 21)
- gradlew, gradlew.bat - Gradle wrapper scripts
- local.properties - SDK location (not in git)

### Gradle Version Catalog
- gradle/libs.versions.toml - All dependency versions centralized

### App Module (android/app/)
- build.gradle.kts - App dependencies (Compose, Room, Hilt, Supabase)
- proguard-rules.pro - ProGuard configuration
- src/main/AndroidManifest.xml - Permissions, hardware features

### Kotlin Source (app/src/main/kotlin/com/zagot/zagotplus/)

**Application (implemented)**
- ZagotApp.kt - @HiltAndroidApp with periodic sync initialization
- MainActivity.kt - @AndroidEntryPoint single activity with Compose

**Data Layer (data/) - FULLY IMPLEMENTED**

*local/* - Room database
- converter/Converters.kt - TypeConverters (UUID, Instant, BigDecimal)
- entity/LocationEntity.kt - Location table entity
- entity/ProductEntity.kt - Product table entity with imageUri
- entity/TransactionEntity.kt - Transaction table with foreign keys, indexes, and batch_id
- entity/PurchaseBatchEntity.kt - Batch grouping for multi-position purchases
- dao/LocationDao.kt - Location CRUD with Flow queries
- dao/ProductDao.kt - Product CRUD with active filtering
- dao/TransactionDao.kt - Transaction CRUD with sync queries
- dao/PurchaseBatchDao.kt - Batch CRUD with today's batches query
- DatabaseModule.kt - Hilt module for database singleton
- ZagotDatabase.kt - Room database (version 3 with MIGRATION_1_2, MIGRATION_2_3)

*remote/* - Supabase integration
- dto/LocationDto.kt - Location DTO with entity mapping
- dto/ProductDto.kt - Product DTO with entity mapping
- dto/TransactionDto.kt - Transaction DTO with entity mapping
- SupabaseModule.kt - Hilt module for Supabase client

*repository/* - Repository implementations
- LocationRepositoryImpl.kt - Location repository with entity-domain mapping
- ProductRepositoryImpl.kt - Product repository with entity-domain mapping and imageUri
- TransactionRepositoryImpl.kt - Transaction repository with inventory computation + device ID + batch support
- PurchaseBatchRepositoryImpl.kt - Atomic batch creation with transactions
- RepositoryModule.kt - Hilt bindings for repositories

*preferences/* - SharedPreferences wrappers
- DevicePreferences.kt - Persistent UUID per device for transaction tracking

**Domain Layer (domain/) - FULLY IMPLEMENTED**

*model/*
- Location.kt - Domain model with LocationType enum (KIOSK, MOBILE)
- Product.kt - Domain model with price, active status, and imageUri
- Transaction.kt - Domain model with TransactionType enum and batchId
- InventoryItem.kt - Computed inventory (location + product + weight)
- PurchaseBatch.kt - Batch domain model (totals, item count)

*repository/* - Interfaces
- LocationRepository.kt - Location data access interface
- ProductRepository.kt - Product data access interface with image support
- TransactionRepository.kt - Transaction + inventory interface
- PurchaseBatchRepository.kt - Batch CRUD with atomic creation

**Sync Layer (sync/) - FULLY IMPLEMENTED**
- SyncWorker.kt - CoroutineWorker with @HiltWorker for background sync
- SyncManager.kt - Periodic (15min) and manual sync scheduling
- SyncService.kt - Push/pull sync logic with deduplication
- SyncResult.kt - Sealed class: Success/Partial/Failure with phase tracking
- SyncPreferences.kt - SharedPreferences for last sync timestamp
- SyncStatus.kt - Sync state model (IDLE, SYNCING, ERROR)
- SyncStatusRepository.kt - StateFlow for reactive sync status

**UI Layer (ui/) - FULLY IMPLEMENTED**

*theme/*
- Theme.kt - Material 3 theme (placeholder colors)
- Type.kt - Typography configuration

*navigation/*
- Destinations.kt - Sealed class with route definitions, bottom nav items, and secondary destinations (Products, Reports, Settings)
- NavGraph.kt - Scaffold with TopAppBar (overflow menu), NavigationBar, and NavHost

*components/*
- SyncStatusIcon.kt - Animated sync status indicator

*screens/auth/*
- AuthPreferences.kt - Salted SHA-256 PIN hash storage with persistent lockout
- PinScreen.kt - Large numeric keypad UI with PIN dots
- PinViewModel.kt - PIN set/verify/lockout logic (survives app restart)

*screens/purchase/*
- PurchaseScreen.kt - Batch history list, weight placeholder, new client navigation
- PurchaseViewModel.kt - Today's batches observation, navigation state
- PurchaseEntryScreen.kt - Multi-step entry flow (product grid → weight → positions)
- PurchaseEntryViewModel.kt - Entry flow state with position management
- PurchaseSummaryScreen.kt - Full-screen summary overlay with tap-to-confirm

*screens/sale/*
- SaleScreen.kt - Similar to purchase with inventory awareness
- SaleViewModel.kt - Inventory tracking, over-stock warning

*screens/inventory/*
- InventoryScreen.kt - Location tabs (TabRow), product list with quantities
- InventoryViewModel.kt - Location selection, sync integration

*screens/history/*
- HistoryScreen.kt - Transaction list with filters (type, date, location, product search)
- HistoryViewModel.kt - Filter state management, pagination

*screens/products/*
- ProductsScreen.kt - Product list with CRUD operations, FAB, dialogs
- ProductsViewModel.kt - Add/edit/toggle operations, validation

*screens/reports/*
- ReportsScreen.kt - Daily summaries with date picker, copy/share export
- ReportsViewModel.kt - Summary computation, product/location breakdowns

*screens/settings/*
- SettingsScreen.kt - Sync status, device ID, location selector, app info
- SettingsViewModel.kt - Sync status, pending count, location selection

### Test Source (app/src/test/kotlin/com/zagot/zagotplus/)
- ui/screens/purchase/PurchaseViewModelTest.kt - 6 unit tests (redesigned)
- ui/screens/sale/SaleViewModelTest.kt - 10 unit tests
- ui/screens/products/ProductsViewModelTest.kt - 16 unit tests
- ui/screens/auth/AuthPreferencesTest.kt - 9 unit tests (salt, lockout, persistence)
- ui/screens/auth/PinViewModelTest.kt - Unit tests (needs Robolectric)
- data/repository/TransactionRepositoryImplTest.kt - 9 unit tests (computeInventory)
- sync/SyncServiceTest.kt - @Ignored (needs interface extraction for Supabase mocking)

### Resources (app/src/main/res/)
- values/strings.xml - String resources
- values-uk/strings.xml - Ukrainian translations
- values/themes.xml - XML theme fallback
- mipmap-*/.gitkeep - Launcher icons (Phase 4)

## Supabase Backend (supabase/) - IMPLEMENTED

### Migrations
- migrations/20260111000000_initial_schema.sql - Initial schema with:
  - locations table (kiosk/mobile types)
  - products table (with default pricing)
  - transactions table (append-only ledger with local_id UNIQUE)
  - inventory view (computed from transactions)
  - RLS policies (permissive anon access)
  - Performance indexes
  - Seed data (2 locations, 4 products)
- migrations/20260111000001_purchase_batches.sql - Batch support:
  - purchase_batches table
  - batch_id FK on transactions
- migrations/20260111000002_product_images.sql - Image support:
  - image_uri column on products

### Documentation
- README.md - Setup instructions, schema overview, testing guide

## Documentation (docs/) - NOT YET CREATED

Planned:
- HARDWARE.md - Scales and printer integration details
- RECEIPTS.md - Receipt format and legal notes
- SYNC.md - Sync strategy and conflict resolution
- SCHEMA.md - Database schema documentation

---

## Update Instructions

This file is updated during `reflect` mode. To update:

1. Scan project directories for significant changes
2. Group files by module/feature area
3. Note file counts per directory
4. Add brief purpose annotations for key files
5. Update the "Updated" date

Keep it scannable:
- Use consistent formatting
- Group related files
- Summarize large directories (e.g., "16 controllers" not listing all 16)
- Focus on structure, not exhaustive listing
