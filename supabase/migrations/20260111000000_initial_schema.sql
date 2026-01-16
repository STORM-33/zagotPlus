-- Zagot+ Initial Database Schema
-- Created: 2026-01-11
-- Description: Core tables for inventory and transaction management

-- ============================================================================
-- TABLES
-- ============================================================================

-- Locations table
-- Represents physical locations where transactions occur (kiosk, mobile)
create table locations (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  type text not null check (type in ('kiosk', 'mobile')),
  created_at timestamptz default now()
);

-- Products table
-- Catalog of products (nuts and seeds) with default pricing
create table products (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  default_buy_price numeric(10,2),
  default_sell_price numeric(10,2),
  is_active boolean default true,
  created_at timestamptz default now()
);

-- Transactions table
-- Append-only ledger of all operations (purchase, sale, transfer_out, transfer_in)
create table transactions (
  id uuid primary key default gen_random_uuid(),
  local_id text unique not null,           -- device-generated UUID, prevents duplicates
  location_id uuid references locations(id),
  type text not null check (type in ('purchase', 'sale', 'transfer_out', 'transfer_in')),
  transfer_location_id uuid references locations(id),  -- for transfers: other location
  product_id uuid references products(id),
  weight_kg numeric(10,3) not null,
  price_per_kg numeric(10,2),
  total_amount numeric(10,2),
  notes text,
  device_id text,                          -- identifies which device created it
  created_at timestamptz default now(),
  synced_at timestamptz
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Improve query performance for common access patterns
create index idx_transactions_local_id on transactions(local_id);
create index idx_transactions_location on transactions(location_id);
create index idx_transactions_product on transactions(product_id);
create index idx_transactions_created on transactions(created_at desc);
create index idx_transactions_synced on transactions(synced_at) where synced_at is null;

-- ============================================================================
-- VIEWS
-- ============================================================================

-- Inventory view
-- Computes current stock levels from transaction history
create view inventory as
select
  location_id,
  product_id,
  sum(case
    when type in ('purchase', 'transfer_in') then weight_kg
    when type in ('sale', 'transfer_out') then -weight_kg
  end) as quantity_kg
from transactions
group by location_id, product_id;

-- ============================================================================
-- ROW LEVEL SECURITY (RLS)
-- ============================================================================

-- Enable RLS on all tables
alter table locations enable row level security;
alter table products enable row level security;
alter table transactions enable row level security;

-- Create permissive policies for anon key access
-- (Single organization, trusted devices - all operations allowed)
create policy "Allow all for anon" on locations for all using (true) with check (true);
create policy "Allow all for anon" on products for all using (true) with check (true);
create policy "Allow all for anon" on transactions for all using (true) with check (true);

-- ============================================================================
-- SEED DATA
-- ============================================================================

-- Initial locations (fixed UUIDs for consistency across environments)
insert into locations (id, name, type) values
  ('00000000-0000-0000-0000-000000000001', 'Кіоск', 'kiosk'),
  ('00000000-0000-0000-0000-000000000002', 'Склад', 'mobile');

-- Initial products
insert into products (id, name, default_buy_price, default_sell_price) values
  ('00000000-0000-0000-0000-000000000011', 'Горіх білий', 45.00, 65.00),
  ('00000000-0000-0000-0000-000000000012', 'Горіх чорний', 40.00, 60.00),
  ('00000000-0000-0000-0000-000000000013', 'Насіння біле', 50.00, 70.00),
  ('00000000-0000-0000-0000-000000000014', 'Насіння чорне', 48.00, 68.00);
