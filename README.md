# Zagot+ (Заготівля+)

Inventory and accounting system for agricultural procurement business (nuts and pumpkin seeds).

## Overview

Zagot+ manages the complete lifecycle of purchasing from the population, tracking inventory across locations, and wholesale sales to buyers. Designed for offline-first operation with cloud synchronization.

## Tech Stack

- **Android**: Kotlin, Jetpack Compose, Room Database, Hilt DI, WorkManager
- **Backend**: Supabase (PostgreSQL + Realtime + Auth)
- **Hardware**:
  - Scales: Dniprovesy VPD405E-T via USR-W610 (RS232→WiFi, TCP/IP)
  - Printer: Xprinter XP-58IIH (Bluetooth, ESC/POS)

## Architecture

- **Offline-first**: Room is the source of truth, Supabase is the sync target
- **Immutable transactions**: No edits, only reversals (append-only ledger)
- **Computed inventory**: Real-time sum of transactions (not stored state)
- **Conflict-free sync**: UUID `local_id` prevents duplicates

## Operations

- **Kiosk (tablet)**: Purchase from population at fixed location, print receipts
- **Mobile (phone)**: Purchase at various locations, wholesale sales, inventory transfers
- Kiosk operates offline during work hours, syncs via phone hotspot at end of day
- Phone has mobile data, syncs frequently

## Setup

_Coming soon: setup instructions will be added as the project develops._

## Development

See `MASTER_PLAN.md` for complete project specification and development phases.

Project managed using Claude Auto OS workflow system (see `CLAUDE.md`).

## License

Proprietary - Internal use only
