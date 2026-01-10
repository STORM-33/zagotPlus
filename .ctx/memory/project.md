# Project: Zagot+ (Заготівля+)

## Purpose
Inventory and accounting system for agricultural procurement business (nuts and pumpkin seeds).
- Purchase from population at kiosk (tablet) and mobile locations (phone)
- Track inventory across locations
- Transfer goods between locations
- Wholesale sales to buyers
- Offline-first architecture with cloud sync via Supabase

## Tech Stack
- **Android**: Kotlin, Jetpack Compose, Room Database, Hilt DI, WorkManager
- **Backend**: Supabase (PostgreSQL + Realtime + Auth)
- **Hardware**:
  - Scales: Dniprovesy VPD405E-T via USR-W610 (RS232→WiFi, TCP/IP)
  - Printer: Xprinter XP-58IIH (Bluetooth, ESC/POS)

## Structure
```
zagot-plus/
├── CLAUDE.md                    # Claude Auto OS config
├── MASTER_PLAN.md               # Complete project specification
├── .ctx/                        # Claude Auto OS workspace
├── android/                     # Android app (Kotlin + Compose)
│   └── app/src/main/kotlin/com/zagot/
│       ├── data/                # Room + Supabase + Repositories
│       ├── domain/              # Models and business logic
│       ├── sync/                # WorkManager sync service
│       └── ui/                  # Compose screens + navigation
├── docs/                        # Hardware integration docs
└── supabase/                    # SQL migrations (future)
```

## Architecture

### Key Design Decisions
1. **Offline-first**: Room is source of truth, Supabase is sync target
2. **Immutable transactions**: No edits, only reversals (append-only ledger)
3. **Computed inventory**: Sum of transactions in real-time (not stored state)
4. **Conflict-free sync**: UUID `local_id` prevents duplicates, append-only prevents conflicts
5. **Negative inventory allowed**: Business problem (flagged as warning), not technical blocker

### Data Flow
```
User Action → Room DB (immediate) → WorkManager (background) → Supabase (when online)
                ↓                                                      ↓
           Local UI Update ←─────────────── Realtime subscription ←───┘
```

### Sync Strategy
- Push: Local transactions with `synced_at=null` → Supabase (upsert by `local_id`)
- Pull: New transactions from other devices (`created_at > last_sync`)
- No conflicts: `local_id` UNIQUE constraint handles duplicates

### Transfer Logic
Physical goods movement creates two linked transactions atomically:
- `transfer_out` at source location (-weight)
- `transfer_in` at destination location (+weight)

## Conventions
- **Kotlin**: Official Android style guide
- **Commits**: Conventional commits (`feat:`, `fix:`, `refactor:`, `test:`, `docs:`, `chore:`)
- **Git**: Feature branches (`feature/{plan-name}`), merge to main via PR
- **TDD**: Encouraged for features, strict for bugfixes (regression tests required)
- **Language**: UI text in Ukrainian, code/comments in English

## Entry Points
- `ZagotApp.kt` - Application class (Hilt setup)
- `MainActivity.kt` - Single activity host
- `data/repository/*Repository.kt` - Data access layer
- `sync/SyncWorker.kt` - Background sync orchestration

## Database Schema

### Core Tables (Supabase)
- `locations` - Kiosk and mobile purchase points
- `products` - Nut/seed types with default prices
- `transactions` - All operations (purchase, sale, transfer_in, transfer_out)
- `inventory` - VIEW (computed from transactions)

### Transaction Types
| Type | Effect | Example |
|------|--------|---------|
| `purchase` | +inventory | Buy from population |
| `sale` | -inventory | Sell to buyer |
| `transfer_out` | -inventory | Send to other location |
| `transfer_in` | +inventory | Receive from other location |

## Hardware Integration

### Scales (TCP/IP)
- Connect to USR-W610 WiFi adapter at `{ip}:{port}`
- Parse: `ST,GS,+  12.34kg\r\n` (GS=stable, US=unstable)
- Only accept stable readings for transactions

### Printer (Bluetooth ESC/POS)
- Pair via Bluetooth SPP
- Send ESC/POS commands for receipt formatting
- Receipt format: Header (center) + Items (left) + Total (bold)

## Operational Constraints
- Kiosk has **no internet** during work hours → offline-first critical
- Phone has mobile data → syncs frequently, acts as bridge
- Kiosk syncs via phone hotspot at end of day
- Single organization (no multi-tenancy)

## Status
Memory initialized - ready for planning

## Development Phases
See MASTER_PLAN.md for complete breakdown:
- Phase 0: Setup (3 sessions) - repo, Android project, Supabase schema
- Phase 1: Data Layer (4 sessions) - Room, repositories, sync
- Phase 2: Core UI (5 sessions) - navigation, auth, purchase/sale/inventory screens
- Phase 3: Supporting UI (4 sessions) - history, products, reports, settings
- Phase 4: Hardware (4 sessions) - scales, printer, receipts, polish

Total: 20 sessions
