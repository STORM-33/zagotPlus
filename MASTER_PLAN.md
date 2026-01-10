# Master Plan: Zagot+ (Заготівля+)

## Project Overview

**Name:** Zagot+ (from Ukrainian "заготівля" - procurement/harvesting)  
**Purpose:** Inventory and accounting system for nut and pumpkin seed purchase/resale business  
**Tech Stack:** Kotlin, Android (Jetpack Compose, Room), Supabase

## Business Context

### Operations

- **Kiosk (tablet):** Purchase nuts/seeds from population, issue receipts, track inventory
- **Mobile (phone):** Purchase at various locations, sell wholesale to buyers, track transfers

### Hardware

- **Scales:** Dniprovesy VPD405E-T (60kg, RS-232) + USR-W610 (RS232→WiFi converter)
- **Printer:** Xprinter XP-58IIH (58mm thermal, Bluetooth)
- **Devices:** Android tablet (kiosk), Android phone (mobile)

### Constraints

- No internet at kiosk during work hours
- Kiosk syncs via phone hotspot at end of day
- Phone has mobile data, syncs frequently
- Must work fully offline with local database
- Single organization (no multi-tenancy)

---

## Architecture

```
┌─────────────────────────────────────────────────────────────┐
│                        ARCHITECTURE                         │
└─────────────────────────────────────────────────────────────┘

                         ┌──────────────────┐
                         │    SUPABASE      │
                         │                  │
                         │  PostgreSQL      │
                         │  Realtime        │
                         │  Auth (anon)     │
                         └────────┬─────────┘
                                  │
                    ┌─────────────┴─────────────┐
                    │  HTTPS (when connected)   │
                    │                           │
           ┌────────▼────────┐        ┌────────▼────────┐
           │     KIOSK       │        │     PHONE       │
           │    (tablet)     │        │    (mobile)     │
           │                 │        │                 │
           │  Room DB        │        │  Room DB        │
           │  (offline)      │        │  (online/       │
           │                 │        │   offline)      │
           └───────┬─────────┘        └─────────────────┘
                   │
        ┌──────────┼──────────┐
        ▼          ▼          ▼
    [Scales]  [Printer]  [Hotspot]
     TCP/IP   Bluetooth   (sync)
```

### Key Design Decisions

1. **Supabase instead of custom backend** - handles sync, realtime, auth out of the box
2. **Transactions are immutable** - no edits, only reversals
3. **Inventory is computed** - sum of transactions, not stored state
4. **Conflict resolution** - append-only transactions + unique `local_id` = no conflicts
5. **Negative inventory** - allowed, flagged as warning (business problem, not tech)

---

## Database Schema

### Supabase Tables

```sql
create table locations (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  type text not null check (type in ('kiosk', 'mobile')),
  created_at timestamptz default now()
);

create table products (
  id uuid primary key default gen_random_uuid(),
  name text not null,
  default_buy_price numeric(10,2),
  default_sell_price numeric(10,2),
  is_active boolean default true,
  created_at timestamptz default now()
);

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

-- Inventory is a VIEW, not a table
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

### Row Level Security

```sql
-- Simple RLS: all devices use anon key, just prevent external access
alter table locations enable row level security;
alter table products enable row level security;
alter table transactions enable row level security;

create policy "Allow all for anon" on locations for all using (true) with check (true);
create policy "Allow all for anon" on products for all using (true) with check (true);
create policy "Allow all for anon" on transactions for all using (true) with check (true);
```

### Transfer Logic

Physical goods movement creates two linked transactions:

```
Transfer 20kg walnuts from Kiosk → Phone:

