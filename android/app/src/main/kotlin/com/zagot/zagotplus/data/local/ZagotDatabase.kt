package com.zagot.zagotplus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zagot.zagotplus.data.local.converter.Converters
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.local.entity.PurchaseBatchEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity

/**
 * Room database for Zagot+ application.
 * Offline-first local storage with Supabase sync.
 *
 * Entities: LocationEntity, ProductEntity, TransactionEntity, PurchaseBatchEntity
 * Version: 2 (added purchase_batches and batch_id FK)
 */
@Database(
    entities = [
        LocationEntity::class,
        ProductEntity::class,
        TransactionEntity::class,
        PurchaseBatchEntity::class
    ],
    version = 3,
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
    }
}
