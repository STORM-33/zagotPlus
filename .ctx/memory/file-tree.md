# File Tree

Updated: 2026-01-10

## Overview

Zagot+ is organized as a monorepo with Android app, documentation, and Claude Auto OS workspace.
Android follows Clean Architecture with clear separation: data, domain, and UI layers.

## Root (/)

- CLAUDE.md - Claude Auto OS configuration
- MASTER_PLAN.md - Complete project specification (20 sessions across 5 phases)
- README.md - Project overview and setup instructions
- .gitignore - Version control exclusions

## Claude Workspace (.ctx/)

### state and planning (6 files)
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

### modes/ (workflow instructions)
- plan.md - Planning mode instructions
- work.md - Execution mode instructions
- do.md - Quick task mode
- reflect.md - Memory consolidation mode
- validate.md - Consistency checking
- archive.md - Completed work archiving
- blocked.md - Recovery from blockers
- status.md - Status reporting

### templates/ (session briefs)
- feature.md - New feature session template
- bugfix.md - Bug fix session template
- refactor.md - Code improvement template
- research.md - Investigation template

### sessions/{phase}/{session}/ (work artifacts)
- brief.md - Session objectives and requirements
- report.md - Session outcomes and decisions

### history/ (archived work)
- index.md - Decisions, patterns, lessons learned
- {plan-name}/ - Archived completed plans

## Android App (android/)

### Project Root (android/)
- build.gradle.kts - Project-level Gradle config
- settings.gradle.kts - Module configuration
- gradle.properties - Build properties

### App Module (android/app/)
- build.gradle.kts - App dependencies (Compose, Room, Hilt, Supabase)
- AndroidManifest.xml - Permissions, hardware features

### Main Source (android/app/src/main/)

#### Kotlin Source (kotlin/com/zagot/)

**Application**
- ZagotApp.kt - Application class with Hilt setup
- MainActivity.kt - Single activity container

**Data Layer (data/)**
- local/ - Room database
  - entities/ - Room entities (Location, Product, Transaction)
  - dao/ - Data Access Objects
  - ZagotDatabase.kt - Room database instance
  - TypeConverters.kt - UUID, Timestamp, Enum converters
- remote/ - Supabase integration
  - SupabaseClient.kt - Supabase instance
  - dto/ - Data Transfer Objects for API
- repository/ - Repository pattern implementations
  - LocationRepository.kt
  - ProductRepository.kt
  - TransactionRepository.kt
  - InventoryRepository.kt

**Domain Layer (domain/)**
- model/ - Business models (may differ from entities)
  - Transaction.kt
  - InventoryItem.kt
  - Location.kt
  - Product.kt

**Sync Layer (sync/)**
- SyncWorker.kt - WorkManager periodic sync
- SyncService.kt - Push/pull logic
- ConflictResolver.kt - Duplicate detection (local_id)

**UI Layer (ui/)**
- theme/ - Compose theme (colors, typography, shapes)
- navigation/ - NavHost and navigation graph
- components/ - Reusable UI components
- screens/
  - auth/ - PIN entry screen
  - purchase/ - Purchase flow (kiosk + mobile)
  - sale/ - Sale flow
  - transfer/ - Transfer between locations
  - inventory/ - Stock view
  - history/ - Transaction list
  - products/ - Product CRUD
  - reports/ - Daily summaries
  - settings/ - Sync status, device config

**Hardware Integration (hardware/)**
- scales/ - TCP/IP communication with USR-W610
  - ScalesManager.kt - Connection and data parsing
  - ScalesProtocol.kt - Protocol parser (ST,GS,+12.34kg)
- printer/ - Bluetooth ESC/POS
  - PrinterManager.kt - Bluetooth connection
  - ReceiptFormatter.kt - ESC/POS command builder
  - ReceiptTemplate.kt - Ukrainian receipt layout

#### Resources (res/)
- values/ - strings.xml (Ukrainian), colors.xml, themes.xml
- drawable/ - Icons and graphics
- mipmap/ - App launcher icons

## Documentation (docs/)

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
