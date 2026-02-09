package com.zagot.zagotplus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zagot.zagotplus.data.local.converter.Converters
import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.SaleBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.local.entity.PurchaseBatchEntity
import com.zagot.zagotplus.data.local.entity.SaleBatchEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.syncengine.db.SyncMetadataDao
import com.zagot.syncengine.db.SyncMetadataEntity
import com.zagot.syncengine.db.SyncOutboxDao
import com.zagot.syncengine.db.SyncOutboxEntity
import com.zagot.syncengine.db.SyncOutboxMigrations

/**
 * Room database for Zagot+ application.
 * Offline-first local storage with Supabase sync.
 *
 * Entities: LocationEntity, ProductEntity, TransactionEntity, PurchaseBatchEntity, SaleBatchEntity, ExpenseCategoryEntity, CashOperationEntity
 * Version: 16 (add sync_outbox failure tracking columns)
 */
@Database(
    entities = [
        LocationEntity::class,
        ProductEntity::class,
        TransactionEntity::class,
        PurchaseBatchEntity::class,
        SaleBatchEntity::class,
        ExpenseCategoryEntity::class,
        CashOperationEntity::class,
        SyncMetadataEntity::class,
        SyncOutboxEntity::class
    ],
    version = 16,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class ZagotDatabase : RoomDatabase() {

    /**
     * Provides access to locations table.
     */
    abstract fun locationDao(): LocationDao

    /**
     * Provides access to products table.
     */
    abstract fun productDao(): ProductDao

    /**
     * Provides access to transactions table.
     */
    abstract fun transactionDao(): TransactionDao

    /**
     * Provides access to purchase_batches table.
     */
    abstract fun purchaseBatchDao(): PurchaseBatchDao

    /**
     * Provides access to sale_batches table.
     */
    abstract fun saleBatchDao(): SaleBatchDao

    /**
     * Provides access to expense_categories table.
     */
    abstract fun expenseCategoryDao(): ExpenseCategoryDao

    /**
     * Provides access to cash_operations table.
     */
    abstract fun cashOperationDao(): CashOperationDao

    /**
     * Provides access to sync_metadata table.
     */
    abstract fun syncMetadataDao(): SyncMetadataDao

    /**
     * Provides access to sync_outbox table.
     */
    abstract fun syncOutboxDao(): SyncOutboxDao

    companion object {
        /**
         * Migration from version 1 to 2: Add purchase_batches table and batch_id FK.
         * SQLite doesn't support adding FK via ALTER TABLE, so we recreate transactions table.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create purchase_batches table first (required for FK)
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS purchase_batches (
                        id TEXT NOT NULL PRIMARY KEY,
                        local_id TEXT NOT NULL,
                        location_id TEXT,
                        notes TEXT,
                        total_weight_kg TEXT,
                        total_amount TEXT,
                        item_count INTEGER,
                        device_id TEXT,
                        created_at INTEGER NOT NULL,
                        synced_at INTEGER,
                        FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_purchase_batches_local_id ON purchase_batches(local_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_batches_location_id ON purchase_batches(location_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_batches_synced_at ON purchase_batches(synced_at)")

                // Recreate transactions table with batch_id FK
                // Step 1: Create new table with FK constraint
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transactions_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        local_id TEXT NOT NULL,
                        location_id TEXT,
                        type TEXT NOT NULL,
                        transfer_location_id TEXT,
                        product_id TEXT,
                        weight_kg TEXT NOT NULL,
                        price_per_kg TEXT,
                        total_amount TEXT,
                        notes TEXT,
                        device_id TEXT,
                        created_at INTEGER NOT NULL,
                        synced_at INTEGER,
                        batch_id TEXT,
                        FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
                        FOREIGN KEY (transfer_location_id) REFERENCES locations(id) ON DELETE RESTRICT,
                        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
                        FOREIGN KEY (batch_id) REFERENCES purchase_batches(id) ON DELETE SET NULL
                    )
                """)
                
                // Step 2: Copy data from old table (batch_id will be null)
                db.execSQL("""
                    INSERT INTO transactions_new (
                        id, local_id, location_id, type, transfer_location_id,
                        product_id, weight_kg, price_per_kg, total_amount, notes,
                        device_id, created_at, synced_at, batch_id
                    )
                    SELECT 
                        id, local_id, location_id, type, transfer_location_id,
                        product_id, weight_kg, price_per_kg, total_amount, notes,
                        device_id, created_at, synced_at, NULL
                    FROM transactions
                """)
                
                // Step 3: Drop old table
                db.execSQL("DROP TABLE transactions")
                
                // Step 4: Rename new table
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
                
                // Step 5: Recreate all indexes
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_local_id ON transactions(local_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_location_id ON transactions(location_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_transfer_location_id ON transactions(transfer_location_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_product_id ON transactions(product_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_batch_id ON transactions(batch_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_synced_at ON transactions(synced_at)")
            }
        }

        /**
         * Migration from version 2 to 3: Add image_uri column to products.
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE products ADD COLUMN image_uri TEXT")
            }
        }

        /**
         * Migration from version 3 to 4: Add missing created_at index on purchase_batches.
         * This index was added to PurchaseBatchEntity but not in MIGRATION_1_2.
         */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_batches_created_at ON purchase_batches(created_at)")
            }
        }

        /**
         * Migration from version 4 to 5: Add created_at index on transactions.
         * Improves performance for date-based queries and sync pull operations.
         */
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_created_at ON transactions(created_at)")
            }
        }

        /**
         * Migration from version 5 to 6: Add local_id and synced_at to products for sync support.
         * Products can now be created/updated locally and synced to Supabase.
         */
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add local_id column (use id as default value for existing rows)
                db.execSQL("ALTER TABLE products ADD COLUMN local_id TEXT NOT NULL DEFAULT ''")
                // Update existing rows to use id as local_id
                db.execSQL("UPDATE products SET local_id = id WHERE local_id = ''")
                // Add synced_at column
                db.execSQL("ALTER TABLE products ADD COLUMN synced_at INTEGER")
                // Create indexes
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_products_local_id ON products(local_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_synced_at ON products(synced_at)")
            }
        }

        /**
         * Migration from version 6 to 7: Add expense_categories and cash_operations tables.
         * Implements cash register functionality.
         */
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create expense_categories table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS expense_categories (
                        id TEXT NOT NULL PRIMARY KEY,
                        local_id TEXT NOT NULL,
                        name TEXT NOT NULL,
                        is_active INTEGER NOT NULL DEFAULT 1,
                        created_at INTEGER NOT NULL,
                        synced_at INTEGER
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_expense_categories_local_id ON expense_categories(local_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_categories_synced_at ON expense_categories(synced_at)")

                // Create cash_operations table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS cash_operations (
                        id TEXT NOT NULL PRIMARY KEY,
                        local_id TEXT NOT NULL,
                        location_id TEXT,
                        type TEXT NOT NULL,
                        amount TEXT NOT NULL,
                        category_id TEXT,
                        transaction_id TEXT,
                        notes TEXT,
                        device_id TEXT,
                        created_at INTEGER NOT NULL,
                        synced_at INTEGER,
                        FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
                        FOREIGN KEY (category_id) REFERENCES expense_categories(id) ON DELETE SET NULL,
                        FOREIGN KEY (transaction_id) REFERENCES transactions(id) ON DELETE CASCADE
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cash_operations_local_id ON cash_operations(local_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_location_id ON cash_operations(location_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_category_id ON cash_operations(category_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_transaction_id ON cash_operations(transaction_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_synced_at ON cash_operations(synced_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_created_at ON cash_operations(created_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_type ON cash_operations(type)")
            }
        }

        /**
         * Migration from version 7 to 8: Add sale_batches table and sale_batch_id FK on transactions.
         * Implements sale batching similar to purchase batching.
         */
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create sale_batches table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sale_batches (
                        id TEXT NOT NULL PRIMARY KEY,
                        local_id TEXT NOT NULL,
                        location_id TEXT,
                        notes TEXT,
                        total_weight_kg TEXT,
                        total_amount TEXT,
                        item_count INTEGER,
                        device_id TEXT,
                        created_at INTEGER NOT NULL,
                        synced_at INTEGER,
                        FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT
                    )
                """)
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_sale_batches_local_id ON sale_batches(local_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_batches_location_id ON sale_batches(location_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_batches_synced_at ON sale_batches(synced_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_batches_created_at ON sale_batches(created_at)")

                // Add sale_batch_id column to transactions (with FK and index)
                // SQLite doesn't support adding FK via ALTER TABLE, so we recreate transactions table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS transactions_new (
                        id TEXT NOT NULL PRIMARY KEY,
                        local_id TEXT NOT NULL,
                        location_id TEXT,
                        type TEXT NOT NULL,
                        transfer_location_id TEXT,
                        product_id TEXT,
                        weight_kg TEXT NOT NULL,
                        price_per_kg TEXT,
                        total_amount TEXT,
                        notes TEXT,
                        device_id TEXT,
                        created_at INTEGER NOT NULL,
                        synced_at INTEGER,
                        batch_id TEXT,
                        sale_batch_id TEXT,
                        FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
                        FOREIGN KEY (transfer_location_id) REFERENCES locations(id) ON DELETE RESTRICT,
                        FOREIGN KEY (product_id) REFERENCES products(id) ON DELETE RESTRICT,
                        FOREIGN KEY (batch_id) REFERENCES purchase_batches(id) ON DELETE SET NULL,
                        FOREIGN KEY (sale_batch_id) REFERENCES sale_batches(id) ON DELETE SET NULL
                    )
                """)
                
                // Copy data from old table (sale_batch_id will be null)
                db.execSQL("""
                    INSERT INTO transactions_new (
                        id, local_id, location_id, type, transfer_location_id,
                        product_id, weight_kg, price_per_kg, total_amount, notes,
                        device_id, created_at, synced_at, batch_id, sale_batch_id
                    )
                    SELECT 
                        id, local_id, location_id, type, transfer_location_id,
                        product_id, weight_kg, price_per_kg, total_amount, notes,
                        device_id, created_at, synced_at, batch_id, NULL
                    FROM transactions
                """)
                
                // Drop old table
                db.execSQL("DROP TABLE transactions")
                
                // Rename new table
                db.execSQL("ALTER TABLE transactions_new RENAME TO transactions")
                
                // Recreate all indexes
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_transactions_local_id ON transactions(local_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_location_id ON transactions(location_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_transfer_location_id ON transactions(transfer_location_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_product_id ON transactions(product_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_batch_id ON transactions(batch_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_sale_batch_id ON transactions(sale_batch_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_synced_at ON transactions(synced_at)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_created_at ON transactions(created_at)")
            }
        }

        /**
         * Migration from version 8 to 9: Change cash_operations FK from transactions to purchase_batches.
         * Renames transaction_id column to batch_id and updates FK reference.
         * This fixes the bug where purchase payments were incorrectly referencing batch IDs
         * as transaction IDs, causing FK constraint failures.
         * 
         * CRITICAL: Uses transaction for atomic migration to prevent data loss on crash.
         */
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Disable FK constraints temporarily for atomic migration
                db.execSQL("PRAGMA foreign_keys=off")
                db.beginTransaction()
                try {
                    // SQLite doesn't support altering FK constraints, so we need to recreate the table
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS cash_operations_new (
                            id TEXT NOT NULL PRIMARY KEY,
                            local_id TEXT NOT NULL,
                            location_id TEXT,
                            type TEXT NOT NULL,
                            amount TEXT NOT NULL,
                            category_id TEXT,
                            batch_id TEXT,
                            notes TEXT,
                            device_id TEXT,
                            created_at INTEGER NOT NULL,
                            synced_at INTEGER,
                            FOREIGN KEY (location_id) REFERENCES locations(id) ON DELETE RESTRICT,
                            FOREIGN KEY (category_id) REFERENCES expense_categories(id) ON DELETE SET NULL,
                            FOREIGN KEY (batch_id) REFERENCES purchase_batches(id) ON DELETE CASCADE
                        )
                    """)
                    
                    // Copy data from old table (transaction_id becomes batch_id)
                    db.execSQL("""
                        INSERT INTO cash_operations_new (
                            id, local_id, location_id, type, amount, category_id,
                            batch_id, notes, device_id, created_at, synced_at
                        )
                        SELECT 
                            id, local_id, location_id, type, amount, category_id,
                            transaction_id, notes, device_id, created_at, synced_at
                        FROM cash_operations
                    """)
                    
                    // Drop old table
                    db.execSQL("DROP TABLE cash_operations")
                    
                    // Rename new table
                    db.execSQL("ALTER TABLE cash_operations_new RENAME TO cash_operations")
                    
                    // Recreate all indexes
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_cash_operations_local_id ON cash_operations(local_id)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_location_id ON cash_operations(location_id)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_category_id ON cash_operations(category_id)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_batch_id ON cash_operations(batch_id)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_synced_at ON cash_operations(synced_at)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_created_at ON cash_operations(created_at)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_type ON cash_operations(type)")
                    
                    db.setTransactionSuccessful()
                } finally {
                    db.endTransaction()
                    db.execSQL("PRAGMA foreign_keys=on")
                }
            }
        }

        /**
         * Migration from version 9 to 10: Add batch correction support.
         * Adds is_voided, corrects_batch_id, and correction_reason columns to
         * purchase_batches and sale_batches tables.
         * 
         * This enables the correction workflow where:
         * - Original batch gets is_voided=true
         * - New correction batch has corrects_batch_id pointing to original
         * - Voided batches are excluded from inventory calculations
         */
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add correction columns to purchase_batches
                db.execSQL("ALTER TABLE purchase_batches ADD COLUMN is_voided INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE purchase_batches ADD COLUMN corrects_batch_id TEXT")
                db.execSQL("ALTER TABLE purchase_batches ADD COLUMN correction_reason TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_batches_is_voided ON purchase_batches(is_voided)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_batches_corrects_batch_id ON purchase_batches(corrects_batch_id)")

                // Add correction columns to sale_batches
                db.execSQL("ALTER TABLE sale_batches ADD COLUMN is_voided INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE sale_batches ADD COLUMN corrects_batch_id TEXT")
                db.execSQL("ALTER TABLE sale_batches ADD COLUMN correction_reason TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_batches_is_voided ON sale_batches(is_voided)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_batches_corrects_batch_id ON sale_batches(corrects_batch_id)")
            }
        }

        /**
         * Migration from version 10 to 11: Add is_transfer flag to cash_operations.
         * Replaces fragile pattern-matching (notes LIKE 'Переказ%') with explicit boolean flag.
         * 
         * This fixes:
         * - Transfer detection no longer relies on text pattern matching
         * - Queries can use index on is_transfer for better performance
         * - Legacy transfers are migrated based on notes pattern
         */
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add is_transfer column with default false
                db.execSQL("ALTER TABLE cash_operations ADD COLUMN is_transfer INTEGER NOT NULL DEFAULT 0")
                
                // Migrate existing transfers: set is_transfer=1 where notes starts with 'Переказ'
                db.execSQL("UPDATE cash_operations SET is_transfer = 1 WHERE notes LIKE 'Переказ%'")
                
                // Create index for efficient filtering
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_is_transfer ON cash_operations(is_transfer)")
            }
        }

        /**
         * Migration from version 11 to 12: Add sync columns to locations, audit columns for voiding,
         * transfer_pair_id for linking transfer pairs, and composite index for inventory queries.
         */
        val MIGRATION_11_12 = object : Migration(11, 12) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add sync columns to locations
                db.execSQL("ALTER TABLE locations ADD COLUMN local_id TEXT NOT NULL DEFAULT ''")
                db.execSQL("UPDATE locations SET local_id = id WHERE local_id = ''")
                db.execSQL("ALTER TABLE locations ADD COLUMN synced_at INTEGER")
                db.execSQL("ALTER TABLE locations ADD COLUMN device_id TEXT")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_locations_local_id ON locations(local_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_locations_synced_at ON locations(synced_at)")
                
                // Add audit columns to purchase_batches
                db.execSQL("ALTER TABLE purchase_batches ADD COLUMN voided_at INTEGER")
                db.execSQL("ALTER TABLE purchase_batches ADD COLUMN voided_by_device_id TEXT")
                
                // Add audit columns to sale_batches
                db.execSQL("ALTER TABLE sale_batches ADD COLUMN voided_at INTEGER")
                db.execSQL("ALTER TABLE sale_batches ADD COLUMN voided_by_device_id TEXT")
                
                // Add transfer_pair_id to cash_operations
                db.execSQL("ALTER TABLE cash_operations ADD COLUMN transfer_pair_id TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_transfer_pair_id ON cash_operations(transfer_pair_id)")
                
                // Add composite index for inventory queries
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_location_product ON transactions(location_id, product_id)")
            }
        }
        
        /**
         * Migration from version 12 to 13: Drop stale composite index.
         * The index_transactions_location_product was added in MIGRATION_11_12 but the entity
         * was later changed to only use single-column indices. Room validates schema exactly,
         * so this stale index must be removed.
         */
        val MIGRATION_12_13 = object : Migration(12, 13) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("DROP INDEX IF EXISTS index_transactions_location_product")
            }
        }

        /**
         * Migration from version 13 to 14: Add server_updated_at column to all syncable tables.
         * This enables server-wins conflict resolution during sync.
         * 
         * When pushing records, we compare local server_updated_at with server's current value.
         * If server has a newer timestamp, we skip the push and let pull retrieve the correct data.
         */
        val MIGRATION_13_14 = object : Migration(13, 14) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Add server_updated_at to sale_batches
                db.execSQL("ALTER TABLE sale_batches ADD COLUMN server_updated_at INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sale_batches_server_updated_at ON sale_batches(server_updated_at)")
                
                // Add server_updated_at to purchase_batches
                db.execSQL("ALTER TABLE purchase_batches ADD COLUMN server_updated_at INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_purchase_batches_server_updated_at ON purchase_batches(server_updated_at)")
                
                // Add server_updated_at to transactions
                db.execSQL("ALTER TABLE transactions ADD COLUMN server_updated_at INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_server_updated_at ON transactions(server_updated_at)")
                
                // Add server_updated_at to cash_operations
                db.execSQL("ALTER TABLE cash_operations ADD COLUMN server_updated_at INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_cash_operations_server_updated_at ON cash_operations(server_updated_at)")
                
                // Add server_updated_at to expense_categories
                db.execSQL("ALTER TABLE expense_categories ADD COLUMN server_updated_at INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_expense_categories_server_updated_at ON expense_categories(server_updated_at)")
                
                // Add server_updated_at to products
                db.execSQL("ALTER TABLE products ADD COLUMN server_updated_at INTEGER DEFAULT NULL")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_products_server_updated_at ON products(server_updated_at)")
            }
        }

        /**
         * Migration from version 14 to 15: Add sync_metadata and sync_outbox tables
         * for the realtime sync engine.
         */
        val MIGRATION_14_15 = object : Migration(14, 15) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // sync_metadata: tracks last_synced_at per table
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_metadata (
                        table_name TEXT NOT NULL PRIMARY KEY,
                        last_synced_at INTEGER NOT NULL DEFAULT 0
                    )
                """)

                // sync_outbox: change tracker for offline mutations
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS sync_outbox (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        table_name TEXT NOT NULL,
                        record_id TEXT NOT NULL,
                        operation TEXT NOT NULL,
                        payload TEXT NOT NULL,
                        created_at INTEGER NOT NULL,
                        synced INTEGER NOT NULL DEFAULT 0
                    )
                """)
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_table_name_record_id ON sync_outbox(table_name, record_id)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_synced ON sync_outbox(synced)")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sync_outbox_created_at ON sync_outbox(created_at)")
            }
        }

        /**
         * Migration from version 15 to 16: Add failure tracking columns to sync_outbox.
         */
        val MIGRATION_15_16 = SyncOutboxMigrations.addFailureTracking(15, 16)
    }
}
