-- Tests for audit columns and transfer tracking
-- Tests migration: 20260118100000_additional_fixes.sql

BEGIN;
SELECT plan(8);

-- Test 1: Purchase batches have audit columns
SELECT has_column('purchase_batches', 'voided_at', 'purchase_batches has voided_at column');
SELECT has_column('purchase_batches', 'voided_by_device_id', 'purchase_batches has voided_by_device_id column');

-- Test 2: Sale batches have audit columns
SELECT has_column('sale_batches', 'voided_at', 'sale_batches has voided_at column');
SELECT has_column('sale_batches', 'voided_by_device_id', 'sale_batches has voided_by_device_id column');

-- Test 3: Cash operations has transfer_pair_id
SELECT has_column('cash_operations', 'transfer_pair_id', 'cash_operations has transfer_pair_id column');

-- Test 4: Audit columns are populated when voiding
DO $$
DECLARE
    v_location_id UUID;
    v_product_id UUID;
    v_batch_id UUID;
    v_voided_at TIMESTAMPTZ;
    v_voided_by TEXT;
BEGIN
    v_location_id := test_create_location('Audit Test Location');
    v_product_id := test_create_product('Audit Test Product');
    v_batch_id := test_create_purchase_batch(v_location_id, v_product_id, 50.0, 500.0);
    
    -- Void the batch with audit info
    UPDATE purchase_batches 
    SET is_voided = true, 
        voided_at = NOW(), 
        voided_by_device_id = 'audit-test-device'
    WHERE id = v_batch_id;
    
    -- Check audit columns
    SELECT voided_at, voided_by_device_id 
    INTO v_voided_at, v_voided_by
    FROM purchase_batches WHERE id = v_batch_id;
    
    PERFORM ok(
        v_voided_at IS NOT NULL,
        'voided_at should be set when voiding a batch'
    );
    
    PERFORM ok(
        v_voided_by = 'audit-test-device',
        'voided_by_device_id should be set when voiding a batch'
    );
END $$;

-- Test 5: Transfer pair linking works
DO $$
DECLARE
    v_location1_id UUID;
    v_location2_id UUID;
    v_transfer_pair UUID := gen_random_uuid();
    v_withdrawal_id UUID;
    v_deposit_id UUID;
    v_matched_count INT;
BEGIN
    v_location1_id := test_create_location('Transfer Source');
    v_location2_id := test_create_location('Transfer Destination');
    
    -- Create withdrawal
    v_withdrawal_id := gen_random_uuid();
    INSERT INTO cash_operations (id, local_id, location_id, type, amount, device_id, transfer_pair_id)
    VALUES (v_withdrawal_id, v_withdrawal_id::text, v_location1_id, 'withdrawal', 1000.0, 'test-device', v_transfer_pair::text);
    
    -- Create matching deposit
    v_deposit_id := gen_random_uuid();
    INSERT INTO cash_operations (id, local_id, location_id, type, amount, device_id, transfer_pair_id)
    VALUES (v_deposit_id, v_deposit_id::text, v_location2_id, 'deposit', 1000.0, 'test-device', v_transfer_pair::text);
    
    -- Verify they can be linked
    SELECT COUNT(*) INTO v_matched_count
    FROM cash_operations
    WHERE transfer_pair_id = v_transfer_pair::text;
    
    PERFORM ok(
        v_matched_count = 2,
        'Transfer pair should link both sides of the transfer'
    );
END $$;

-- Cleanup
SELECT test_cleanup();
SELECT * FROM finish();
ROLLBACK;
