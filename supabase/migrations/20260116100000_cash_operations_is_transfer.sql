-- Add 'is_transfer' column to cash_operations table
-- Created: 2026-01-16
-- Description: Track whether a cash operation is part of a transfer between locations

-- ============================================================================
-- ADD COLUMN
-- ============================================================================

ALTER TABLE cash_operations 
ADD COLUMN IF NOT EXISTS is_transfer boolean NOT NULL DEFAULT false;

-- ============================================================================
-- INDEX
-- ============================================================================

CREATE INDEX IF NOT EXISTS idx_cash_operations_is_transfer 
ON cash_operations(is_transfer) 
WHERE is_transfer = true;

-- ============================================================================
-- COMMENT
-- ============================================================================

COMMENT ON COLUMN cash_operations.is_transfer IS 'True if this operation is part of a cash transfer between locations';
