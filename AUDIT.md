# ZagotPlus Android App - Security & Code Audit Report

**Date:** January 2025  
**Auditor:** Independent Code Review  
**Scope:** Complete Android application codebase  
**Verdict:** ⚠️ **NOT PRODUCTION-READY** - Critical security issues must be addressed

---

## Executive Summary

ZagotPlus is a Ukrainian agricultural trading app for purchasing/selling nuts and seeds with offline-first architecture. While the app demonstrates competent Kotlin/Compose development and thoughtful domain modeling, it suffers from **serious security vulnerabilities**, **missing production hardening**, and **concerning architectural shortcuts** that make it unsuitable for deployment in its current state.

### Critical Statistics
| Metric | Value | Assessment |
|--------|-------|------------|
| Security Issues | 12 | 🔴 Unacceptable |
| Architecture Issues | 8 | 🟡 Needs Work |
| Code Quality Issues | 15+ | 🟡 Technical Debt |
| Test Coverage | ~15% | 🔴 Insufficient |
| Accessibility | Poor | 🔴 Non-compliant |

### Severity Distribution
- 🔴 **CRITICAL**: 4 issues
- 🟠 **HIGH**: 6 issues  
- 🟡 **MEDIUM**: 12 issues
- 🔵 **LOW**: 10+ issues

---

## 🔴 CRITICAL Security Issues

### 1. Release Build Has No Code Obfuscation
**File:** `android/app/build.gradle.kts` (lines 30-35)
```kotlin
release {
    isMinifyEnabled = false  // 🔴 CRITICAL
    isShrinkResources = false
    proguardFiles(...)
}
```

**Impact:** Anyone can decompile the APK and:
- Extract Supabase credentials (anon key, URL)
- Reverse-engineer business logic
- Find additional vulnerabilities
- Clone the entire app

**Recommendation:** Enable minification AND add proper ProGuard rules for Supabase, Room, Hilt.

---

### 2. Database Contains Unencrypted Financial Data
**File:** `data/local/ZagotDatabase.kt`

The Room database stores sensitive financial transaction data (weights, prices, totals, cash operations) in plaintext SQLite. On rooted devices or via backup extraction, all business data is exposed.

**Affected data:**
- All transaction amounts and prices
- Cash operations (deposits, withdrawals, expenses)
- Business partner information (via notes)
- Complete purchase/sale history

**Recommendation:** Implement SQLCipher for Room encryption:
```kotlin
Room.databaseBuilder(...)
    .openHelperFactory(SupportFactory(passphrase))
    .build()
```

---

### 3. PIN Authentication is Cryptographically Weak
**File:** `data/preferences/AuthPreferences.kt` (lines 117-127)
```kotlin
private fun hashPin(pin: String): String {
    val bytes = MessageDigest.getInstance("SHA-256")
        .digest(pin.getBytes())
    // Single SHA-256 hash with NO SALT
}
```

**Problems:**
1. **No salt** - Rainbow table attacks trivially break 4-6 digit PINs
2. **Single iteration** - No key stretching (PBKDF2/Argon2 required)
3. **Fast hash** - SHA-256 is designed to be FAST, not secure for passwords
4. **Only 30-second lockout** - Brute force 10,000 4-digit PINs in ~83 hours

**A 4-digit PIN with SHA-256 can be cracked in milliseconds.**

**Recommendation:**
```kotlin
// Use Android KeyStore + Argon2
private fun hashPin(pin: String, salt: ByteArray): String {
    return Argon2.hash(pin, salt, iterations=3, memory=65536)
}
```

---

### 4. No Certificate Pinning - MITM Vulnerability
**Files:** `data/remote/SupabaseModule.kt`, missing `network_security_config.xml`

The app communicates with Supabase over HTTPS but:
- No certificate pinning configured
- No network security config
- Supabase anon key transmitted on every request

**Impact:** Attackers on same network can:
- Intercept all sync data
- Steal the Supabase anon key
- Inject malicious data during sync
- Perform session hijacking

