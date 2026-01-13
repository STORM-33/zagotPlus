-- Zagot+ Cash Operations Schema Update
-- Created: 2026-01-14
-- Description: Change cash_operations FK from transactions to purchase_batches
-- 
-- This fixes a bug where purchase payments incorrectly referenced batch IDs
-- as transaction IDs, causing FK constraint failures.

-- ============================================================================
-- RENAME COLUMN AND UPDATE FOREIGN KEY
-- ============================================================================

-- Step 1: Drop the old foreign key constraint and index
alter table cash_operations drop constraint if exists cash_operations_transaction_id_fkey;
drop index if exists idx_cash_operations_transaction;

-- Step 2: Rename the column
alter table cash_operations rename column transaction_id to batch_id;

-- Step 3: Add new foreign key constraint referencing purchase_batches
alter table cash_operations 
  add constraint cash_operations_batch_id_fkey 
  foreign key (batch_id) references purchase_batches(id) on delete cascade;

-- Step 4: Create new index
create index idx_cash_operations_batch on cash_operations(batch_id);
