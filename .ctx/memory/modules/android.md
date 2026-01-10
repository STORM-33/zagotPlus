# Module: Android App

Updated: 2026-01-10

## Tech Stack

- **Language**: Kotlin 1.9+
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: Clean Architecture (data, domain, ui)
- **DI**: Hilt (Dagger)
- **Database**: Room 2.6+
- **Async**: Kotlin Coroutines + Flow
- **Background**: WorkManager (sync)
- **Navigation**: Compose Navigation

## Key Dependencies

```kotlin
// build.gradle.kts (app)
dependencies {
    // Compose
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose")
    implementation("androidx.navigation:navigation-compose")

    // Room
    implementation("androidx.room:room-runtime")
    implementation("androidx.room:room-ktx")
    kapt("androidx.room:room-compiler")

    // Hilt
    implementation("com.google.dagger:hilt-android")
    kapt("com.google.dagger:hilt-compiler")

    // Supabase
    implementation("io.github.jan-tennert.supabase:postgrest-kt")
    implementation("io.github.jan-tennert.supabase:realtime-kt")

    // WorkManager
    implementation("androidx.work:work-runtime-ktx")
    implementation("androidx.hilt:hilt-work")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android")
}
```

## Architecture Layers

### Data Layer

**Responsibility**: Data access and persistence

**Components**:
- `entities/` - Room entities (DB schema)
- `dao/` - Data Access Objects (CRUD operations)
- `dto/` - Data Transfer Objects (Supabase API)
- `repository/` - Repository pattern (abstracts data sources)

**Pattern**:
```kotlin
// Repository uses both Room (local) and Supabase (remote)
class TransactionRepository @Inject constructor(
    private val localDao: TransactionDao,
    private val supabaseClient: SupabaseClient
) {
    // Offline-first: always return from Room
    fun getTransactions(): Flow<List<Transaction>> = localDao.getAll()

    // Write to Room immediately, sync later
    suspend fun createTransaction(tx: Transaction) {
        localDao.insert(tx.copy(syncedAt = null))
        // WorkManager will sync in background
    }
}
```

### Domain Layer

**Responsibility**: Business logic and models

**Components**:
- `model/` - Domain models (may differ from entities)
- `usecase/` - Optional: complex business logic

**Example**:
```kotlin
// Domain model may compute derived fields
data class InventoryItem(
    val productId: UUID,
    val productName: String,
    val quantityKg: BigDecimal,
    val isLow: Boolean // computed: qty < threshold
)
```

### UI Layer

**Responsibility**: User interface and interaction

**Components**:
- `screens/` - Screen composables
- `components/` - Reusable UI elements
- `navigation/` - Navigation graph
- `theme/` - Material 3 theme

**Pattern**:
```kotlin
// Screen with ViewModel
@Composable
fun PurchaseScreen(
    viewModel: PurchaseViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsState()

    // UI reacts to state changes
    when (uiState) {
        is Loading -> LoadingIndicator()
        is Success -> PurchaseForm(...)
        is Error -> ErrorMessage(...)
    }
}
```

## Offline-First Strategy

1. **Write path**: UI → Room (immediate) → WorkManager (background) → Supabase
2. **Read path**: Room only (Supabase updates via Realtime)
3. **Sync detection**: `synced_at = null` marks pending transactions
4. **Network resilience**: App fully functional without internet

## State Management

- **ViewModels**: Hold UI state, survive configuration changes
- **StateFlow**: Reactive state for UI (hot stream)
- **Flow**: Data streams from Room (cold stream, converted to StateFlow in VM)

```kotlin
class PurchaseViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel() {
    private val _uiState = MutableStateFlow<UiState>(UiState.Idle)
    val uiState: StateFlow<UiState> = _uiState.asStateFlow()

    fun createPurchase(weight: BigDecimal, product: Product) {
        viewModelScope.launch {
            _uiState.value = UiState.Loading
            try {
                repository.createTransaction(...)
                _uiState.value = UiState.Success
            } catch (e: Exception) {
                _uiState.value = UiState.Error(e.message)
            }
        }
    }
}
```

## Room Database

### Entities

