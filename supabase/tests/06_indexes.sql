-- Tests for indexes
-- Tests migration: 20260118000000_fix_voided_batch_filtering.sql
-- Tests migration: 20260118100000_additional_fixes.sql

BEGIN;
SELECT plan(6);

-- Test 1: idx_transactions_type exists
SELECT has_index('transactions', 'idx_transactions_type', 'transactions has type index');

-- Test 2: idx_transactions_location_product composite index exists
SELECT has_index('transactions', 'idx_transactions_location_product', 'transactions has composite location_product index');

-- Test 3: Partial indexes for voided batches exist
SELECT has_index('purchase_batches', 'idx_purchase_batches_voided', 'purchase_batches has partial voided index');
SELECT has_index('sale_batches', 'idx_sale_batches_voided', 'sale_batches has partial voided index');

-- Test 4: Transfer pair index exists
SELECT has_index('cash_operations', 'idx_cash_operations_transfer_pair', 'cash_operations has transfer_pair index');

-- Test 5: Locations synced index exists
SELECT has_index('locations', 'idx_locations_synced', 'locations has synced_at partial index');

SELECT * FROM finish();
ROLLBACK;