1. type='transfer_out', location=kiosk,  transfer_location=phone, weight=20
2. type='transfer_in',  location=phone,  transfer_location=kiosk, weight=20
```

Both created atomically on initiating device, synced together.

---

## Sync Strategy

### Flow

1. Device creates transaction → saved to Room with `synced_at = null`
2. WorkManager detects pending transactions
3. Push to Supabase (upsert with `local_id` as conflict key)
4. Pull new transactions from other devices (where `created_at > last_sync`)
5. Mark local as synced

### Conflict Handling

| Scenario | Resolution |
|----------|------------|
| Same transaction synced twice | `local_id` UNIQUE constraint ignores duplicate |
| Both devices sold same stock | Both valid. Negative inventory = warning |
| Need to "edit" transaction | Create reversal transaction instead |

---

## Development Phases

### Phase 0: Setup (3 sessions)

| # | Session | Complexity | Description |
|---|---------|------------|-------------|
| 1 | init-repo | low | Monorepo structure, .gitignore, README, CLAUDE.md |
| 2 | init-android | medium | Android project, Gradle, Hilt skeleton |
| 3 | init-supabase | medium | Supabase project, schema, RLS policies |

### Phase 1: Data Layer (4 sessions)

| # | Session | Complexity | Description |
|---|---------|------------|-------------|
| 1 | room-schema | medium | Room entities, DAOs, type converters |
| 2 | repository | medium | Repository pattern, offline-first logic |
| 3 | supabase-sync | high | Sync service, push/pull, deduplication |
| 4 | sync-worker | medium | WorkManager, retry logic, sync status |

### Phase 2: Core UI (5 sessions)

| # | Session | Complexity | Description |
|---|---------|------------|-------------|
| 1 | navigation | medium | Nav graph, bottom bar, screen scaffolds |
| 2 | auth-pin | low | PIN entry, simple session management |
| 3 | screen-purchase | high | Buy flow with mock weight input |
| 4 | screen-sale | high | Sell flow with manual weight entry |
| 5 | screen-inventory | medium | Stock view computed from transactions |

### Phase 3: Supporting UI (4 sessions)

| # | Session | Complexity | Description |
|---|---------|------------|-------------|
| 1 | screen-history | medium | Transaction list, filters, search |
| 2 | screen-products | medium | Product CRUD |
| 3 | screen-reports | medium | Daily summary, basic export |
| 4 | screen-settings | low | Sync status, device config |

### Phase 4: Hardware (4 sessions)

| # | Session | Complexity | Description |
|---|---------|------------|-------------|
| 1 | scales-integration | high | TCP socket, USR-W610, protocol parsing |
| 2 | printer-integration | high | Bluetooth, ESC/POS commands |
| 3 | receipt-format | low | Receipt template for purchases |
| 4 | polish | medium | Error handling, edge cases, UX |

**Total: 20 sessions**

---

## Hardware Notes

### Scales (Dniprovesy VPD405E-T via USR-W610)

```
CONNECTION:
- USR-W610 bridges RS-232 to WiFi (TCP socket)
- Android connects to USR-W610 IP:port

EXPECTED DATA FORMAT:
"ST,GS,+  12.34kg\r\n"
 │  │  │  └── weight value
 │  │  └───── sign (+/-)
 │  └──────── status (GS=stable, US=unstable)
 └─────────── header

PARSING:
- Regex: ST,(\w+),([+-])\s*(\d+\.?\d*)\s*kg
- Only accept GS (stable) readings for transactions
```

### Printer (XP-58IIH via Bluetooth)

```
CONNECTION:
- Bluetooth SPP profile
- ESC/POS command set

