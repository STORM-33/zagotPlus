# Session: fix-sync-partial-failure

Type: bugfix
Complexity: high

## Problem

If `pushPendingTransactions()` succeeds but `pullNewTransactions()` fails:
- Pushed transactions are marked as synced locally
- Entire sync returns failure
- User thinks sync failed, but data is on server
- Retry won't re-push those transactions

## Root Cause

`SyncService.sync()` has single try/catch that treats any failure as total failure, ignoring partial success.

## Success Criteria

- [ ] `SyncResult` distinguishes Success, Partial, and Failure
- [ ] Push succeeds + pull fails → returns `Partial`
- [ ] UI can show partial success message
- [ ] Existing tests still pass

## Files

- `android/app/src/main/kotlin/com/zagot/zagotplus/sync/SyncResult.kt`
- `android/app/src/main/kotlin/com/zagot/zagotplus/sync/SyncService.kt`

## Approach

1. Convert `SyncResult` to sealed class with three states
2. Track push/pull separately with individual error handling
3. Return Partial when push succeeds but pull fails
4. Return failure counts alongside success counts
