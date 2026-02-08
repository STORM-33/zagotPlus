# Session Brief: audit-build-and-dependencies

## Type
Audit

## Complexity
Medium

## Goal
Audit build configuration, dependencies, and security settings for production readiness.

## Files to Audit
- `android/build.gradle.kts` - Root build config
- `android/gradle/libs.versions.toml` - Dependency versions
- `android/app/build.gradle.kts` - App build config
- `android/app/proguard-rules.pro` - ProGuard rules
- `android/app/src/main/AndroidManifest.xml` - Permissions

## Checklist
- [ ] All dependencies are up-to-date (no known vulnerabilities)
- [ ] ProGuard rules are configured (isMinifyEnabled = false noted!)
- [ ] Signing config is ready for release
- [ ] Version code/name are set
- [ ] targetSdk is current (34)
- [ ] minSdk is appropriate (26)
- [ ] Test configurations are correct
- [ ] No debug code in release build
- [ ] Supabase credentials not in source control
- [ ] BuildConfig doesn't leak secrets
- [ ] INTERNET permission is declared
- [ ] No unnecessary permissions