```kotlin
@Entity(tableName = "transactions")
data class TransactionEntity(
    @PrimaryKey val id: UUID = UUID.randomUUID(),
    val localId: UUID, // unique across all devices
    val locationId: UUID,
    val type: TransactionType,
    val productId: UUID,
    val weightKg: BigDecimal,
    val pricePerKg: BigDecimal?,
    val totalAmount: BigDecimal?,
    val notes: String?,
    val deviceId: String,
    val createdAt: Instant,
    val syncedAt: Instant? // null = pending sync
)

enum class TransactionType {
    PURCHASE, SALE, TRANSFER_OUT, TRANSFER_IN
}
```

### DAOs

```kotlin
@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY created_at DESC")
    fun getAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions WHERE synced_at IS NULL")
    suspend fun getPendingSync(): List<TransactionEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(transaction: TransactionEntity)

    @Query("UPDATE transactions SET synced_at = :syncedAt WHERE local_id = :localId")
    suspend fun markSynced(localId: UUID, syncedAt: Instant)
}
```

### TypeConverters

```kotlin
class Converters {
    @TypeConverter
    fun fromTimestamp(value: Long?): Instant? = value?.let { Instant.ofEpochMilli(it) }

    @TypeConverter
    fun toTimestamp(instant: Instant?): Long? = instant?.toEpochMilli()

    @TypeConverter
    fun fromUUID(uuid: UUID?): String? = uuid?.toString()

    @TypeConverter
    fun toUUID(string: String?): UUID? = string?.let { UUID.fromString(it) }

    @TypeConverter
    fun fromBigDecimal(value: BigDecimal?): String? = value?.toString()

    @TypeConverter
    fun toBigDecimal(string: String?): BigDecimal? = string?.let { BigDecimal(it) }
}
```

## Hilt Setup

```kotlin
// Application class
@HiltAndroidApp
class ZagotApp : Application()

// MainActivity
@AndroidEntryPoint
class MainActivity : ComponentActivity()

// ViewModel
@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val repository: TransactionRepository
) : ViewModel()

// Repository
@Singleton
class TransactionRepository @Inject constructor(
    private val dao: TransactionDao,
    private val supabaseClient: SupabaseClient
)

// Module for Room
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): ZagotDatabase {
        return Room.databaseBuilder(
            context,
            ZagotDatabase::class.java,
            "zagot.db"
        ).build()
    }

    @Provides
    fun provideTransactionDao(db: ZagotDatabase): TransactionDao = db.transactionDao()
}
```

## Navigation

```kotlin
// Navigation routes
sealed class Screen(val route: String) {
    object Purchase : Screen("purchase")
    object Sale : Screen("sale")
    object Inventory : Screen("inventory")
    object History : Screen("history")
    // ...
}

// NavHost
@Composable
fun ZagotNavHost(navController: NavHostController) {
    NavHost(navController, startDestination = Screen.Purchase.route) {
        composable(Screen.Purchase.route) { PurchaseScreen() }
        composable(Screen.Sale.route) { SaleScreen() }
        composable(Screen.Inventory.route) { InventoryScreen() }
        // ...
    }
}
```

## Testing Strategy

- **Unit tests**: ViewModels, Repositories, UseCases
- **Integration tests**: Room DAOs with in-memory database
- **UI tests**: Compose screens with test rules
- **Instrumented tests**: Hardware integration (scales, printer)

## Conventions

- **Naming**: `*ViewModel`, `*Repository`, `*Dao`, `*Screen`
- **Packages**: Group by feature (not by layer) in `ui/screens/`
- **Composables**: PascalCase, preview functions for each screen
- **State**: Immutable data classes, sealed classes for states
- **Coroutines**: Use `viewModelScope` in VMs, `CoroutineScope` in repositories

## Permissions

```xml
<!-- AndroidManifest.xml -->
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />

<uses-feature android:name="android.hardware.bluetooth" android:required="true" />
```

## Build Configuration

- **Minimum SDK**: 24 (Android 7.0)
- **Target SDK**: 34 (Android 14)
- **Compile SDK**: 34
- **JVM Target**: 17

## Resources

- **Strings**: Ukrainian (values-uk/strings.xml is default)
- **Colors**: Material 3 dynamic colors
- **Theme**: Dark mode support via `isSystemInDarkTheme()`
