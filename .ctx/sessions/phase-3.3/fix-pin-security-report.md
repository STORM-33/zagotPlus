# Session Report: fix-pin-security

Status: completed
Duration: ~15 min
Complexity: medium

## Summary

Improved PIN security by adding salt to hashing and persisting lockout state.

## Changes

### AuthPreferences.kt
- Added `SecureRandom` salt generation (16 bytes → 32 hex chars)
- PIN hash now computed as `SHA-256(salt + pin)`
- Salt stored in SharedPreferences alongside hash
- Added lockout persistence methods:
  - `recordFailedAttempt()` - increments counter, sets lockout after 3 attempts
  - `isLockedOut()` - checks if `lockout_until` timestamp is in future
  - `clearLockout()` - resets after successful login
  - `getFailedAttempts()` / `getLockoutRemainingSeconds()`

### PinViewModel.kt
- Now reads persisted lockout state on init
- Starts countdown timer if already locked out (app restart case)
- Calls `authPreferences.recordFailedAttempt()` instead of tracking in memory
- Calls `authPreferences.clearLockout()` on successful authentication

## Tests Added

- `setPin stores salt alongside hash`
- `same PIN produces different hashes due to salt`
- `clearPin removes both hash and salt`
- `recordFailedAttempt increments counter`
- `lockout is set after 3 failed attempts`
- `isLockedOut returns true when lockout_until is in future`
- `clearLockout resets failed attempts and lockout`
- `getFailedAttempts returns stored count`
- `getLockoutRemainingSeconds returns correct value`

## Security Improvements

| Before | After |
|--------|-------|
| SHA-256 without salt | SHA-256 with 16-byte random salt |
| Lockout in memory only | Lockout persisted to SharedPreferences |
| Attacker can restart app to reset lockout | Lockout survives app restart |
| Rainbow table attack possible | Salt makes precomputation impractical |

## Breaking Changes

Existing users will need to re-set their PIN after update (old hash format incompatible). This is acceptable for a security fix.

## Verification

- All 72 unit tests pass (6 skipped - SyncServiceTest @Ignored)
- Build successful
