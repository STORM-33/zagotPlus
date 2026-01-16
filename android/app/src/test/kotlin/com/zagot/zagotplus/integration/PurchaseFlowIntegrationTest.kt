package com.zagot.zagotplus.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.repository.TransactionRepositoryImpl
import com.zagot.zagotplus.domain.model.LocationType
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
 * Integration tests for purchase flow.
 * Tests the full stack: Repository → DAO → Database
 * 
 * Uses Robolectric for in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class PurchaseFlowIntegrationTest {

    private lateinit var database: ZagotDatabase
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var syncManager: SyncManager
    private lateinit var context: Context

    // Test data IDs
    private val locationId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val productId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val testInstant = Instant.parse("2024-01-15T10:00:00Z")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        
        // Create real in-memory database
        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        
        // Mock DevicePreferences
        devicePreferences = mockk(relaxed = true)
        every { devicePreferences.getDeviceId() } returns "test-device-id"
        
        // Mock SyncManager to no-op
        syncManager = mockk(relaxed = true)
        
        // Create real repository with in-memory database
        transactionRepository = TransactionRepositoryImpl(
            database = database,
            transactionDao = database.transactionDao(),
            devicePreferences = devicePreferences,
            syncManager = syncManager
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
                name = "Склад Рівне",
                type = LocationType.KIOSK.name,
                createdAt = testInstant
            )
        )
    }

    private suspend fun insertTestProduct() {
        database.productDao().insert(
            ProductEntity(
                id = productId,
                localId = "local-product-1",
                name = "Горіх білий",
                defaultBuyPrice = BigDecimal("45.00"),
                defaultSellPrice = BigDecimal("55.00"),
                isActive = true,
                createdAt = testInstant,
                syncedAt = null
            )
        )
    }

    // ==================== Purchase Creation Tests ====================

    @Test
    fun `createPurchase persists transaction to database`() = runTest {
        insertTestLocation()
        insertTestProduct()

        val transaction = transactionRepository.createPurchase(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("10.5"),
            pricePerKg = BigDecimal("45.00"),
            notes = "Test purchase"
        )

        // Verify transaction returned
        assertEquals(TransactionType.PURCHASE, transaction.type)
        assertTrue(transaction.weightKg.compareTo(BigDecimal("10.5")) == 0)
        assertTrue(transaction.pricePerKg!!.compareTo(BigDecimal("45.00")) == 0)

        // Verify persisted to database
        val allTransactions = transactionRepository.getPaginatedTransactions(100, 0).first()
        assertEquals(1, allTransactions.size)
        assertEquals(transaction.id, allTransactions[0].id)
    }

    @Test
    fun `createPurchase updates inventory correctly`() = runTest {
        insertTestLocation()
        insertTestProduct()

        // Create first purchase
        transactionRepository.createPurchase(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("10.0"),
            pricePerKg = BigDecimal("45.00")
        )

        // Create second purchase
        transactionRepository.createPurchase(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("5.0"),
            pricePerKg = BigDecimal("46.00")
        )

        // Verify inventory shows cumulative weight
        val inventory = transactionRepository.getInventory().first()
        assertEquals(1, inventory.size)
        assertTrue(inventory[0].totalWeightKg.compareTo(BigDecimal("15.0")) == 0)
    }

    @Test
    fun `createPurchase calculates total amount correctly`() = runTest {
        insertTestLocation()
        insertTestProduct()

        val transaction = transactionRepository.createPurchase(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("10.5"),
            pricePerKg = BigDecimal("45.00")
        )

        // 10.5 * 45.00 = 472.50
        assertTrue(transaction.totalAmount!!.compareTo(BigDecimal("472.50")) == 0)
    }

    @Test
    fun `createPurchase rejects negative weight`() = runTest {
        insertTestLocation()
        insertTestProduct()

        val exception = assertThrows(IllegalArgumentException::class.java) {
            kotlinx.coroutines.test.runTest {
                transactionRepository.createPurchase(
                    locationId = locationId,
                    productId = productId,
                    weightKg = BigDecimal("-5.0"),
                    pricePerKg = BigDecimal("45.00")
                )
            }
        }
        assertTrue(exception.message!!.contains("positive"))
    }

    // ==================== Sale Flow Tests ====================

    @Test
    fun `createSale after purchase reduces inventory`() = runTest {
        insertTestLocation()
        insertTestProduct()

        // Purchase 20 kg
        transactionRepository.createPurchase(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("20.0"),
            pricePerKg = BigDecimal("45.00")
        )

        // Sell 8 kg
        transactionRepository.createSale(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("8.0"),
            pricePerKg = BigDecimal("55.00")
        )

        // Verify inventory reduced
        val inventory = transactionRepository.getInventory().first()
        assertEquals(1, inventory.size)
        // 20 - 8 = 12 kg remaining
        assertTrue(inventory[0].totalWeightKg.compareTo(BigDecimal("12.0")) == 0)
    }

    @Test
    fun `multiple transactions correctly aggregate in inventory`() = runTest {
        insertTestLocation()
        insertTestProduct()

        // Purchase 100 kg
        transactionRepository.createPurchase(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("100.0"),
            pricePerKg = BigDecimal("45.00")
        )

        // Sell 30 kg
        transactionRepository.createSale(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("30.0"),
            pricePerKg = BigDecimal("55.00")
        )

        // Purchase 25 kg more
        transactionRepository.createPurchase(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("25.0"),
            pricePerKg = BigDecimal("44.00")
        )

        // Sell 15 kg
        transactionRepository.createSale(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("15.0"),
            pricePerKg = BigDecimal("56.00")
        )

        // Verify inventory: 100 - 30 + 25 - 15 = 80 kg
        val inventory = transactionRepository.getInventory().first()
        assertEquals(1, inventory.size)
        assertTrue(inventory[0].totalWeightKg.compareTo(BigDecimal("80.0")) == 0)
    }

    // ==================== Transfer Tests ====================

    @Test
    fun `createTransfer creates linked transactions`() = runTest {
        insertTestLocation()
        insertTestProduct()
        
        // Create second location
        val toLocationId = UUID.fromString("33333333-3333-3333-3333-333333333333")
        database.locationDao().insert(
            LocationEntity(
                id = toLocationId,
                name = "Склад Київ",
                type = LocationType.KIOSK.name,
                createdAt = testInstant
            )
        )

        // Purchase at first location
        transactionRepository.createPurchase(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("50.0"),
            pricePerKg = BigDecimal("45.00")
        )

        // Transfer 20 kg to second location
        val (outTx, inTx) = transactionRepository.createTransfer(
            fromLocationId = locationId,
            toLocationId = toLocationId,
            productId = productId,
            weightKg = BigDecimal("20.0")
        )

        // Verify transactions are linked
        assertEquals(TransactionType.TRANSFER_OUT, outTx.type)
        assertEquals(TransactionType.TRANSFER_IN, inTx.type)
        assertEquals(toLocationId, outTx.transferLocationId)
        assertEquals(locationId, inTx.transferLocationId)

        // Verify inventory at each location
        val inventoryLoc1 = transactionRepository.getInventoryByLocation(locationId).first()
        val inventoryLoc2 = transactionRepository.getInventoryByLocation(toLocationId).first()

        assertEquals(1, inventoryLoc1.size)
        assertTrue(inventoryLoc1[0].totalWeightKg.compareTo(BigDecimal("30.0")) == 0) // 50 - 20

        assertEquals(1, inventoryLoc2.size)
        assertTrue(inventoryLoc2[0].totalWeightKg.compareTo(BigDecimal("20.0")) == 0) // 0 + 20
    }

    // ==================== Batch Sales Tests ====================

    @Test
    fun `createSales atomically creates multiple transactions`() = runTest {
        insertTestLocation()
        insertTestProduct()

        // Create second product
        val product2Id = UUID.fromString("44444444-4444-4444-4444-444444444444")
        database.productDao().insert(
            ProductEntity(
                id = product2Id,
                localId = "local-product-2",
                name = "Горіх червоний",
                defaultBuyPrice = BigDecimal("35.00"),
                defaultSellPrice = BigDecimal("45.00"),
                isActive = true,
                createdAt = testInstant,
                syncedAt = null
            )
        )

        // Purchase both products
        transactionRepository.createPurchase(
            locationId = locationId,
            productId = productId,
            weightKg = BigDecimal("50.0"),
            pricePerKg = BigDecimal("45.00")
        )
        transactionRepository.createPurchase(
            locationId = locationId,
            productId = product2Id,
            weightKg = BigDecimal("30.0"),
            pricePerKg = BigDecimal("35.00")
        )

        // Batch sale of both products
        val sales = listOf(
            com.zagot.zagotplus.domain.repository.SaleInput(
                locationId = locationId,
                productId = productId,
                weightKg = BigDecimal("10.0"),
                pricePerKg = BigDecimal("55.00")
            ),
            com.zagot.zagotplus.domain.repository.SaleInput(
                locationId = locationId,
                productId = product2Id,
                weightKg = BigDecimal("5.0"),
                pricePerKg = BigDecimal("45.00")
            )
        )

        val createdSales = transactionRepository.createSales(sales)

        // Verify all sales created
        assertEquals(2, createdSales.size)
        assertTrue(createdSales.all { it.type == TransactionType.SALE })

        // Verify inventory for both products
        val inventory = transactionRepository.getInventory().first()
        assertEquals(2, inventory.size)
        
        val product1Inv = inventory.find { it.productId == productId }!!
        val product2Inv = inventory.find { it.productId == product2Id }!!
        
        assertTrue(product1Inv.totalWeightKg.compareTo(BigDecimal("40.0")) == 0) // 50 - 10
        assertTrue(product2Inv.totalWeightKg.compareTo(BigDecimal("25.0")) == 0) // 30 - 5
    }

    // ==================== Pagination Tests ====================

    @Test
    fun `getPaginatedTransactions returns correct page`() = runTest {
        insertTestLocation()
        insertTestProduct()

        // Create 10 purchases
        repeat(10) { i ->
            transactionRepository.createPurchase(
                locationId = locationId,
                productId = productId,
                weightKg = BigDecimal("${i + 1}.0"),
                pricePerKg = BigDecimal("45.00")
            )
        }

        // Get first page (3 items)
        val page1 = transactionRepository.getPaginatedTransactions(limit = 3, offset = 0).first()
        assertEquals(3, page1.size)

        // Get second page
        val page2 = transactionRepository.getPaginatedTransactions(limit = 3, offset = 3).first()
        assertEquals(3, page2.size)

        // Verify no overlap between pages
        val page1Ids = page1.map { it.id }.toSet()
        val page2Ids = page2.map { it.id }.toSet()
        assertTrue(page1Ids.intersect(page2Ids).isEmpty())
    }

    @Test
    fun `getTotalTransactionCount returns correct count`() = runTest {
        insertTestLocation()
        insertTestProduct()

        assertEquals(0, transactionRepository.getTotalTransactionCount().first())

        repeat(5) {
            transactionRepository.createPurchase(
                locationId = locationId,
                productId = productId,
                weightKg = BigDecimal("10.0"),
                pricePerKg = BigDecimal("45.00")
            )
        }

        assertEquals(5, transactionRepository.getTotalTransactionCount().first())
    }
}
