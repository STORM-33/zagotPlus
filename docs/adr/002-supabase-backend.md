# ADR-002: Supabase Backend

## Status
Accepted

## Context

We need a backend to store and sync data across devices. Options considered were Firebase, custom backend, and Supabase.

Requirements:
- PostgreSQL compatibility (for complex queries)
- Easy authentication
- File storage for product images
- Affordable at small scale
- Self-hostable in future if needed

## Decision

We chose **Supabase** as the backend platform:

- **Postgrest**: REST API auto-generated from Postgres schema
- **Storage**: S3-compatible storage for product images
- **Auth**: Built-in auth (for future multi-tenant support)
- **Realtime**: Available if needed later

Architecture:
```
Android App
    ↓
Supabase Client SDK (ktor-client-android)
    ↓
Supabase Edge Functions / Postgrest
    ↓
PostgreSQL (with RLS policies)
```

## Consequences

### Positive
- Full Postgres power (complex queries, triggers, functions)
- Open source - can self-host if needed
- Good Kotlin SDK
- Built-in storage solution
- Row Level Security for multi-tenant in future

### Negative
- Less mature than Firebase
- Kotlin SDK updates may lag behind
- Need to manage Postgres schema migrations
- No offline persistence SDK (we use Room locally)

## Alternatives Considered

1. **Firebase Firestore**
   - Pros: Mature, excellent offline support
   - Cons: NoSQL limits complex queries, vendor lock-in
   
2. **Custom Backend (Spring Boot + PostgreSQL)**
   - Pros: Full control
   - Cons: More development time, hosting complexity
   
3. **Appwrite**
   - Pros: Open source, self-hostable
   - Cons: Less mature Kotlin SDK, smaller community
