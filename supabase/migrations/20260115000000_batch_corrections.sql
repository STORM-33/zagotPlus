-- Migration: Add batch correction support
-- This allows batches to be "voided" (soft-deleted) and linked to correction batches
-- Transactions remain immutable; only batch metadata is updated

-- Add correction columns to purchase_batches
ALTER TABLE purchase_batches 
    ADD COLUMN IF NOT EXISTS is_voided BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS corrects_batch_id UUID,
    ADD COLUMN IF NOT EXISTS correction_reason TEXT;

-- Add foreign key constraint with RESTRICT to prevent deletion of referenced batches
DO $$ BEGIN
    ALTER TABLE purchase_batches 
        ADD CONSTRAINT fk_purchase_batches_corrects 
        FOREIGN KEY (corrects_batch_id) 
        REFERENCES purchase_batches(id) 
        ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Add indices for efficient filtering
CREATE INDEX IF NOT EXISTS idx_purchase_batches_is_voided ON purchase_batches(is_voided);
CREATE INDEX IF NOT EXISTS idx_purchase_batches_corrects_batch_id ON purchase_batches(corrects_batch_id);

-- Add correction columns to sale_batches
ALTER TABLE sale_batches 
    ADD COLUMN IF NOT EXISTS is_voided BOOLEAN NOT NULL DEFAULT false,
    ADD COLUMN IF NOT EXISTS corrects_batch_id UUID,
    ADD COLUMN IF NOT EXISTS correction_reason TEXT;

-- Add foreign key constraint with RESTRICT to prevent deletion of referenced batches
DO $$ BEGIN
    ALTER TABLE sale_batches 
        ADD CONSTRAINT fk_sale_batches_corrects 
        FOREIGN KEY (corrects_batch_id) 
        REFERENCES sale_batches(id) 
        ON DELETE RESTRICT;
EXCEPTION WHEN duplicate_object THEN NULL;
END $$;

-- Add indices for efficient filtering
CREATE INDEX IF NOT EXISTS idx_sale_batches_is_voided ON sale_batches(is_voided);
CREATE INDEX IF NOT EXISTS idx_sale_batches_corrects_batch_id ON sale_batches(corrects_batch_id);

-- Update RLS policies to include new columns (they inherit existing row policies)
-- No changes needed since we're just adding columns, not changing access patterns

COMMENT ON COLUMN purchase_batches.is_voided IS 'True if this batch has been voided due to a correction';
COMMENT ON COLUMN purchase_batches.corrects_batch_id IS 'Reference to the original batch this one corrects';
COMMENT ON COLUMN purchase_batches.correction_reason IS 'User-provided reason for the correction';
COMMENT ON COLUMN sale_batches.is_voided IS 'True if this batch has been voided due to a correction';
COMMENT ON COLUMN sale_batches.corrects_batch_id IS 'Reference to the original batch this one corrects';
COMMENT ON COLUMN sale_batches.correction_reason IS 'User-provided reason for the correction';
