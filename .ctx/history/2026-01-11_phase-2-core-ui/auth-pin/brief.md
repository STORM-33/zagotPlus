# Session Brief: auth-pin

Type: feature
Phase: phase-2
Complexity: low
Created: 2026-01-11

## Objective

Implement simple 4-digit PIN entry screen that gates access to the main app.

## Background

The app needs basic access control. A simple PIN prevents unauthorized access without complex authentication. PIN is stored locally (SharedPreferences), no server auth required.

## Requirements

- [ ] Create PIN entry screen with 4-digit input
- [ ] Store PIN hash in SharedPreferences (not plain text)
- [ ] First launch: prompt to set PIN
- [ ] Subsequent launches: prompt to enter PIN
- [ ] Lock/unlock app state (navigate to main after success)
- [ ] Show error on wrong PIN (3 attempts max, then lockout timer)

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `android/app/src/main/kotlin/com/zagot/zagotplus/ui/navigation/NavGraph.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/sync/SyncPreferences.kt` (pattern)

## Implementation Notes

- Use SHA-256 hash for PIN storage (not security-critical, just basic deterrent)
- SharedPreferences key: `pin_hash`
- PIN pad with large buttons (tablet-friendly)
- No biometrics (keep it simple)
- After successful PIN, navigate to Purchase screen (default)

### Suggested Structure

```
ui/
├── screens/
│   └── auth/
│       ├── PinScreen.kt        # PIN entry UI
│       └── PinViewModel.kt     # PIN logic
data/
└── preferences/
    └── AuthPreferences.kt      # PIN storage
```

### UI Design

- Large numeric keypad (0-9)
- 4 dots showing entered digits (filled/empty)
- "Forgot PIN" → show hint to reinstall app
- Ukrainian labels: "Введіть PIN-код", "Неправильний PIN"

## TDD

Mode: encouraged

### Test Plan
- [ ] Test: correct PIN unlocks app
- [ ] Test: wrong PIN shows error
- [ ] Test: 3 wrong attempts triggers lockout
- [ ] Test: PIN hash stored, not plain text

### Test Command
```
./gradlew testDebugUnitTest
```

## Success Criteria

- [ ] First launch shows "Set PIN" flow
- [ ] PIN is hashed before storage
- [ ] Correct PIN navigates to main app
- [ ] Wrong PIN shows error message
- [ ] 3 failed attempts = 30s lockout
- [ ] Build succeeds

## Out of Scope

- Biometric authentication
- PIN recovery (reinstall is the recovery)
- Remote lockout
- Session timeout (auto-lock after inactivity)

## Dependencies

- Requires: navigation
- Blocks: none
