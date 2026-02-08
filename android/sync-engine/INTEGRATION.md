# Sync Engine Integration Guide

## Overview

`:sync-engine` is a standalone Android library module for offline-first sync with Supabase.
It handles the full sync lifecycle: OFFLINE → CATCHING_UP → LIVE, with outbox-based change
tracking, conflict resolution, realtime subscriptions, and periodic safety syncs.

## Quick Start

### 1. Add dependency

```kotlin
// settings.gradle.kts
include(":sync-engine")

// app/build.gradle.kts
implementation(project(":sync-engine"))
testImplementation(testFixtures(project(":sync-engine")))
```

### 2. Add sync tables to your Room database

The sync engine requires two tables in your Room database. Add these entities:

```kotlin
@Database(
    entities = [
        // ... your entities ...
        SyncOutboxEntity::class,
        SyncMetadataEntity::class,
    ],
    // ...
)
abstract class YourDatabase : RoomDatabase() {
    // ... your DAOs ...
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun syncMetadataDao(): SyncMetadataDao
}
```

If migrating an existing database, use `SyncMigrationHelper.buildMigration(fromVersion, toVersion)`.

### 3. Provide Hilt bindings

```kotlin
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncBindingsModule {
    @Binds @Singleton
    abstract fun bindSyncEngine(impl: SyncEngineImpl): SyncEngine

    @Binds @Singleton
    abstract fun bindSyncRemoteClient(impl: SupabaseSyncRemoteClient): SyncRemoteClient

    @Binds @Singleton
    abstract fun bindRealtimeChannel(impl: SupabaseRealtimeChannel): RealtimeChannelContract

    @Binds @Singleton
    abstract fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor
}

@Module
@InstallIn(SingletonComponent::class)
object SyncProviderModule {
    @Provides @Singleton
    fun provideRoomDatabase(db: YourDatabase): RoomDatabase = db

    @Provides @Singleton
    fun provideWorkManager(@ApplicationContext ctx: Context): WorkManager =
        WorkManager.getInstance(ctx)

    @Provides @Singleton
    fun provideSyncEngineConfig(): SyncEngineConfig = SyncEngineConfig(
        safetySyncIntervalMinutes = 15L,
        pushBatchSize = 200,
    )
}
```

### 4. Register tables

Create a registrar class that maps your tables:

```kotlin
@Singleton
class YourSyncRegistrar @Inject constructor(
    @RawDao private val workerDao: WorkerDao,  // raw = no outbox interception
    // ... other DAOs
) {
    fun registerAll(engine: SyncEngine) {
        engine.registerTable(SyncTableConfig(
            tableName = "workers",
            applyToRoom = { records ->
                val entities = records.map { WorkerDto.fromRecord(it).toEntity() }
                workerDao.upsertAll(entities)
            }
        ))
        // ... more tables in FK dependency order
    }
}
```

### 5. Create SyncAware DAO wrappers

For each DAO that writes data, create an outbox-instrumented wrapper:

```kotlin
@Singleton
class SyncAwareWorkerDao @Inject constructor(
    @RawDao private val dao: WorkerDao,
    private val outboxDao: SyncOutboxDao,
    private val syncEngine: SyncEngine,
) : WorkerDao by dao {

    override suspend fun insert(worker: WorkerEntity) {
        dao.insert(worker)
        outboxDao.insert(SyncOutboxEntity(
            tableName = "workers",
            recordId = worker.id.toString(),
            operation = "INSERT",
            payload = SyncJson.encodeToString(WorkerDto.fromEntity(worker)),
            createdAt = System.currentTimeMillis(),
        ))
        syncEngine.notifyOutboxChanged()
    }

    // Override update/delete similarly...
}
```

### 6. Start the engine

```kotlin
@HiltAndroidApp
class YourApp : Application() {
    @Inject lateinit var syncEngine: SyncEngine
    @Inject lateinit var registrar: YourSyncRegistrar

    override fun onCreate() {
        super.onCreate()
        registrar.registerAll(syncEngine)
        syncEngine.start()
    }
}
```

## Configuration

All tunables are in `SyncEngineConfig`:

| Parameter | Default | Description |
|-----------|---------|-------------|
| `safetySyncIntervalMinutes` | 15 | WorkManager periodic interval |
| `livePushDebounceMs` | 200 | Debounce for LIVE push after write |
| `pushBatchSize` | 200 | Max records per HTTP push |
| `maxCatchUpRetries` | 3 | Retries on buffer overflow during catch-up |
| `outboxPruneRetentionMs` | 30 min | Keep synced outbox entries for debugging |
| `fkRetryDelayMs` | 500 | Delay before FK constraint retry |

## Key Contracts

- **Table registration order** matters — register parent tables before children (FK deps)
- **`registerTable()` must be called before `start()`** (enforced with check)
- **`applyToRoom` callbacks must use raw DAOs** (not SyncAware wrappers) to avoid outbox loops
- **ConflictResolver must return one of the two input map instances** (identity comparison)
- **Timestamps**: Supabase returns ISO-8601 strings. The engine handles both ISO strings and Long epoch millis.

## Testing

Test fakes are in `testFixtures`:

```kotlin
testImplementation(testFixtures(project(":sync-engine")))

// Available fakes:
import com.zagot.syncengine.testing.FakeSupabaseClient
import com.zagot.syncengine.testing.FakeRealtimeChannel
import com.zagot.syncengine.testing.FakeNetworkMonitor
```

## Package Structure

```
com.zagot.syncengine
├── api/          # Public API: SyncEngine, SyncContracts, Config
├── dao/          # Pull/Push coordinators, ConflictReconciler
├── db/           # Room entities + DAOs for outbox/metadata
├── network/      # NetworkMonitor interface + Android impl
├── realtime/     # RealtimeBuffer + RealtimeManager
├── remote/       # Supabase implementations
├── state/        # State machine (SyncState, SyncEvent)
└── util/         # JsonUtil, SyncJson, SyncTableConfig, @RawDao
```
