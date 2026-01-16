package com.zagot.zagotplus.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.dao.TransactionQueryBuilder
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
 * Integration tests for transfer flow between locations.
 * Tests the full stack: Repository → DAO → Database
 *
 * Uses Robolectric for in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TransferFlowIntegrationTest {

    private lateinit var database: ZagotDatabase
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var syncManager: SyncManager
    private lateinit var context: Context

    // Test data IDs
    private val locationRivne = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val locationKyiv = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val locationLviv = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val productId = UUID.fromString("44444444-4444-4444-4444-444444444444")
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

    private suspend fun insertTestLocations() {
        database.locationDao().insert(
            LocationEntity(
                id = locationRivne,
                name = "Склад Рівне",
                type = LocationType.KIOSK.name,
                createdAt = testInstant
            )
        )
        database.locationDao().insert(
            LocationEntity(
                id = locationKyiv,
                name = "Склад Київ",
                type = LocationType.KIOSK.name,
                createdAt = testInstant
            )
        )
        database.locationDao().insert(
            LocationEntity(
                id = locationLviv,
                name = "Склад Львів",
                type = LocationType.MOBILE.name,
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

    private suspend fun purchaseAtLocation(locationId: UUID, weight: BigDecimal) {
        transactionRepository.createPurchase(
            locationId = locationId,
            productId = productId,
            weightKg = weight,
            pricePerKg = BigDecimal("45.00")
        )
    }

    // ==================== Transfer Creation Tests ====================

    @Test
    fun `createTransfer creates paired transactions`() = runTest {
        insertTestLocations()
        insertTestProduct()
        purchaseAtLocation(locationRivne, BigDecimal("100.0"))

        val (outTx, inTx) = transactionRepository.createTransfer(
            fromLocationId = locationRivne,
            toLocationId = locationKyiv,
            productId = productId,
            weightKg = BigDecimal("30.0")
        )

        // Verify transaction types
        assertEquals(TransactionType.TRANSFER_OUT, outTx.type)
        assertEquals(TransactionType.TRANSFER_IN, inTx.type)

        // Verify weights match (out is negative, in is positive)
        assertTrue(outTx.weightKg.compareTo(BigDecimal("-30.0")) == 0)
        assertTrue(inTx.weightKg.compareTo(BigDecimal("30.0")) == 0)

        // Verify locations are cross-linked
        assertEquals(locationRivne, outTx.locationId)
        assertEquals(locationKyiv, outTx.transferLocationId)
        assertEquals(locationKyiv, inTx.locationId)
        assertEquals(locationRivne, inTx.transferLocationId)
    }

    @Test
    fun `transfer updates inventory at both locations`() = runTest {
        insertTestLocations()
        insertTestProduct()

        // Purchase 100kg at Rivne
        purchaseAtLocation(locationRivne, BigDecimal("100.0"))

        // Initial inventory
        val rivneInventoryBefore = transactionRepository.getInventoryByLocation(locationRivne).first()
        val kyivInventoryBefore = transactionRepository.getInventoryByLocation(locationKyiv).first()

        assertEquals(1, rivneInventoryBefore.size)
        assertTrue(rivneInventoryBefore[0].totalWeightKg.compareTo(BigDecimal("100.0")) == 0)
        assertTrue(kyivInventoryBefore.isEmpty())

        // Transfer 40kg from Rivne to Kyiv
        transactionRepository.createTransfer(
            fromLocationId = locationRivne,
            toLocationId = locationKyiv,
            productId = productId,
            weightKg = BigDecimal("40.0")
        )

        // Verify updated inventory
        val rivneInventoryAfter = transactionRepository.getInventoryByLocation(locationRivne).first()
        val kyivInventoryAfter = transactionRepository.getInventoryByLocation(locationKyiv).first()

        // Rivne: 100 - 40 = 60
        assertEquals(1, rivneInventoryAfter.size)
        assertTrue(rivneInventoryAfter[0].totalWeightKg.compareTo(BigDecimal("60.0")) == 0)

        // Kyiv: 0 + 40 = 40
        assertEquals(1, kyivInventoryAfter.size)
        assertTrue(kyivInventoryAfter[0].totalWeightKg.compareTo(BigDecimal("40.0")) == 0)
    }

    @Test
    fun `multiple transfers maintain correct inventory`() = runTest {
        insertTestLocations()
        insertTestProduct()

        // Purchase at different locations
        purchaseAtLocation(locationRivne, BigDecimal("100.0"))
        purchaseAtLocation(locationKyiv, BigDecimal("50.0"))

        // Transfer Rivne -> Kyiv
        transactionRepository.createTransfer(
            fromLocationId = locationRivne,
            toLocationId = locationKyiv,
            productId = productId,
            weightKg = BigDecimal("25.0")
        )

        // Transfer Kyiv -> Lviv
        transactionRepository.createTransfer(
            fromLocationId = locationKyiv,
            toLocationId = locationLviv,
            productId = productId,
            weightKg = BigDecimal("30.0")
        )

        // Verify inventories
        val rivneInv = transactionRepository.getInventoryByLocation(locationRivne).first()
        val kyivInv = transactionRepository.getInventoryByLocation(locationKyiv).first()
        val lvivInv = transactionRepository.getInventoryByLocation(locationLviv).first()

        // Rivne: 100 - 25 = 75
        assertTrue(rivneInv[0].totalWeightKg.compareTo(BigDecimal("75.0")) == 0)

        // Kyiv: 50 + 25 - 30 = 45
        assertTrue(kyivInv[0].totalWeightKg.compareTo(BigDecimal("45.0")) == 0)

        // Lviv: 0 + 30 = 30
        assertTrue(lvivInv[0].totalWeightKg.compareTo(BigDecimal("30.0")) == 0)
    }

    @Test
    fun `global inventory remains unchanged after transfer`() = runTest {
        insertTestLocations()
        insertTestProduct()

        purchaseAtLocation(locationRivne, BigDecimal("100.0"))

        // Get global inventory before transfer (sum all entries for this product)
        val globalBefore = transactionRepository.getInventory().first()
        val totalBefore = globalBefore.filter { it.productId == productId }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.totalWeightKg }

        // Transfer
        transactionRepository.createTransfer(
            fromLocationId = locationRivne,
            toLocationId = locationKyiv,
            productId = productId,
            weightKg = BigDecimal("40.0")
        )

        // Get global inventory after transfer (sum all entries for this product)
        val globalAfter = transactionRepository.getInventory().first()
        val totalAfter = globalAfter.filter { it.productId == productId }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.totalWeightKg }

        // Global total should be unchanged (100kg)
        assertTrue(totalBefore.compareTo(totalAfter) == 0)
    }

    // ==================== Chain Transfer Tests ====================

    @Test
    fun `chain of transfers maintains consistency`() = runTest {
        insertTestLocations()
        insertTestProduct()

        // Start with 200kg at Rivne
        purchaseAtLocation(locationRivne, BigDecimal("200.0"))

        // Chain: Rivne -> Kyiv -> Lviv -> Rivne
        transactionRepository.createTransfer(
            fromLocationId = locationRivne,
            toLocationId = locationKyiv,
            productId = productId,
            weightKg = BigDecimal("80.0")
        )

        transactionRepository.createTransfer(
            fromLocationId = locationKyiv,
            toLocationId = locationLviv,
            productId = productId,
            weightKg = BigDecimal("50.0")
        )

        transactionRepository.createTransfer(
            fromLocationId = locationLviv,
            toLocationId = locationRivne,
            productId = productId,
            weightKg = BigDecimal("20.0")
        )

        // Verify final state
        val rivneInv = transactionRepository.getInventoryByLocation(locationRivne).first()
        val kyivInv = transactionRepository.getInventoryByLocation(locationKyiv).first()
        val lvivInv = transactionRepository.getInventoryByLocation(locationLviv).first()

        // Rivne: 200 - 80 + 20 = 140
        assertTrue(rivneInv[0].totalWeightKg.compareTo(BigDecimal("140.0")) == 0)

        // Kyiv: 0 + 80 - 50 = 30
        assertTrue(kyivInv[0].totalWeightKg.compareTo(BigDecimal("30.0")) == 0)

        // Lviv: 0 + 50 - 20 = 30
        assertTrue(lvivInv[0].totalWeightKg.compareTo(BigDecimal("30.0")) == 0)

        // Total: 140 + 30 + 30 = 200
        val global = transactionRepository.getInventory().first()
        val globalTotal = global.filter { it.productId == productId }
            .fold(BigDecimal.ZERO) { acc, item -> acc + item.totalWeightKg }
        assertTrue(globalTotal.compareTo(BigDecimal("200.0")) == 0)
    }

    // ==================== Transfer with Sale Tests ====================

    @Test
    fun `transfer followed by sale reduces inventory correctly`() = runTest {
        insertTestLocations()
        insertTestProduct()

        purchaseAtLocation(locationRivne, BigDecimal("100.0"))

        // Transfer 60kg to Kyiv
        transactionRepository.createTransfer(
            fromLocationId = locationRivne,
            toLocationId = locationKyiv,
            productId = productId,
            weightKg = BigDecimal("60.0")
        )

        // Sell 25kg from Kyiv
        transactionRepository.createSale(
            locationId = locationKyiv,
            productId = productId,
            weightKg = BigDecimal("25.0"),
            pricePerKg = BigDecimal("55.00")
        )

        // Verify inventory
        val rivneInv = transactionRepository.getInventoryByLocation(locationRivne).first()
        val kyivInv = transactionRepository.getInventoryByLocation(locationKyiv).first()

        // Rivne: 100 - 60 = 40
        assertTrue(rivneInv[0].totalWeightKg.compareTo(BigDecimal("40.0")) == 0)

        // Kyiv: 0 + 60 - 25 = 35
        assertTrue(kyivInv[0].totalWeightKg.compareTo(BigDecimal("35.0")) == 0)
    }

    // ==================== Transaction Filtering Tests ====================

    @Test
    fun `getAllTransactions includes transfer transactions`() = runTest {
        insertTestLocations()
        insertTestProduct()

        purchaseAtLocation(locationRivne, BigDecimal("50.0"))

        transactionRepository.createTransfer(
            fromLocationId = locationRivne,
            toLocationId = locationKyiv,
            productId = productId,
            weightKg = BigDecimal("20.0")
        )

        val allTransactions = transactionRepository.getPaginatedTransactions(100, 0).first()

        // Should have 3 transactions: 1 purchase + 1 transfer_out + 1 transfer_in
        assertEquals(3, allTransactions.size)

        val types = allTransactions.map { it.type }
        assertTrue(types.contains(TransactionType.PURCHASE))
        assertTrue(types.contains(TransactionType.TRANSFER_OUT))
        assertTrue(types.contains(TransactionType.TRANSFER_IN))
    }

    @Test
    fun `getTransactionsByType filters correctly`() = runTest {
        insertTestLocations()
        insertTestProduct()

        purchaseAtLocation(locationRivne, BigDecimal("100.0"))
        transactionRepository.createSale(
            locationId = locationRivne,
            productId = productId,
            weightKg = BigDecimal("20.0"),
            pricePerKg = BigDecimal("55.00")
        )
        transactionRepository.createTransfer(
            fromLocationId = locationRivne,
            toLocationId = locationKyiv,
            productId = productId,
            weightKg = BigDecimal("30.0")
        )

        // Get only transfers using query builder
        val transferOutQuery = TransactionQueryBuilder()
            .withTypes(listOf("transfer_out"))
            .build()
        val transferInQuery = TransactionQueryBuilder()
            .withTypes(listOf("transfer_in"))
            .build()
        
        val transfersOut = database.transactionDao().getFiltered(transferOutQuery)
        val transfersIn = database.transactionDao().getFiltered(transferInQuery)

        assertEquals(1, transfersOut.size)
        assertEquals(1, transfersIn.size)
    }
}
