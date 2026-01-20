# Supabase Database Tests

Tests for database schema, migrations, and business logic using pgTAP.

## Prerequisites

1. Supabase CLI installed
2. Local Supabase instance running (`supabase start`)
3. Migrations applied (`supabase db reset`)

## Running Tests

### Option 1: Using Supabase CLI (recommended)

```bash
# Start local Supabase
supabase start

# Reset database with migrations
supabase db reset

# Run tests
supabase test db
```

### Option 2: Direct psql connection

```bash
# Connect to local database
psql postgresql://postgres:postgres@localhost:54322/postgres

# Run setup first
\i tests/00_setup.sql

# Run individual test files
\i tests/01_inventory_voided_filtering.sql
\i tests/02_cash_balance_voided_filtering.sql
\i tests/03_rls_policies.sql
\i tests/04_locations_sync.sql
\i tests/05_audit_and_transfers.sql
\i tests/06_indexes.sql
\i tests/07_fk_constraints.sql
```

## Test Files

| File | Description | Status |
|------|-------------|--------|
| `00_setup.sql` | Test helpers and setup functions | ✅ Run |
| `01_inventory_voided_filtering.sql` | Tests inventory view excludes voided batches | ✅ Run |
| `02_cash_balance_voided_filtering.sql` | Tests cash_balance view excludes voided batches | ✅ Run |
| `03_rls_policies.sql` | Tests RLS policies don't allow anon access | ⚠️  Skip (RLS migration not applied) |
| `04_locations_sync.sql` | Tests locations table sync columns and trigger | ✅ Run |
| `05_audit_and_transfers.sql` | Tests audit columns and transfer_pair_id | ✅ Run |
| `06_indexes.sql` | Tests required indexes exist | ✅ Run |
| `07_fk_constraints.sql` | Tests ON DELETE RESTRICT behavior | ✅ Run |

**Note:** Test `03_rls_policies.sql` should be skipped until migration `20260118110000_fix_rls_policies.sql` is applied.

## Test Helpers

The setup file provides helper functions:

- `test_create_location(name)` - Creates a test location
- `test_create_product(name)` - Creates a test product
- `test_create_purchase_batch(location_id, product_id, weight, amount)` - Creates purchase with transaction
- `test_create_sale_batch(location_id, product_id, weight, amount)` - Creates sale with transaction
- `test_create_cash_operation(location_id, type, amount, batch_id)` - Creates cash operation
- `test_cleanup()` - Removes all test data

All test data uses `device_id = 'test-device'` for easy cleanup.

## Writing New Tests

```sql
BEGIN;
SELECT plan(N);  -- N = number of tests

-- Your tests here using pgTAP functions:
-- ok(condition, description)
-- is(got, expected, description)
-- has_table(table_name, description)
-- has_column(table, column, description)
-- has_index(table, index_name, description)
-- etc.

SELECT * FROM finish();
ROLLBACK;  -- Always rollback to keep database clean
```
