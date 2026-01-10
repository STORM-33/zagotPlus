# Session Brief: init-supabase

Type: feature
Phase: phase-0
Complexity: medium
Created: 2026-01-11

## Objective

Set up Supabase project with database schema and Row Level Security policies.

## Background

Supabase provides the cloud backend for sync between kiosk and mobile devices. Need tables for locations, products, and transactions with proper security.

## Requirements

- [ ] Create Supabase project (or configure existing one)
- [ ] Create `locations` table
- [ ] Create `products` table
- [ ] Create `transactions` table with all columns
- [ ] Create `inventory` view (computed from transactions)
- [ ] Enable Row Level Security on all tables
- [ ] Create RLS policies for anon access
- [ ] Create supabase/ directory with migration SQL files
- [ ] Document connection details (without secrets)

## Context Files

Load these files before starting:
- `.ctx/memory/project.md`
- `.ctx/memory/modules/supabase.md`
- `MASTER_PLAN.md` (Database Schema section)

## Implementation Notes

Schema from MASTER_PLAN.md:

```sql
-- locations: id, name, type, created_at
-- products: id, name, default_buy_price, default_sell_price, is_active, created_at
-- transactions: id, local_id (unique), location_id, type, transfer_location_id,
--               product_id, weight_kg, price_per_kg, total_amount, notes,
--               device_id, created_at, synced_at
-- inventory: VIEW computed from transactions
```

Transaction types: 'purchase', 'sale', 'transfer_out', 'transfer_in'

RLS: Simple anon access for all operations (single org, no multi-tenancy).

Save migration files in `supabase/migrations/` with timestamps.

## TDD

Mode: optional

### Test Plan
- [ ] All tables created successfully
- [ ] Inventory view returns correct aggregation
- [ ] RLS allows anon access

### Test Command
```
-- Manual verification via Supabase dashboard or psql
```

## Success Criteria

- [ ] All tables exist in Supabase
- [ ] `local_id` has UNIQUE constraint on transactions
- [ ] Inventory view computes correctly
- [ ] RLS policies allow CRUD operations
- [ ] Migration SQL files saved locally

## Out of Scope

- Realtime subscriptions setup (Phase 1)
- Android Supabase client config (Phase 1)
- Seed data

## Dependencies

- Requires: init-repo
- Blocks: supabase-sync (Phase 1)