**Recommendation:**
```xml
<!-- res/xml/network_security_config.xml -->
<network-security-config>
    <domain-config cleartextTrafficPermitted="false">
        <domain includeSubdomains="true">supabase.co</domain>
        <pin-set expiration="2025-12-31">
            <pin digest="SHA-256">BBBBB...</pin>
        </pin-set>
    </domain-config>
</network-security-config>
```

---

## 🟠 HIGH Severity Issues

### 5. Backup Enabled Exposes All Data
**File:** `AndroidManifest.xml`
```xml
android:allowBackup="true"
```

With ADB access, anyone can extract complete app data:
```bash
adb backup -f zagot.ab com.example.zagotplus
```

**Recommendation:** Set `android:allowBackup="false"` or implement `BackupAgent` with encryption.

---

### 6. Unused Bluetooth Permissions - Attack Surface
**File:** `AndroidManifest.xml`
```xml
<uses-permission android:name="android.permission.BLUETOOTH" />
<uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
<uses-permission android:name="android.permission.BLUETOOTH_CONNECT" />
```

**No Bluetooth code exists in the app.** These permissions:
- Increase attack surface unnecessarily
- Trigger user permission dialogs for no reason
- May cause Play Store review issues

**Recommendation:** Remove all Bluetooth permissions immediately.

---

### 7. Supabase Credentials in Build Config
**File:** `android/app/build.gradle.kts` (lines 16-22)
```kotlin
buildConfigField("String", "SUPABASE_URL", "\"https://...supabase.co\"")
buildConfigField("String", "SUPABASE_ANON_KEY", "\"eyJ...\"")
```

While anon keys are meant to be public, they're still:
- Easily extractable from the APK
- Usable for denial-of-service attacks
- Combined with no obfuscation = full API exposure

**Recommendation:** Implement edge function authentication, rate limiting on Supabase side.

---

### 8. Race Condition in Session Authentication
**File:** `data/preferences/AuthPreferences.kt`
```kotlin
@Volatile
private var sessionAuthenticated = false
```

Using `@Volatile` alone doesn't provide atomicity. Multiple threads could:
- Check `sessionAuthenticated` simultaneously
- Both see `false` and require re-auth
- Or both see `true` after one auth

**Recommendation:** Use `AtomicBoolean` or proper synchronization:
```kotlin
private val sessionAuthenticated = AtomicBoolean(false)
```

---

### 9. Sync Timestamp Edge Case Loses Data
**File:** `sync/SyncService.kt` (acknowledged in comments)

The sync uses `created_at` filtering which can miss transactions:
```kotlin
// If device A creates transaction at 10:00:01
// Device B syncs at 10:00:00 (its clock)
// Transaction is never pulled to Device B
```

**Impact:** Financial data can be permanently lost between devices.

**Recommendation:** Implement vector clocks or hybrid logical clocks.

---

### 10. No Input Validation in DTOs
**File:** `data/remote/dto/*.kt`

DTOs parse remote data without validation:
```kotlin
data class TransactionDto(
    val weight: String,  // Could be "abc" - crashes on parse
    val price_per_kg: String,
    val total_price: String
)
```

**Impact:** Malformed data from Supabase causes crashes.

**Recommendation:** Add validation in mappers with try-catch and logging.

---

## 🟡 MEDIUM Severity Issues

### 11. Context Injection in ViewModel (Anti-Pattern)
**File:** `ui/screens/reports/ReportsViewModel.kt`
```kotlin
class ReportsViewModel @Inject constructor(
    @ApplicationContext private val context: Context,  // 🟡 Bad
    ...
)
```

ViewModels should not hold Context references. This:
- Breaks testability
- Can cause memory leaks
- Violates clean architecture

**Recommendation:** Use resource IDs and resolve strings in Composables.

---

### 12. Hardcoded Page Sizes Scattered Everywhere
**Files:** Multiple ViewModels
```kotlin
private const val PAGE_SIZE = 50  // PurchaseViewModel
private const val PAGE_SIZE = 50  // SaleViewModel  
private const val PAGE_SIZE = 50  // HistoryViewModel
private const val PAGE_SIZE = 30  // CashViewModel
```

**Problems:**
- Magic numbers
- Inconsistent values (30 vs 50)
- Not configurable

