-- Zagot+ Cash Operations Schema
-- Created: 2026-01-13
-- Description: Tables for cash operations and expense categories sync

-- ============================================================================
-- EXPENSE CATEGORIES TABLE
-- ============================================================================

-- User-defined categories for organizing cash payments
create table expense_categories (
  id uuid primary key default gen_random_uuid(),
  local_id text unique not null,           -- device-generated UUID, prevents duplicates
  name text not null,
  is_active boolean default true,
  created_at timestamptz default now(),
  synced_at timestamptz
);

-- ============================================================================
-- CASH OPERATIONS TABLE
-- ============================================================================

-- Tracks all cash movements: deposits, withdrawals, payments, and purchases
create table cash_operations (
  id uuid primary key default gen_random_uuid(),
  local_id text unique not null,           -- device-generated UUID, prevents duplicates
  location_id uuid references locations(id),
  type text not null check (type in ('deposit', 'withdrawal', 'payment', 'purchase')),
  amount numeric(10,2) not null,
  category_id uuid references expense_categories(id) on delete set null,
  transaction_id uuid references transactions(id) on delete cascade,
  notes text,
  device_id text,                          -- identifies which device created it
  created_at timestamptz default now(),
  synced_at timestamptz
);

-- ============================================================================
-- INDEXES
-- ============================================================================

-- Expense categories indexes
create index idx_expense_categories_local_id on expense_categories(local_id);
create index idx_expense_categories_synced on expense_categories(synced_at) where synced_at is null;

-- Cash operations indexes
create index idx_cash_operations_local_id on cash_operations(local_id);
create index idx_cash_operations_location on cash_operations(location_id);
create index idx_cash_operations_category on cash_operations(category_id);
create index idx_cash_operations_transaction on cash_operations(transaction_id);
create index idx_cash_operations_created on cash_operations(created_at desc);
create index idx_cash_operations_synced on cash_operations(synced_at) where synced_at is null;
create index idx_cash_operations_type on cash_operations(type);

-- ============================================================================
-- VIEWS
-- ============================================================================

-- Cash balance view per location
create view cash_balance as
select
  location_id,
  sum(case
    when type = 'deposit' then amount
    when type in ('withdrawal', 'payment', 'purchase') then -amount
  end) as balance
from cash_operations
group by location_id;

-- Total cash balance view
create view total_cash_balance as
select
  sum(case
    when type = 'deposit' then amount
    when type in ('withdrawal', 'payment', 'purchase') then -amount
  end) as balance
from cash_operations;

-- ============================================================================
-- ROW LEVEL SECURITY (RLS)
-- ============================================================================

-- Enable RLS on tables
alter table expense_categories enable row level security;
alter table cash_operations enable row level security;

-- Create permissive policies for anon key access
create policy "Allow all for anon" on expense_categories for all using (true) with check (true);
create policy "Allow all for anon" on cash_operations for all using (true) with check (true);
