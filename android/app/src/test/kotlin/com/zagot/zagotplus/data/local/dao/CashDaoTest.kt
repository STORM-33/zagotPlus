package com.zagot.zagotplus.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * DAO tests for CashDao (ExpenseCategoryDao and CashOperationDao) using Robolectric.
 * Tests verify SQL queries for cash management features.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CashDaoTest {

    private lateinit var database: ZagotDatabase
    private lateinit var expenseCategoryDao: ExpenseCategoryDao
    private lateinit var cashOperationDao: CashOperationDao
    private lateinit var locationDao: LocationDao
    private lateinit var productDao: ProductDao
    private lateinit var transactionDao: TransactionDao

    private val testInstant = Instant.parse("2024-01-15T10:00:00Z")
    private val testLocationId = UUID.randomUUID()
    private val testProductId = UUID.randomUUID()

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        expenseCategoryDao = database.expenseCategoryDao()
        cashOperationDao = database.cashOperationDao()
        locationDao = database.locationDao()
        productDao = database.productDao()
        transactionDao = database.transactionDao()

        // Insert test location and product for FK
        runTest {
            locationDao.insert(LocationEntity(
                id = testLocationId,
                name = "Склад №1",
                type = "kiosk",
                createdAt = testInstant
            ))
            productDao.insert(ProductEntity(
                id = testProductId,
                localId = "prod-local-$testProductId",
                name = "Яблука",
                defaultBuyPrice = BigDecimal("45.00"),
                defaultSellPrice = BigDecimal("55.00"),
                isActive = true,
                createdAt = testInstant
            ))
        }
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== ExpenseCategoryDao Tests ====================

    private fun createExpenseCategory(
        id: UUID = UUID.randomUUID(),
        name: String = "Транспорт",
        isActive: Boolean = true,
        syncedAt: Instant? = null
    ) = ExpenseCategoryEntity(
        id = id,
        localId = "cat-local-$id",
        name = name,
        isActive = isActive,
        createdAt = testInstant,
        syncedAt = syncedAt
    )

    @Test
    fun `insert and getById returns category`() = runTest {
        val category = createExpenseCategory()
        expenseCategoryDao.insert(category)

        val result = expenseCategoryDao.getById(category.id)

        assertNotNull(result)
        assertEquals(category.name, result?.name)
    }

    @Test
    fun `getAllCategories returns all categories`() = runTest {
        val cat1 = createExpenseCategory(name = "Транспорт")
        val cat2 = createExpenseCategory(name = "Їжа")
        expenseCategoryDao.insert(cat1)
        expenseCategoryDao.insert(cat2)

        val result = expenseCategoryDao.getAllCategories().first()

        assertEquals(2, result.size)
    }

    @Test
    fun `getActiveCategories returns only active categories`() = runTest {
        val active = createExpenseCategory(name = "Активна", isActive = true)
        val inactive = createExpenseCategory(name = "Неактивна", isActive = false)
        expenseCategoryDao.insert(active)
        expenseCategoryDao.insert(inactive)

        val result = expenseCategoryDao.getActiveCategories().first()

        assertEquals(1, result.size)
        assertEquals("Активна", result[0].name)
    }

    @Test
    fun `getUnsynced returns categories with null syncedAt`() = runTest {
        val synced = createExpenseCategory(name = "Синхронізована", syncedAt = testInstant)
        val unsynced = createExpenseCategory(name = "Не синхронізована", syncedAt = null)
        expenseCategoryDao.insert(synced)
        expenseCategoryDao.insert(unsynced)

        val result = expenseCategoryDao.getUnsynced()

        assertEquals(1, result.size)
        assertEquals("Не синхронізована", result[0].name)
    }

    @Test
    fun `expenseCategory markSynced updates syncedAt`() = runTest {
        val category = createExpenseCategory(syncedAt = null)
        expenseCategoryDao.insert(category)

        val syncTime = Instant.parse("2024-01-20T12:00:00Z")
        expenseCategoryDao.markSynced(category.id, syncTime)

        val result = expenseCategoryDao.getById(category.id)
        assertEquals(syncTime, result?.syncedAt)
    }

    @Test
    fun `getByLocalId returns correct category`() = runTest {
        val category = createExpenseCategory()
        expenseCategoryDao.insert(category)

        val result = expenseCategoryDao.getByLocalId(category.localId)

        assertNotNull(result)
        assertEquals(category.id, result?.id)
    }

    @Test
    fun `deactivate sets isActive to false`() = runTest {
        val category = createExpenseCategory(isActive = true)
        expenseCategoryDao.insert(category)

        expenseCategoryDao.deactivate(category.id)

        val result = expenseCategoryDao.getById(category.id)
        assertFalse(result?.isActive ?: true)
    }

    @Test
    fun `update modifies category fields`() = runTest {
        val category = createExpenseCategory(name = "Оригінальна")
        expenseCategoryDao.insert(category)

        val updated = category.copy(name = "Оновлена")
        expenseCategoryDao.update(updated)

        val result = expenseCategoryDao.getById(category.id)
        assertEquals("Оновлена", result?.name)
    }

    // ==================== CashOperationDao Tests ====================

    private fun createCashOperation(
        id: UUID = UUID.randomUUID(),
        locationId: UUID? = testLocationId,
        type: String = "deposit",
        amount: BigDecimal = BigDecimal("1000.00"),
        categoryId: UUID? = null,
        batchId: UUID? = null,
        notes: String? = null,
        syncedAt: Instant? = null,
        createdAt: Instant = testInstant
    ) = CashOperationEntity(
        id = id,
        localId = "cash-local-$id",
        locationId = locationId,
        type = type,
        amount = amount,
        categoryId = categoryId,
        batchId = batchId,
        notes = notes,
        deviceId = "test-device",
        createdAt = createdAt,
        syncedAt = syncedAt
    )

    @Test
    fun `insert and getById returns cash operation`() = runTest {
        val operation = createCashOperation()
        cashOperationDao.insert(operation)

        val result = cashOperationDao.getById(operation.id)

        assertNotNull(result)
        assertEquals(operation.type, result?.type)
        assertEquals(0, operation.amount.compareTo(result?.amount))
    }

    @Test
    fun `getByLocation returns operations for specific location`() = runTest {
        val op1 = createCashOperation(locationId = testLocationId, notes = "Op1")
        val otherLocationId = UUID.randomUUID()
        
        // Insert another location for FK
        locationDao.insert(LocationEntity(
            id = otherLocationId,
            name = "Інший склад",
            type = "kiosk",
            createdAt = testInstant
        ))
        
        val op2 = createCashOperation(locationId = otherLocationId, notes = "Op2")
        cashOperationDao.insert(op1)
        cashOperationDao.insert(op2)

        val result = cashOperationDao.getByLocation(testLocationId).first()

        assertEquals(1, result.size)
        assertEquals("Op1", result[0].notes)
    }

    @Test
    fun `getUnsynced returns operations with null syncedAt`() = runTest {
        val synced = createCashOperation(type = "deposit", syncedAt = testInstant)
        val unsynced = createCashOperation(type = "withdrawal", syncedAt = null)
        cashOperationDao.insert(synced)
        cashOperationDao.insert(unsynced)

        val result = cashOperationDao.getUnsynced()

        assertEquals(1, result.size)
        assertEquals("withdrawal", result[0].type)
    }

    @Test
    fun `cashOperation markSynced updates syncedAt`() = runTest {
        val operation = createCashOperation(syncedAt = null)
        cashOperationDao.insert(operation)

        val syncTime = Instant.parse("2024-01-20T12:00:00Z")
        cashOperationDao.markSynced(operation.id, syncTime)

        val result = cashOperationDao.getById(operation.id)
        assertEquals(syncTime, result?.syncedAt)
    }

    @Test
    fun `getByLocalId returns correct operation`() = runTest {
        val operation = createCashOperation()
        cashOperationDao.insert(operation)

        val result = cashOperationDao.getByLocalId(operation.localId)

        assertNotNull(result)
        assertEquals(operation.id, result?.id)
    }

    @Test
    fun `getBalanceByLocation calculates correct balance`() = runTest {
        // Deposit 1000
        val deposit = createCashOperation(type = "deposit", amount = BigDecimal("1000.00"))
        // Withdraw 300
        val withdrawal = createCashOperation(type = "withdrawal", amount = BigDecimal("300.00"))
        // Payment 200
        val payment = createCashOperation(type = "payment", amount = BigDecimal("200.00"))
        
        cashOperationDao.insert(deposit)
        cashOperationDao.insert(withdrawal)
        cashOperationDao.insert(payment)

        val balance = cashOperationDao.getBalanceByLocation(testLocationId).first()

        // 1000 - 300 - 200 = 500
        assertEquals(0, BigDecimal("500.00").compareTo(balance))
    }

    @Test
    fun `getTotalBalance calculates balance across all locations`() = runTest {
        // Insert another location
        val otherLocationId = UUID.randomUUID()
        locationDao.insert(LocationEntity(
            id = otherLocationId,
            name = "Інший склад",
            type = "kiosk",
            createdAt = testInstant
        ))

        val deposit1 = createCashOperation(locationId = testLocationId, type = "deposit", amount = BigDecimal("1000.00"))
        val deposit2 = createCashOperation(locationId = otherLocationId, type = "deposit", amount = BigDecimal("500.00"))
        val withdrawal = createCashOperation(locationId = testLocationId, type = "withdrawal", amount = BigDecimal("200.00"))
        
        cashOperationDao.insert(deposit1)
        cashOperationDao.insert(deposit2)
        cashOperationDao.insert(withdrawal)

        val totalBalance = cashOperationDao.getTotalBalance().first()

        // 1000 + 500 - 200 = 1300
        assertEquals(0, BigDecimal("1300.00").compareTo(totalBalance))
    }

    @Test
    fun `getRecentByLocation returns limited ordered results`() = runTest {
        val op1 = createCashOperation(createdAt = Instant.parse("2024-01-15T08:00:00Z"))
        val op2 = createCashOperation(createdAt = Instant.parse("2024-01-15T10:00:00Z"))
        val op3 = createCashOperation(createdAt = Instant.parse("2024-01-15T09:00:00Z"))
        
        cashOperationDao.insert(op1)
        cashOperationDao.insert(op2)
        cashOperationDao.insert(op3)

        val result = cashOperationDao.getRecentByLocation(testLocationId, 2).first()

        assertEquals(2, result.size)
        // Should be ordered by created_at DESC
        assertEquals(op2.id, result[0].id)
        assertEquals(op3.id, result[1].id)
    }

    @Test
    fun `getByLocationAndDate filters by date range`() = runTest {
        val startOfDay = Instant.parse("2024-01-15T00:00:00Z")
        val endOfDay = Instant.parse("2024-01-16T00:00:00Z")
        
        val inRange = createCashOperation(createdAt = Instant.parse("2024-01-15T12:00:00Z"))
        val beforeRange = createCashOperation(createdAt = Instant.parse("2024-01-14T23:59:59Z"))
        val afterRange = createCashOperation(createdAt = Instant.parse("2024-01-16T00:00:01Z"))
        
        cashOperationDao.insert(inRange)
        cashOperationDao.insert(beforeRange)
        cashOperationDao.insert(afterRange)

        val result = cashOperationDao.getByLocationAndDate(testLocationId, startOfDay, endOfDay).first()

        assertEquals(1, result.size)
        assertEquals(inRange.id, result[0].id)
    }

    @Test
    fun `insertAll inserts multiple operations`() = runTest {
        val operations = listOf(
            createCashOperation(type = "deposit"),
            createCashOperation(type = "withdrawal"),
            createCashOperation(type = "payment")
        )
        
        cashOperationDao.insertAll(operations)

        val all = cashOperationDao.getAllOperations().first()
        assertEquals(3, all.size)
    }

    @Test
    fun `getAllOperations returns all operations ordered by date`() = runTest {
        val op1 = createCashOperation(createdAt = Instant.parse("2024-01-15T08:00:00Z"))
        val op2 = createCashOperation(createdAt = Instant.parse("2024-01-15T10:00:00Z"))
        
        cashOperationDao.insert(op1)
        cashOperationDao.insert(op2)

        val result = cashOperationDao.getAllOperations().first()

        assertEquals(2, result.size)
        assertEquals(op2.id, result[0].id) // Most recent first
    }

    @Test
    fun `purchase transaction affects balance negatively`() = runTest {
        val deposit = createCashOperation(type = "deposit", amount = BigDecimal("5000.00"))
        cashOperationDao.insert(deposit)
        
        // Create a purchase transaction (this is now how purchases affect balance)
        val purchaseTransaction = TransactionEntity(
            id = UUID.randomUUID(),
            localId = "tx-purchase-1",
            locationId = testLocationId,
            type = "purchase",
            transferLocationId = null,
            productId = testProductId,
            weightKg = BigDecimal("40.00"),
            pricePerKg = BigDecimal("50.00"),
            totalAmount = BigDecimal("2000.00"),
            notes = null,
            deviceId = "test-device",
            createdAt = testInstant,
            syncedAt = null
        )
        transactionDao.insert(purchaseTransaction)

        val balance = cashOperationDao.getBalanceByLocation(testLocationId).first()

        // 5000 (deposit) - 2000 (purchase transaction) = 3000
        assertEquals(0, BigDecimal("3000.00").compareTo(balance))
    }

    @Test
    fun `getDailyBalanceChange calculates daily movement`() = runTest {
        val startOfDay = Instant.parse("2024-01-15T00:00:00Z")
        val endOfDay = Instant.parse("2024-01-16T00:00:00Z")
        
        val deposit = createCashOperation(
            type = "deposit", 
            amount = BigDecimal("1000.00"),
            createdAt = Instant.parse("2024-01-15T10:00:00Z")
        )
        val withdrawal = createCashOperation(
            type = "withdrawal", 
            amount = BigDecimal("400.00"),
            createdAt = Instant.parse("2024-01-15T14:00:00Z")
        )
        // This one is outside the date range
        val outsideDeposit = createCashOperation(
            type = "deposit", 
            amount = BigDecimal("500.00"),
            createdAt = Instant.parse("2024-01-14T10:00:00Z")
        )
        
        cashOperationDao.insert(deposit)
        cashOperationDao.insert(withdrawal)
        cashOperationDao.insert(outsideDeposit)

        val dailyChange = cashOperationDao.getDailyBalanceChange(
            testLocationId, 
            startOfDay, 
            endOfDay
        ).first()

        // 1000 - 400 = 600 (only operations within the day)
        assertEquals(0, BigDecimal("600.00").compareTo(dailyChange))
    }
}
