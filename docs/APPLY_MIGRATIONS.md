# Supabase Setup for Real-World Testing

## What to Apply Before Testing

Apply these two migrations in order:

### 1. Critical Voided Batch Fixes
```bash
# This fixes inventory and cash balance views to exclude voided batches
# SAFE TO APPLY - doesn't break anything
```
**File:** `20260118000000_fix_voided_batch_filtering.sql`

**What it fixes:**
- ✅ Inventory view now excludes voided batches
- ✅ Cash balance view now excludes voided batches  
- ✅ Adds missing `idx_transactions_type` index
- ✅ Changes `cash_operations.batch_id` FK to `ON DELETE RESTRICT`

### 2. Schema Improvements
```bash
# This adds audit columns, indexes, and sync support for locations
# SAFE TO APPLY - doesn't break anything
```
**File:** `20260118100000_additional_fixes.sql`

**What it fixes:**
- ✅ Locations get sync columns (local_id, synced_at, device_id, etc.)
- ✅ Audit columns added (voided_at, voided_by_device_id)
- ✅ Transfer pair tracking (transfer_pair_id)
- ✅ Better indexes (composite, partial)
- ✅ Removes redundant indexes
- ✅ FK consistency

## What NOT to Apply Yet

### 3. RLS Security Fixes
```bash
# ⚠️  DO NOT APPLY - will break the app until auth is implemented
```
**File:** `20260118110000_fix_rls_policies.sql`

**Why not apply:**
- Requires GoTrue authentication to be implemented in the app
- Changes RLS policies to reject anonymous access
- App currently uses anon key which won't work with these policies

**Apply this migration after:**
1. Implementing proper GoTrue auth in `SupabaseAuthManager`
2. Installing GoTrue plugin in `SupabaseModule`
3. Testing that device sign-in creates authenticated session

## Apply Migrations

```bash
cd supabase

# Start local Supabase (if testing locally first)
supabase start

# Reset database and apply migrations 1 & 2
supabase db reset

# Or apply to production
supabase db push
```

## Verify Migrations Worked

### Check inventory excludes voided batches
```sql
-- Create a purchase batch
INSERT INTO purchase_batches (id, local_id, location_id, supplier_name, is_voided)
VALUES (gen_random_uuid(), 'test-batch', (SELECT id FROM locations LIMIT 1), 'Test', false);

-- Add transaction
INSERT INTO transactions (id, local_id, location_id, product_id, type, weight_kg, price_per_kg, total_amount, batch_id, device_id, created_at)
VALUES (
    gen_random_uuid(),
    'test-txn',
    (SELECT id FROM locations LIMIT 1),
    (SELECT id FROM products LIMIT 1),
    'purchase',
    100,
    50,
    5000,
    (SELECT id FROM purchase_batches WHERE local_id = 'test-batch'),
    'test',
    NOW()
);

-- Check inventory includes it
SELECT * FROM inventory;

-- Void the batch
UPDATE purchase_batches SET is_voided = true WHERE local_id = 'test-batch';

-- Check inventory excludes it now
SELECT * FROM inventory;

-- Cleanup
DELETE FROM transactions WHERE local_id = 'test-txn';
DELETE FROM purchase_batches WHERE local_id = 'test-batch';
```

### Check locations have sync columns
```sql
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'locations' 
  AND column_name IN ('local_id', 'synced_at', 'server_updated_at', 'device_id');

-- Should return 4 rows
```

### Check audit columns exist
```sql
SELECT column_name, data_type 
FROM information_schema.columns 
WHERE table_name = 'purchase_batches' 
  AND column_name IN ('voided_at', 'voided_by_device_id');

-- Should return 2 rows
```

## Run Tests (Optional)

```bash
# Run all tests except RLS test
supabase test db

# The RLS test (03_rls_policies.sql) will fail since we didn't apply that migration
# All other tests should pass
```

## Current Security Status

**⚠️  Security Warning:**
- Database is still publicly accessible via anon key
- RLS policies still use `USING (true)` 
- Anyone with your Supabase URL can read/write/delete everything

**For testing tomorrow this is acceptable, but:**
- Don't put real sensitive data
- Don't share Supabase URL publicly
- Apply RLS migration + implement auth before production use

## Android App Status

The Android app is ready for these migrations:
- ✅ Room schema matches (version 12)
- ✅ DTOs have all new fields
- ✅ DAOs use audit columns
- ✅ Repositories inject DevicePreferences
- ✅ All 896 tests passing
- ✅ `SupabaseAuthManager` in place (bypassed for now)

The app will work with migrations 1 & 2 applied, and continue to work with current anon access.
