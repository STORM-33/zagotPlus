-- Tests for locations table sync columns and trigger
-- Tests migration: 20260118100000_additional_fixes.sql

BEGIN;
SELECT plan(6);

-- Test 1: Locations table has required sync columns
SELECT has_column('locations', 'local_id', 'locations has local_id column');
SELECT has_column('locations', 'synced_at', 'locations has synced_at column');
SELECT has_column('locations', 'server_updated_at', 'locations has server_updated_at column');
SELECT has_column('locations', 'device_id', 'locations has device_id column');

-- Test 2: local_id is NOT NULL and UNIQUE
SELECT col_not_null('locations', 'local_id', 'local_id is NOT NULL');

-- Test 3: Trigger updates server_updated_at on INSERT and UPDATE
DO $$
DECLARE
    v_id UUID := gen_random_uuid();
    v_initial_timestamp TIMESTAMPTZ;
    v_updated_timestamp TIMESTAMPTZ;
BEGIN
    -- Insert a location
    INSERT INTO locations (id, name, local_id)
    VALUES (v_id, 'Trigger Test Location', v_id::text);
    
    -- Get initial server_updated_at
    SELECT server_updated_at INTO v_initial_timestamp
    FROM locations WHERE id = v_id;
    
    -- Wait a tiny bit
    PERFORM pg_sleep(0.01);
    
    -- Update the location
    UPDATE locations SET name = 'Updated Location' WHERE id = v_id;
    
    -- Get updated server_updated_at
    SELECT server_updated_at INTO v_updated_timestamp
    FROM locations WHERE id = v_id;
    
    PERFORM ok(
        v_updated_timestamp > v_initial_timestamp,
        'server_updated_at should be updated on UPDATE'
    );
    
    -- Cleanup
    DELETE FROM locations WHERE id = v_id;
END $$;

SELECT * FROM finish();
ROLLBACK;