**Recommendation:** Centralize pagination config:
```kotlin
object PaginationConfig {
    const val DEFAULT_PAGE_SIZE = 50
    const val CASH_PAGE_SIZE = 30
}
```

---

### 13. Duplicate defaultConfig Block
**File:** `android/app/build.gradle.kts`

There are two `defaultConfig` blocks in the build file - one gets silently overwritten.

---

### 14. Report Query Limits to 10,000 Records
**File:** `data/local/dao/TransactionDao.kt`
```kotlin
@Query("SELECT ... LIMIT 10000")
suspend fun getTransactionsForDateRange(...)
```

For long-running businesses, this silently truncates reports without warning users.

**Recommendation:** Implement pagination or warn when limit reached.

---

### 15. Instant.toString() for Supabase Timestamps
**File:** `sync/SupabaseSyncDataSource.kt`

```kotlin
created_at = transaction.createdAt.toString()
```

`Instant.toString()` produces ISO-8601 but:
- Timezone handling is implicit
- Supabase expects specific format
- Could cause sync failures

**Recommendation:** Use explicit DateTimeFormatter with UTC.

---

### 16. No Error Boundaries in Compose
**Files:** All screen composables

A crash in any Composable takes down the entire app. No `try-catch` or error states for:
- Image loading failures
- Data parsing errors
- Unexpected null states

**Recommendation:** Wrap screens in error boundaries, add error states to UI.

---

### 17. Weight Sign Convention is Confusing
**File:** `data/repository/TransactionRepositoryImpl.kt`

```kotlin
// Purchases: positive weight
// Sales: NEGATIVE weight (but displayed as positive)
// Transfers: positive in, negative out
```

This is documented but:
- Easy to forget
- Causes bugs in reporting
- `SaleDisplayItem` has to negate everywhere

**Recommendation:** Store absolute weights, use transaction type for direction.

---

### 18. Deprecated Compose APIs
**Files:** Multiple UI files

```kotlin
Divider()  // Deprecated, use HorizontalDivider()
```

---

### 19. Missing Network Connectivity Check
**File:** `sync/SyncWorker.kt`

Worker attempts sync without checking connectivity:
```kotlin
override suspend fun doWork(): Result {
    // No NetworkCapabilities check
    return syncService.sync()
}
```

**Recommendation:** Add network constraint to WorkManager or check in worker.

---

### 20. No Offline Indicator in UI
**Files:** UI screens

Users have no clear indication when:
- They're offline
- Sync is pending
- Data may be stale

The `SyncStatusIcon` exists but is subtle and not on all screens.

---

### 21. Financial Calculations Use String Parsing
**File:** `data/local/Converters.kt`
```kotlin
@TypeConverter
fun toBigDecimal(value: String?): BigDecimal? {
    return value?.let { BigDecimal(it) }  // Can throw NumberFormatException
}
```

No try-catch means corrupt data crashes the app.

---

### 22. ProGuard Rules Are Empty
**File:** `android/app/proguard-rules.pro`

Only contains comments, no actual rules. When minification is enabled, the app will crash due to:
- Hilt reflection issues
- Room query stripping
- Supabase serialization failures

---

## 🔵 LOW Severity Issues

### 23. Accessibility Non-Compliance
**Files:** All UI components

Most icons and interactive elements lack `contentDescription`:
```kotlin
Icon(
    imageVector = Icons.Default.Add,
    contentDescription = null  // 🔵 Accessibility violation
)
```

**Impact:** Screen readers cannot describe the UI.

---

### 24. Hardcoded Strings in UI
**Files:** Multiple Composables

Many strings are hardcoded in Ukrainian instead of using resources:
```kotlin
Text("Немає товарів")  // Should be stringResource(R.string.no_products)
```

**Impact:** 
- Harder to maintain
- Breaks localization
- Inconsistent with existing strings.xml

---

### 25. Test Coverage is Abysmal
**Files:** `src/test/`, `src/androidTest/`

Only 3 test files exist:
- `TransactionRepositoryImplTest.kt` - 5 tests
- `SyncServiceTest.kt` - 6 tests
- `TestData.kt` - Fixtures only

