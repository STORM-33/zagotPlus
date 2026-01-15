-- Add 'adjustment' transaction type for inventory corrections (inventorization)
-- Created: 2026-01-15
-- Description: Enables correcting inventory discrepancies between recorded and actual stock

-- ============================================================================
-- UPDATE CONSTRAINT
-- ============================================================================

-- Drop existing constraint
ALTER TABLE transactions 
DROP CONSTRAINT IF EXISTS transactions_type_check;

-- Add new constraint with 'adjustment' type
ALTER TABLE transactions 
ADD CONSTRAINT transactions_type_check 
CHECK (type IN ('purchase', 'sale', 'transfer_out', 'transfer_in', 'adjustment'));

-- ============================================================================
-- UPDATE INVENTORY VIEW
-- ============================================================================

-- Recreate inventory view to handle adjustments
-- Adjustment transactions store signed weight directly (+/- for add/remove)
CREATE OR REPLACE VIEW inventory AS
SELECT
  location_id,
  product_id,
  SUM(CASE
    WHEN type IN ('purchase', 'transfer_in') THEN weight_kg
    WHEN type IN ('sale', 'transfer_out') THEN -weight_kg
    WHEN type = 'adjustment' THEN weight_kg  -- Already signed
  END) AS quantity_kg
FROM transactions
GROUP BY location_id, product_id;

-- ============================================================================
-- COMMENT
-- ============================================================================

COMMENT ON COLUMN transactions.type IS 'Transaction type: purchase (+), sale (-), transfer_out (-), transfer_in (+), adjustment (+/-)';
