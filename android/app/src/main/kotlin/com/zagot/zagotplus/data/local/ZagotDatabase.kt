package com.zagot.zagotplus.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.zagot.zagotplus.data.local.converter.Converters
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity

/**
 * Room database for Zagot+ application.
 * Offline-first local storage with Supabase sync.
 *
 * Entities: LocationEntity, ProductEntity, TransactionEntity
 * Version: 1 (initial schema)
 */
@Database(
    entities = [
        LocationEntity::class,
        ProductEntity::class,
        TransactionEntity::class
    ],
    version = 1,
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
}