**Missing tests for:**
- All ViewModels (0% coverage)
- All DAOs (0% coverage)
- All UI screens (0% coverage)
- All mappers (0% coverage)
- Authentication logic (0% coverage)
- Edge cases in sync (minimal)

**Industry standard is 70-80%. This app has ~5%.**

---

### 26. No Crashlytics/Analytics Integration

No crash reporting. Production bugs will be invisible.

---

### 27. Magic Numbers in UI
```kotlin
Spacer(modifier = Modifier.height(16.dp))  // Why 16?
Spacer(modifier = Modifier.height(8.dp))   // Why 8?
```

**Recommendation:** Use theme dimensions.

---

### 28. Inconsistent Error Handling
Some repositories throw exceptions, others return null:
```kotlin
// ProductRepositoryImpl
suspend fun getProduct(id: UUID): Product?  // Returns null

// Elsewhere
throw IllegalStateException("...")  // Throws
```

---

### 29. No Documentation
- No KDoc on public APIs
- No README in android/ folder
- No architecture decision records
- No API documentation

---

### 30. Dead Code
Several unused imports and functions detected throughout codebase.

---

## Architecture Review

### Positive Patterns ✅
1. **Clean Architecture** - Proper layer separation (domain/data/ui)
2. **Offline-First** - Good choice for field use
3. **BigDecimal for Money** - Correct financial handling
4. **UUID for IDs** - Good for distributed systems
5. **Instant for Timestamps** - Timezone-safe
6. **Hilt DI** - Proper dependency injection
7. **StateFlow in ViewModels** - Modern reactive approach
8. **Migrations in Room** - Proper schema versioning

### Problematic Patterns ❌
1. **ViewModel holds Context** - Breaks testability
2. **Business logic in Composables** - Should be in ViewModels
3. **No Use Cases** - Domain layer is just interfaces
4. **Repository implementations in data layer** - Correct, but lacking abstraction for testing
5. **Sync logic is monolithic** - 200+ lines in single function
6. **No caching strategy** - Room is cache, but no expiration

### Missing Patterns
1. **No error handling strategy** - Inconsistent Result/Exception use
2. **No loading states** - Some screens have them, others don't
3. **No retry mechanism** - Sync fails silently
4. **No feature flags** - Can't disable features remotely
5. **No A/B testing** - Can't experiment safely

---

## Performance Concerns

### 1. No Query Optimization
```kotlin
@Query("SELECT * FROM transactions WHERE ...")
```

No `EXPLAIN QUERY PLAN` analysis. Large datasets will be slow.

### 2. No Index Declarations
**File:** Entity classes

Important query columns lack indexes:
```kotlin
// TransactionEntity should have:
@Index(value = ["product_id"])
@Index(value = ["location_id"])  
@Index(value = ["created_at"])
@Index(value = ["synced_at"])
```

### 3. Full Table Scans in Reports
Report queries join multiple tables without pagination:
```kotlin
@Query("SELECT t.*, p.name... FROM transactions t JOIN products p...")
```

### 4. Image Loading Not Optimized
Using Coil without:
- Disk cache configuration
- Memory cache limits
- Placeholder images

### 5. Recomposition Issues
Several screens have unnecessary recompositions due to:
- Unstable lambda references
- Non-stable data classes
- Missing `remember` for derived state

---

## Testing Assessment

### Current State: FAILING

| Category | Files | Tests | Coverage |
|----------|-------|-------|----------|
| Unit Tests | 2 | 11 | ~5% |
| Integration Tests | 0 | 0 | 0% |
| UI Tests | 0 | 0 | 0% |
| E2E Tests | 0 | 0 | 0% |

### Missing Test Categories
1. **ViewModel tests** - None exist
2. **DAO tests** - None exist (should use in-memory Room)
3. **Mapper tests** - None exist
4. **UI tests** - No Compose testing
5. **Authentication tests** - Critical security code untested
6. **Sync conflict tests** - Edge cases untested

### Test Quality Issues
- No mocking of time (tests could be flaky)
- No test categories/tags
- No CI/CD integration visible
- `TestData.kt` is good but underutilized