KEY COMMANDS:
- ESC @     : Initialize
- ESC a n   : Align (0=left, 1=center, 2=right)
- ESC ! n   : Font size
- GS V m    : Cut paper
- ESC d n   : Feed n lines
```

### Receipt Format (Purchase)

```
┌────────────────────────────────┐
│       ФОП Петренко             │  center, bold
│    вул. Центральна, 5          │  center
├────────────────────────────────┤
│ Чек №: 00047                   │
│ Дата: 29.12.2025 14:35         │
├────────────────────────────────┤
│ Горіх білий      24.5 кг       │
│   45.00 грн/кг     1 102.50    │
│                                │
│ Насіння біле     12.0 кг       │
│   50.00 грн/кг       600.00    │
├────────────────────────────────┤
│ РАЗОМ:           1 702.50 грн  │  bold
│ Оплата: готівка                │
├────────────────────────────────┤
│     Дякуємо за співпрацю!      │  center
└────────────────────────────────┘
```

---

## Repository Structure

```
zagot-plus/
├── CLAUDE.md                    # Claude Auto OS config
├── MASTER_PLAN.md               # This file
├── .ctx/
│   ├── state.md
│   ├── plan.md
│   ├── journal.md
│   ├── scratchpad.md
│   ├── memory/
│   │   ├── project.md
│   │   └── modules/
│   │       ├── android.md
│   │       ├── supabase.md
│   │       └── hardware.md
│   └── sessions/
│       └── {phase}/{session}/
│
├── android/
│   ├── build.gradle.kts
│   └── app/
│       └── src/main/kotlin/com/zagot/
│           ├── ZagotApp.kt
│           ├── MainActivity.kt
│           ├── data/
│           │   ├── local/           # Room
│           │   ├── remote/          # Supabase
│           │   └── repository/
│           ├── domain/
│           │   └── model/
│           ├── sync/
│           │   └── SyncWorker.kt
│           └── ui/
│               ├── navigation/
│               └── screens/
│
└── docs/
    ├── HARDWARE.md
    └── RECEIPTS.md
```

---

## project.md Content

```markdown
# Project: Zagot+

## Purpose
Inventory and accounting system for agricultural procurement business.
Purchases from population, stock tracking, transfers, wholesale sales.
Offline-first with Supabase sync.

## Tech Stack
- Android: Kotlin + Jetpack Compose + Room + Hilt
- Backend: Supabase (PostgreSQL + Realtime)
- Hardware: Scales (TCP/IP), Printer (Bluetooth ESC/POS)

## Architecture
- Offline-first: Room is source of truth
- Sync: Push pending → Pull new from others
- Inventory: Computed from transactions (not stored)

## Conventions
- Kotlin: Official style guide
- Commits: Conventional (feat/fix/refactor/test/docs)
- Tests: TDD for business logic

## Entry Points
- ZagotApp.kt - Application class
- MainActivity.kt - Single activity
```

---

## Session Brief Template

```markdown
# Session: {session-name}

Phase: {phase}
Complexity: {low|medium|high}
Created: {date}

## Objective
{One sentence}

## Requirements
- [ ] {Requirement 1}
- [ ] {Requirement 2}

## Context Files
- `.ctx/memory/project.md`
- `.ctx/memory/modules/{module}.md`

## Implementation Notes
{Approach, patterns}

## Success Criteria
- [ ] Works as described
- [ ] Tests pass

## Out of Scope
- {What NOT to do}

## Dependencies
- Requires: {session-name | none}
```

---

## Risk Mitigation

| Risk | Mitigation |
|------|------------|
| USR-W610 protocol unknown | Phase 4 research, fallback to manual entry |
| Printer compatibility issues | Phase 4 research, test before polish |
| Supabase free tier limits | Monitor usage, upgrade if needed |
| Offline data loss | Room with WAL, periodic JSON backup |

---

## Success Criteria

### MVP Complete

- [ ] Purchase goods at kiosk (mock weight)
- [ ] Purchase goods on phone
- [ ] Sell goods on phone
- [ ] Transfer between locations
- [ ] View inventory (both devices)
- [ ] Sync works reliably

### Production Ready

- [ ] Scales integration works
- [ ] Printer integration works
- [ ] Receipt printing works
- [ ] Error handling complete
- [ ] Tested on real devices

---

## Legal Notes (For Later)

**Purchase receipts:** When buying from population, this is "акт закупівлі" (purchase act), not fiscal receipt. No РРО required - just internal accounting document.

**Sales:** Wholesale to businesses = накладна (invoice). Sales to individuals may require РРО above threshold. Consult accountant before launch.

---

*Last updated: January 2025*
