# Session: db-batches

Type: feature
Complexity: medium
Status: pending

## Objective
Add database support for purchase batches - grouping multiple transaction line items under one client session.

## Context
Currently transactions are individual. For the new purchase flow, we need to group purchases by "client" (a single weighing session with multiple products). Notes apply to the batch, not individual items.

## Requirements

### Supabase Migration
Add to new migration file:
```sql
CREATE TABLE purchase_batches (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  local_id TEXT UNIQUE NOT NULL,
  location_id UUID REFERENCES locations(id),
  notes TEXT,
  total_weight_kg NUMERIC(10,3),
  total_amount NUMERIC(10,2),
  item_count INTEGER,
  device_id TEXT,
  created_at TIMESTAMPTZ DEFAULT now(),
  synced_at TIMESTAMPTZ
);

ALTER TABLE transactions ADD COLUMN batch_id UUID REFERENCES purchase_batches(id);
CREATE INDEX idx_transactions_batch ON transactions(batch_id);
```

### Room Changes
1. `PurchaseBatchEntity` - mirrors Supabase table
2. `PurchaseBatchDao` - CRUD + today's batches query
3. Add `batchId` column to `TransactionEntity`
4. Database migration (version 1 → 2)

### Domain Layer
1. `PurchaseBatch` domain model
2. `PurchaseBatchRepository` interface + implementation
3. Update `TransactionRepository` to support batch creation

## Success Criteria
- [ ] Supabase migration SQL created
- [ ] Room entities and DAOs work
- [ ] Can create batch with multiple transactions atomically
- [ ] Can query today's batches
- [ ] Database migration tested (no data loss)
- [ ] Build passes

## Files to Create/Modify
- `supabase/migrations/20260111000001_purchase_batches.sql` (create)
- `data/local/entity/PurchaseBatchEntity.kt` (create)
- `data/local/dao/PurchaseBatchDao.kt` (create)
- `data/local/entity/TransactionEntity.kt` (modify - add batchId)
- `data/local/ZagotDatabase.kt` (modify - add entity, bump version, migration)
- `domain/model/PurchaseBatch.kt` (create)
- `domain/repository/PurchaseBatchRepository.kt` (create)
- `data/repository/PurchaseBatchRepositoryImpl.kt` (create)
- `data/repository/RepositoryModule.kt` (modify - add binding)

## Notes
- Use @Transaction annotation for atomic batch+transactions insert
- batch local_id format: UUID (same as transactions)
