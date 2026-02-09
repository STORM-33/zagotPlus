# History Index

Last updated: 2026-02-09

## Plans

| Plan | Date | Sessions | Status | Location |
|------|------|----------|--------|----------|
| Phase 0: Setup | 2026-01-11 | 3 | completed | history/phase-0-setup |
| Phase 1: Data Layer | 2026-01-11 | 4 | completed | history/phase-1-data-layer |
| Phase 2: Core UI | 2026-01-11 | 5 | completed | history/phase-2-core-ui |
| Phase 3: Audit Remediation | 2026-01-11 | 7 | completed | history/phase-3-audit-remediation |
| Phase 4: Supporting UI | 2026-01-11 | 4 | completed | history/2026-01-11_phase-4-supporting-ui |
| Phase 5: Purchase Flow Redesign | 2026-01-11 | 5 | completed | history/2026-01-11_phase-5-purchase-flow |
| Test Coverage | 2026-01-12 | 2 | completed | history/2026-01-12_test-coverage |

## Decisions Log

| Date | Plan | Decision | Reasoning | Session |
|------|------|----------|-----------|---------|
| 2026-01-11 | Phase 0 | AGP 8.3.1 (not 8.2.0) | JDK 21 compatibility requires AGP 8.3+ | init-android |
| 2026-01-11 | Phase 0 | Gradle 8.4 (not 8.2) | Required by AGP 8.3.1 | init-android |
| 2026-01-11 | Phase 0 | Single migration file | Atomicity and simplicity over multiple files | init-supabase |
| 2026-01-11 | Phase 0 | Fixed UUIDs for seed data | Consistency across environments | init-supabase |
| 2026-01-11 | Phase 0 | Permissive RLS for anon | Single-org use case; can tighten later | init-supabase |
| 2026-01-11 | Phase 0 | Defer launcher icons | Will add in Phase 4 polish | init-android |
| 2026-01-11 | Phase 1 | UUID→TEXT, BigDecimal→TEXT | Preserve precision in SQLite | room-schema |
| 2026-01-11 | Phase 1 | Instant→INTEGER (epoch millis) | Efficient storage for timestamps | room-schema |
| 2026-01-11 | Phase 1 | Index on transfer_location_id | Prevent full table scans on transfer queries | room-schema |
| 2026-01-11 | Phase 1 | Domain models separate from entities | Clean architecture boundary | repository |
| 2026-01-11 | Phase 1 | Inventory computed on-the-fly | No cached state to synchronize | repository |
| 2026-01-11 | Phase 1 | Sales stored as negative weight | Simplifies inventory sum calculation | repository |
| 2026-01-11 | Phase 1 | Transfer UUID prefix linking | -out/-in suffix pairs linked transactions | repository |
| 2026-01-11 | Phase 1 | SharedPreferences for sync timestamp | Simpler than DataStore for single value | supabase-sync |
| 2026-01-11 | Phase 1 | Individual transaction push | Partial success possible on batch failure | supabase-sync |
| 2026-01-11 | Phase 1 | @HiltWorker over custom factory | Simpler Hilt integration for WorkManager | sync-worker |
| 2026-01-11 | Phase 1 | KEEP policy for periodic work | Prevents duplicate schedule registration | sync-worker |
| 2026-01-11 | Phase 1 | fallbackToDestructiveMigration | Dev mode convenience (will add migrations for prod) | room-schema |
| 2026-01-11 | Phase 1 | OnConflictStrategy.REPLACE in DAOs | Enables sync upserts for duplicate handling | room-schema |
| 2026-01-11 | Phase 2 | Bottom nav order: Purchase→Sale→Inventory→History | Matches typical workflow frequency | navigation |
| 2026-01-11 | Phase 2 | Start destination: Purchase | Most common operation | navigation |
| 2026-01-11 | Phase 2 | Sync icon states (4 variants) | CloudDone/Cloud/Sync/CloudOff for clear status | navigation |
| 2026-01-11 | Phase 2 | SHA-256 for PIN hash | Simple deterrent, not security-critical | auth-pin |
| 2026-01-11 | Phase 2 | SharedPreferences for PIN | Consistent with SyncPreferences pattern | auth-pin |
| 2026-01-11 | Phase 2 | No biometrics | Minimal scope per requirements | auth-pin |
| 2026-01-11 | Phase 2 | Large circular buttons | Tablet-friendly UI for PIN entry | auth-pin |
| 2026-01-11 | Phase 2 | collectAsStateWithLifecycle | Lifecycle-aware state collection | screen-purchase |
| 2026-01-11 | Phase 2 | BigDecimal for calculations | Financial precision | screen-purchase |
| 2026-01-11 | Phase 2 | Negative inventory allowed | Business warning, not technical blocker | screen-sale |
| 2026-01-11 | Phase 2 | Refresh button (not pull-to-refresh) | Compose BOM 2024.01.00 lacks PullToRefreshBox | screen-inventory |
| 2026-01-11 | Phase 2 | Display all products even with 0 inventory | Completeness for stock tracking | screen-inventory |
| 2026-01-11 | Phase 3 | SyncResult as sealed class (3 states) | Distinguish Success/Partial/Failure for better UX | fix-sync-partial-failure |
| 2026-01-11 | Phase 3 | Partial sync treated as success | Data is safe on server, pull retries automatically | fix-sync-partial-failure |
| 2026-01-11 | Phase 3 | Remove destructive migration fallback | Prevent data loss on schema updates in production | fix-database-migrations |
| 2026-01-11 | Phase 3 | @Ignore SyncServiceTest (not delete) | Preserve test logic for future use with proper mocking | test-sync-service |
| 2026-01-11 | Phase 3 | UUID per device stored in SharedPreferences | Persistent device ID for transaction tracking | implement-device-id |
| 2026-01-11 | Phase 3 | Salt + PBKDF2 for PIN hashing | Security hardening over simple SHA-256 | fix-pin-security |
| 2026-01-11 | Phase 3 | Persisted lockout with timestamp | Survives app restart, uses monotonic time | fix-pin-security |
| 2026-01-11 | Phase 3 | String type for decimal DTOs | Preserves exact precision in JSON serialization | fix-decimal-precision |
| 2026-01-11 | Phase 4 | RawQuery for dynamic filtering | Flexible WHERE clauses vs multiple fixed queries | screen-history |
| 2026-01-11 | Phase 4 | 300ms debounce for search | Avoid excessive queries while typing | screen-history |
| 2026-01-11 | Phase 4 | Navigation via overflow menu | Products, Reports, Settings are secondary screens | screen-products |
| 2026-01-11 | Phase 4 | No delete, only deactivate | Referential integrity for products | screen-products |
| 2026-01-11 | Phase 4 | Active products sorted first | Better UX for product list | screen-products |
| 2026-01-11 | Phase 4 | DatePickerDialog defaults to today | Most common use case for reports | screen-reports |
| 2026-01-11 | Phase 4 | BigDecimal.sumOf for aggregation | Accurate decimal summation | screen-reports |
| 2026-01-11 | Phase 4 | Location selection in DevicePreferences | Persist default location across sessions | screen-settings |
| 2026-01-11 | Phase 5 | database.withTransaction for atomic batch | Ensures batch + transactions created together | db-batches |
| 2026-01-11 | Phase 5 | SQLite date functions for today query | localtime aware filtering for batches | db-batches |
| 2026-01-11 | Phase 5 | SET_NULL on batch_id FK delete | Preserve transactions if batch deleted | db-batches |
| 2026-01-11 | Phase 5 | Store content:// URI directly | No file copying needed for images | product-images |
| 2026-01-11 | Phase 5 | Coil for async image loading | Built-in caching, simpler than Glide | product-images |
| 2026-01-11 | Phase 5 | LazyColumn with Flow for batch list | Reactive UI with automatic updates | purchase-main-screen |
| 2026-01-11 | Phase 5 | Weight placeholder until scales | "--" display, scales integration deferred | purchase-main-screen |
| 2026-01-11 | Phase 5 | GridCells.Adaptive(120.dp) for product grid | Responsive layout for phone/tablet | purchase-entry-flow |
| 2026-01-11 | Phase 5 | 3-state enum for entry screen flow | PRODUCT_GRID → WEIGHT_ENTRY → POSITIONS_LIST | purchase-entry-flow |
| 2026-01-11 | Phase 5 | PurchasePosition data class with UUID | Line items with unique IDs for list management | purchase-entry-flow |
| 2026-01-11 | Phase 5 | Overlay instead of separate navigation | Simpler flow for summary display | purchase-summary |
| 2026-01-11 | Phase 5 | Tap to confirm (not button) | Matches brief specification for summary | purchase-summary |
| 2026-01-11 | Phase 5 | isSaving flag prevents double-tap | UX protection during async save | purchase-summary |
| 2026-01-12 | Test Coverage | MockK for repository mocking | Industry standard for Kotlin mocking | repository-tests |
| 2026-01-12 | Test Coverage | runTest for coroutine tests | Kotlin coroutines test library | viewmodel-tests |
| 2026-01-12 | Test Coverage | UnconfinedTestDispatcher for immediate execution | Synchronous test execution | viewmodel-tests |
| 2026-01-14 | Location Cash | Location+date composite key for batches | Groups cash history by location for proper separation | cash-dao-location |
| 2026-01-11 | Phase 5 | database.withTransaction for atomic batch | Ensures batch + transactions created together | db-batches |
| 2026-01-11 | Phase 5 | SQLite date functions for today query | localtime aware filtering for batches | db-batches |
| 2026-01-11 | Phase 5 | SET_NULL on batch_id FK delete | Preserve transactions if batch deleted | db-batches |
| 2026-01-11 | Phase 5 | Store content:// URI directly | No file copying needed for images | product-images |
| 2026-01-11 | Phase 5 | Coil for async image loading | Built-in caching, simpler than Glide | product-images |
| 2026-01-11 | Phase 5 | LazyColumn with Flow for batch list | Reactive UI with automatic updates | purchase-main-screen |
| 2026-01-11 | Phase 5 | Weight placeholder until scales | "--" display, scales integration deferred | purchase-main-screen |
| 2026-01-11 | Phase 5 | GridCells.Adaptive(120.dp) for product grid | Responsive layout for phone/tablet | purchase-entry-flow |
| 2026-01-11 | Phase 5 | 3-state enum for entry screen flow | PRODUCT_GRID → WEIGHT_ENTRY → POSITIONS_LIST | purchase-entry-flow |
| 2026-01-11 | Phase 5 | PurchasePosition data class with UUID | Line items with unique IDs for list management | purchase-entry-flow |

