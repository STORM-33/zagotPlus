-- Additional fixes for database issues (SAFE TO APPLY)
-- Created: 2026-01-18
-- Description:
--   1. Add sync columns to locations table
--   2. Remove redundant local_id indexes (UNIQUE constraint already creates index)
--   3. Change is_voided indexes to partial indexes WHERE is_voided = true
--   4. Add transfer_pair_id for linking cash transfer pairs
--   5. Add audit columns for voiding (voided_at, voided_by_device_id)
--   6. Add composite index (location_id, product_id) on transactions
--   7. Fix ON DELETE consistency - standardize to RESTRICT
--
-- NOTE: RLS policy fixes are in a separate migration (20260118110000)
--       to be applied after auth is implemented

-- ============================================================================
-- FIX 1: ADD SYNC COLUMNS TO LOCATIONS
-- ============================================================================
-- Makes locations consistent with other syncable entities

ALTER TABLE locations ADD COLUMN IF NOT EXISTS local_id TEXT;
ALTER TABLE locations ADD COLUMN IF NOT EXISTS synced_at TIMESTAMPTZ;
ALTER TABLE locations ADD COLUMN IF NOT EXISTS server_updated_at TIMESTAMPTZ DEFAULT NOW();
ALTER TABLE locations ADD COLUMN IF NOT EXISTS device_id TEXT;

-- Set local_id for existing rows (use id as local_id)
UPDATE locations SET local_id = id::text WHERE local_id IS NULL;

-- Make local_id NOT NULL and UNIQUE
ALTER TABLE locations ALTER COLUMN local_id SET NOT NULL;
ALTER TABLE locations ADD CONSTRAINT locations_local_id_key UNIQUE (local_id);

-- Add index for unsynced locations
CREATE INDEX IF NOT EXISTS idx_locations_synced ON locations(synced_at) WHERE synced_at IS NULL;

-- Use existing trigger function (consistent with other tables)
DROP TRIGGER IF EXISTS trigger_locations_server_updated_at ON locations;
CREATE TRIGGER trigger_locations_server_updated_at
    BEFORE INSERT OR UPDATE ON locations
    FOR EACH ROW
    EXECUTE FUNCTION update_server_updated_at();

-- ============================================================================
-- FIX 2: REMOVE REDUNDANT LOCAL_ID INDEXES
-- ============================================================================
-- UNIQUE constraint already creates an index, these are redundant

DROP INDEX IF EXISTS idx_transactions_local_id;
DROP INDEX IF EXISTS idx_purchase_batches_local_id;
DROP INDEX IF EXISTS idx_sale_batches_local_id;
DROP INDEX IF EXISTS idx_expense_categories_local_id;
DROP INDEX IF EXISTS idx_cash_operations_local_id;
-- Note: idx_products_local_id is a UNIQUE INDEX, not redundant (products.local_id was added later)

-- ============================================================================
-- FIX 3: CHANGE IS_VOIDED INDEXES TO PARTIAL INDEXES
-- ============================================================================
-- Boolean indexes with low selectivity are inefficient
-- Partial index on is_voided = true is more useful (finding voided records)

DROP INDEX IF EXISTS idx_purchase_batches_is_voided;
CREATE INDEX idx_purchase_batches_voided ON purchase_batches(id) WHERE is_voided = true;

DROP INDEX IF EXISTS idx_sale_batches_is_voided;
CREATE INDEX idx_sale_batches_voided ON sale_batches(id) WHERE is_voided = true;

-- ============================================================================
-- FIX 4: ADD TRANSFER PAIR TRACKING
-- ============================================================================
-- Links the two sides of a cash transfer for reconciliation

ALTER TABLE cash_operations ADD COLUMN IF NOT EXISTS transfer_pair_id TEXT;
CREATE INDEX IF NOT EXISTS idx_cash_operations_transfer_pair ON cash_operations(transfer_pair_id) WHERE transfer_pair_id IS NOT NULL;

COMMENT ON COLUMN cash_operations.transfer_pair_id IS 'UUID linking both sides of a cash transfer. Same value on withdrawal and deposit.';

-- ============================================================================
-- FIX 5: ADD AUDIT COLUMNS FOR VOIDING
-- ============================================================================
-- Track who voided a batch and when

-- Purchase batches
ALTER TABLE purchase_batches ADD COLUMN IF NOT EXISTS voided_at TIMESTAMPTZ;
ALTER TABLE purchase_batches ADD COLUMN IF NOT EXISTS voided_by_device_id TEXT;

-- Sale batches
ALTER TABLE sale_batches ADD COLUMN IF NOT EXISTS voided_at TIMESTAMPTZ;
ALTER TABLE sale_batches ADD COLUMN IF NOT EXISTS voided_by_device_id TEXT;

COMMENT ON COLUMN purchase_batches.voided_at IS 'Timestamp when batch was voided';
COMMENT ON COLUMN purchase_batches.voided_by_device_id IS 'Device that voided this batch';
COMMENT ON COLUMN sale_batches.voided_at IS 'Timestamp when batch was voided';
COMMENT ON COLUMN sale_batches.voided_by_device_id IS 'Device that voided this batch';

-- ============================================================================
-- FIX 6: ADD COMPOSITE INDEX FOR INVENTORY QUERIES
-- ============================================================================
-- The inventory view queries (location_id, product_id) together

CREATE INDEX IF NOT EXISTS idx_transactions_location_product ON transactions(location_id, product_id);

-- ============================================================================
-- FIX 7: FIX ON DELETE CONSISTENCY
-- ============================================================================
-- Standardize to RESTRICT to prevent accidental data loss

-- Check and fix transactions.batch_id FK (if it exists without ON DELETE)
DO $$ BEGIN
    -- Drop existing FK if it exists
    ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_batch_id_fkey;
    -- Add with RESTRICT
    ALTER TABLE transactions 
        ADD CONSTRAINT transactions_batch_id_fkey 
        FOREIGN KEY (batch_id) REFERENCES purchase_batches(id) ON DELETE RESTRICT;
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

-- Check and fix transactions.sale_batch_id FK
DO $$ BEGIN
    ALTER TABLE transactions DROP CONSTRAINT IF EXISTS transactions_sale_batch_id_fkey;
    ALTER TABLE transactions 
        ADD CONSTRAINT transactions_sale_batch_id_fkey 
        FOREIGN KEY (sale_batch_id) REFERENCES sale_batches(id) ON DELETE RESTRICT;
EXCEPTION WHEN undefined_table THEN NULL;
END $$;

-- Note: cash_operations.batch_id was already fixed to RESTRICT in previous migration
