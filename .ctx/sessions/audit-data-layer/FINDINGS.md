# FINDINGS: audit-data-layer

Audit Date: 2026-01-19
Status: complete
Audited Files: ~40

## Summary

Data layer audit complete. Found **1 critical** issue (DTO precision loss), **3 high** priority issues, and **1 medium** issue.

---

## 🔴 CRITICAL Issues

### 1. DTO Decimal Precision Loss
**Location:** All DTOs in `data/remote/dto/`
**Files:** `TransactionDto.kt`, `PurchaseBatchDto.kt`, `SaleBatchDto.kt`, `ProductDto.kt`, `CashOperationDto.kt`

**Issue:** All DTOs use `Double` for decimal fields (weightKg, pricePerKg, totalAmount, etc.) instead of `String`. This causes precision loss during sync.

**Impact:**
- Entity uses `BigDecimal` for exact decimal storage
- Converting BigDecimal → Double → BigDecimal loses precision
- Financial calculations can have rounding errors
- Supabase uses `numeric(10,2)` (exact decimal) but JSON transmission uses floating-point

**Example:**
```kotlin
// TransactionDto.kt:36-38
val weightKg: Double,
val pricePerKg: Double?,
val totalAmount: Double?,

// TransactionDto.kt:99-101 (fromEntity)
weightKg = entity.weightKg.toDouble(),  // Precision loss here!
```

**Fix:** Change all decimal DTO fields from `Double` to `String`, use `BigDecimal(string)` and `.toPlainString()` for conversion.

**History Note:** Journal entry mentions "fix(sync): use String instead of Double for decimal values in DTOs" (455bd5e) was committed, but the actual code still uses Double. Either the fix was reverted or never applied correctly.

---

## 🟠 HIGH Priority Issues

### 2. LocationDao Missing Sync Methods
**Location:** `data/local/dao/LocationDao.kt`

**Issue:** LocationDao lacks sync infrastructure that other DAOs have:
- Missing `getUnsynced()`
- Missing `markSynced(id, syncedAt)`
- Missing `getAllLocalIds()`
- Missing `getByLocalId(localId)`

**Impact:**
- Locations created on device cannot be synced to Supabase
- LocationEntity has sync columns (local_id, synced_at, device_id) but DAO doesn't use them

**Note:** Initial Supabase schema says "locations are not created/modified on devices, only pulled from server" - but the entity has sync columns, suggesting this may have changed.

**Fix:** Add sync methods to LocationDao if local location creation is needed, or remove sync columns from LocationEntity if not.

---

### 3. CashRepositoryImpl.transfer() Not Atomic
**Location:** `data/repository/CashRepositoryImpl.kt:206-248`

**Issue:** The `transfer()` method inserts two cash operations (withdrawal + deposit) with separate `insert()` calls, not wrapped in `withTransaction`.

**Impact:**
- If app crashes or DB error occurs between inserts, only one side of transfer will be saved
- Cash balance will be incorrect (money withdrawn but not deposited, or vice versa)

**Code:**
```kotlin
// CashRepositoryImpl.kt:245-248
cashOperationDao.insert(withdrawalEntity)  // First insert
cashOperationDao.insert(depositEntity)     // Second insert - NOT atomic!
```

**Fix:** Wrap both inserts in `database.withTransaction { ... }`:
```kotlin
database.withTransaction {
    cashOperationDao.insert(withdrawalEntity)
    cashOperationDao.insert(depositEntity)
}
```

---

### 4. Converters Missing Error Handling
**Location:** `data/local/converter/Converters.kt`

**Issue:** TypeConverters don't catch exceptions for malformed data:
- `UUID.fromString(it)` throws `IllegalArgumentException` for invalid UUIDs
- `BigDecimal(it)` throws `NumberFormatException` for invalid numbers

**Impact:**
- Corrupted database data could crash the app on read
- Sync of malformed data from server could cause crashes

**Code:**
```kotlin
// Converters.kt:23
fun toUUID(string: String?): UUID? {
    return string?.let { UUID.fromString(it) }  // Can throw!
}

// Converters.kt:45
fun toBigDecimal(string: String?): BigDecimal? {
    return string?.let { BigDecimal(it) }  // Can throw!
}
```

