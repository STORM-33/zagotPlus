-- Test setup: Enable pgTAP extension and create test helpers
-- Run this before other tests

-- Enable pgTAP for testing
CREATE EXTENSION IF NOT EXISTS pgtap;

-- Test helper: Create a test location
CREATE OR REPLACE FUNCTION test_create_location(
    p_name TEXT DEFAULT 'Test Location'
) RETURNS UUID AS $$
DECLARE
    v_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO locations (id, name, local_id)
    VALUES (v_id, p_name, v_id::text);
    RETURN v_id;
END;
$$ LANGUAGE plpgsql;

-- Test helper: Create a test product
CREATE OR REPLACE FUNCTION test_create_product(
    p_name TEXT DEFAULT 'Test Product'
) RETURNS UUID AS $$
DECLARE
    v_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO products (id, name, local_id)
    VALUES (v_id, p_name, v_id::text);
    RETURN v_id;
END;
$$ LANGUAGE plpgsql;

-- Test helper: Create a purchase batch with transactions
CREATE OR REPLACE FUNCTION test_create_purchase_batch(
    p_location_id UUID,
    p_product_id UUID,
    p_weight_kg DECIMAL DEFAULT 100.0,
    p_amount DECIMAL DEFAULT 1000.0
) RETURNS UUID AS $$
DECLARE
    v_batch_id UUID := gen_random_uuid();
    v_tx_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO purchase_batches (id, local_id, location_id, device_id, total_weight_kg, total_amount, item_count)
    VALUES (v_batch_id, v_batch_id::text, p_location_id, 'test-device', p_weight_kg, p_amount, 1);
    
    INSERT INTO transactions (id, local_id, location_id, product_id, batch_id, type, weight_kg, price_per_kg, total_price, device_id)
    VALUES (v_tx_id, v_tx_id::text, p_location_id, p_product_id, v_batch_id, 'purchase', p_weight_kg, p_amount / p_weight_kg, p_amount, 'test-device');
    
    RETURN v_batch_id;
END;
$$ LANGUAGE plpgsql;

-- Test helper: Create a sale batch with transactions
CREATE OR REPLACE FUNCTION test_create_sale_batch(
    p_location_id UUID,
    p_product_id UUID,
    p_weight_kg DECIMAL DEFAULT 50.0,
    p_amount DECIMAL DEFAULT 600.0
) RETURNS UUID AS $$
DECLARE
    v_batch_id UUID := gen_random_uuid();
    v_tx_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO sale_batches (id, local_id, location_id, device_id, total_weight_kg, total_amount, item_count)
    VALUES (v_batch_id, v_batch_id::text, p_location_id, 'test-device', p_weight_kg, p_amount, 1);
    
    INSERT INTO transactions (id, local_id, location_id, product_id, sale_batch_id, type, weight_kg, price_per_kg, total_price, device_id)
    VALUES (v_tx_id, v_tx_id::text, p_location_id, p_product_id, v_batch_id, 'sale', -p_weight_kg, p_amount / p_weight_kg, p_amount, 'test-device');
    
    RETURN v_batch_id;
END;
$$ LANGUAGE plpgsql;

-- Test helper: Create a cash operation
CREATE OR REPLACE FUNCTION test_create_cash_operation(
    p_location_id UUID,
    p_type TEXT,
    p_amount DECIMAL,
    p_batch_id UUID DEFAULT NULL
) RETURNS UUID AS $$
DECLARE
    v_id UUID := gen_random_uuid();
BEGIN
    INSERT INTO cash_operations (id, local_id, location_id, type, amount, batch_id, device_id)
    VALUES (v_id, v_id::text, p_location_id, p_type, p_amount, p_batch_id, 'test-device');
    RETURN v_id;
END;
$$ LANGUAGE plpgsql;

-- Test helper: Clean up test data
CREATE OR REPLACE FUNCTION test_cleanup() RETURNS VOID AS $$
BEGIN
    DELETE FROM transactions WHERE device_id = 'test-device';
    DELETE FROM cash_operations WHERE device_id = 'test-device';
    DELETE FROM purchase_batches WHERE device_id = 'test-device';
    DELETE FROM sale_batches WHERE device_id = 'test-device';
    DELETE FROM products WHERE local_id LIKE '%-%-%-%-%'; -- UUIDs
    DELETE FROM locations WHERE local_id LIKE '%-%-%-%-%'; -- UUIDs
END;
$$ LANGUAGE plpgsql;
