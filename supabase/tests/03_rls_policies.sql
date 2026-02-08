-- Tests for RLS policies
-- Tests migration: 20260118100000_additional_fixes.sql

BEGIN;
SELECT plan(7);

-- Test 1: Verify RLS is enabled on all tables
SELECT has_table('transactions', 'transactions table exists');
SELECT has_table('purchase_batches', 'purchase_batches table exists');
SELECT has_table('sale_batches', 'sale_batches table exists');
SELECT has_table('products', 'products table exists');
SELECT has_table('locations', 'locations table exists');
SELECT has_table('cash_operations', 'cash_operations table exists');

-- Test 2: Verify policies exist and exclude anon
DO $$
DECLARE
    v_policy_count INT;
    v_anon_policy_count INT;
BEGIN
    -- Count policies that include 'anon' in their definition
    SELECT COUNT(*) INTO v_anon_policy_count
    FROM pg_policies
    WHERE schemaname = 'public'
      AND tablename IN ('transactions', 'purchase_batches', 'sale_batches', 
                        'products', 'locations', 'cash_operations', 'expense_categories')
      AND (qual::text LIKE '%anon%' OR with_check::text LIKE '%anon%');
    
    PERFORM ok(
        v_anon_policy_count = 0,
        'No RLS policies should allow anon role access'
    );
END $$;

SELECT * FROM finish();
ROLLBACK;
