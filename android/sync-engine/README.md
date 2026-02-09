# Sync Engine Module

Portable offline-first sync engine for Android apps backed by Supabase.

## Architecture

```
┌──────────────┐     ┌────────────────┐     ┌──────────────────┐
│NetworkMonitor│────▶│SyncStateMachine│◀────│ RealtimeManager  │
└──────────────┘     └────────────────┘     └──────────────────┘
                           │                         │
                    ┌──────┴───────┐          ┌──────┴───────┐
                    │SyncEngineImpl│◀─────────│RealtimeBuffer│
                    └──────┬───────┘          └──────────────┘
                    ┌──────┴──────┐
              ┌─────┤             ├─────┐
              ▼     ▼             ▼     ▼
        ┌──────┐ ┌──────┐  ┌────────┐ ┌──────┐
        │ Pull │ │ Push │  │Conflict│ │Outbox│
        │Coord │ │Coord │  │Reconcil│ │ DAO  │
        └──────┘ └──────┘  └────────┘ └──────┘
```

### State Machine

```
OFFLINE ──(connectivity)──▶ CATCHING_UP ──(done)──▶ LIVE
   ▲                              │                   │
   └──────(connectivity lost)─────┴───────────────────┘
```

## Integration Guide

### 1. Add Dependency

```kotlin
implementation(project(":sync-engine"))
```

### 2. Add Room Entities

Include `SyncOutboxEntity` and `SyncMetadataEntity` in your Room database:

```kotlin
@Database(
    entities = [
        // your entities...
        SyncOutboxEntity::class,
        SyncMetadataEntity::class,
    ],
    version = N,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun syncOutboxDao(): SyncOutboxDao
    abstract fun syncMetadataDao(): SyncMetadataDao
    // your DAOs...
}
```

### 3. Add Room Migration (if upgrading)

```kotlin
val MIGRATION_X_Y = SyncOutboxMigrations.addFailureTracking(fromVersion = X, toVersion = Y)

Room.databaseBuilder(context, AppDatabase::class.java, "app.db")
    .addMigrations(MIGRATION_X_Y)
    .build()
```

### 4. Create Hilt Module

```kotlin
@Module
@InstallIn(SingletonComponent::class)
object SyncModule {

    @Provides @Singleton
    fun provideSyncRemoteClient(supabase: SupabaseClient): SyncRemoteClient =
        SupabaseSyncRemoteClient(supabase)

    @Provides @Singleton
    fun provideRealtimeChannel(supabase: SupabaseClient): RealtimeChannelContract =
        SupabaseRealtimeChannel(supabase)

    @Provides @Singleton
    fun provideNetworkMonitor(@ApplicationContext ctx: Context): NetworkMonitor =
        ConnectivityNetworkMonitor(ctx)

    @Provides @Singleton
    fun provideRoomDatabase(db: AppDatabase): RoomDatabase = db

    @Provides @Singleton
    fun provideSyncOutboxDao(db: AppDatabase): SyncOutboxDao = db.syncOutboxDao()

    @Provides @Singleton
    fun provideSyncMetadataDao(db: AppDatabase): SyncMetadataDao = db.syncMetadataDao()

    @Provides @Singleton
    fun provideSyncEngineConfig(): SyncEngineConfig = SyncEngineConfig()
}
```

### 5. Write SyncAware DAO Wrappers

Wrap every local write with an outbox entry:

```kotlin
class SyncAwareProductDao(
    private val productDao: ProductDao,
    private val outboxDao: SyncOutboxDao,
    private val database: RoomDatabase,
    private val engine: SyncEngine,
) {
    suspend fun upsert(product: ProductEntity) {
        database.withTransaction {
            productDao.upsert(product)
            outboxDao.insert(SyncOutboxEntity(
                tableName = "products",
                recordId = product.id,
                operation = "UPSERT",
                payload = Json.encodeToString(product.toRecord()),
                createdAt = System.currentTimeMillis(),
            ))
        }
        engine.notifyOutboxChanged()
    }
}
```

### 6. Register Tables (FK order)

