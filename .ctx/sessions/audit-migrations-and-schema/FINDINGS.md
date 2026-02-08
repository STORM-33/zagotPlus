# FINDINGS: audit-migrations-and-schema

Audit Date: 2026-01-19
Status: complete
Audited Files: 17 migrations + 8 test files + schema.sql

## Summary

Migrations and schema audit complete. Found **1 high** priority issue (schema.sql mismatch), **1 medium** issue (RLS policies allow anon). Schema design is solid with proper idempotent migrations, comprehensive indexes, and good test coverage.

---

## 🟠 HIGH Priority Issues

### 1. schema.sql Missing Audit Columns
**Location:** `supabase/schema.sql` vs `supabase/migrations/20260118100000_additional_fixes.sql`

**Issue:** The `voided_at` and `voided_by_device_id` columns are defined in migration `20260118100000_additional_fixes.sql` and tested in `05_audit_and_transfers.sql`, but are NOT present in `schema.sql`.

**Migration has:**
```sql
ALTER TABLE purchase_batches ADD COLUMN IF NOT EXISTS voided_at TIMESTAMPTZ;
ALTER TABLE purchase_batches ADD COLUMN IF NOT EXISTS voided_by_device_id TEXT;
ALTER TABLE sale_batches ADD COLUMN IF NOT EXISTS voided_at TIMESTAMPTZ;
ALTER TABLE sale_batches ADD COLUMN IF NOT EXISTS voided_by_device_id TEXT;
```

**schema.sql has:**
- `purchase_batches`: `is_voided`, `corrects_batch_id`, `correction_reason` ✅
- `purchase_batches`: `voided_at`, `voided_by_device_id` ❌ MISSING
- Same for `sale_batches`

**Impact:**
- Either migration wasn't applied to production, OR
- schema.sql is out of date (dumped before migration)
- Android app expects these columns for audit trail

**Resolution Steps:**
1. Check if production DB has these columns: `\d purchase_batches`
2. If missing, apply migration: `supabase db push`
3. If present, regenerate schema.sql: `supabase db dump`

---

## 🟡 MEDIUM Priority Issues

### 2. RLS Policies Still Allow Anonymous Access
**Location:** `supabase/schema.sql:602-627`

**Issue:** All tables have `"Allow all for anon"` policies active. Migration `20260118110000_fix_rls_policies.sql` exists but is explicitly NOT APPLIED (waiting for auth implementation).

**Current state:**
```sql
CREATE POLICY "Allow all for anon" ON "public"."transactions" USING (true) WITH CHECK (true);
```

**Prepared fix (not applied):**
```sql
CREATE POLICY "Allow authenticated access" ON transactions 
    FOR ALL USING (auth.role() IN ('authenticated', 'service_role'))
    WITH CHECK (auth.role() IN ('authenticated', 'service_role'));
```

**Impact:**
- Anyone with anon key can read/write all data
- Acceptable for pre-production with trusted devices
- Should be fixed before multi-device or external access

**Prerequisites for applying:**
1. Implement Supabase Auth in app
2. Add device registration flow
3. Test authenticated access
4. Then apply `20260118110000_fix_rls_policies.sql`

---

## ⚪ LOW Priority Issues

None identified.

---

## ✅ Good Patterns Observed

### 1. Idempotent Migrations
All migrations use idempotent patterns:
```sql
-- Columns
ALTER TABLE x ADD COLUMN IF NOT EXISTS y;

-- Indexes
CREATE INDEX IF NOT EXISTS idx_name ON table(column);

-- Constraints (with exception handling)
DO $$ BEGIN
    ALTER TABLE x ADD CONSTRAINT c FOREIGN KEY...;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;
```

### 2. Comprehensive Indexes
| Table | Indexes | Purpose |
|-------|---------|---------|
| transactions | local_id, location, product, created, synced, type, batch, sale_batch, server_updated | Sync, filtering, inventory |
| purchase_batches | local_id, location, created, synced, is_voided, corrects_batch_id, server_updated | Correction workflow |
| cash_operations | local_id, location, type, batch, category, is_transfer, server_updated | Cash tracking |
| products | local_id, synced, server_updated | Sync |

Partial indexes for sync efficiency:
```sql
CREATE INDEX idx_transactions_synced ON transactions(synced_at) WHERE synced_at IS NULL;
```

### 3. Proper Foreign Key Behavior
| Constraint | ON DELETE | Reason |
|------------|-----------|--------|
| transactions → locations | (default) | Preserve history |
| transactions → products | (default) | Preserve history |
| transactions → purchase_batches | (default) | Preserve batch link |
| cash_operations → purchase_batches | RESTRICT | Prevent orphaned cash ops |
| cash_operations → expense_categories | SET NULL | Allow category deletion |
| purchase_batches → corrects_batch_id | RESTRICT | Preserve correction chain |

