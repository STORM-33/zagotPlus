-- Zagot+ Purchase Batches Migration
-- Created: 2026-01-11
-- Description: Add purchase_batches table and batch_id FK to transactions

-- ============================================================================
-- NEW TABLE: purchase_batches
-- ============================================================================

-- Purchase batches group multiple transaction line items per client session
create table purchase_batches (
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

create index idx_purchase_batches_local_id on purchase_batches(local_id);
create index idx_purchase_batches_location on purchase_batches(location_id);
create index idx_purchase_batches_created on purchase_batches(created_at desc);
create index idx_purchase_batches_synced on purchase_batches(synced_at) where synced_at is null;

-- ============================================================================
-- ALTER TRANSACTIONS TABLE
-- ============================================================================

-- Add batch_id FK to transactions (nullable for backward compatibility)
alter table transactions add column batch_id uuid references purchase_batches(id);
create index idx_transactions_batch on transactions(batch_id);

-- ============================================================================
-- ROW LEVEL SECURITY
-- ============================================================================

alter table purchase_batches enable row level security;
create policy "Allow all for anon" on purchase_batches for all using (true) with check (true);
