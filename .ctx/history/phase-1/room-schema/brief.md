# Session Brief: room-schema

Type: feature
Phase: phase-1
Complexity: medium
Created: 2026-01-11

## Objective

Create Room database schema with entities, DAOs, and type converters matching Supabase structure.

## Background

The app requires local persistence for offline operation. Room entities must mirror Supabase tables exactly to enable seamless sync. This is the foundation for all data operations.

## Requirements

- [ ] Create `LocationEntity` with id, name, type, createdAt
- [ ] Create `ProductEntity` with id, name, defaultBuyPrice, defaultSellPrice, isActive, createdAt
- [ ] Create `TransactionEntity` with all fields including localId, syncedAt
- [ ] Create `ZagotDatabase` extending RoomDatabase
- [ ] Create DAOs for each entity with CRUD operations
- [ ] Create TypeConverters for UUID, Instant, BigDecimal
- [ ] Add Room dependencies to version catalog
- [ ] Configure Hilt module for database injection

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `MASTER_PLAN.md` (Database Schema section)
- `supabase/migrations/` (schema reference)
- `android/app/build.gradle.kts`
- `android/gradle/libs.versions.toml`

## Implementation Notes

**Entity mappings (Supabase → Room):**
- `uuid` → `String` (stored as TEXT)
- `text` → `String`
- `numeric(10,2)` → `BigDecimal`
- `numeric(10,3)` → `BigDecimal`
- `boolean` → `Boolean`
- `timestamptz` → `Instant`

**File locations:**
- Entities: `data/local/entity/`
- DAOs: `data/local/dao/`
- Database: `data/local/ZagotDatabase.kt`
- TypeConverters: `data/local/converter/`
- Hilt module: `data/local/DatabaseModule.kt`

**Key considerations:**
- Use `@PrimaryKey` with `String` for UUIDs
- Add index on `localId` for fast sync lookups
- Add index on `syncedAt` for finding unsynced records

## TDD

Mode: encouraged

### Test Plan
- [ ] Test: Entity insert and query works
- [ ] Test: TypeConverters round-trip correctly
- [ ] Test: DAO returns Flow for reactive UI

### Test Command
```
./gradlew :app:testDebugUnitTest
```

## Success Criteria

- [ ] All three entities compile and migrate
- [ ] DAOs provide insert, update, delete, query operations
- [ ] Database can be injected via Hilt
- [ ] TypeConverters handle all non-primitive types
- [ ] Build succeeds: `./gradlew assembleDebug`

## Out of Scope

- Repository layer (session 2)
- Supabase client setup (session 3)
- Sync logic (sessions 3-4)
- Computed inventory query (session 2)

## Dependencies

- Requires: none (Phase 0 complete)
- Blocks: repository, supabase-sync