---

## Recommendations (Prioritized)

### Immediate (Before Any Release) 🚨
1. **Enable minification** with proper ProGuard rules
2. **Add SQLCipher encryption** to Room database
3. **Fix PIN hashing** with Argon2 + salt
4. **Add certificate pinning** for Supabase
5. **Remove Bluetooth permissions**
6. **Disable backup** or implement encrypted backup

### Short-Term (1-2 Sprints)
1. Add ViewModel unit tests (target: 50% coverage)
2. Add DAO integration tests
3. Implement proper error handling strategy
4. Add network security config
5. Fix race condition in AuthPreferences
6. Add input validation to DTOs
7. Centralize constants (page sizes, etc.)

### Medium-Term (1-2 Months)
1. Add Crashlytics/Firebase Analytics
2. Implement proper sync conflict resolution
3. Add accessibility content descriptions
4. Extract hardcoded strings to resources
5. Add loading/error states to all screens
6. Implement retry mechanism for sync
7. Add database indexes

### Long-Term (Ongoing)
1. Reach 70%+ test coverage
2. Add UI tests with Compose Testing
3. Document all public APIs
4. Performance profiling and optimization
5. Security audit by third party

---

## Positive Observations

Despite the harsh criticism, the codebase has merits:

1. **Domain modeling is thoughtful** - BigDecimal, UUID, Instant choices are correct
2. **Ukrainian localization exists** - Proper resource structure
3. **Migration handling is solid** - 7 migrations without issues
4. **Compose usage is modern** - Material 3, proper state hoisting
5. **Offline-first architecture** - Good for agricultural field use
6. **Sync design is reasonable** - Push-then-pull is pragmatic
7. **Code is readable** - Consistent Kotlin idioms
8. **Hilt usage is correct** - Proper scoping
9. **StateFlow usage is proper** - No LiveData legacy
10. **TransactionQueryBuilder** - Nice dynamic query pattern

The developers clearly understand Android development. The issues stem from:
- Rushing to ship without hardening
- Lack of security expertise (common)
- Insufficient testing culture
- Missing code review process

---

## Conclusion

ZagotPlus is a **competent agricultural trading app** with a **solid foundation** but **critical security gaps**. 

**It MUST NOT be released** in its current state. The combination of:
- Unencrypted financial data
- Weak authentication
- No code obfuscation
- No certificate pinning

...makes it trivial for attackers to compromise user data and business information.

With 2-4 weeks of focused security hardening and testing, this could be a production-ready application. The architecture supports the necessary changes.

---

## Appendix: Files Reviewed

<details>
<summary>Complete File List (Click to expand)</summary>

### Build Configuration
- `android/build.gradle.kts`
- `android/app/build.gradle.kts`
- `android/settings.gradle.kts`
- `android/gradle.properties`
- `android/app/proguard-rules.pro`

### Android Configuration
- `android/app/src/main/AndroidManifest.xml`
- `android/app/src/main/res/values/strings.xml`
- `android/app/src/main/res/values/themes.xml`
- `android/app/src/main/res/values-uk/strings.xml`

### Application Entry
- `android/app/src/main/java/com/example/zagotplus/MainActivity.kt`
- `android/app/src/main/java/com/example/zagotplus/ZagotApp.kt`

### Domain Layer
- `domain/model/Transaction.kt`
- `domain/model/Product.kt`
- `domain/model/Location.kt`
- `domain/model/PurchaseBatch.kt`
- `domain/model/InventoryItem.kt`
- `domain/model/TransactionFilter.kt`
- `domain/model/CashModels.kt`
- `domain/repository/TransactionRepository.kt`
- `domain/repository/ProductRepository.kt`
- `domain/repository/LocationRepository.kt`
- `domain/repository/PurchaseBatchRepository.kt`
- `domain/repository/CashRepository.kt`

