# History Index

Last updated: 2026-01-11

## Plans

| Plan | Date | Sessions | Status |
|------|------|----------|--------|
| Phase 0: Setup | 2026-01-11 | 3 | completed |

## Decisions Log

| Date | Plan | Decision | Reasoning | Session |
|------|------|----------|-----------|---------|
| 2026-01-11 | Phase 0 | AGP 8.3.1 (not 8.2.0) | JDK 21 compatibility requires AGP 8.3+ | init-android |
| 2026-01-11 | Phase 0 | Gradle 8.4 (not 8.2) | Required by AGP 8.3.1 | init-android |
| 2026-01-11 | Phase 0 | Single migration file | Atomicity and simplicity over multiple files | init-supabase |
| 2026-01-11 | Phase 0 | Fixed UUIDs for seed data | Consistency across environments | init-supabase |
| 2026-01-11 | Phase 0 | Permissive RLS for anon | Single-org use case; can tighten later | init-supabase |
| 2026-01-11 | Phase 0 | Defer launcher icons | Will add in Phase 4 polish | init-android |

## Lessons Learned

| Date | Plan | Lesson | Context |
|------|------|--------|---------|
| 2026-01-11 | Phase 0 | JDK 21 requires AGP 8.3+ and Gradle 8.4+ | Windows dev machine with JDK 21 |
| 2026-01-11 | Phase 0 | LF→CRLF warnings are normal on Windows | Git autocrlf setting |
| 2026-01-11 | Phase 0 | Version catalog (libs.versions.toml) keeps deps organized | Modern Gradle pattern |

## Patterns & Solutions

| Problem | Solution | Used In |
|---------|----------|---------|
| Conflict-free sync | UNIQUE constraint on local_id (UUID generated client-side) | init-supabase |
| Computed inventory | Database VIEW summing transactions | init-supabase |
| Hilt setup | @HiltAndroidApp on App class, @AndroidEntryPoint on Activity | init-android |
| Dependency management | Gradle version catalog (libs.versions.toml) | init-android |

## Tags

#android #supabase #hilt #compose #offline-first #jdk21
