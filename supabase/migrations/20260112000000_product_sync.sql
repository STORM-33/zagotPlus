-- Zagot+ Product Sync Support
-- Created: 2026-01-12
-- Description: Add local_id column to products table for bidirectional sync

-- Add local_id column for sync deduplication
alter table products add column local_id text;

-- Set existing products to use their id as local_id
update products set local_id = id::text where local_id is null;

-- Make local_id not null and unique
alter table products alter column local_id set not null;
create unique index idx_products_local_id on products(local_id);

-- Add synced_at column for tracking sync status (optional, for future use)
alter table products add column synced_at timestamptz;
