# Session Brief: init-android

Type: feature
Phase: phase-0
Complexity: medium
Created: 2026-01-11

## Objective

Create Android project skeleton with Jetpack Compose and Hilt dependency injection.

## Background

Need a working Android project as the foundation for all app development. Must be properly configured from the start to avoid migration issues later.

## Requirements

- [ ] Create android/ directory structure per MASTER_PLAN.md
- [ ] Configure Gradle with Kotlin DSL (build.gradle.kts)
- [ ] Add dependencies: Compose, Hilt, Room (versions only, not full impl)
- [ ] Create ZagotApp.kt with @HiltAndroidApp
- [ ] Create MainActivity.kt with @AndroidEntryPoint
- [ ] Create placeholder package structure (data/, domain/, ui/, sync/)
- [ ] Verify project compiles with `./gradlew build`

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `.ctx/memory/modules/android.md`
- `MASTER_PLAN.md` (Repository Structure section)

## Implementation Notes

Package: `com.zagot.zagotplus`

Dependencies to include:
- Jetpack Compose BOM (latest stable)
- Hilt + Hilt Navigation Compose
- Room (runtime + KSP compiler)
- WorkManager
- Supabase Kotlin SDK
- Kotlinx Serialization
- Kotlinx Coroutines

Use version catalog (libs.versions.toml) for dependency management.

Target SDK: 34 (Android 14)
Min SDK: 26 (Android 8.0)

## TDD

Mode: optional

No business logic tests in this session.

### Test Plan
- [ ] Verify Hilt injection works (app starts without crash)

### Test Command
```
./gradlew :app:testDebugUnitTest
```

## Success Criteria

- [ ] `./gradlew build` succeeds without errors
- [ ] App installs on emulator/device
- [ ] Hilt is properly configured (no DI errors)
- [ ] Package structure matches MASTER_PLAN.md

## Out of Scope

- Room entities and DAOs (Phase 1)
- Actual UI screens (Phase 2)
- Sync implementation (Phase 1)
- Hardware integration (Phase 4)

## Dependencies

- Requires: init-repo
- Blocks: room-schema (Phase 1)
