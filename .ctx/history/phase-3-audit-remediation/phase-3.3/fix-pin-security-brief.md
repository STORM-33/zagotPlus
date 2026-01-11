# Session Brief: fix-pin-security

Type: bugfix
Complexity: medium
TDD: strict (security fix requires regression tests)

## Problem

From audit:
1. PIN hashing uses SHA-256 **without salt** - vulnerable to rainbow table attacks
2. Lockout state is **memory-only** - cleared on app restart, defeating brute-force protection

## Success Criteria

1. [ ] PIN hash includes random salt (unique per PIN)
2. [ ] Salt stored alongside hash in SharedPreferences
3. [ ] Lockout state persisted (failed attempts + lockout timestamp)
4. [ ] Lockout survives app restart
5. [ ] All existing PIN tests pass
6. [ ] New tests for salted hashing and persisted lockout

## Files to Modify

- `AuthPreferences.kt` - Add salt generation, persist lockout
- `PinViewModel.kt` - Check persisted lockout on init

## Implementation Notes

- Use `SecureRandom` for salt generation
- Store format: `{salt}:{hash}` or separate keys
- Lockout: store `lockout_until` timestamp, check on verify
- Migration: clear existing PIN (re-setup required) - acceptable for security fix

## Risks

- Existing users must re-enter PIN after update (one-time)
