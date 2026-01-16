package com.zagot.zagotplus.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.repository.CashRepositoryImpl
import com.zagot.zagotplus.data.repository.SaleBatchRepositoryImpl
import com.zagot.zagotplus.data.repository.TransactionRepositoryImpl
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.domain.model.SaleBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.sync.SyncManager
import io.mockk.every
import io.mockk.mockk
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
 * System tests for complete order-to-payment lifecycle.
 * Tests: Purchase → Sale → Cash Payment flow across multiple repositories.
 *
 * Verifies data consistency when operations span multiple repositories.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class OrderPaymentLifecycleIntegrationTest {

    private lateinit var database: ZagotDatabase
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var saleBatchRepository: SaleBatchRepositoryImpl
    private lateinit var cashRepository: CashRepositoryImpl
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var syncManager: SyncManager
    private lateinit var context: Context

    // Test data IDs
    private val locationId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val productId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val product2Id = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val categoryId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val testInstant = Instant.parse("2024-01-15T10:00:00Z")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        devicePreferences = mockk(relaxed = true)
        every { devicePreferences.getDeviceId() } returns "test-device-id"

        syncManager = mockk(relaxed = true)

        transactionRepository = TransactionRepositoryImpl(
            database = database,
            transactionDao = database.transactionDao(),
            devicePreferences = devicePreferences,
            syncManager = syncManager
        )

        saleBatchRepository = SaleBatchRepositoryImpl(
            database = database,
            saleBatchDao = database.saleBatchDao(),
            transactionDao = database.transactionDao(),
            syncManager = syncManager
        )

        cashRepository = CashRepositoryImpl(
            cashOperationDao = database.cashOperationDao(),
            expenseCategoryDao = database.expenseCategoryDao(),
            devicePreferences = devicePreferences
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Setup Helpers ====================

    private suspend fun insertTestLocation() {
        database.locationDao().insert(
            LocationEntity(
                id = locationId,
                name = "Кіоск Рівне",
                type = LocationType.KIOSK.name,
                createdAt = testInstant
            )
        )
    }

    private suspend fun insertTestProducts() {
        database.productDao().insert(
            ProductEntity(
                id = productId,
                localId = "local-product-1",
                name = "Горіх білий",
                defaultBuyPrice = BigDecimal("45.00"),
                defaultSellPrice = BigDecimal("55.00"),
                isActive = true,
                createdAt = testInstant
            )
        )
        database.productDao().insert(
            ProductEntity(
                id = product2Id,
                localId = "local-product-2",
                name = "Горіх червоний",
                defaultBuyPrice = BigDecimal("50.00"),
                defaultSellPrice = BigDecimal("65.00"),
                isActive = true,
                createdAt = testInstant
            )
        )
    }

    private suspend fun insertTestCategory() {
        database.expenseCategoryDao().insert(
            ExpenseCategoryEntity(
                id = categoryId,
                localId = "cat-1",
                name = "Закупки",
                isActive = true,
                createdAt = testInstant,
                syncedAt = null
            )
        )
    }

    private fun createSaleBatch(
        batchId: UUID = UUID.randomUUID(),
        weightKg: BigDecimal,
        amount: BigDecimal,
        itemCount: Int
    ): SaleBatch = SaleBatch(
        id = batchId,
        localId = "batch-${batchId}",
        locationId = locationId,
        notes = null,
        totalWeightKg = weightKg,
        totalAmount = amount,
        itemCount = itemCount,
        deviceId = "test-device",
        createdAt = Instant.now(),
        syncedAt = null
    )

    private fun createSaleTransaction(
        batchId: UUID,
        product: UUID,
        weightKg: BigDecimal,
        pricePerKg: BigDecimal
    ): Transaction = Transaction(
        id = UUID.randomUUID(),
        localId = "tx-${UUID.randomUUID()}",
        locationId = locationId,
        type = TransactionType.SALE,
        transferLocationId = null,
        productId = product,
        weightKg = weightKg.negate(), // Sales are stored as negative weight
        pricePerKg = pricePerKg,
        totalAmount = weightKg.multiply(pricePerKg),
        notes = null,
        deviceId = "test-device",
        createdAt = Instant.now(),
        syncedAt = null,
        batchId = null,
        saleBatchId = batchId
    )

    // ==================== Full Lifecycle Tests ====================

    @Test
    fun `complete purchase to sale to cash flow lifecycle`() = runTest {
        insertTestLocation()
        insertTestProducts()

        // Step 1: Purchase goods (inventory increases, cash decreases)
        transactionRepository.createPurchase(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("100.00"),
            pricePerKg = BigDecimal("45.00")
        )

        // Record cash payment for purchase
        cashRepository.payment(
            locationId = locationId,
            amount = BigDecimal("4500.00"), // 100 * 45
            categoryId = null,
            notes = "Закупка горіхів"
        )

        // Verify inventory and cash state after purchase
        val inventoryAfterPurchase = transactionRepository.getInventory().first()
        val balanceAfterPurchase = cashRepository.getBalance(locationId).first()
        
        assertEquals(1, inventoryAfterPurchase.size)
        assertTrue(inventoryAfterPurchase[0].totalWeightKg.compareTo(BigDecimal("100.00")) == 0)
        assertTrue(balanceAfterPurchase.compareTo(BigDecimal("-4500.00")) == 0) // Payment = negative

        // Step 2: Sell some goods (inventory decreases, cash increases)
        val batchId = UUID.randomUUID()
        val batch = createSaleBatch(batchId, BigDecimal("30.00"), BigDecimal("1650.00"), 1)
        val transaction = createSaleTransaction(batchId, productId, BigDecimal("30.00"), BigDecimal("55.00"))
        
        saleBatchRepository.createBatchWithTransactions(batch, listOf(transaction))

        // Record cash deposit from sale
        cashRepository.deposit(
            locationId = locationId,
            amount = BigDecimal("1650.00"), // 30 * 55
            notes = "Продаж горіхів"
        )

        // Verify final state
        val finalInventory = transactionRepository.getInventory().first()
        val finalBalance = cashRepository.getBalance(locationId).first()

        // Inventory: 100 - 30 = 70kg
        assertTrue(finalInventory[0].totalWeightKg.compareTo(BigDecimal("70.00")) == 0)
        
        // Cash: -4500 + 1650 = -2850
        assertTrue(finalBalance.compareTo(BigDecimal("-2850.00")) == 0)
    }

    @Test
    fun `multiple sales accumulate correctly in cash and reduce inventory`() = runTest {
        insertTestLocation()
        insertTestProducts()

        // Purchase initial stock
        transactionRepository.createPurchase(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("500.00"),
            pricePerKg = BigDecimal("45.00")
        )

        // First sale: 50kg @ 55/kg = 2750
        val batch1Id = UUID.randomUUID()
        val batch1 = createSaleBatch(batch1Id, BigDecimal("50.00"), BigDecimal("2750.00"), 1)
        val tx1 = createSaleTransaction(batch1Id, productId, BigDecimal("50.00"), BigDecimal("55.00"))
        saleBatchRepository.createBatchWithTransactions(batch1, listOf(tx1))
        cashRepository.deposit(locationId, BigDecimal("2750.00"), "Sale 1")

        // Second sale: 75kg @ 55/kg = 4125
        val batch2Id = UUID.randomUUID()
        val batch2 = createSaleBatch(batch2Id, BigDecimal("75.00"), BigDecimal("4125.00"), 1)
        val tx2 = createSaleTransaction(batch2Id, productId, BigDecimal("75.00"), BigDecimal("55.00"))
        saleBatchRepository.createBatchWithTransactions(batch2, listOf(tx2))
        cashRepository.deposit(locationId, BigDecimal("4125.00"), "Sale 2")

        // Third sale: 25kg @ 60/kg = 1500
        val batch3Id = UUID.randomUUID()
        val batch3 = createSaleBatch(batch3Id, BigDecimal("25.00"), BigDecimal("1500.00"), 1)
        val tx3 = createSaleTransaction(batch3Id, productId, BigDecimal("25.00"), BigDecimal("60.00"))
        saleBatchRepository.createBatchWithTransactions(batch3, listOf(tx3))
        cashRepository.deposit(locationId, BigDecimal("1500.00"), "Sale 3")

        // Verify inventory: 500 - 50 - 75 - 25 = 350kg
        val inventory = transactionRepository.getInventory().first()
        assertTrue(inventory[0].totalWeightKg.compareTo(BigDecimal("350.00")) == 0)

        // Verify cash: 2750 + 4125 + 1500 = 8375
        val balance = cashRepository.getBalance(locationId).first()
        assertTrue(balance.compareTo(BigDecimal("8375.00")) == 0)
    }

    @Test
    fun `sale batch with multiple products updates all inventories correctly`() = runTest {
        insertTestLocation()
        insertTestProducts()

        // Purchase both products
        transactionRepository.createPurchase(locationId, productId, BigDecimal("100.00"), BigDecimal("45.00"))
        transactionRepository.createPurchase(locationId, product2Id, BigDecimal("80.00"), BigDecimal("50.00"))

        // Single sale batch with both products
        val batchId = UUID.randomUUID()
        val batch = createSaleBatch(batchId, BigDecimal("35.00"), BigDecimal("2075.00"), 2)
        val tx1 = createSaleTransaction(batchId, productId, BigDecimal("20.00"), BigDecimal("55.00"))
        val tx2 = createSaleTransaction(batchId, product2Id, BigDecimal("15.00"), BigDecimal("65.00"))
        
        saleBatchRepository.createBatchWithTransactions(batch, listOf(tx1, tx2))

        // Verify inventory for each product
        val inventory = transactionRepository.getInventory().first()
        
        val product1Inv = inventory.find { it.productId == productId }
        val product2Inv = inventory.find { it.productId == product2Id }

        assertTrue(product1Inv?.totalWeightKg?.compareTo(BigDecimal("80.00")) == 0) // 100 - 20
        assertTrue(product2Inv?.totalWeightKg?.compareTo(BigDecimal("65.00")) == 0) // 80 - 15
    }

    @Test
    fun `transactions and batches maintain referential integrity`() = runTest {
        insertTestLocation()
        insertTestProducts()

        transactionRepository.createPurchase(locationId, productId, BigDecimal("100.00"), BigDecimal("45.00"))

        val batchId = UUID.randomUUID()
        val batch = createSaleBatch(batchId, BigDecimal("25.00"), BigDecimal("1375.00"), 1)
        val transaction = createSaleTransaction(batchId, productId, BigDecimal("25.00"), BigDecimal("55.00"))
        
        saleBatchRepository.createBatchWithTransactions(batch, listOf(transaction))

        // Verify transaction references the batch
        val allTransactions = transactionRepository.getPaginatedTransactions(100, 0).first()
        val saleTransaction = allTransactions.find { it.type == TransactionType.SALE }
        
        assertNotNull("Sale transaction should exist", saleTransaction)
        assertEquals(batchId, saleTransaction?.saleBatchId)
    }

    @Test
    fun `cash operations maintain correct balance through deposits and withdrawals`() = runTest {
        insertTestLocation()
        insertTestCategory()

        // Start with initial deposit
        cashRepository.deposit(locationId, BigDecimal("10000.00"), "Initial deposit")
        
        var balance = cashRepository.getBalance(locationId).first()
        assertTrue(balance.compareTo(BigDecimal("10000.00")) == 0)

        // Payment for purchase
        cashRepository.payment(locationId, BigDecimal("3000.00"), categoryId, "Purchase 1")
        balance = cashRepository.getBalance(locationId).first()
        assertTrue(balance.compareTo(BigDecimal("7000.00")) == 0)

        // Deposit from sale
        cashRepository.deposit(locationId, BigDecimal("2500.00"), "Sale 1")
        balance = cashRepository.getBalance(locationId).first()
        assertTrue(balance.compareTo(BigDecimal("9500.00")) == 0)

        // Withdrawal
        cashRepository.withdraw(locationId, BigDecimal("1000.00"), "End of day withdrawal")
        balance = cashRepository.getBalance(locationId).first()
        assertTrue(balance.compareTo(BigDecimal("8500.00")) == 0)

        // Another payment
        cashRepository.payment(locationId, BigDecimal("2000.00"), null, "Misc expense")
        balance = cashRepository.getBalance(locationId).first()
        assertTrue(balance.compareTo(BigDecimal("6500.00")) == 0)
    }

    @Test
    fun `concurrent purchases and sales maintain data consistency`() = runTest {
        insertTestLocation()
        insertTestProducts()

        // Initial purchase
        transactionRepository.createPurchase(locationId, productId, BigDecimal("1000.00"), BigDecimal("45.00"))

        // Simulate concurrent operations (in sequence for test, but tests data consistency)
        transactionRepository.createPurchase(locationId, productId, BigDecimal("200.00"), BigDecimal("46.00"))
        
        val batch1Id = UUID.randomUUID()
        val batch1 = createSaleBatch(batch1Id, BigDecimal("100.00"), BigDecimal("5500.00"), 1)
        val tx1 = createSaleTransaction(batch1Id, productId, BigDecimal("100.00"), BigDecimal("55.00"))
        saleBatchRepository.createBatchWithTransactions(batch1, listOf(tx1))
        
        transactionRepository.createPurchase(locationId, productId, BigDecimal("150.00"), BigDecimal("45.50"))
        
        val batch2Id = UUID.randomUUID()
        val batch2 = createSaleBatch(batch2Id, BigDecimal("50.00"), BigDecimal("2800.00"), 1)
        val tx2 = createSaleTransaction(batch2Id, productId, BigDecimal("50.00"), BigDecimal("56.00"))
        saleBatchRepository.createBatchWithTransactions(batch2, listOf(tx2))

        // Final inventory: 1000 + 200 - 100 + 150 - 50 = 1200
        val inventory = transactionRepository.getInventory().first()
        assertTrue(inventory[0].totalWeightKg.compareTo(BigDecimal("1200.00")) == 0)

        // All transactions should be recorded
        val allTransactions = transactionRepository.getPaginatedTransactions(100, 0).first()
        assertEquals(5, allTransactions.size) // 3 purchases + 2 sales
    }
}
