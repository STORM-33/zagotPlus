-- Tests for cash_balance view voided batch filtering
-- Tests migration: 20260118000000_fix_voided_batch_filtering.sql

BEGIN;
SELECT plan(5);

-- Setup test data
SELECT test_cleanup();

-- Test 1: Cash operations linked to voided batches are excluded
DO $$
DECLARE
    v_location_id UUID;
    v_product_id UUID;
    v_batch_id UUID;
    v_balance_before DECIMAL;
    v_balance_after DECIMAL;
BEGIN
    v_location_id := test_create_location('Cash Test Location');
    v_product_id := test_create_product('Cash Test Product');
    
    -- Add initial deposit (not linked to batch)
    PERFORM test_create_cash_operation(v_location_id, 'deposit', 5000.0, NULL);
    
    -- Create a purchase batch
    v_batch_id := test_create_purchase_batch(v_location_id, v_product_id, 100.0, 1000.0);
    
    -- Add cash operation linked to the purchase batch
    PERFORM test_create_cash_operation(v_location_id, 'purchase', 1000.0, v_batch_id);
    
    -- Check balance before voiding (5000 - 1000 = 4000)
    SELECT balance INTO v_balance_before
    FROM cash_balance
    WHERE location_id = v_location_id;
    
    PERFORM ok(
        v_balance_before = 4000.0,
        'Cash balance should be 4000 before voiding (5000 deposit - 1000 purchase)'
    );
    
    -- Void the purchase batch
    UPDATE purchase_batches SET is_voided = true WHERE id = v_batch_id;
    
    -- Check balance after voiding (should be 5000, purchase excluded)
    SELECT balance INTO v_balance_after
    FROM cash_balance
    WHERE location_id = v_location_id;
    
    PERFORM ok(
        v_balance_after = 5000.0,
        'Cash balance should be 5000 after voiding (purchase operation excluded)'
    );
END $$;

-- Test 2: Cash operations without batch_id are always included
DO $$
DECLARE
    v_location_id UUID;
    v_balance DECIMAL;
BEGIN
    v_location_id := test_create_location('Cash Standalone Test Location');
    
    -- Add various cash operations without batch links
    PERFORM test_create_cash_operation(v_location_id, 'deposit', 10000.0, NULL);
    PERFORM test_create_cash_operation(v_location_id, 'withdrawal', 2000.0, NULL);
    PERFORM test_create_cash_operation(v_location_id, 'payment', 500.0, NULL);
    
    -- Check balance (10000 - 2000 - 500 = 7500)
    SELECT balance INTO v_balance
    FROM cash_balance
    WHERE location_id = v_location_id;
    
    PERFORM ok(
        v_balance = 7500.0,
        'Cash operations without batch_id should always be included'
    );
END $$;

-- Test 3: Total cash balance aggregates correctly
DO $$
DECLARE
    v_location1_id UUID;
    v_location2_id UUID;
    v_total DECIMAL;
BEGIN
    v_location1_id := test_create_location('Total Cash Test Location 1');
    v_location2_id := test_create_location('Total Cash Test Location 2');
    
    -- Add deposits to both locations
    PERFORM test_create_cash_operation(v_location1_id, 'deposit', 1000.0, NULL);
    PERFORM test_create_cash_operation(v_location2_id, 'deposit', 2000.0, NULL);
    
    -- Check total balance
    SELECT balance INTO v_total FROM total_cash_balance;
    
    -- Note: There may be other test data, so we check if it includes our amounts
    PERFORM ok(
        v_total >= 3000.0,
        'Total cash balance should aggregate all locations'
    );
END $$;

-- Test 4: Voided batch cash operations excluded from total
DO $$
DECLARE
    v_location_id UUID;
    v_product_id UUID;
    v_batch_id UUID;
    v_total_before DECIMAL;
    v_total_after DECIMAL;
BEGIN
    v_location_id := test_create_location('Total Voided Test Location');
    v_product_id := test_create_product('Total Voided Test Product');
    
    -- Record total before
    SELECT COALESCE(balance, 0) INTO v_total_before FROM total_cash_balance;
    
    -- Add deposit
    PERFORM test_create_cash_operation(v_location_id, 'deposit', 10000.0, NULL);
    
    -- Create purchase with cash operation
    v_batch_id := test_create_purchase_batch(v_location_id, v_product_id, 100.0, 3000.0);
    PERFORM test_create_cash_operation(v_location_id, 'purchase', 3000.0, v_batch_id);
    
    -- Void the batch
    UPDATE purchase_batches SET is_voided = true WHERE id = v_batch_id;
    
    -- Check that total increased by 10000 (deposit only, purchase excluded)
    SELECT balance INTO v_total_after FROM total_cash_balance;
    
    PERFORM ok(
        v_total_after = v_total_before + 10000.0,
        'Total cash balance should exclude voided batch operations'
    );
END $$;

-- Cleanup
SELECT test_cleanup();
SELECT * FROM finish();
ROLLBACK;
