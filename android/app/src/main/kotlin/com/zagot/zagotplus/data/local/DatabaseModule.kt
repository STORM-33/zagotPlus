package com.zagot.zagotplus.data.local

import android.content.Context
import androidx.room.Room
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.SaleBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.syncengine.db.SyncMetadataDao
import com.zagot.syncengine.db.SyncOutboxDao
import com.zagot.syncengine.util.RawDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing Room database and DAOs.
 * Installed in SingletonComponent for application-wide scope.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    /**
     * Database migrations. Add new migrations here as schema evolves.
     * NEVER use fallbackToDestructiveMigration() - user data must be preserved.
     */
    private val MIGRATIONS: Array<Migration> = arrayOf(
        ZagotDatabase.MIGRATION_1_2,
        ZagotDatabase.MIGRATION_2_3,
        ZagotDatabase.MIGRATION_3_4,
        ZagotDatabase.MIGRATION_4_5,
        ZagotDatabase.MIGRATION_5_6,
        ZagotDatabase.MIGRATION_6_7,
        ZagotDatabase.MIGRATION_7_8,
        ZagotDatabase.MIGRATION_8_9,
        ZagotDatabase.MIGRATION_9_10,
        ZagotDatabase.MIGRATION_10_11,
        ZagotDatabase.MIGRATION_11_12,
        ZagotDatabase.MIGRATION_12_13,
        ZagotDatabase.MIGRATION_13_14,
        ZagotDatabase.MIGRATION_14_15
    )

    /**
     * Provides singleton instance of ZagotDatabase.
     * Uses explicit migrations to preserve user data across schema changes.
     */
    @Provides
    @Singleton
    fun provideDatabase(
        @ApplicationContext context: Context
    ): ZagotDatabase {
        return Room.databaseBuilder(
            context,
            ZagotDatabase::class.java,
            "zagot_database"
        )
            .addMigrations(*MIGRATIONS)
            .build()
    }

    /**
     * Provides LocationDao from database.
     * Qualified with @RawDao — the unqualified binding is the SyncAware wrapper.
     */
    @Provides
    @Singleton
    @RawDao
    fun provideLocationDao(database: ZagotDatabase): LocationDao {
        return database.locationDao()
    }

    /**
     * Provides ProductDao from database.
     */
    @Provides
    @Singleton
    @RawDao
    fun provideProductDao(database: ZagotDatabase): ProductDao {
        return database.productDao()
    }

    /**
     * Provides TransactionDao from database.
     */
    @Provides
    @Singleton
    @RawDao
    fun provideTransactionDao(database: ZagotDatabase): TransactionDao {
        return database.transactionDao()
    }

    /**
     * Provides PurchaseBatchDao from database.
     */
    @Provides
    @Singleton
    @RawDao
    fun providePurchaseBatchDao(database: ZagotDatabase): PurchaseBatchDao {
        return database.purchaseBatchDao()
    }

    /**
     * Provides SaleBatchDao from database.
     */
    @Provides
    @Singleton
    @RawDao
    fun provideSaleBatchDao(database: ZagotDatabase): SaleBatchDao {
        return database.saleBatchDao()
    }

    /**
     * Provides ExpenseCategoryDao from database.
     */
    @Provides
    @Singleton
    @RawDao
    fun provideExpenseCategoryDao(database: ZagotDatabase): ExpenseCategoryDao {
        return database.expenseCategoryDao()
    }

    /**
     * Provides CashOperationDao from database.
     */
    @Provides
    @Singleton
    @RawDao
    fun provideCashOperationDao(database: ZagotDatabase): CashOperationDao {
        return database.cashOperationDao()
    }

    @Provides
    @Singleton
    fun provideSyncMetadataDao(database: ZagotDatabase): SyncMetadataDao {
        return database.syncMetadataDao()
    }

    @Provides
    @Singleton
    fun provideSyncOutboxDao(database: ZagotDatabase): SyncOutboxDao {
        return database.syncOutboxDao()
    }
}
