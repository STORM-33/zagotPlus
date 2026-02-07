package com.zagot.zagotplus.sync.engine

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
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Registers all Zagot+ tables with the sync engine (spec Section 4).
 *
 * Tables are registered in FK dependency order:
 * locations → products → expense_categories → purchase_batches →
 * sale_batches → transactions → cash_operations
 *
 * IMPORTANT: applyToRoom callbacks use RAW DAOs (not SyncAware wrappers)
 * to avoid outbox loops — remote data must not generate outbox entries.
 *
 * IMPORTANT: purchase_batches and sale_batches use upsertAll() (not insertAll())
 * because REPLACE triggers FK ON DELETE SET_NULL, breaking batch_id references.
 */
@Singleton
class ZagotSyncRegistrar @Inject constructor(
    private val locationDao: LocationDao,
    private val productDao: ProductDao,
    private val expenseCategoryDao: ExpenseCategoryDao,
    private val purchaseBatchDao: PurchaseBatchDao,
    private val saleBatchDao: SaleBatchDao,
    private val transactionDao: TransactionDao,
    private val cashOperationDao: CashOperationDao,
) {
    companion object {
        private const val TAG = "ZagotSyncRegistrar"
    }

    /**
     * Register all 7 syncable tables with the sync engine.
     * Must be called before [SyncEngine.start].
     */
    fun registerAll(engine: SyncEngine) {
        Log.d(TAG, "Registering all Zagot+ tables")

        // 1. locations — no FK deps, no server_updated_at (uses created_at)
        engine.registerTable(SyncTableConfig(
            tableName = "locations",
            timestampColumn = "created_at",
            applyToRoom = { records ->
                val entities = records.map { LocationDto.fromRecord(it).toEntity() }
                locationDao.insertAll(entities)
            }
        ))

        // 2. products — no FK deps
        engine.registerTable(SyncTableConfig(
            tableName = "products",
            applyToRoom = { records ->
                val entities = records.map { ProductDto.fromRecord(it).toEntity() }
                productDao.insertAll(entities)
            }
        ))

        // 3. expense_categories — no FK deps
        engine.registerTable(SyncTableConfig(
            tableName = "expense_categories",
            applyToRoom = { records ->
                val entities = records.map { ExpenseCategoryDto.fromRecord(it).toEntity() }
                expenseCategoryDao.insertAll(entities)
            }
        ))

        // 4. purchase_batches — FK: locations
        // Uses upsertAll() to avoid FK ON DELETE SET_NULL from REPLACE strategy
        engine.registerTable(SyncTableConfig(
            tableName = "purchase_batches",
            applyToRoom = { records ->
                val entities = records.map { PurchaseBatchDto.fromRecord(it).toEntity() }
                purchaseBatchDao.upsertAll(entities)
            }
        ))

        // 5. sale_batches — FK: locations
        // Uses upsertAll() to avoid FK ON DELETE SET_NULL from REPLACE strategy
        engine.registerTable(SyncTableConfig(
            tableName = "sale_batches",
            applyToRoom = { records ->
                val entities = records.map { SaleBatchDto.fromRecord(it).toEntity() }
                saleBatchDao.upsertAll(entities)
            }
        ))

        // 6. transactions — FK: locations, products, purchase_batches, sale_batches
        engine.registerTable(SyncTableConfig(
            tableName = "transactions",
            applyToRoom = { records ->
                val entities = records.map { TransactionDto.fromRecord(it).toEntity() }
                transactionDao.insertAll(entities)
            }
        ))

        // 7. cash_operations — FK: locations, expense_categories, purchase_batches
        engine.registerTable(SyncTableConfig(
            tableName = "cash_operations",
            applyToRoom = { records ->
                val entities = records.map { CashOperationDto.fromRecord(it).toEntity() }
                cashOperationDao.insertAll(entities)
            }
        ))

        Log.d(TAG, "Registered 7 tables")
    }
}