```kotlin
@Inject lateinit var engine: SyncEngine

fun setupSync() {
    // Register parent tables first
    engine.registerTable(SyncTableConfig(
        tableName = "categories",
        applyToRoom = { records -> categoryDao.upsertFromRecords(records) },
    ))
    // Then child tables
    engine.registerTable(SyncTableConfig(
        tableName = "products",
        applyToRoom = { records -> productDao.upsertFromRecords(records) },
        deleteFromRoom = { pk -> productDao.deleteById(pk) },
    ))

    engine.start()
}
```

## Configuration Reference

| Field | Default | Description |
|-------|---------|-------------|
| `safetySyncIntervalMinutes` | 15 | WorkManager periodic safety sync interval |
| `livePushDebounceMs` | 200 | Debounce for LIVE push after outbox change |
| `pushBatchSize` | 200 | Max records per push HTTP request |
| `pullPageSize` | 1000 | Max records per pull HTTP request |
| `pullOverlapWindowMs` | 5000 | Overlap window subtracted from cursor |
| `realtimeBufferMaxEvents` | 1000 | Max buffered realtime events during catch-up |
| `maxCatchUpDurationMs` | 30min | Circuit breaker for long catch-ups |
| `maxCatchUpRetries` | 3 | Retry attempts on buffer overflow |
| `outboxPruneRetentionMs` | 30min | How long to keep synced outbox entries |
| `fkRetryDelayMs` | 500 | FK constraint retry base delay |
| `fkRetryMaxAttempts` | 3 | FK constraint retry limit |
| `onPushFailed` | null | Callback on terminal push failure |
| `onSyncComplete` | null | Callback on sync cycle completion |

## SyncTableConfig Reference

| Field | Default | Description |
|-------|---------|-------------|
| `tableName` | — | Remote table name (required) |
| `primaryKey` | `"id"` | PK column name |
| `timestampColumn` | `"server_updated_at"` | Timestamp column for cursor |
| `softDeleteColumn` | `"deleted_at"` | Soft-delete column (null to disable) |
| `fullPull` | false | Always pull all records (no cursor) |
| `conflictResolver` | LastWriteWins | Conflict resolution strategy |
| `applyToRoom` | — | Callback to upsert records to Room |
| `deleteFromRoom` | null | Callback for hard-delete events |
| `pullPageSize` | null | Override engine default page size |

## Testing Guide

The module provides test fakes in the `testFixtures` source set:

```kotlin
testImplementation(testFixtures(project(":sync-engine")))
```

Available fakes:
- **`FakeSupabaseClient`** — in-memory remote with `injectRemoteRecord()`, `failNextCalls()`
- **`FakeRealtimeChannel`** — manual event emission with `emitEvent()`
- **`FakeNetworkMonitor`** — `simulateOnline()` / `simulateOffline()`

### Unit Test Pattern

```kotlin
val fakeClient = FakeSupabaseClient()
val stateMachine = SyncStateMachine()
val coordinator = PullCoordinator(FakeSyncMetadataDao(), stateMachine)

fakeClient.injectRemoteRecord("products", mapOf("id" to "p1", "updated_at" to 100L))
val records = coordinator.pull(fakeClient, config)
assertThat(records).hasSize(1)
```

### Integration Test Pattern

Use Robolectric + in-memory Room:

```kotlin
@RunWith(RobolectricTestRunner::class)
class MyIntegrationTest {
    private lateinit var db: TestSyncDatabase

    @Before
    fun setup() {
        db = Room.inMemoryDatabaseBuilder(context, TestSyncDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        // ... create SyncEngineImpl with real Room + fakes ...
    }
}
```

## Troubleshooting

### Outbox loop (records keep re-syncing)

Your SyncAware DAO wrapper is probably writing to the outbox inside the
`applyToRoom` callback. The `applyToRoom` callback should **only** write to
Room (UPSERT) — never to the outbox.

### FK ordering errors

Register parent tables before child tables. The engine processes tables in
registration order for both pull and push.

### Stuck cursor (records lost on large imports)

The compound cursor `(timestamp, pk)` handles same-timestamp pagination.
If you see "Cursor stuck" warnings, verify your `primaryKey` config matches
the actual PK column name in Supabase.

### Safety sync not running

WorkManager requires `NetworkType.CONNECTED` constraint. Check that your
device has network connectivity and WorkManager is not being throttled
by battery optimization.