## Lessons Learned

| Date | Plan | Lesson | Context |
|------|------|--------|---------|
| 2026-01-11 | Phase 0 | JDK 21 requires AGP 8.3+ and Gradle 8.4+ | Windows dev machine with JDK 21 |
| 2026-01-11 | Phase 0 | LF→CRLF warnings are normal on Windows | Git autocrlf setting |
| 2026-01-11 | Phase 0 | Version catalog (libs.versions.toml) keeps deps organized | Modern Gradle pattern |
| 2026-01-11 | Phase 1 | Room KSP warns about missing FK indexes | Add indexes proactively on foreign keys |
| 2026-01-11 | Phase 1 | Flow-based DAOs enable reactive UI | Use Flow for lists, suspend for one-shot |
| 2026-01-11 | Phase 1 | BuildConfig fields for API credentials | Keeps secrets out of code |
| 2026-01-11 | Phase 1 | TypeConverters validate at compile time | Room KSP catches mapping errors early |
| 2026-01-11 | Phase 1 | Extension functions clean entity mapping | Keep repository implementations concise |
| 2026-01-11 | Phase 1 | WorkManager constraints require proper policy | KEEP prevents duplicate periodic schedules |
| 2026-01-11 | Phase 2 | material-icons-extended increases APK size | Can optimize with R8 proguard rules if needed |
| 2026-01-11 | Phase 2 | Android Context tests need Robolectric | Pure ViewModel tests work without it |
| 2026-01-11 | Phase 2 | JVM memory crashes during test | Reduce Gradle heap to 1GB for constrained env |
| 2026-01-11 | Phase 2 | BigDecimal.compareTo for test assertions | scale-independent equality checking |
| 2026-01-11 | Phase 2 | lifecycle-runtime-compose needed | Required for collectAsStateWithLifecycle |
| 2026-01-11 | Phase 3 | Supabase client cannot be unit-tested directly | Library starts internal coroutines, causes hangs | test-sync-service |
| 2026-01-11 | Phase 3 | Extract interface for testability | Wrap Supabase calls in interface for mocking | test-sync-service |
| 2026-01-11 | Phase 3 | SharedPreferences for device UUID | Same pattern as SyncPreferences | implement-device-id |
| 2026-01-11 | Phase 4 | TransactionQueryBuilder for SQL | Cleaner than string concatenation | screen-history |
| 2026-01-11 | Phase 4 | Filter state resets on app restart | Not persisted - per session only | screen-history |
| 2026-01-11 | Phase 4 | Summary computation in ViewModel | Flexibility over DAO aggregation | screen-reports |
| 2026-01-11 | Phase 5 | Room MIGRATION_1_2 and MIGRATION_2_3 | Explicit SQL for schema upgrades | db-batches, product-images |
| 2026-01-11 | Phase 5 | ActivityResultContracts.GetContent | Modern image picker API | product-images |
| 2026-01-11 | Phase 5 | Divider vs HorizontalDivider | Material3 compatibility issue | purchase-main-screen |
| 2026-01-11 | Phase 5 | Regex for decimal input validation | Clean validation without exceptions | purchase-entry-flow |
| 2026-01-11 | Phase 5 | scrim color for overlay | MaterialTheme.colorScheme.scrim for standard overlay effect | purchase-summary |
| 2026-01-11 | Phase 5 | Atomic transactions prevent partial saves | Always use database.withTransaction for multi-insert ops | db-batches |
| 2026-01-11 | Phase 5 | Adaptive grid better than fixed columns | GridCells.Adaptive handles different screen sizes | purchase-entry-flow |
| 2026-01-11 | Phase 5 | Nullable columns ease migrations | Add new optional fields as nullable for backwards compat | product-images |
| 2026-01-11 | Phase 5 | Overlay patterns reduce navigation depth | Full-screen overlay with tap-to-dismiss for simple confirmations | purchase-summary |
| 2026-01-11 | Phase 5 | Content URIs simplify image handling | No need to copy files, system handles lifecycle | product-images |
| 2026-01-11 | Phase 5 | Separate domain models from entities | PurchasePosition vs Transaction - different concerns | purchase-entry-flow |
| 2026-01-12 | Test Coverage | MockK relaxed mode simplifies setup | Use for DAOs, strict for critical mocks | repository-tests |
| 2026-01-12 | Test Coverage | every { dao.method() } returns flowOf() | Standard pattern for Flow-returning DAO mocks | repository-tests |
| 2026-01-12 | Test Coverage | coVerify for suspend function verification | Coroutine-aware verification in tests | repository-tests |
| 2026-01-12 | Test Coverage | Test complexity drives coverage depth | Low=smoke tests, Medium=full coverage, High=edge cases | viewmodel-tests |
| 2026-02-05 | Production Readiness Audit | DTO decimal fields must use String to avoid precision loss | audit-data-layer |
| 2026-02-05 | Production Readiness Audit | Verify release signing + R8 minification early to avoid ship blockers | audit-build-and-dependencies |

