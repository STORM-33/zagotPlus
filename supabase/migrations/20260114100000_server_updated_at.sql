-- Zagot+ Server Updated At Migration
-- Created: 2026-01-14
-- Description: Add server_updated_at column to fix sync edge case
--
-- Problem: Using created_at for sync filtering can miss records.
-- If Device A creates a transaction at 12:00, but doesn't sync until 12:10,
-- and Device B syncs at 12:05, Device B might never see Device A's 12:00 transaction.
--
-- Solution: Use server_updated_at which is set by a trigger when the server
-- receives the record. Pulling data based on when the server received it
-- ensures no gaps are left.

-- ============================================================================
-- FUNCTION: Update server_updated_at on insert/update
-- ============================================================================

create or replace function update_server_updated_at()
returns trigger as $$
begin
  new.server_updated_at = now();
  return new;
end;
$$ language plpgsql;

-- ============================================================================
-- ADD server_updated_at TO ALL SYNCED TABLES
-- ============================================================================

-- transactions table
alter table transactions add column server_updated_at timestamptz default now();
create index idx_transactions_server_updated on transactions(server_updated_at);

create trigger trg_transactions_server_updated_at
  before insert or update on transactions
  for each row execute function update_server_updated_at();

-- purchase_batches table
alter table purchase_batches add column server_updated_at timestamptz default now();
create index idx_purchase_batches_server_updated on purchase_batches(server_updated_at);

create trigger trg_purchase_batches_server_updated_at
  before insert or update on purchase_batches
  for each row execute function update_server_updated_at();

-- sale_batches table
alter table sale_batches add column server_updated_at timestamptz default now();
create index idx_sale_batches_server_updated on sale_batches(server_updated_at);

create trigger trg_sale_batches_server_updated_at
  before insert or update on sale_batches
  for each row execute function update_server_updated_at();

-- expense_categories table
alter table expense_categories add column server_updated_at timestamptz default now();
create index idx_expense_categories_server_updated on expense_categories(server_updated_at);

create trigger trg_expense_categories_server_updated_at
  before insert or update on expense_categories
  for each row execute function update_server_updated_at();

-- cash_operations table
alter table cash_operations add column server_updated_at timestamptz default now();
create index idx_cash_operations_server_updated on cash_operations(server_updated_at);

create trigger trg_cash_operations_server_updated_at
  before insert or update on cash_operations
  for each row execute function update_server_updated_at();

-- products table (for user-created products that sync)
alter table products add column server_updated_at timestamptz default now();
create index idx_products_server_updated on products(server_updated_at);

create trigger trg_products_server_updated_at
  before insert or update on products
  for each row execute function update_server_updated_at();
