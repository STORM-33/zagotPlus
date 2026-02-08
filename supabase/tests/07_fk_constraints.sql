-- Tests for ON DELETE RESTRICT behavior
-- Tests migration: 20260118000000_fix_voided_batch_filtering.sql
-- Tests migration: 20260118100000_additional_fixes.sql

BEGIN;
SELECT plan(3);

-- Test 1: Cannot delete purchase_batch with linked transactions
DO $$
DECLARE
    v_location_id UUID;
    v_product_id UUID;
    v_batch_id UUID;
    v_error_occurred BOOLEAN := false;
BEGIN
    v_location_id := test_create_location('FK Test Location');
    v_product_id := test_create_product('FK Test Product');
    v_batch_id := test_create_purchase_batch(v_location_id, v_product_id, 100.0, 1000.0);
    
    -- Try to delete the batch (should fail due to FK constraint)
    BEGIN
        DELETE FROM purchase_batches WHERE id = v_batch_id;
    EXCEPTION WHEN foreign_key_violation THEN
        v_error_occurred := true;
    END;
    
    PERFORM ok(
        v_error_occurred,
        'Deleting purchase_batch with linked transactions should fail (ON DELETE RESTRICT)'
    );
END $$;

-- Test 2: Cannot delete sale_batch with linked transactions
DO $$
DECLARE
    v_location_id UUID;
    v_product_id UUID;
    v_batch_id UUID;
    v_error_occurred BOOLEAN := false;
BEGIN
    v_location_id := test_create_location('FK Sale Test Location');
    v_product_id := test_create_product('FK Sale Test Product');
    v_batch_id := test_create_sale_batch(v_location_id, v_product_id, 50.0, 600.0);
    
    -- Try to delete the batch (should fail due to FK constraint)
    BEGIN
        DELETE FROM sale_batches WHERE id = v_batch_id;
    EXCEPTION WHEN foreign_key_violation THEN
        v_error_occurred := true;
    END;
    
    PERFORM ok(
        v_error_occurred,
        'Deleting sale_batch with linked transactions should fail (ON DELETE RESTRICT)'
    );
END $$;

-- Test 3: Cannot delete purchase_batch with linked cash_operations
DO $$
DECLARE
    v_location_id UUID;
    v_product_id UUID;
    v_batch_id UUID;
    v_error_occurred BOOLEAN := false;
BEGIN
    v_location_id := test_create_location('FK Cash Test Location');
    v_product_id := test_create_product('FK Cash Test Product');
    v_batch_id := test_create_purchase_batch(v_location_id, v_product_id, 100.0, 1000.0);
    
    -- Link a cash operation to the batch
    PERFORM test_create_cash_operation(v_location_id, 'purchase', 1000.0, v_batch_id);
    
    -- First delete the transactions to isolate the cash_operations FK test
    DELETE FROM transactions WHERE batch_id = v_batch_id;
    
    -- Try to delete the batch (should fail due to cash_operations FK)
    BEGIN
        DELETE FROM purchase_batches WHERE id = v_batch_id;
    EXCEPTION WHEN foreign_key_violation THEN
        v_error_occurred := true;
    END;
    
    PERFORM ok(
        v_error_occurred,
        'Deleting purchase_batch with linked cash_operations should fail (ON DELETE RESTRICT)'
    );
END $$;

-- Cleanup
SELECT test_cleanup();
SELECT * FROM finish();
ROLLBACK;
