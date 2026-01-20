# Audit Report: Build & Dependencies

**Session:** phase-3/audit-build-and-dependencies
**Date:** 2026-01-19
**Status:** Complete

---

## Summary

Audited build configuration, dependencies, and security settings. Found **2 critical**, **2 high**, and **3 medium** issues requiring attention.

---

## Findings

### 🔴 CRITICAL

#### 1. Release Build Uses Debug Signing (line 42-43)
**File:** `android/app/build.gradle.kts`
```kotlin
signingConfig = signingConfigs.getByName("debug")
```
**Risk:** APK signed with debug key cannot be uploaded to Play Store. Debug keys expose app to tampering.
**Fix:** Create release keystore and configure proper signing:
```kotlin
signingConfigs {
    create("release") {
        storeFile = file("path/to/keystore.jks")
        storePassword = System.getenv("KEYSTORE_PASSWORD")
        keyAlias = "key-alias"
        keyPassword = System.getenv("KEY_PASSWORD")
    }
}
release {
    signingConfig = signingConfigs.getByName("release")
}
```

#### 2. Supabase Credentials Visible in local.properties
**File:** `android/local.properties` (line 5-6)
```
supabase.url=https://zyfkdxyxmbnocazhnadz.supabase.co
supabase.anon.key=sb_secret_BJ_ngBPfcOnBWTCmA3veng_mVyvB0b-
```
**Risk:** While local.properties is gitignored, this is a real API key visible in local filesystem. The anon key appears to be a service key (not anon) based on prefix `sb_secret_`.
**Fix:** 
1. Verify this is truly an anon key (safe to expose) vs service key (never expose)
2. Consider using environment variables for CI/CD builds
3. Rotate key if this was ever committed to git

---

### 🟠 HIGH

#### 3. ProGuard/R8 Disabled for Release (line 37)
**File:** `android/app/build.gradle.kts`
```kotlin
isMinifyEnabled = false
```
**Risk:** 
- APK size larger than necessary
- No code obfuscation (easy reverse engineering)
- No unused code removal
**Fix:** Enable minification for release builds:
```kotlin
isMinifyEnabled = true
isShrinkResources = true
```
**Note:** ProGuard rules are already properly configured in `proguard-rules.pro`.

#### 4. Certificate Pinning Not Configured
**File:** `android/app/src/main/res/xml/network_security_config.xml` (line 23-33)
```xml
<!-- TODO: Replace 'your-project.supabase.co' with actual Supabase project domain -->
<!-- TODO: Generate actual certificate pins before production deployment -->
```
**Risk:** Without certificate pinning, app is vulnerable to MITM attacks if device has compromised CA.
**Fix:** Before production:
1. Generate SHA-256 pins for Supabase certificate
2. Uncomment and configure the `<domain-config>` section
3. Include backup pin for CA rotation

---

### 🟡 MEDIUM

#### 5. Version Code/Name Still Default
**File:** `android/app/build.gradle.kts` (line 27-28)
```kotlin
versionCode = 1
versionName = "1.0"
```
**Risk:** First release should have proper versioning strategy.
**Recommendation:** Consider semantic versioning and auto-increment for versionCode in CI.

#### 6. Bluetooth Permission Declared But Hardware Required
**File:** `android/app/src/main/AndroidManifest.xml` (line 11-12)
```xml
<uses-feature
    android:name="android.hardware.bluetooth"
    android:required="true" />
```
**Risk:** App cannot be installed on devices without Bluetooth hardware.
**Question:** Is this intentional? If scale/printer are optional, should be `required="false"`.

#### 7. security-crypto Alpha Version
**File:** `android/gradle/libs.versions.toml` (line 33)
```
securityCrypto = "1.1.0-alpha06"
```
**Risk:** Alpha version may have bugs or API changes.
**Status:** This is currently the latest stable-ish release. Monitor for stable release.

---

## Verified OK ✓

| Item | Status |
|------|--------|
| targetSdk = 34 | ✓ Current |
| minSdk = 26 (Android 8.0) | ✓ Appropriate |
| INTERNET permission | ✓ Declared |
| Compose BOM 2024.02.00 | ✓ Recent |
| Kotlin 1.9.21 | ✓ Stable |
| Room 2.6.1 | ✓ Stable |
| Hilt 2.48.1 | ✓ Stable |
| local.properties in .gitignore | ✓ Yes |
| Cleartext traffic disabled | ✓ Yes |
| Auth prefs excluded from backup | ✓ Yes |
| Debug-only tooling scoped correctly | ✓ Yes |
| Test JVM args configured | ✓ Yes |
| ProGuard rules comprehensive | ✓ Yes |

---

## Recommendations (Priority Order)

1. **Before Release:** Create release keystore and configure signing
2. **Before Release:** Enable ProGuard minification (`isMinifyEnabled = true`)
3. **Before Release:** Verify Supabase key type (anon vs service)
4. **Before Release:** Configure certificate pinning for Supabase
5. **Post-Release:** Decide if Bluetooth should be required
6. **Monitor:** security-crypto stable release

---

## Dependencies Security Check

All dependencies were reviewed against known vulnerability databases:
- No known CVEs for current versions
- Supabase 2.0.3, Ktor 2.3.7, Room 2.6.1 are recent stable releases
- Accompanist swiperefresh (0.32.0) is deprecated but still functional

---

## Files Modified
None (audit only)

## Session Stats
- Files reviewed: 8
- Issues found: 7
- Critical: 2
- High: 2
- Medium: 3
