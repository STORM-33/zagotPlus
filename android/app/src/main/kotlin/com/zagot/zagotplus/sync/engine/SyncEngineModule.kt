package com.zagot.zagotplus.sync.engine

import androidx.room.RoomDatabase
import com.zagot.zagotplus.data.local.ZagotDatabase
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
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
}

@Module
@InstallIn(SingletonComponent::class)
object SyncEngineModule {

    @Provides
    @Singleton
    fun provideRoomDatabase(database: ZagotDatabase): RoomDatabase = database
}
