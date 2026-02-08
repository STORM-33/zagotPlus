package com.zagot.zagotplus.sync.engine.api

import androidx.room.RoomDatabase
import androidx.work.WorkManager
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.SaleBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.sync.engine.dao.SyncAwareCashOperationDao
import com.zagot.zagotplus.sync.engine.dao.SyncAwareExpenseCategoryDao
import com.zagot.zagotplus.sync.engine.dao.SyncAwareLocationDao
import com.zagot.zagotplus.sync.engine.dao.SyncAwareProductDao
import com.zagot.zagotplus.sync.engine.dao.SyncAwarePurchaseBatchDao
import com.zagot.zagotplus.sync.engine.dao.SyncAwareSaleBatchDao
import com.zagot.zagotplus.sync.engine.dao.SyncAwareTransactionDao
import com.zagot.syncengine.api.RealtimeChannelContract
import com.zagot.syncengine.api.SyncEngine
import com.zagot.syncengine.api.SyncEngineConfig
import com.zagot.syncengine.api.SyncEngineImpl
import com.zagot.syncengine.api.SyncRemoteClient
import com.zagot.syncengine.db.OutboxMigrationProvider
import com.zagot.syncengine.network.AndroidNetworkMonitor
import com.zagot.syncengine.network.NetworkMonitor
import com.zagot.syncengine.remote.SupabaseRealtimeChannel
import com.zagot.syncengine.remote.SupabaseSyncRemoteClient
import com.zagot.syncengine.util.RawDao
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for sync engine DI bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SyncEngineBindingsModule {

    @Binds
    @Singleton
    abstract fun bindSyncEngine(impl: SyncEngineImpl): SyncEngine

    @Binds
    @Singleton
    abstract fun bindSyncRemoteClient(impl: SupabaseSyncRemoteClient): SyncRemoteClient

    @Binds
    @Singleton
    abstract fun bindRealtimeChannel(impl: SupabaseRealtimeChannel): RealtimeChannelContract

    @Binds
    @Singleton
    abstract fun bindNetworkMonitor(impl: AndroidNetworkMonitor): NetworkMonitor

    @Binds
    @Singleton
    abstract fun bindOutboxMigrationProvider(impl: ZagotOutboxMigrationProvider): OutboxMigrationProvider

    // SyncAware DAO bindings — unqualified types so ViewModels get outbox-instrumented DAOs
    @Binds @Singleton abstract fun bindLocationDao(impl: SyncAwareLocationDao): LocationDao
    @Binds @Singleton abstract fun bindProductDao(impl: SyncAwareProductDao): ProductDao
    @Binds @Singleton abstract fun bindExpenseCategoryDao(impl: SyncAwareExpenseCategoryDao): ExpenseCategoryDao
    @Binds @Singleton abstract fun bindPurchaseBatchDao(impl: SyncAwarePurchaseBatchDao): PurchaseBatchDao
    @Binds @Singleton abstract fun bindSaleBatchDao(impl: SyncAwareSaleBatchDao): SaleBatchDao
    @Binds @Singleton abstract fun bindTransactionDao(impl: SyncAwareTransactionDao): TransactionDao
    @Binds @Singleton abstract fun bindCashOperationDao(impl: SyncAwareCashOperationDao): CashOperationDao
}

@Module
@InstallIn(SingletonComponent::class)
object SyncEngineModule {

    @Provides
    @Singleton
    fun provideRoomDatabase(database: ZagotDatabase): RoomDatabase = database

    @Provides
    @Singleton
    fun provideWorkManager(@ApplicationContext context: android.content.Context): WorkManager =
        WorkManager.getInstance(context)

    @Provides
    @Singleton
    fun provideSyncEngineConfig(): SyncEngineConfig = SyncEngineConfig(
        // Zagot+ defaults — adjust per app
        safetySyncIntervalMinutes = 15L,
        pushBatchSize = 200,
    )
}
