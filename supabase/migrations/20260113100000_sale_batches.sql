-- Zagot+ Sale Batches Migration
-- Created: 2026-01-13
-- Description: Add sale_batches table and sale_batch_id FK to transactions

-- ============================================================================
-- NEW TABLE: sale_batches
-- ============================================================================

-- Sale batches group multiple sale transaction line items per client session
create table sale_batches (
  id uuid primary key default gen_random_uuid(),
  local_id text unique not null,           -- device-generated UUID, prevents duplicates
  location_id uuid references locations(id),
  notes text,
  total_weight_kg numeric(10,3),
  total_amount numeric(10,2),
  item_count integer,
  device_id text,                          -- identifies which device created it
  created_at timestamptz default now(),
  synced_at timestamptz
);

-- ============================================================================
-- INDEXES
-- ============================================================================

create index idx_sale_batches_local_id on sale_batches(local_id);
create index idx_sale_batches_location on sale_batches(location_id);
create index idx_sale_batches_created on sale_batches(created_at desc);
create index idx_sale_batches_synced on sale_batches(synced_at) where synced_at is null;

-- ============================================================================
-- ALTER TRANSACTIONS TABLE
-- ============================================================================

-- Add sale_batch_id FK to transactions (nullable for backward compatibility)
alter table transactions add column sale_batch_id uuid references sale_batches(id);
create index idx_transactions_sale_batch on transactions(sale_batch_id);

-- ============================================================================
-- ROW LEVEL SECURITY
-- ============================================================================

alter table sale_batches enable row level security;
create policy "Allow all for anon" on sale_batches for all using (true) with check (true);
