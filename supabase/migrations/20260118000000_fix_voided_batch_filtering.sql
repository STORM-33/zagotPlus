-- Fix critical issues with voided batch filtering and missing indexes
-- Created: 2026-01-18
-- Description: 
--   1. Update inventory view to exclude voided batches (CRITICAL)
--   2. Update cash_balance views to exclude voided batches (CRITICAL)
--   3. Add missing idx_transactions_type index
--   4. Add missing idx_products_synced partial index
--   5. Change cash_operations.batch_id FK from CASCADE to RESTRICT

-- ============================================================================
-- FIX 1: INVENTORY VIEW - EXCLUDE VOIDED BATCHES
-- ============================================================================

CREATE OR REPLACE VIEW inventory AS
SELECT
    location_id,
    product_id,
    SUM(CASE
        WHEN type IN ('purchase', 'transfer_in') THEN weight_kg
        WHEN type IN ('sale', 'transfer_out') THEN -weight_kg
        WHEN type = 'adjustment' THEN weight_kg
    END) AS quantity_kg
FROM transactions t
WHERE
    (t.batch_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM purchase_batches pb WHERE pb.id = t.batch_id AND pb.is_voided = true
    ))
    AND (t.sale_batch_id IS NULL OR NOT EXISTS (
        SELECT 1 FROM sale_batches sb WHERE sb.id = t.sale_batch_id AND sb.is_voided = true
    ))
GROUP BY location_id, product_id;

-- ============================================================================
-- FIX 2: CASH_BALANCE VIEW - EXCLUDE VOIDED BATCHES
-- ============================================================================

-- Drop and recreate cash_balance view
DROP VIEW IF EXISTS cash_balance;
CREATE VIEW cash_balance AS
SELECT
    location_id,
    SUM(CASE
        WHEN type = 'deposit' THEN amount
        WHEN type IN ('withdrawal', 'payment', 'purchase') THEN -amount
    END) AS balance
FROM cash_operations co
WHERE co.batch_id IS NULL OR NOT EXISTS (
    SELECT 1 FROM purchase_batches pb WHERE pb.id = co.batch_id AND pb.is_voided = true
)
GROUP BY location_id;

-- Drop and recreate total_cash_balance view
DROP VIEW IF EXISTS total_cash_balance;
CREATE VIEW total_cash_balance AS
SELECT
    SUM(CASE
        WHEN type = 'deposit' THEN amount
        WHEN type IN ('withdrawal', 'payment', 'purchase') THEN -amount
    END) AS balance
FROM cash_operations co
WHERE co.batch_id IS NULL OR NOT EXISTS (
    SELECT 1 FROM purchase_batches pb WHERE pb.id = co.batch_id AND pb.is_voided = true
);

-- ============================================================================
-- FIX 3: ADD MISSING INDEX ON TRANSACTIONS.TYPE
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_transactions_type ON transactions(type);

-- ============================================================================
-- FIX 4: ADD MISSING PARTIAL INDEX FOR UNSYNCED PRODUCTS
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_products_synced ON products(synced_at) WHERE synced_at IS NULL;

-- ============================================================================
-- FIX 5: CHANGE CASH_OPERATIONS.BATCH_ID FK FROM CASCADE TO RESTRICT
-- ============================================================================
-- This prevents accidental deletion of purchase_batches that would silently
-- delete cash operations. Forces users to void batches instead of deleting.

ALTER TABLE cash_operations DROP CONSTRAINT IF EXISTS cash_operations_batch_id_fkey;
ALTER TABLE cash_operations
    ADD CONSTRAINT cash_operations_batch_id_fkey
    FOREIGN KEY (batch_id) REFERENCES purchase_batches(id) ON DELETE RESTRICT;

-- ============================================================================
-- COMMENTS
-- ============================================================================

COMMENT ON VIEW inventory IS 'Current stock levels, excludes voided batches';
COMMENT ON VIEW cash_balance IS 'Cash balance per location, excludes voided purchase batches';
COMMENT ON VIEW total_cash_balance IS 'Total cash balance across all locations, excludes voided purchase batches';
