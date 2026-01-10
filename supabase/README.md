# Supabase Configuration

Database backend for Zagot+ inventory and sync system.

## Setup

### 1. Create Supabase Project

1. Go to [supabase.com](https://supabase.com)
2. Create new project
3. Choose a name (e.g., "zagot-plus")
4. Set database password (save securely!)
5. Select region closest to Ukraine (Europe West recommended)

### 2. Run Migration

Option A: **Supabase Dashboard (Recommended)**
1. Open project in Supabase dashboard
2. Go to SQL Editor
3. Copy contents of `migrations/20260111000000_initial_schema.sql`
4. Paste and run

Option B: **Supabase CLI**
```bash
# Install Supabase CLI
npm install -g supabase

# Link to your project
supabase link --project-ref <your-project-ref>

# Run migrations
supabase db push
```

### 3. Get Connection Details

From Supabase Dashboard → Settings → API:

- **Project URL**: `https://<project-ref>.supabase.co`
- **Anon (public) key**: `eyJ...` (long string)

### 4. Configure Android App

Add to `android/local.properties`:
```properties
supabase.url=https://<project-ref>.supabase.co
supabase.key=<anon-key>
```

**Important**: Never commit `local.properties` or hardcode keys in source!

## Schema Overview

### Tables

- **locations** - Physical locations (kiosk, mobile)
- **products** - Product catalog (nuts/seeds with pricing)
- **transactions** - Append-only ledger of all operations

### Views

- **inventory** - Real-time stock levels (computed from transactions)

### Transaction Types

| Type | Effect | Use Case |
|------|--------|----------|
| `purchase` | +inventory | Buy from population |
| `sale` | -inventory | Sell to buyer |
| `transfer_out` | -inventory | Send to another location |
| `transfer_in` | +inventory | Receive from another location |

## Security

### Row Level Security (RLS)

All tables have RLS enabled with permissive policies for anon key access.

**Current policy**: Allow all operations (single organization, trusted devices)

**Future**: Consider restricting by `device_id` or `location_id` for production

### API Keys

- **Anon key**: Client-side use, RLS enforced
- **Service role key**: Server-side only, bypasses RLS (not used in this project)

## Sync Strategy

### Conflict-Free Design

- Transactions are **append-only** (never edited)
- `local_id` (UUID) ensures **deduplication**
- Negative inventory is **allowed** (business decision)

### How Sync Works

1. **Push**: Device uploads transactions with `synced_at = null`
2. **Upsert**: Supabase ignores duplicates via `local_id` UNIQUE constraint
3. **Pull**: Device fetches transactions created after last sync timestamp
4. **Realtime** (optional): Subscribe to live updates from other devices

## Monitoring

Check Supabase Dashboard for:
- **Database size** (500MB free tier limit)
- **API requests** (monitor usage)
- **Query performance** (check slow queries)

## Testing

### Verify Schema

Run in Supabase SQL Editor:

```sql
-- Check tables exist
select table_name from information_schema.tables
where table_schema = 'public';

-- Check RLS enabled
select tablename, rowsecurity from pg_tables
where schemaname = 'public';

-- Test inventory view
select * from inventory;
```

### Insert Test Data

Test transactions already seeded in migration:
- 2 locations (Кіоск, Мобільний)
- 4 products (white/black walnuts, white/black seeds)

Create a test transaction:

```sql
insert into transactions (local_id, location_id, type, product_id, weight_kg, price_per_kg, total_amount, device_id)
values (
  gen_random_uuid()::text,
  '00000000-0000-0000-0000-000000000001',
  'purchase',
  '00000000-0000-0000-0000-000000000011',
  10.5,
  45.00,
  472.50,
  'test-device'
);

-- Check inventory updated
select * from inventory;
```

## Troubleshooting

**Migration fails**: Check if tables already exist (drop and re-run if needed)

**RLS blocks access**: Ensure using anon key, check policies in Dashboard → Authentication → Policies

**Slow queries**: Add indexes (already included in migration)

**Sync fails**: Check device has internet, verify API key is correct

## Next Steps

1. Configure Android Supabase client (Phase 1)
2. Implement sync service with WorkManager (Phase 1)
3. Add Realtime subscriptions for live updates (Phase 1)
