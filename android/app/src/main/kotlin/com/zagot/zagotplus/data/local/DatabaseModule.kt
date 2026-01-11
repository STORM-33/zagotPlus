package com.zagot.zagotplus.data.local

import android.content.Context
import androidx.room.Room
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
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
     * Provides singleton instance of ZagotDatabase.
     * Uses fallbackToDestructiveMigration during development.
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
            .fallbackToDestructiveMigration() // TODO: Remove in production, add migrations
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
}
