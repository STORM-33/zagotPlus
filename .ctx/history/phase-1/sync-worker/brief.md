# Session Brief: sync-worker

Type: feature
Phase: phase-1
Complexity: medium
Created: 2026-01-11

## Objective

Implement WorkManager-based background sync with retry logic and sync status tracking.

## Background

Sync must happen automatically in the background when the device has network connectivity. WorkManager handles:
- Scheduling sync when online
- Retrying on failure
- Surviving app restarts
- Respecting battery/data constraints

## Requirements

- [ ] Create `SyncWorker` extending CoroutineWorker
- [ ] Inject SyncService via Hilt WorkerFactory
- [ ] Implement doWork(): push then pull
- [ ] Configure periodic sync (every 15 minutes when online)
- [ ] Configure one-time sync trigger for immediate sync
- [ ] Add network constraint (only sync when connected)
- [ ] Implement exponential backoff retry on failure
- [ ] Track sync status (idle, syncing, error) in repository/preferences
- [ ] Expose sync status Flow for UI observation

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `sync/SyncService.kt` (from session 3)
- `data/repository/*.kt` (from session 2)
- `ZagotApp.kt` (for WorkManager init)

## Implementation Notes

**Worker setup with Hilt:**
```kotlin
@HiltWorker
class SyncWorker @AssistedInject constructor(
    @Assisted context: Context,
    @Assisted params: WorkerParameters,
    private val syncService: SyncService
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        return try {
            syncService.pushAll()
            syncService.pullAll()
            Result.success()
        } catch (e: Exception) {
            if (runAttemptCount < 3) Result.retry()
            else Result.failure()
        }
    }
}
```

**Periodic sync setup:**
```kotlin
val syncRequest = PeriodicWorkRequestBuilder<SyncWorker>(
    15, TimeUnit.MINUTES
).setConstraints(
    Constraints.Builder()
        .setRequiredNetworkType(NetworkType.CONNECTED)
        .build()
).setBackoffCriteria(
    BackoffPolicy.EXPONENTIAL,
    WorkRequest.MIN_BACKOFF_MILLIS,
    TimeUnit.MILLISECONDS
).build()

WorkManager.getInstance(context)
    .enqueueUniquePeriodicWork(
        "sync",
        ExistingPeriodicWorkPolicy.KEEP,
        syncRequest
    )
```

**File locations:**
- Worker: `sync/SyncWorker.kt`
- Status tracking: `sync/SyncStatusRepository.kt`
- WorkManager setup: `sync/SyncManager.kt`
- Hilt factory: `sync/SyncWorkerFactory.kt` (if needed)

**Key considerations:**
- Initialize WorkManager in Application.onCreate()
- Use unique work names to prevent duplicate schedules
- Provide manual trigger for "sync now" button
- Track last successful sync timestamp

## TDD

Mode: encouraged

### Test Plan
- [ ] Test: Worker calls syncService.pushAll() and pullAll()
- [ ] Test: Worker returns retry on network failure
- [ ] Test: Worker returns failure after max retries
- [ ] Test: Periodic work is scheduled with correct constraints

### Test Command
```
./gradlew :app:testDebugUnitTest
```

## Success Criteria

- [ ] SyncWorker executes push/pull correctly
- [ ] Periodic sync scheduled every 15 minutes
- [ ] Sync only runs when network connected
- [ ] Retry with exponential backoff works
- [ ] Manual sync trigger available
- [ ] Sync status observable via Flow
- [ ] Build succeeds: `./gradlew assembleDebug`

## Out of Scope

- Sync status UI (Phase 2 settings screen)
- Realtime sync (Supabase Realtime)
- Selective sync (sync specific tables only)
- Sync conflict UI (not needed - append-only model)

## Dependencies

- Requires: repository, supabase-sync
- Blocks: none (Phase 1 complete after this)
