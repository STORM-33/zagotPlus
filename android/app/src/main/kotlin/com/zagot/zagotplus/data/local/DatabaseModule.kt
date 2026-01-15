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
        ZagotDatabase.MIGRATION_9_10
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
     */
    @Provides
    @Singleton
    fun provideLocationDao(database: ZagotDatabase): LocationDao {
        return database.locationDao()
    }

    /**
     * Provides ProductDao from database.
     */
    @Provides
    @Singleton
    fun provideProductDao(database: ZagotDatabase): ProductDao {
        return database.productDao()
    }

    /**
     * Provides TransactionDao from database.
     */
    @Provides
    @Singleton
    fun provideTransactionDao(database: ZagotDatabase): TransactionDao {
        return database.transactionDao()
    }

    /**
     * Provides PurchaseBatchDao from database.
     */
    @Provides
    @Singleton
    fun providePurchaseBatchDao(database: ZagotDatabase): PurchaseBatchDao {
        return database.purchaseBatchDao()
    }

    /**
     * Provides SaleBatchDao from database.
     */
    @Provides
    @Singleton
    fun provideSaleBatchDao(database: ZagotDatabase): SaleBatchDao {
        return database.saleBatchDao()
    }

    /**
     * Provides ExpenseCategoryDao from database.
     */
    @Provides
    @Singleton
    fun provideExpenseCategoryDao(database: ZagotDatabase): ExpenseCategoryDao {
        return database.expenseCategoryDao()
    }

    /**
     * Provides CashOperationDao from database.
     */
    @Provides
    @Singleton
    fun provideCashOperationDao(database: ZagotDatabase): CashOperationDao {
        return database.cashOperationDao()
    }
}