**Fix:** Add try-catch blocks returning null for invalid data:
```kotlin
fun toUUID(string: String?): UUID? = try {
    string?.let { UUID.fromString(it) }
} catch (e: IllegalArgumentException) {
    null
}
```

---

## 🟡 MEDIUM Priority Issues

### 5. AuthPreferences Lockout Clock Bypass
**Location:** `data/preferences/AuthPreferencesImpl.kt:168-170`

**Issue:** Lockout uses `System.currentTimeMillis()` which can be bypassed by changing device clock backward.

**Impact:**
- User can bypass PIN lockout by setting device time back
- Security measure defeated

**Code:**
```kotlin
override fun isLockedOut(): Boolean {
    val lockoutUntil = prefs.getLong(KEY_LOCKOUT_UNTIL, 0)
    return lockoutUntil > System.currentTimeMillis()  // Clock-based
}
```

**Fix:** Use `SystemClock.elapsedRealtime()` for tamper-resistant timing (already mentioned in history but not implemented for lockout checking).

---

## ⚪ LOW Priority Issues

None identified.

---

## Verification Checklist

Based on plan.md session checklist:

| Check | Status | Notes |
|-------|--------|-------|
| Room schema version is correct | ✅ | Version 13, matches migrations |
| All migrations are idempotent | ✅ | Use `IF NOT EXISTS`, `IF EXISTS` |
| Type converters handle edge cases | ⚠️ | Missing try-catch for malformed data |
| DAOs handle concurrent access | ✅ | Room handles thread safety |
| DTOs match Supabase schema | 🔴 | Type mismatch (Double vs numeric) |
| Repository error handling | ⚠️ | transfer() not atomic |
| Preferences handle corruption | ✅ | DevicePreferences has try-catch |

---

## Recommendations

### Immediate (Before Release)
1. **Fix DTO precision loss** - Change Double to String for all decimal fields
2. **Make transfer() atomic** - Wrap in withTransaction

### Should Fix Soon
3. **Add error handling to Converters** - Prevent crashes from corrupted data
4. **Clarify LocationDao sync** - Either add sync methods or remove unused columns

### Can Fix Later
5. **Fix lockout clock bypass** - Use elapsedRealtime() instead of currentTimeMillis()

---

## Files Audited

### Room Database
- [x] ZagotDatabase.kt - Version 13, 7 entities, 13 migrations
- [x] Converters.kt - UUID, Instant, BigDecimal converters
- [x] DatabaseModule.kt - Hilt singleton provider

### DAOs
- [x] TransactionDao.kt - Comprehensive, voided batch exclusion
- [x] PurchaseBatchDao.kt - Void/correction support
- [x] SaleBatchDao.kt - Mirrors PurchaseBatchDao
- [x] CashDao.kt - Complex queries, optimized
- [x] LocationDao.kt - **Missing sync methods**
- [x] ProductDao.kt - Complete with sync

### Entities
- [x] TransactionEntity.kt - Proper FKs and indices
- [x] PurchaseBatchEntity.kt - Voiding support
- [x] SaleBatchEntity.kt - Mirrors PurchaseBatchEntity
- [x] LocationEntity.kt - Has unused sync columns
- [x] ProductEntity.kt - Complete
- [x] CashEntity.kt - ExpenseCategory + CashOperation

### DTOs
- [x] TransactionDto.kt - **Double precision issue**
- [x] PurchaseBatchDto.kt - **Double precision issue**
- [x] SaleBatchDto.kt - **Double precision issue**
- [x] ProductDto.kt - **Double precision issue**
- [x] CashOperationDto.kt - **Double precision issue**
- [x] LocationDto.kt - OK

### Repositories
- [x] PurchaseBatchRepositoryImpl.kt - Good atomic operations
- [x] SaleBatchRepositoryImpl.kt - OK
- [x] CashRepositoryImpl.kt - **transfer() not atomic**
- [x] TransactionRepositoryImpl.kt - OK
- [x] ProductRepositoryImpl.kt - OK
- [x] LocationRepositoryImpl.kt - OK

### Preferences
- [x] AuthPreferencesImpl.kt - PBKDF2, **clock bypass possible**
- [x] DevicePreferences.kt - EncryptedSharedPreferences, secure
