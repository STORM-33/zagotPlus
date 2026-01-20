-- Tests for inventory view voided batch filtering
-- Tests migration: 20260118000000_fix_voided_batch_filtering.sql

BEGIN;
SELECT plan(6);

-- Setup test data
SELECT test_cleanup();

DO $$
DECLARE
    v_location_id UUID;
    v_product_id UUID;
    v_purchase_batch_id UUID;
    v_sale_batch_id UUID;
    v_inventory_before DECIMAL;
    v_inventory_after DECIMAL;
BEGIN
    -- Create test entities
    v_location_id := test_create_location('Inventory Test Location');
    v_product_id := test_create_product('Inventory Test Product');
    
    -- Create a purchase (adds 100kg)
    v_purchase_batch_id := test_create_purchase_batch(v_location_id, v_product_id, 100.0, 1000.0);
    
    -- Create a sale (removes 30kg)
    v_sale_batch_id := test_create_sale_batch(v_location_id, v_product_id, 30.0, 360.0);
    
    -- Check inventory before voiding (should be 100 - 30 = 70)
    SELECT quantity_kg INTO v_inventory_before
    FROM inventory
    WHERE location_id = v_location_id AND product_id = v_product_id;
    
    PERFORM ok(
        v_inventory_before = 70.0,
        'Inventory should be 70kg before voiding (100 purchased - 30 sold)'
    );
    
    -- Void the purchase batch
    UPDATE purchase_batches SET is_voided = true WHERE id = v_purchase_batch_id;
    
    -- Check inventory after voiding purchase (should be -30, only sale remains)
    SELECT COALESCE(quantity_kg, 0) INTO v_inventory_after
    FROM inventory
    WHERE location_id = v_location_id AND product_id = v_product_id;
    
    PERFORM ok(
        v_inventory_after = -30.0,
        'Inventory should be -30kg after voiding purchase batch (only sale counts)'
    );
    
    -- Unvoid the purchase
    UPDATE purchase_batches SET is_voided = false WHERE id = v_purchase_batch_id;
    
    -- Void the sale batch
    UPDATE sale_batches SET is_voided = true WHERE id = v_sale_batch_id;
    
    -- Check inventory (should be 100, only purchase remains)
    SELECT quantity_kg INTO v_inventory_after
    FROM inventory
    WHERE location_id = v_location_id AND product_id = v_product_id;
    
    PERFORM ok(
        v_inventory_after = 100.0,
        'Inventory should be 100kg after voiding sale batch (only purchase counts)'
    );
    
    -- Void both batches
    UPDATE purchase_batches SET is_voided = true WHERE id = v_purchase_batch_id;
    
    -- Check inventory (should be NULL or 0, nothing counts)
    SELECT COALESCE(quantity_kg, 0) INTO v_inventory_after
    FROM inventory
    WHERE location_id = v_location_id AND product_id = v_product_id;
    
    PERFORM ok(
        v_inventory_after = 0 OR v_inventory_after IS NULL,
        'Inventory should be 0 when both batches are voided'
    );
END $$;

-- Test that transactions without batch_id are included
DO $$
DECLARE
    v_location_id UUID;
    v_product_id UUID;
    v_tx_id UUID := gen_random_uuid();
    v_inventory DECIMAL;
BEGIN
    v_location_id := test_create_location('Adjustment Test Location');
    v_product_id := test_create_product('Adjustment Test Product');
    
    -- Create an adjustment transaction (no batch_id)
    INSERT INTO transactions (id, local_id, location_id, product_id, type, weight_kg, device_id)
    VALUES (v_tx_id, v_tx_id::text, v_location_id, v_product_id, 'adjustment', 25.0, 'test-device');
    
    -- Check inventory
    SELECT quantity_kg INTO v_inventory
    FROM inventory
    WHERE location_id = v_location_id AND product_id = v_product_id;
    
    PERFORM ok(
        v_inventory = 25.0,
        'Adjustment transactions without batch_id should be included in inventory'
    );
END $$;

-- Test transfer transactions
DO $$
DECLARE
    v_location_id UUID;
    v_product_id UUID;
    v_tx_in_id UUID := gen_random_uuid();
    v_tx_out_id UUID := gen_random_uuid();
    v_inventory DECIMAL;
BEGIN
    v_location_id := test_create_location('Transfer Test Location');
    v_product_id := test_create_product('Transfer Test Product');
    
    -- Create transfer_in transaction
    INSERT INTO transactions (id, local_id, location_id, product_id, type, weight_kg, device_id)
    VALUES (v_tx_in_id, v_tx_in_id::text, v_location_id, v_product_id, 'transfer_in', 50.0, 'test-device');
    
    -- Check inventory after transfer_in
    SELECT quantity_kg INTO v_inventory
    FROM inventory
    WHERE location_id = v_location_id AND product_id = v_product_id;
    
    PERFORM ok(
        v_inventory = 50.0,
        'transfer_in should add to inventory'
    );
END $$;

-- Cleanup
SELECT test_cleanup();
SELECT * FROM finish();
ROLLBACK;
