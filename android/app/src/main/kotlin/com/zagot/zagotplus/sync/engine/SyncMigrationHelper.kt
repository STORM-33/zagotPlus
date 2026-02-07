package com.zagot.zagotplus.sync.engine

import android.content.Context
import android.util.Log
import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.SaleBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.remote.dto.CashOperationDto
import com.zagot.zagotplus.data.remote.dto.ExpenseCategoryDto
import com.zagot.zagotplus.data.remote.dto.LocationDto
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import com.zagot.zagotplus.data.remote.dto.SaleBatchDto
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * One-time migration: moves existing `synced_at IS NULL` records into the
 * sync outbox so the new engine can push them (spec Section 9, Step 6).
 *
 * Uses SharedPreferences flag to ensure this runs only once per install.
 */
@Singleton
class SyncMigrationHelper @Inject constructor(
    @ApplicationContext private val context: Context,
    @RawDao private val locationDao: LocationDao,
    @RawDao private val productDao: ProductDao,
    @RawDao private val expenseCategoryDao: ExpenseCategoryDao,
    @RawDao private val purchaseBatchDao: PurchaseBatchDao,
    @RawDao private val saleBatchDao: SaleBatchDao,
    @RawDao private val transactionDao: TransactionDao,
    @RawDao private val cashOperationDao: CashOperationDao,
    private val outboxDao: SyncOutboxDao,
) {
    companion object {
        private const val TAG = "SyncMigrationHelper"
        private const val PREFS_NAME = "sync_engine_migration"
        private const val KEY_OUTBOX_MIGRATED = "outbox_migration_done"
    }

    /**
     * Run migration if not already done.
     * Queries all `synced_at IS NULL` records and creates outbox entries.
     */
    suspend fun migrateIfNeeded() {
        val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        if (prefs.getBoolean(KEY_OUTBOX_MIGRATED, false)) {
            Log.d(TAG, "Outbox migration already completed")
            return
        }

        Log.d(TAG, "Starting outbox migration for unsynced records")
        var total = 0
        val now = System.currentTimeMillis()

        // Products
        val unsyncedProducts = productDao.getUnsynced()
        for (entity in unsyncedProducts) {
            outboxDao.insert(SyncOutboxEntity(
                tableName = "products",
                recordId = entity.id.toString(),
                operation = "INSERT",
                payload = SyncJson.encodeToString(ProductDto.fromEntity(entity)),
                createdAt = now,
            ))
        }
        total += unsyncedProducts.size

        // Expense categories
        val unsyncedCategories = expenseCategoryDao.getUnsynced()
        for (entity in unsyncedCategories) {
            outboxDao.insert(SyncOutboxEntity(
                tableName = "expense_categories",
                recordId = entity.id.toString(),
                operation = "INSERT",
                payload = SyncJson.encodeToString(ExpenseCategoryDto.fromEntity(entity)),
                createdAt = now,
            ))
        }
        total += unsyncedCategories.size

        // Purchase batches
        val unsyncedPurchaseBatches = purchaseBatchDao.getUnsynced()
        for (entity in unsyncedPurchaseBatches) {
            outboxDao.insert(SyncOutboxEntity(
                tableName = "purchase_batches",
                recordId = entity.id.toString(),
                operation = "INSERT",
                payload = SyncJson.encodeToString(PurchaseBatchDto.fromEntity(entity)),
                createdAt = now,
            ))
        }
        total += unsyncedPurchaseBatches.size

        // Sale batches
        val unsyncedSaleBatches = saleBatchDao.getUnsynced()
        for (entity in unsyncedSaleBatches) {
            outboxDao.insert(SyncOutboxEntity(
                tableName = "sale_batches",
                recordId = entity.id.toString(),
                operation = "INSERT",
                payload = SyncJson.encodeToString(SaleBatchDto.fromEntity(entity)),
                createdAt = now,
            ))
        }
        total += unsyncedSaleBatches.size

        // Transactions
        val unsyncedTransactions = transactionDao.getUnsynced()
        for (entity in unsyncedTransactions) {
            outboxDao.insert(SyncOutboxEntity(
                tableName = "transactions",
                recordId = entity.id.toString(),
                operation = "INSERT",
                payload = SyncJson.encodeToString(TransactionDto.fromEntity(entity)),
                createdAt = now,
            ))
        }
        total += unsyncedTransactions.size

        // Cash operations
        val unsyncedCashOps = cashOperationDao.getUnsynced()
        for (entity in unsyncedCashOps) {
            outboxDao.insert(SyncOutboxEntity(
                tableName = "cash_operations",
                recordId = entity.id.toString(),
                operation = "INSERT",
                payload = SyncJson.encodeToString(CashOperationDto.fromEntity(entity)),
                createdAt = now,
            ))
        }
        total += unsyncedCashOps.size

        // Mark migration as done
        prefs.edit().putBoolean(KEY_OUTBOX_MIGRATED, true).apply()
        Log.d(TAG, "Outbox migration complete: $total records migrated")
    }
}