| 2026-02-08 | Production Readiness Audit | Avoid collecting infinite Flow when a single emission is needed; use first() | audit-ui-and-viewmodels |
| 2026-02-08 | Production Readiness Audit | Launch suspend calls from viewModelScope to ensure they run | audit-ui-and-viewmodels |
| 2026-02-08 | Production Readiness Audit | Move large transaction queries off main thread (use IO dispatcher) | audit-ui-and-viewmodels |

## Patterns & Solutions

| Problem | Solution | Used In |
|---------|----------|---------|
| Conflict-free sync | UNIQUE constraint on local_id (UUID generated client-side) | init-supabase, repository |
| Computed inventory | Database VIEW summing transactions | init-supabase |
| Hilt setup | @HiltAndroidApp on App class, @AndroidEntryPoint on Activity | init-android |
| Dependency management | Gradle version catalog (libs.versions.toml) | init-android |
| Complex type persistence | TypeConverters for UUID, Instant, BigDecimal | room-schema |
| Reactive data flow | Flow-based DAO queries + collect in UI | room-schema |
| Entity-domain mapping | Extension functions in repository impl | repository |
| Atomic linked transactions | Shared UUID prefix with -out/-in suffix | repository |
| Background sync | WorkManager + CoroutineWorker + @HiltWorker | sync-worker |
| Sync status UI | StateFlow in SyncStatusRepository | sync-worker |
| Deduplication on pull | Skip existing by local_id lookup | supabase-sync |
| Partial push success | Individual transaction upsert, not batch | supabase-sync |
| Offline-first architecture | Repository layer over Room + Supabase | repository, supabase-sync |
| Clean architecture layers | Domain models (entities) ← Repository interfaces ← UI | repository |
| Pending sync tracking | syncedAt=null for unsynced transactions | repository, supabase-sync |
| Periodic background work | WorkManager with ExistingPeriodicWorkPolicy.KEEP | sync-worker |
| Network-aware sync | Constraints.Builder().setRequiredNetworkType(CONNECTED) | sync-worker |
| Navigation with bottom bar | Sealed class Destinations + NavigationBar + NavHost | navigation |
| Auth gate pattern | Check isAuthenticated before showing main content | auth-pin |
| PIN lockout | Track failed attempts, show countdown timer | auth-pin |
| SHA-256 hash storage | MessageDigest + hex encoding for simple deterrent | auth-pin |
| Form validation | UiState computed property (canSave) | screen-purchase, screen-sale |
| Price auto-fill | Set default price on product selection | screen-purchase, screen-sale |
| Snackbar feedback | ScaffoldState.snackbarHostState for success/error | screen-purchase, screen-sale |
| Inventory warning | Compare entered weight to available, show warning | screen-sale |
| Location tabs | TabRow with selectedTabIndex for location switching | screen-inventory |
| Negative inventory display | Red color + warning icon for negative values | screen-inventory |
| Refresh button pattern | IconButton in TopAppBar when no pull-to-refresh | screen-inventory |
| ViewModel test pattern | MockK for repositories, runTest for coroutines | screen-purchase, screen-sale |
| Sync result sealed class | Success/Partial/Failure states with phase tracking | fix-sync-partial-failure |
| Migration infrastructure | Empty MIGRATIONS array + addMigrations(*MIGRATIONS) | fix-database-migrations |
| Deferred test execution | @Ignore with documented reason, not deletion | test-sync-service |
| Device ID injection | Inject DevicePreferences into repository | implement-device-id |
| PBKDF2 key derivation | 10,000 iterations with random salt | fix-pin-security |
| Lockout persistence | Store failed attempts + timestamp in SharedPreferences | fix-pin-security |
| Monotonic lockout timing | SystemClock.elapsedRealtime() for tamper-resistant countdown | fix-pin-security |
| Decimal DTO serialization | Use String instead of Double for BigDecimal in DTOs | fix-decimal-precision |
| Dynamic SQL filters | TransactionQueryBuilder with @RawQuery for flexible WHERE | screen-history |
| Debounced search input | 300ms delay before executing search query | screen-history |
| Secondary screen navigation | Overflow menu for Products/Reports/Settings | screen-products |
| Soft delete pattern | Toggle active status instead of delete for referential integrity | screen-products |
| Sorted list display | Active items first, then inactive (greyed) | screen-products |
| Dialog-based CRUD | AddEditProductDialog for inline editing | screen-products |
| Daily summary reports | Filter transactions by date, aggregate by product/location | screen-reports |
| Clipboard + Share export | ClipboardManager for copy, Intent.ACTION_SEND for share | screen-reports |
| Settings sections | Group settings by category (Sync, Device, Data, About) | screen-settings |
| Batch-level transaction grouping | purchase_batches table with batch_id FK on transactions | db-batches |
| Atomic batch creation | database.withTransaction for batch + transactions | db-batches |
| Today's batches query | SQLite date('now', 'localtime') for timezone-aware filtering | db-batches |
| Product image storage | Store content:// URI directly, no file copying | product-images |
| Async image loading | Coil library with AsyncImage composable | product-images |
| Image picker | ActivityResultContracts.GetContent for modern API | product-images |
| Adaptive grid layout | GridCells.Adaptive(120.dp) for responsive columns | purchase-entry-flow |
| Multi-step entry flow | Enum state machine (PRODUCT_GRID → WEIGHT_ENTRY → POSITIONS_LIST) | purchase-entry-flow |
| Position line items | PurchasePosition data class with UUID for list management | purchase-entry-flow |
| Summary overlay pattern | Full-screen scrim overlay with tap-to-confirm | purchase-summary |
| Double-tap prevention | isSaving flag during async operations | purchase-summary |
| Repository unit tests | MockK relaxed DAOs, flowOf() for Flow returns, coVerify for suspend | repository-tests |
| ViewModel unit tests | UnconfinedTestDispatcher, MockK repositories, runTest coroutines | viewmodel-tests |
| Filter testing | Test all combinations of filter states for ViewModel logic | viewmodel-tests |
| Date range preset testing | Test each DateRangePreset enum value for correctness | viewmodel-tests |
| Device ID display | Truncated UUID with tap-to-copy functionality | screen-settings |
| Location selector | RadioButton list with persisted selection | screen-settings |
| Atomic batch creation | database.withTransaction wraps batch + transactions insert | db-batches |
| Today's batches query | SQLite date functions with localtime for timezone | db-batches |
| Image URI storage | Content URI from picker stored directly (Coil caches) | product-images |
| Product image display | Coil AsyncImage with placeholder Icon | product-images |
| Image picker dialog | ActivityResultContracts.GetContent in composable | product-images |
| Batch list display | LazyColumn with BatchItem composable, Flow observation | purchase-main-screen |
| Weight placeholder | Static "--" text until hardware integration | purchase-main-screen |
| Adaptive product grid | LazyVerticalGrid with GridCells.Adaptive for phone/tablet | purchase-entry-flow |
| Multi-step entry flow | Enum-based screen state (PRODUCT_GRID → WEIGHT_ENTRY → POSITIONS_LIST) | purchase-entry-flow |
| Position item management | PurchasePosition data class with UUID id for list operations | purchase-entry-flow |
| Price auto-fill on select | Set currentPrice from product.defaultBuyPrice when selected | purchase-entry-flow |
| Full-screen summary overlay | Semi-transparent scrim + centered Card for confirmation | purchase-summary |
| Tap to confirm pattern | clickable(onClick = confirmSave) on entire overlay for simple UX | purchase-summary |
| Save-in-progress guard | isSaving flag prevents double-submission during async save | purchase-summary |
| Error recovery to previous state | On save failure, return to POSITIONS_LIST with error message | purchase-summary |
| Batch-grouped transactions | purchase_batches table groups multiple line items by client session | db-batches |
| Today's batches with localtime | `date(created_at / 1000, 'unixepoch', 'localtime')` for TZ-aware filtering | db-batches |
| Coil async image loading | AsyncImage composable with built-in caching for product images | product-images |
| Nullable image_uri column | Backwards compatible optional field for existing products | product-images |
| Adaptive product grid layout | GridCells.Adaptive(120.dp) for responsive phone/tablet design | purchase-entry-flow |
| Multi-step purchase flow | 3-state enum (PRODUCT_GRID → WEIGHT_ENTRY → POSITIONS_LIST) | purchase-entry-flow |
| Purchase position model | Separate from Transaction for line items with UUIDs and calculated totals | purchase-entry-flow |
| Summary confirmation overlay | Full-screen scrim + card with tap-to-dismiss for batch confirmation | purchase-summary |

## Tags

| Location-filtered queries | Add {entity}ByLocationPaged + getTotalCountByLocation methods | cash-dao-location |

#android #supabase #hilt #compose #offline-first #jdk21 #room #workmanager #sync #navigation #auth #viewmodel #testing #mockk #filtering #crud #reports #settings #batches #images #coil #entry-flow #summary-overlay #location-filtering
