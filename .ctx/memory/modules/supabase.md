# Module: Supabase Backend

Updated: 2026-01-10

## Overview

Supabase provides PostgreSQL database, Realtime subscriptions, and simple auth.
Used as sync target for offline-first Android app.

## Project Configuration

- **URL**: `https://{project-ref}.supabase.co`
- **Anon Key**: Public key for client connections (RLS enforces security)
- **Service Role Key**: Server-side only (not used in app)

## Database Schema

### Tables

#### `locations`
```sql
create table locations (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  type text not null check (type in ('kiosk', 'mobile')),
  created_at timestamptz default now()
);
```

**Purpose**: Physical locations where transactions occur (kiosk, phone)

#### `products`
```sql
create table products (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  default_buy_price numeric(10,2),
  default_sell_price numeric(10,2),
  is_active boolean default true,
  created_at timestamptz default now()
);
```

**Purpose**: Product catalog (types of nuts/seeds)

#### `transactions`
```sql
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

create index idx_transactions_local_id on transactions(local_id);
create index idx_transactions_location on transactions(location_id);
create index idx_transactions_created on transactions(created_at desc);
create index idx_transactions_synced on transactions(synced_at) where synced_at is null;
```

**Purpose**: All operations (purchases, sales, transfers). Append-only ledger.

**Key Fields**:
- `local_id`: Unique across all devices, prevents duplicate syncs
- `transfer_location_id`: For transfers, references the other location
- `synced_at`: null = pending sync

#### `inventory` (VIEW)
```sql
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
```

**Purpose**: Real-time computed inventory. Not stored, always fresh.

## Row Level Security (RLS)

Simple policy: allow all operations with anon key (single organization, trusted devices)

```sql
alter table locations enable row level security;
alter table products enable row level security;
alter table transactions enable row level security;

create policy "Allow all for anon" on locations for all using (true) with check (true);
create policy "Allow all for anon" on products for all using (true) with check (true);
create policy "Allow all for anon" on transactions for all using (true) with check (true);
```

**Note**: In production, consider more restrictive policies (e.g., by device_id or location_id)

## Sync Strategy

### Push (Device → Supabase)

```kotlin
// 1. Get pending transactions from Room
val pending = transactionDao.getPendingSync()

// 2. Upsert to Supabase (local_id prevents duplicates)
pending.forEach { tx ->
    supabase.from("transactions")
        .upsert(tx.toDto(), onConflict = "local_id")
        .execute()

    // 3. Mark as synced in Room
    transactionDao.markSynced(tx.localId, Instant.now())
}
```

### Pull (Supabase → Device)

```kotlin
// 1. Get last sync timestamp from preferences
val lastSync = preferences.getLastSyncTime()

// 2. Fetch new transactions from other devices
val newTx = supabase.from("transactions")
    .select()
    .gt("created_at", lastSync)
    .neq("device_id", currentDeviceId) // exclude own transactions
    .execute()

// 3. Insert into Room
newTx.forEach { tx ->
    transactionDao.insert(tx.toEntity().copy(syncedAt = Instant.now()))
}

// 4. Update last sync timestamp
preferences.setLastSyncTime(Instant.now())
```

### Conflict Resolution

**No conflicts!** Design prevents them:
- Transactions are append-only (never edited)
- `local_id` UNIQUE constraint handles duplicate syncs
- Multiple devices can create transactions independently
- Negative inventory is allowed (business decision, not error)

## Realtime Subscriptions

Optional: Listen for new transactions from other devices

```kotlin
val subscription = supabase.from("transactions")
    .on(SupabaseEvent.INSERT) { payload ->
        val newTx = payload.decodeAs<TransactionDto>()
        if (newTx.deviceId != currentDeviceId) {
            // Insert into Room immediately
            transactionDao.insert(newTx.toEntity())
        }
    }
    .subscribe()
```

**Use case**: Phone sees kiosk transactions instantly (when both online)

## Transfer Logic

Transfers create two linked transactions atomically:

```kotlin
suspend fun createTransfer(
    fromLocation: UUID,
    toLocation: UUID,
    product: UUID,
    weight: BigDecimal
) {
    val transferId = UUID.randomUUID() // link both transactions

    val txOut = Transaction(
        localId = UUID.randomUUID(),
        locationId = fromLocation,
        type = TransactionType.TRANSFER_OUT,
        transferLocationId = toLocation,
        productId = product,
        weightKg = weight,
        notes = "Transfer #$transferId"
    )

    val txIn = Transaction(
        localId = UUID.randomUUID(),
        locationId = toLocation,
        type = TransactionType.TRANSFER_IN,
        transferLocationId = fromLocation,
        productId = product,
        weightKg = weight,
        notes = "Transfer #$transferId"
    )

    // Insert both atomically
    transactionDao.insertAll(txOut, txIn)
    // Sync will handle pushing to Supabase
}
```

## Client Configuration

```kotlin
// SupabaseClient.kt
@Singleton
class SupabaseClient @Inject constructor() {
    val client = createSupabaseClient(
        supabaseUrl = BuildConfig.SUPABASE_URL,
        supabaseKey = BuildConfig.SUPABASE_ANON_KEY
    ) {
        install(Postgrest)
        install(Realtime)
    }
}
```

## Data Transfer Objects (DTOs)

```kotlin
// Supabase expects camelCase, Room uses snake_case
@Serializable
data class TransactionDto(
    val id: String,
    val localId: String,
    val locationId: String,
    val type: String,
    val transferLocationId: String?,
    val productId: String,
    val weightKg: String, // BigDecimal as String
    val pricePerKg: String?,
    val totalAmount: String?,
    val notes: String?,
    val deviceId: String,
    val createdAt: String,
    val syncedAt: String?
)

// Mapper extensions
fun TransactionEntity.toDto(): TransactionDto = TransactionDto(...)
fun TransactionDto.toEntity(): TransactionEntity = TransactionEntity(...)
```

## Error Handling

```kotlin
// Sync errors should not crash app
try {
    syncPending()
} catch (e: SupabaseException) {
    Log.e("Sync", "Failed to sync: ${e.message}")
    // Retry later via WorkManager
} catch (e: NetworkException) {
    Log.w("Sync", "No network, will retry")
    // Skip, try next sync interval
}
```

## Seed Data

```sql
-- Initial locations
insert into locations (id, name, type) values
  ('00000000-0000-0000-0000-000000000001', 'Кіоск', 'kiosk'),
  ('00000000-0000-0000-0000-000000000002', 'Мобільний', 'mobile');

-- Initial products
insert into products (id, name, default_buy_price, default_sell_price) values
  ('00000000-0000-0000-0000-000000000011', 'Горіх білий', 45.00, 65.00),
  ('00000000-0000-0000-0000-000000000012', 'Горіх чорний', 40.00, 60.00),
  ('00000000-0000-0000-0000-000000000013', 'Насіння біле', 50.00, 70.00),
  ('00000000-0000-0000-0000-000000000014', 'Насіння чорне', 48.00, 68.00);
```

## Performance Considerations

- **Indexes**: Created on `local_id`, `location_id`, `created_at`, `synced_at`
- **Pagination**: Use `range()` for large transaction lists
- **Batch sync**: Sync up to 100 transactions per request
- **Incremental pull**: Only fetch since last sync timestamp

## Monitoring

- Check Supabase dashboard for:
  - Table size growth
  - Query performance
  - API request count
  - Realtime connections
- Free tier limits: 500MB DB, 2GB bandwidth/month
