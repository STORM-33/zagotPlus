# Session Brief: implement-device-id

Phase: 3.2 (Data Integrity)
Complexity: low
Type: feature

## Objective

Implement persistent device ID generation and usage in transactions to support multi-device conflict resolution and transaction origin tracking.

## Context

From audit: `deviceId = null` breaks multi-device conflict resolution. Each transaction should record which device created it.

Currently in TransactionRepositoryImpl:
```kotlin
deviceId = null, // TODO: Set device ID from shared prefs
```

## Requirements

1. Create `DevicePreferences` class (similar pattern to `SyncPreferences`)
   - Generate UUID on first access
   - Store in SharedPreferences
   - Return same ID on subsequent access

2. Inject `DevicePreferences` into `TransactionRepositoryImpl`

3. Use `deviceId` when creating transactions (purchase, sale, transfer)

## Success Criteria

- [ ] DevicePreferences created with get/generate logic
- [ ] TransactionRepositoryImpl uses deviceId for all transaction creation
- [ ] Build passes
- [ ] Device ID persists across app restarts (by design - SharedPreferences)

## TDD Mode

optional (low complexity)

## Files to Change

- `data/preferences/DevicePreferences.kt` (create)
- `data/repository/TransactionRepositoryImpl.kt` (modify)

## Notes

- No test required for low complexity
- Pattern should match existing SyncPreferences
