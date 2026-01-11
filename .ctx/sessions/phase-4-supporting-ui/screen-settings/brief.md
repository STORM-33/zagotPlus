# Session Brief: screen-settings

Type: feature
Phase: phase-4-supporting-ui
Complexity: low
Created: 2026-01-11

## Objective

Create a Settings screen showing sync status and device configuration.

## Background

Users need visibility into sync state and basic configuration options. This is the central place for device identity and operational settings.

## Requirements

- [ ] Display current sync status (last sync time, pending count)
- [ ] Manual sync trigger button
- [ ] Display device ID (for troubleshooting)
- [ ] Current location selector (which location this device operates as)
- [ ] Navigation to Products screen
- [ ] App version display

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `android/app/src/main/kotlin/com/zagot/zagotplus/sync/SyncStatusRepository.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/data/local/DevicePreferences.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/domain/repository/LocationRepository.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/ui/navigation/Destinations.kt`

## Implementation Notes

1. Create SettingsScreen accessible from nav (menu icon in TopAppBar or 5th nav item)
2. Use PreferenceScreen-like layout with ListItem components
3. SyncStatusRepository provides sync state (already exists)
4. DevicePreferences provides device UUID (already exists)
5. Location selection: RadioButton list or dropdown
6. Store selected location in SharedPreferences (DevicePreferences or new class)
7. BuildConfig.VERSION_NAME for app version

### Layout Structure
```
Settings
├── Sync
│   ├── Status: [Synced / 3 pending / Error]
│   ├── Last sync: 11.01.2026 14:35
│   └── [Sync Now] button
├── Device
│   ├── ID: abc123... (tap to copy)
│   └── Location: [Dropdown: Kiosk / Mobile]
├── Data
│   └── Products → (navigates to ProductsScreen)
└── About
    └── Version: 1.0.0
```

## TDD

Mode: optional (mostly UI wiring)

### Test Plan
- [ ] Test: Location selection persists
- [ ] Test: Sync button triggers sync

### Test Command
```
./gradlew :app:testDebugUnitTest --tests "*.SettingsViewModelTest"
```

## Success Criteria

- [ ] Settings screen accessible from main UI
- [ ] Sync status displays correctly
- [ ] Manual sync button works
- [ ] Device ID displays and can be copied
- [ ] Location selection works and persists
- [ ] Products navigation works
- [ ] App version displays

## Out of Scope

- PIN change (would require auth verification)
- Theme/appearance settings
- Data export/import
- Account management
- Notification settings

## Dependencies

- Requires: none (but recommended after screen-products for navigation)
- Blocks: none
