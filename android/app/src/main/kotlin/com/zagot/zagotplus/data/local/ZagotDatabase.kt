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
    version = 2,
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
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // Create purchase_batches table
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

                // Add batch_id column to transactions
                db.execSQL("ALTER TABLE transactions ADD COLUMN batch_id TEXT")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_transactions_batch_id ON transactions(batch_id)")
            }
        }
    }
}
