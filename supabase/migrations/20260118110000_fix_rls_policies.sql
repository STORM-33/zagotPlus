-- RLS Policy Fixes - Restrict to authenticated users
-- Created: 2026-01-18
-- Description:
--   Fix RLS policies to require authentication instead of allowing anonymous access
--
-- ⚠️  WARNING: DO NOT APPLY until auth is implemented in the app!
-- ⚠️  Applying this without auth will break the app.
--
-- Prerequisites before applying:
--   1. Implement GoTrue authentication in SupabaseAuthManager
--   2. Install GoTrue plugin in SupabaseModule
--   3. Test auth flow works correctly
--   4. Verify device registration creates authenticated session

-- ============================================================================
-- SECURITY - Restrict to authenticated users only
-- ============================================================================
-- SECURITY DECISION: This is a single-organization app where all devices belong
-- to the same business. We restrict to authenticated/service_role (no anon).
-- 
-- For production deployment, consider:
--   1. Use Supabase Auth with device registration flow
--   2. Add device_id claim to JWT for per-device audit trails
--   3. Configure API rate limiting in Supabase dashboard
--
-- The anon key should have minimal permissions in Supabase dashboard settings.

-- Transactions
DROP POLICY IF EXISTS "Allow all for anon" ON transactions;
CREATE POLICY "Allow authenticated access" ON transactions 
    FOR ALL USING (auth.role() IN ('authenticated', 'service_role'))
    WITH CHECK (auth.role() IN ('authenticated', 'service_role'));

-- Purchase batches
DROP POLICY IF EXISTS "Allow all for anon" ON purchase_batches;
CREATE POLICY "Allow authenticated access" ON purchase_batches 
    FOR ALL USING (auth.role() IN ('authenticated', 'service_role'))
    WITH CHECK (auth.role() IN ('authenticated', 'service_role'));

-- Sale batches
DROP POLICY IF EXISTS "Allow all for anon" ON sale_batches;
CREATE POLICY "Allow authenticated access" ON sale_batches 
    FOR ALL USING (auth.role() IN ('authenticated', 'service_role'))
    WITH CHECK (auth.role() IN ('authenticated', 'service_role'));

-- Products
DROP POLICY IF EXISTS "Allow all for anon" ON products;
CREATE POLICY "Allow authenticated access" ON products 
    FOR ALL USING (auth.role() IN ('authenticated', 'service_role'))
    WITH CHECK (auth.role() IN ('authenticated', 'service_role'));

-- Locations
DROP POLICY IF EXISTS "Allow all for anon" ON locations;
CREATE POLICY "Allow authenticated access" ON locations 
    FOR ALL USING (auth.role() IN ('authenticated', 'service_role'))
    WITH CHECK (auth.role() IN ('authenticated', 'service_role'));

-- Expense categories
DROP POLICY IF EXISTS "Allow all for anon" ON expense_categories;
CREATE POLICY "Allow authenticated access" ON expense_categories 
    FOR ALL USING (auth.role() IN ('authenticated', 'service_role'))
    WITH CHECK (auth.role() IN ('authenticated', 'service_role'));

-- Cash operations
DROP POLICY IF EXISTS "Allow all for anon" ON cash_operations;
CREATE POLICY "Allow authenticated access" ON cash_operations 
    FOR ALL USING (auth.role() IN ('authenticated', 'service_role'))
    WITH CHECK (auth.role() IN ('authenticated', 'service_role'));

-- ============================================================================
-- VERIFICATION
-- ============================================================================
-- After applying, verify that:
--   1. Unauthenticated requests are rejected
--   2. Authenticated devices can access all data
--   3. Service role (for admin tools) still works
--
-- Test queries to run:
--   SELECT * FROM transactions; -- Should work with authenticated session
--   -- Should fail with anon key without session
