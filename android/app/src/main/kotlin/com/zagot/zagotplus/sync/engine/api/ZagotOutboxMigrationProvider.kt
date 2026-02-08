package com.zagot.zagotplus.sync.engine.api

import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.SaleBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.remote.dto.CashOperationDto
import com.zagot.zagotplus.data.remote.dto.ExpenseCategoryDto
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import com.zagot.zagotplus.data.remote.dto.SaleBatchDto
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import com.zagot.syncengine.db.OutboxMigrationProvider
import com.zagot.syncengine.db.SyncOutboxEntity
import com.zagot.syncengine.util.RawDao
import com.zagot.syncengine.util.SyncJson
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ZagotOutboxMigrationProvider @Inject constructor(
    @RawDao private val productDao: ProductDao,
    @RawDao private val expenseCategoryDao: ExpenseCategoryDao,
    @RawDao private val purchaseBatchDao: PurchaseBatchDao,
    @RawDao private val saleBatchDao: SaleBatchDao,
    @RawDao private val transactionDao: TransactionDao,
    @RawDao private val cashOperationDao: CashOperationDao,
) : OutboxMigrationProvider {
    override suspend fun buildOutboxEntries(now: Long): List<SyncOutboxEntity> {
        val entries = mutableListOf<SyncOutboxEntity>()

        // Products
        val unsyncedProducts = productDao.getUnsynced()
        for (entity in unsyncedProducts) {
            entries.add(SyncOutboxEntity(
                tableName = "products",
                recordId = entity.id.toString(),
                operation = "INSERT",
                payload = SyncJson.encodeToString(ProductDto.fromEntity(entity)),
                createdAt = now,
            ))
        }

        // Expense categories
        val unsyncedCategories = expenseCategoryDao.getUnsynced()
        for (entity in unsyncedCategories) {
            entries.add(SyncOutboxEntity(
                tableName = "expense_categories",
                recordId = entity.id.toString(),
                operation = "INSERT",
                payload = SyncJson.encodeToString(ExpenseCategoryDto.fromEntity(entity)),
                createdAt = now,
            ))
        }

        // Purchase batches
        val unsyncedPurchaseBatches = purchaseBatchDao.getUnsynced()
        for (entity in unsyncedPurchaseBatches) {
            entries.add(SyncOutboxEntity(
                tableName = "purchase_batches",
                recordId = entity.id.toString(),
                operation = "INSERT",
                payload = SyncJson.encodeToString(PurchaseBatchDto.fromEntity(entity)),
                createdAt = now,
            ))
        }

        // Sale batches
        val unsyncedSaleBatches = saleBatchDao.getUnsynced()
        for (entity in unsyncedSaleBatches) {
            entries.add(SyncOutboxEntity(
                tableName = "sale_batches",
                recordId = entity.id.toString(),
                operation = "INSERT",
                payload = SyncJson.encodeToString(SaleBatchDto.fromEntity(entity)),
                createdAt = now,
            ))
        }

        // Transactions
        val unsyncedTransactions = transactionDao.getUnsynced()
        for (entity in unsyncedTransactions) {
            entries.add(SyncOutboxEntity(
                tableName = "transactions",
                recordId = entity.id.toString(),
                operation = "INSERT",
                payload = SyncJson.encodeToString(TransactionDto.fromEntity(entity)),
                createdAt = now,
            ))
        }

        // Cash operations
        val unsyncedCashOps = cashOperationDao.getUnsynced()
        for (entity in unsyncedCashOps) {
            entries.add(SyncOutboxEntity(
                tableName = "cash_operations",
                recordId = entity.id.toString(),
                operation = "INSERT",
                payload = SyncJson.encodeToString(CashOperationDto.fromEntity(entity)),
                createdAt = now,
            ))
        }

        return entries
    }
}
