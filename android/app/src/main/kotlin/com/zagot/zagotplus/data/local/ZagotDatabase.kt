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
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.local.entity.PurchaseBatchEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity

/**
 * Room database for Zagot+ application.
 * Offline-first local storage with Supabase sync.
 *
 * Entities: LocationEntity, ProductEntity, TransactionEntity, PurchaseBatchEntity, ExpenseCategoryEntity, CashOperationEntity
 * Version: 7 (added expense_categories and cash_operations tables)
 */
@Database(
    entities = [
        LocationEntity::class,
        ProductEntity::class,
        TransactionEntity::class,
        PurchaseBatchEntity::class,
        ExpenseCategoryEntity::class,
        CashOperationEntity::class
    ],
    version = 7,
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
     * Provides access to expense_categories table.
     */
    abstract fun expenseCategoryDao(): ExpenseCategoryDao

    /**
     * Provides access to cash_operations table.
     */
    abstract fun cashOperationDao(): CashOperationDao

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
    }
}
