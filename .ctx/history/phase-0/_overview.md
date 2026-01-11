# Phase 0: Setup

## Purpose

Establish the foundational project structure before any feature development.

## Sessions

| # | Session | Objective | Complexity |
|---|---------|-----------|------------|
| 1 | init-repo | Initialize git, .gitignore, README | low |
| 2 | init-android | Create Android project with Hilt skeleton | medium |
| 3 | init-supabase | Set up Supabase schema and RLS | medium |

## Execution Order

```
init-repo (first)
    ├── init-android (can run after init-repo)
    └── init-supabase (can run after init-repo, parallel with init-android)
```

## Success Criteria

- Git repository initialized with proper .gitignore
- Android project compiles with Hilt configured
- Supabase project has locations, products, transactions tables
- All three devices can connect to Supabase

## Notes

- `.ctx/` structure pre-exists from project initialization
- Sessions 2 and 3 are independent after session 1
