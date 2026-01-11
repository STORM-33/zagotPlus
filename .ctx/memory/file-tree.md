# File Tree

Updated: 2026-01-11 (Post-Phase 1 Reflect)

## Overview

Zagot+ is organized as a monorepo with Android app, Supabase backend, and Claude Auto OS workspace.
Android follows Clean Architecture with clear separation: data, domain, sync, and UI layers.
**Data layer is fully implemented** - Phase 1 complete.

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

### sessions/phase-0/ (Phase 0 - completed, archived)
- _overview.md - Phase summary
- init-repo/ - brief.md, report.md
- init-android/ - brief.md, report.md
- init-supabase/ - brief.md, report.md

### sessions/phase-1/ (Phase 1 - completed)
- _overview.md - Phase summary
- room-schema/ - brief.md, report.md (entities, DAOs, TypeConverters)
- repository/ - brief.md, report.md (repository layer, domain models)
- supabase-sync/ - brief.md, report.md (Supabase client, sync service)
- sync-worker/ - brief.md, report.md (WorkManager, periodic sync)

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
- entity/ProductEntity.kt - Product table entity
- entity/TransactionEntity.kt - Transaction table with foreign keys and indexes
- dao/LocationDao.kt - Location CRUD with Flow queries
- dao/ProductDao.kt - Product CRUD with active filtering
- dao/TransactionDao.kt - Transaction CRUD with sync queries
- DatabaseModule.kt - Hilt module for database singleton
- ZagotDatabase.kt - Room database (version 1)

*remote/* - Supabase integration
- dto/LocationDto.kt - Location DTO with entity mapping
- dto/ProductDto.kt - Product DTO with entity mapping
- dto/TransactionDto.kt - Transaction DTO with entity mapping
- SupabaseModule.kt - Hilt module for Supabase client

*repository/* - Repository implementations
- LocationRepositoryImpl.kt - Location repository with entity-domain mapping
- ProductRepositoryImpl.kt - Product repository with entity-domain mapping
- TransactionRepositoryImpl.kt - Transaction repository with inventory computation
- RepositoryModule.kt - Hilt bindings for repositories

**Domain Layer (domain/) - FULLY IMPLEMENTED**

*model/*
- Location.kt - Domain model with LocationType enum (KIOSK, MOBILE)
- Product.kt - Domain model with price and active status
- Transaction.kt - Domain model with TransactionType enum
- InventoryItem.kt - Computed inventory (location + product + weight)

*repository/* - Interfaces
- LocationRepository.kt - Location data access interface
- ProductRepository.kt - Product data access interface
- TransactionRepository.kt - Transaction + inventory interface

**Sync Layer (sync/) - FULLY IMPLEMENTED**
- SyncWorker.kt - CoroutineWorker with @HiltWorker for background sync
- SyncManager.kt - Periodic (15min) and manual sync scheduling
- SyncService.kt - Push/pull sync logic with deduplication
- SyncPreferences.kt - SharedPreferences for last sync timestamp
- SyncStatus.kt - Sync state model (IDLE, SYNCING, ERROR)
- SyncStatusRepository.kt - StateFlow for reactive sync status

**UI Layer (ui/)**
- theme/Theme.kt - Material 3 theme (placeholder colors)
- theme/Type.kt - Typography configuration
- navigation/.gitkeep - NavHost (Phase 2)
- screens/.gitkeep - Compose screens (Phase 2)
- components/.gitkeep - Reusable components (Phase 2)

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
