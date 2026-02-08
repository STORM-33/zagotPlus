/**
 * Local persistence layer using Room database.
 *
 * This package contains:
 * - [ZagotDatabase] - Main Room database with 7 business entities + 2 sync entities
 * - [DatabaseModule] - Hilt DI module for database and DAO providers
 * - [DatabaseExporter] - Utility for exporting database for debugging
 *
 * Sub-packages:
 * - [dao] - Data Access Objects for CRUD operations
 * - [entity] - Room entities with relationships and indices
 * - [converter] - Type converters for complex types (BigDecimal, UUID, etc.)
 *
 * Database schema (v15):
 * - locations, products, purchase_batches, sale_batches
 * - transactions, expense_categories, cash_operations
 * - sync_metadata, sync_outbox (sync engine tables)
 *
 * All migrations are explicit - NEVER use destructive migration to preserve user data.
 */
package com.zagot.zagotplus.data.local
