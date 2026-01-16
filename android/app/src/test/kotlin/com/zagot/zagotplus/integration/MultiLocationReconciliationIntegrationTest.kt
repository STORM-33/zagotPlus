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
 * System tests for multi-location inventory reconciliation.
 * Tests cross-location inventory consistency and global totals.
 *
 * Scenario: Central warehouse purchases → distributes to multiple kiosks → verifies totals match
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MultiLocationReconciliationIntegrationTest {

    private lateinit var database: ZagotDatabase
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var syncManager: SyncManager
    private lateinit var context: Context

    // Test locations
    private val warehouseId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val kiosk1Id = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val kiosk2Id = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val mobileId = UUID.fromString("44444444-4444-4444-4444-444444444444")
    
    // Test products
    private val productWhiteNut = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa")
    private val productRedNut = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb")
    
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
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Setup Helpers ====================

    private suspend fun insertAllLocations() {
        listOf(
            LocationEntity(warehouseId, "Центральний склад", LocationType.KIOSK.name, testInstant),
            LocationEntity(kiosk1Id, "Кіоск №1", LocationType.KIOSK.name, testInstant),
            LocationEntity(kiosk2Id, "Кіоск №2", LocationType.KIOSK.name, testInstant),
            LocationEntity(mobileId, "Мобільна точка", LocationType.MOBILE.name, testInstant)
        ).forEach { database.locationDao().insert(it) }
    }

    private suspend fun insertAllProducts() {
        listOf(
            ProductEntity(
                id = productWhiteNut,
                localId = "local-white-nut",
                name = "Горіх білий",
                defaultBuyPrice = BigDecimal("45.00"),
                defaultSellPrice = BigDecimal("55.00"),
                isActive = true,
                createdAt = testInstant
            ),
            ProductEntity(
                id = productRedNut,
                localId = "local-red-nut",
                name = "Горіх червоний",
                defaultBuyPrice = BigDecimal("50.00"),
                defaultSellPrice = BigDecimal("65.00"),
                isActive = true,
                createdAt = testInstant
            )
        ).forEach { database.productDao().insert(it) }
    }

    // ==================== Multi-Location Reconciliation Tests ====================

    @Test
    fun `global inventory equals sum of all location inventories`() = runTest {
        insertAllLocations()
        insertAllProducts()

        // Purchase 500kg at warehouse
        transactionRepository.createPurchase(
            locationId = warehouseId,
            productId = productWhiteNut,
            weightKg = BigDecimal("500.00"),
            pricePerKg = BigDecimal("45.00")
        )

        // Transfer to kiosk1: 150kg
        transactionRepository.createTransfer(
            fromLocationId = warehouseId,
            toLocationId = kiosk1Id,
            productId = productWhiteNut,
            weightKg = BigDecimal("150.00")
        )

        // Transfer to kiosk2: 200kg
        transactionRepository.createTransfer(
            fromLocationId = warehouseId,
            toLocationId = kiosk2Id,
            productId = productWhiteNut,
            weightKg = BigDecimal("200.00")
        )

        // Transfer to mobile: 100kg
        transactionRepository.createTransfer(
            fromLocationId = warehouseId,
            toLocationId = mobileId,
            productId = productWhiteNut,
            weightKg = BigDecimal("100.00")
        )

        // Verify global inventory = 500kg
        val globalInventory = transactionRepository.getInventory().first()
        val totalWeight = globalInventory
            .filter { it.productId == productWhiteNut }
            .sumOf { it.totalWeightKg }
        
        assertTrue("Global total should be 500kg", totalWeight.compareTo(BigDecimal("500.00")) == 0)

        // Verify per-location breakdown
        val warehouseInventory = transactionRepository.getInventoryByLocation(warehouseId).first()
        val kiosk1Inventory = transactionRepository.getInventoryByLocation(kiosk1Id).first()
        val kiosk2Inventory = transactionRepository.getInventoryByLocation(kiosk2Id).first()
        val mobileInventory = transactionRepository.getInventoryByLocation(mobileId).first()

        // Warehouse: 500 - 150 - 200 - 100 = 50kg
        val warehouseWeight = warehouseInventory.find { it.productId == productWhiteNut }?.totalWeightKg ?: BigDecimal.ZERO
        assertTrue("Warehouse should have 50kg", warehouseWeight.compareTo(BigDecimal("50.00")) == 0)

        // Kiosk1: 150kg
        val kiosk1Weight = kiosk1Inventory.find { it.productId == productWhiteNut }?.totalWeightKg ?: BigDecimal.ZERO
        assertTrue("Kiosk1 should have 150kg", kiosk1Weight.compareTo(BigDecimal("150.00")) == 0)

        // Kiosk2: 200kg
        val kiosk2Weight = kiosk2Inventory.find { it.productId == productWhiteNut }?.totalWeightKg ?: BigDecimal.ZERO
        assertTrue("Kiosk2 should have 200kg", kiosk2Weight.compareTo(BigDecimal("200.00")) == 0)

        // Mobile: 100kg
        val mobileWeight = mobileInventory.find { it.productId == productWhiteNut }?.totalWeightKg ?: BigDecimal.ZERO
        assertTrue("Mobile should have 100kg", mobileWeight.compareTo(BigDecimal("100.00")) == 0)

        // Sum of locations = global
        val sumOfLocations = warehouseWeight + kiosk1Weight + kiosk2Weight + mobileWeight
        assertTrue("Sum of locations should equal global", sumOfLocations.compareTo(totalWeight) == 0)
    }

    @Test
    fun `multiple products across multiple locations maintain separate inventories`() = runTest {
        insertAllLocations()
        insertAllProducts()

        // Purchase white nuts at warehouse
        transactionRepository.createPurchase(
            locationId = warehouseId,
            productId = productWhiteNut,
            weightKg = BigDecimal("300.00"),
            pricePerKg = BigDecimal("45.00")
        )

        // Purchase red nuts at warehouse
        transactionRepository.createPurchase(
            locationId = warehouseId,
            productId = productRedNut,
            weightKg = BigDecimal("200.00"),
            pricePerKg = BigDecimal("50.00")
        )

        // Transfer white nuts to kiosk1
        transactionRepository.createTransfer(
            fromLocationId = warehouseId,
            toLocationId = kiosk1Id,
            productId = productWhiteNut,
            weightKg = BigDecimal("100.00")
        )

        // Transfer red nuts to kiosk2
        transactionRepository.createTransfer(
            fromLocationId = warehouseId,
            toLocationId = kiosk2Id,
            productId = productRedNut,
            weightKg = BigDecimal("150.00")
        )

        // Verify global inventory for each product
        val globalInventory = transactionRepository.getInventory().first()
        
        val globalWhiteNut = globalInventory
            .filter { it.productId == productWhiteNut }
            .sumOf { it.totalWeightKg }
        val globalRedNut = globalInventory
            .filter { it.productId == productRedNut }
            .sumOf { it.totalWeightKg }

        assertTrue("Global white nut should be 300kg", globalWhiteNut.compareTo(BigDecimal("300.00")) == 0)
        assertTrue("Global red nut should be 200kg", globalRedNut.compareTo(BigDecimal("200.00")) == 0)

        // Verify kiosk1 only has white nuts
        val kiosk1Inventory = transactionRepository.getInventoryByLocation(kiosk1Id).first()
        assertEquals("Kiosk1 should have 1 product", 1, kiosk1Inventory.size)
        assertEquals(productWhiteNut, kiosk1Inventory[0].productId)

        // Verify kiosk2 only has red nuts
        val kiosk2Inventory = transactionRepository.getInventoryByLocation(kiosk2Id).first()
        assertEquals("Kiosk2 should have 1 product", 1, kiosk2Inventory.size)
        assertEquals(productRedNut, kiosk2Inventory[0].productId)
    }

    @Test
    fun `chain of transfers maintains total inventory`() = runTest {
        insertAllLocations()
        insertAllProducts()

        // Purchase at warehouse
        transactionRepository.createPurchase(
            locationId = warehouseId,
            productId = productWhiteNut,
            weightKg = BigDecimal("1000.00"),
            pricePerKg = BigDecimal("45.00")
        )

        // Chain: Warehouse → Kiosk1 → Kiosk2 → Mobile
        transactionRepository.createTransfer(
            fromLocationId = warehouseId,
            toLocationId = kiosk1Id,
            productId = productWhiteNut,
            weightKg = BigDecimal("500.00")
        )

        transactionRepository.createTransfer(
            fromLocationId = kiosk1Id,
            toLocationId = kiosk2Id,
            productId = productWhiteNut,
            weightKg = BigDecimal("300.00")
        )

        transactionRepository.createTransfer(
            fromLocationId = kiosk2Id,
            toLocationId = mobileId,
            productId = productWhiteNut,
            weightKg = BigDecimal("100.00")
        )

        // Verify total still 1000kg despite chain
        val globalInventory = transactionRepository.getInventory().first()
        val totalWeight = globalInventory
            .filter { it.productId == productWhiteNut }
            .sumOf { it.totalWeightKg }

        assertTrue("Chain transfers should preserve total", totalWeight.compareTo(BigDecimal("1000.00")) == 0)

        // Verify each location
        val warehouseWeight = transactionRepository.getInventoryByLocation(warehouseId).first()
            .find { it.productId == productWhiteNut }?.totalWeightKg ?: BigDecimal.ZERO
        val kiosk1Weight = transactionRepository.getInventoryByLocation(kiosk1Id).first()
            .find { it.productId == productWhiteNut }?.totalWeightKg ?: BigDecimal.ZERO
        val kiosk2Weight = transactionRepository.getInventoryByLocation(kiosk2Id).first()
            .find { it.productId == productWhiteNut }?.totalWeightKg ?: BigDecimal.ZERO
        val mobileWeight = transactionRepository.getInventoryByLocation(mobileId).first()
            .find { it.productId == productWhiteNut }?.totalWeightKg ?: BigDecimal.ZERO

        // Warehouse: 1000 - 500 = 500
        assertTrue("Warehouse should have 500kg", warehouseWeight.compareTo(BigDecimal("500.00")) == 0)
        // Kiosk1: 500 - 300 = 200
        assertTrue("Kiosk1 should have 200kg", kiosk1Weight.compareTo(BigDecimal("200.00")) == 0)
        // Kiosk2: 300 - 100 = 200
        assertTrue("Kiosk2 should have 200kg", kiosk2Weight.compareTo(BigDecimal("200.00")) == 0)
        // Mobile: 100
        assertTrue("Mobile should have 100kg", mobileWeight.compareTo(BigDecimal("100.00")) == 0)
    }

    @Test
    fun `sales at kiosk reduce only that location inventory`() = runTest {
        insertAllLocations()
        insertAllProducts()

        // Setup: Purchase and distribute
        transactionRepository.createPurchase(
            locationId = warehouseId,
            productId = productWhiteNut,
            weightKg = BigDecimal("400.00"),
            pricePerKg = BigDecimal("45.00")
        )

        transactionRepository.createTransfer(
            fromLocationId = warehouseId,
            toLocationId = kiosk1Id,
            productId = productWhiteNut,
            weightKg = BigDecimal("200.00")
        )

        transactionRepository.createTransfer(
            fromLocationId = warehouseId,
            toLocationId = kiosk2Id,
            productId = productWhiteNut,
            weightKg = BigDecimal("200.00")
        )

        // Sale at kiosk1: 50kg
        transactionRepository.createSale(
            locationId = kiosk1Id,
            productId = productWhiteNut,
            weightKg = BigDecimal("50.00"),
            pricePerKg = BigDecimal("55.00")
        )

        // Verify kiosk1 reduced, kiosk2 unchanged
        val kiosk1Weight = transactionRepository.getInventoryByLocation(kiosk1Id).first()
            .find { it.productId == productWhiteNut }?.totalWeightKg ?: BigDecimal.ZERO
        val kiosk2Weight = transactionRepository.getInventoryByLocation(kiosk2Id).first()
            .find { it.productId == productWhiteNut }?.totalWeightKg ?: BigDecimal.ZERO

        assertTrue("Kiosk1 should have 150kg after sale", kiosk1Weight.compareTo(BigDecimal("150.00")) == 0)
        assertTrue("Kiosk2 should still have 200kg", kiosk2Weight.compareTo(BigDecimal("200.00")) == 0)

        // Global total: 400 - 50 = 350kg
        val globalInventory = transactionRepository.getInventory().first()
        val totalWeight = globalInventory
            .filter { it.productId == productWhiteNut }
            .sumOf { it.totalWeightKg }
        assertTrue("Global should be 350kg after sale", totalWeight.compareTo(BigDecimal("350.00")) == 0)
    }

    @Test
    fun `empty locations have zero inventory`() = runTest {
        insertAllLocations()
        insertAllProducts()

        // Only add inventory to warehouse
        transactionRepository.createPurchase(
            locationId = warehouseId,
            productId = productWhiteNut,
            weightKg = BigDecimal("100.00"),
            pricePerKg = BigDecimal("45.00")
        )

        // Kiosk1, kiosk2, mobile should have empty inventory
        val kiosk1Inventory = transactionRepository.getInventoryByLocation(kiosk1Id).first()
        val kiosk2Inventory = transactionRepository.getInventoryByLocation(kiosk2Id).first()
        val mobileInventory = transactionRepository.getInventoryByLocation(mobileId).first()

        assertTrue("Kiosk1 should be empty", kiosk1Inventory.isEmpty())
        assertTrue("Kiosk2 should be empty", kiosk2Inventory.isEmpty())
        assertTrue("Mobile should be empty", mobileInventory.isEmpty())
    }

    @Test
    fun `oversold location shows negative inventory`() = runTest {
        insertAllLocations()
        insertAllProducts()

        // Purchase 50kg at kiosk
        transactionRepository.createPurchase(
            locationId = kiosk1Id,
            productId = productWhiteNut,
            weightKg = BigDecimal("50.00"),
            pricePerKg = BigDecimal("45.00")
        )

        // Sell 80kg (30kg oversold)
        transactionRepository.createSale(
            locationId = kiosk1Id,
            productId = productWhiteNut,
            weightKg = BigDecimal("80.00"),
            pricePerKg = BigDecimal("55.00")
        )

        // Verify negative inventory
        val kiosk1Inventory = transactionRepository.getInventoryByLocation(kiosk1Id).first()
        val weight = kiosk1Inventory.find { it.productId == productWhiteNut }?.totalWeightKg ?: BigDecimal.ZERO

        assertTrue("Oversold location should show -30kg", weight.compareTo(BigDecimal("-30.00")) == 0)
    }
}