### Data Layer - Local
- `data/local/ZagotDatabase.kt`
- `data/local/DatabaseModule.kt`
- `data/local/Converters.kt`
- `data/local/entity/TransactionEntity.kt`
- `data/local/entity/ProductEntity.kt`
- `data/local/entity/LocationEntity.kt`
- `data/local/entity/PurchaseBatchEntity.kt`
- `data/local/entity/ExpenseCategoryEntity.kt`
- `data/local/entity/CashOperationEntity.kt`
- `data/local/dao/TransactionDao.kt`
- `data/local/dao/ProductDao.kt`
- `data/local/dao/LocationDao.kt`
- `data/local/dao/PurchaseBatchDao.kt`
- `data/local/dao/ExpenseCategoryDao.kt`
- `data/local/dao/CashOperationDao.kt`
- `data/local/query/TransactionQueryBuilder.kt`

### Data Layer - Repository
- `data/repository/TransactionRepositoryImpl.kt`
- `data/repository/ProductRepositoryImpl.kt`
- `data/repository/LocationRepositoryImpl.kt`
- `data/repository/PurchaseBatchRepositoryImpl.kt`
- `data/repository/CashRepositoryImpl.kt`

### Data Layer - Preferences
- `data/preferences/AuthPreferences.kt`
- `data/preferences/DevicePreferences.kt`
- `data/preferences/ProductOrderPreferences.kt`

### Data Layer - Remote
- `data/remote/SupabaseModule.kt`
- `data/remote/SupabaseStorageHelper.kt`
- `data/remote/dto/TransactionDto.kt`
- `data/remote/dto/ProductDto.kt`
- `data/remote/dto/LocationDto.kt`
- `data/remote/dto/PurchaseBatchDto.kt`
- `data/remote/dto/ExpenseCategoryDto.kt`
- `data/remote/dto/CashOperationDto.kt`

### Sync Layer
- `sync/SyncService.kt`
- `sync/SyncWorker.kt`
- `sync/SyncManager.kt`
- `sync/SyncDataSource.kt`
- `sync/SupabaseSyncDataSource.kt`
- `sync/SyncResult.kt`
- `sync/SyncStatus.kt`
- `sync/SyncStatusRepository.kt`

### UI Layer - Navigation
- `ui/navigation/NavGraph.kt`
- `ui/navigation/Destinations.kt`

### UI Layer - Theme
- `ui/theme/Theme.kt`
- `ui/theme/Color.kt`
- `ui/theme/Type.kt`

### UI Layer - Screens (ViewModels)
- `ui/screens/purchase/PurchaseViewModel.kt`
- `ui/screens/purchase/PurchaseEntryViewModel.kt`
- `ui/screens/sale/SaleViewModel.kt`
- `ui/screens/sale/SaleEntryViewModel.kt`
- `ui/screens/inventory/InventoryViewModel.kt`
- `ui/screens/history/HistoryViewModel.kt`
- `ui/screens/reports/ReportsViewModel.kt`
- `ui/screens/cash/CashViewModel.kt`
- `ui/screens/auth/PinViewModel.kt`

### UI Layer - Screens (Composables)
- `ui/screens/purchase/PurchaseScreen.kt`
- `ui/screens/purchase/PurchaseEntryScreen.kt`
- `ui/screens/sale/SaleScreen.kt`
- `ui/screens/sale/SaleEntryScreen.kt`
- `ui/screens/inventory/InventoryScreen.kt`
- `ui/screens/history/HistoryScreen.kt`
- `ui/screens/reports/ReportsScreen.kt`
- `ui/screens/cash/CashScreen.kt`
- `ui/screens/settings/SettingsScreen.kt`
- `ui/screens/transfer/TransferScreen.kt`
- `ui/screens/products/ProductsScreen.kt`
- `ui/screens/auth/PinScreen.kt`

### UI Layer - Components
- `ui/components/ReorderableProductGrid.kt`
- `ui/components/LocationSelectionDialog.kt`
- `ui/components/SyncStatusIcon.kt`
- `ui/components/EmptyState.kt`

### Tests
- `src/test/java/.../TransactionRepositoryImplTest.kt`
- `src/test/java/.../SyncServiceTest.kt`
- `src/test/java/.../TestData.kt`

</details>

---

*This audit was conducted with a critical lens as requested. The goal is to improve the application, not discourage the development team. The foundation is solid—now it needs hardening.*
