/**
 * Repository implementations for the domain layer.
 *
 * Repositories act as the single source of truth for domain data,
 * coordinating between local database and remote API:
 * - [LocationRepositoryImpl] - Location/warehouse management
 * - [ProductRepositoryImpl] - Product catalog with images
 * - [TransactionRepositoryImpl] - Purchase/sale transactions
 * - [PurchaseBatchRepositoryImpl] - Purchase batch operations
 * - [SaleBatchRepositoryImpl] - Sale batch operations
 * - [CashRepositoryImpl] - Cash operations and expenses
 * - [RepositoryModule] - Hilt bindings for repository interfaces
 *
 * Each repository:
 * - Exposes reactive [kotlinx.coroutines.flow.Flow] for UI updates
 * - Handles offline-first data access (local DB is source of truth)
 * - Delegates sync to the sync engine, not direct API calls
 */
package com.zagot.zagotplus.data.repository