### 4. Views with Voided Filtering
Both `inventory` and `cash_balance` views correctly exclude voided batches:
```sql
WHERE (t.batch_id IS NULL OR NOT EXISTS (
    SELECT 1 FROM purchase_batches pb WHERE pb.id = t.batch_id AND pb.is_voided = true
))
```

### 5. Server Timestamps via Trigger
Consistent `server_updated_at` trigger on all synced tables:
```sql
CREATE OR REPLACE FUNCTION update_server_updated_at() RETURNS trigger AS $$
begin
  new.server_updated_at = now();
  return new;
end;
$$ LANGUAGE plpgsql;
```

### 6. Comprehensive Test Suite
| Test File | Purpose | Tests |
|-----------|---------|-------|
| 00_setup.sql | Test helpers | - |
| 01_inventory_voided_filtering.sql | Inventory view | 6 |
| 02_cash_balance_voided_filtering.sql | Cash balance views | - |
| 03_rls_policies.sql | RLS verification | 7 |
| 04_locations_sync.sql | Location sync | - |
| 05_audit_and_transfers.sql | Audit columns | - |
| 06_indexes.sql | Index existence | 6 |
| 07_fk_constraints.sql | FK behavior | 3 |

### 7. Proper Type Constraints
```sql
CONSTRAINT "transactions_type_check" CHECK (
    type = ANY (ARRAY['purchase', 'sale', 'transfer_out', 'transfer_in', 'adjustment'])
)
CONSTRAINT "locations_type_check" CHECK (type = ANY (ARRAY['kiosk', 'mobile']))
CONSTRAINT "cash_operations_type_check" CHECK (
    type = ANY (ARRAY['deposit', 'withdrawal', 'payment', 'purchase'])
)
```

### 8. Numeric Precision
Consistent precision across schema:
- Weights: `numeric(10,3)` - 3 decimal places for kg
- Money: `numeric(10,2)` - 2 decimal places for hryvnia

---

## Verification Checklist

Based on plan.md session checklist:

| Check | Status | Notes |
|-------|--------|-------|
| Migrations in correct order | ✅ | Timestamp-ordered, dependencies respected |
| Each migration idempotent | ✅ | IF NOT EXISTS, DO $$ EXCEPTION blocks |
| Indexes for common queries | ✅ | Comprehensive coverage |
| FK ON DELETE behavior | ✅ | RESTRICT where data integrity matters |
| RLS policies correct | ⚠️ | Correct but "anon" still allowed |
| Constraints prevent invalid data | ✅ | CHECK constraints on type columns |
| Views compute correctly | ✅ | Tested, excludes voided batches |
| Triggers don't cause loops | ✅ | Simple server_updated_at trigger |

---

## Recommendations

### Immediate (Before Release)
1. **Verify production schema** - Check if voided_at columns exist
2. **Sync schema.sql** - Regenerate from production: `supabase db dump`

### Should Fix Soon (After Auth Implementation)
3. **Apply RLS fix** - Apply `20260118110000_fix_rls_policies.sql`

### Can Fix Later
4. None identified

---

## Files Audited

### Migrations (17 files)
- [x] 20260111000000_initial_schema.sql - Core tables, seed data
- [x] 20260111000001_purchase_batches.sql
- [x] 20260111000002_product_images.sql
- [x] 20260112000000_product_sync.sql
- [x] 20260112100000_product_images_storage.sql
- [x] 20260113000000_cash_operations.sql
- [x] 20260113100000_sale_batches.sql
- [x] 20260114000000_cash_operations_batch_id.sql
- [x] 20260114100000_server_updated_at.sql
- [x] 20260115000000_batch_corrections.sql
- [x] 20260115100000_adjustment_type.sql
- [x] 20260115200000_rename_location_mobile_to_sklad.sql
- [x] 20260116000000_is_transfer_flag.sql
- [x] 20260116100000_cash_operations_is_transfer.sql
- [x] 20260118000000_fix_voided_batch_filtering.sql
- [x] 20260118100000_additional_fixes.sql
- [x] 20260118110000_fix_rls_policies.sql (prepared, not applied)

### Schema
- [x] schema.sql - Full current schema dump

### Tests
- [x] 00_setup.sql - Test helpers
- [x] 01_inventory_voided_filtering.sql
- [x] 02_cash_balance_voided_filtering.sql
- [x] 03_rls_policies.sql
- [x] 04_locations_sync.sql
- [x] 05_audit_and_transfers.sql
- [x] 06_indexes.sql
- [x] 07_fk_constraints.sql
