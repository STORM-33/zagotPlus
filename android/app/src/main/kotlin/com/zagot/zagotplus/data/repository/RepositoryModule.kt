package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.preferences.AuthPreferences
import com.zagot.zagotplus.data.preferences.AuthPreferencesImpl
import com.zagot.zagotplus.domain.repository.CashRepository
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.PurchaseBatchRepository
import com.zagot.zagotplus.domain.repository.SaleBatchRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module for repository bindings.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindLocationRepository(
        impl: LocationRepositoryImpl
    ): LocationRepository

    @Binds
    @Singleton
    abstract fun bindProductRepository(
        impl: ProductRepositoryImpl
    ): ProductRepository

    @Binds
    @Singleton
    abstract fun bindTransactionRepository(
        impl: TransactionRepositoryImpl
    ): TransactionRepository

    @Binds
    @Singleton
    abstract fun bindPurchaseBatchRepository(
        impl: PurchaseBatchRepositoryImpl
    ): PurchaseBatchRepository

    @Binds
    @Singleton
    abstract fun bindSaleBatchRepository(
        impl: SaleBatchRepositoryImpl
    ): SaleBatchRepository

    @Binds
    @Singleton
    abstract fun bindCashRepository(
        impl: CashRepositoryImpl
    ): CashRepository

    @Binds
    @Singleton
    abstract fun bindAuthPreferences(
        impl: AuthPreferencesImpl
    ): AuthPreferences
}
