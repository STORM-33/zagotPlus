-- Migration: Add is_transfer flag to cash_operations
-- Purpose: Replace fragile pattern-matching transfer detection with explicit boolean flag
-- This enables:
--   1. Reliable transfer detection without relying on text patterns in notes
--   2. Index-based filtering for efficient queries excluding transfers
--   3. Proper sync support for transfer operations

-- Add is_transfer column with default false
ALTER TABLE cash_operations
ADD COLUMN IF NOT EXISTS is_transfer BOOLEAN NOT NULL DEFAULT false;

-- Migrate existing transfers: set is_transfer=true where notes starts with 'Переказ'
UPDATE cash_operations
SET is_transfer = true
WHERE notes LIKE 'Переказ%';

-- Create index for efficient filtering on is_transfer
CREATE INDEX IF NOT EXISTS idx_cash_operations_is_transfer ON cash_operations(is_transfer);

-- Add comment for documentation
COMMENT ON COLUMN cash_operations.is_transfer IS 'True if this operation is part of a transfer between locations. Transfers are excluded from global balance calculations.';
