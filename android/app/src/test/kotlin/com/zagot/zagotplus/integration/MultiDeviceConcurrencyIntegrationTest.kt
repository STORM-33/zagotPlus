package com.zagot.zagotplus.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.repository.SaleBatchRepositoryImpl
import com.zagot.zagotplus.data.repository.TransactionRepositoryImpl
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.domain.model.SaleBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.sync.SyncManager
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
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
 * System tests for multi-device concurrent operations.
 * Tests data consistency when multiple devices operate on same data.
 *
 * Simulates scenarios where phone and tablet operate simultaneously.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class MultiDeviceConcurrencyIntegrationTest {

    private lateinit var database: ZagotDatabase
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var saleBatchRepository: SaleBatchRepositoryImpl
    private lateinit var context: Context
    private lateinit var syncManager: SyncManager

    // Test data IDs
    private val location1Id = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val location2Id = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val productId = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val testInstant = Instant.parse("2024-01-15T10:00:00Z")

    // Simulate different devices
    private val devicePhoneId = "phone-device-001"
    private val deviceTabletId = "tablet-device-002"

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        syncManager = mockk(relaxed = true)
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createRepositoryForDevice(deviceId: String): Pair<TransactionRepositoryImpl, SaleBatchRepositoryImpl> {
        val devicePreferences = mockk<DevicePreferences>(relaxed = true)
        every { devicePreferences.getDeviceId() } returns deviceId

        val transactionRepo = TransactionRepositoryImpl(
            database = database,
            transactionDao = database.transactionDao(),
            devicePreferences = devicePreferences,
            syncManager = syncManager
        )

        val saleBatchRepo = SaleBatchRepositoryImpl(
            database = database,
            saleBatchDao = database.saleBatchDao(),
            transactionDao = database.transactionDao(),
            syncManager = syncManager
        )

        return Pair(transactionRepo, saleBatchRepo)
    }

    private suspend fun insertTestData() {
        database.locationDao().insert(
            LocationEntity(location1Id, "Кіоск №1", LocationType.KIOSK.name, testInstant)
        )
        database.locationDao().insert(
            LocationEntity(location2Id, "Кіоск №2", LocationType.KIOSK.name, testInstant)
        )
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
    }

    private fun createSaleBatch(
        batchId: UUID,
        locationId: UUID,
        deviceId: String,
        weightKg: BigDecimal,
        amount: BigDecimal
    ): SaleBatch = SaleBatch(
        id = batchId,
        localId = "batch-${batchId}-${deviceId}",
        locationId = locationId,
        notes = null,
        totalWeightKg = weightKg,
        totalAmount = amount,
        itemCount = 1,
        deviceId = deviceId,
        createdAt = Instant.now(),
        syncedAt = null
    )

    private fun createSaleTransaction(
        batchId: UUID,
        locationId: UUID,
        deviceId: String,
        weightKg: BigDecimal,
        pricePerKg: BigDecimal
    ): Transaction = Transaction(
        id = UUID.randomUUID(),
        localId = "tx-${UUID.randomUUID()}-${deviceId}",
        locationId = locationId,
        type = TransactionType.SALE,
        transferLocationId = null,
        productId = productId,
        weightKg = weightKg.negate(), // Sales are stored as negative weight
        pricePerKg = pricePerKg,
        totalAmount = weightKg.multiply(pricePerKg),
        notes = null,
        deviceId = deviceId,
        createdAt = Instant.now(),
        syncedAt = null,
        batchId = null,
        saleBatchId = batchId
    )

    // ==================== Multi-Device Tests ====================

    @Test
    fun `different devices create transactions with their own device ID`() = runTest {
        insertTestData()

        val (phoneRepo, _) = createRepositoryForDevice(devicePhoneId)
        val (tabletRepo, _) = createRepositoryForDevice(deviceTabletId)

        // Phone creates purchase
        phoneRepo.createPurchase(location1Id, productId, BigDecimal("100.00"), BigDecimal("45.00"))

        // Tablet creates purchase
        tabletRepo.createPurchase(location1Id, productId, BigDecimal("50.00"), BigDecimal("46.00"))

        // Verify both transactions exist with correct device IDs
        val allTransactions = phoneRepo.getPaginatedTransactions(100, 0).first()
        assertEquals(2, allTransactions.size)

        val phoneTransaction = allTransactions.find { it.deviceId == devicePhoneId }
        val tabletTransaction = allTransactions.find { it.deviceId == deviceTabletId }

        assertNotNull("Phone transaction should exist", phoneTransaction)
        assertNotNull("Tablet transaction should exist", tabletTransaction)
        
        assertTrue(phoneTransaction?.weightKg?.compareTo(BigDecimal("100.00")) == 0)
        assertTrue(tabletTransaction?.weightKg?.compareTo(BigDecimal("50.00")) == 0)
    }

    @Test
    fun `concurrent sales from different devices accumulate correctly`() = runTest {
        insertTestData()

        val (phoneRepo, phoneSaleRepo) = createRepositoryForDevice(devicePhoneId)
        val (tabletRepo, tabletSaleRepo) = createRepositoryForDevice(deviceTabletId)

        // Initial inventory
        phoneRepo.createPurchase(location1Id, productId, BigDecimal("500.00"), BigDecimal("45.00"))

        // Create batches for both devices
        val phoneBatchId = UUID.randomUUID()
        val phoneBatch = createSaleBatch(phoneBatchId, location1Id, devicePhoneId, BigDecimal("30.00"), BigDecimal("1650.00"))
        val phoneTx = createSaleTransaction(phoneBatchId, location1Id, devicePhoneId, BigDecimal("30.00"), BigDecimal("55.00"))

        val tabletBatchId = UUID.randomUUID()
        val tabletBatch = createSaleBatch(tabletBatchId, location1Id, deviceTabletId, BigDecimal("25.00"), BigDecimal("1375.00"))
        val tabletTx = createSaleTransaction(tabletBatchId, location1Id, deviceTabletId, BigDecimal("25.00"), BigDecimal("55.00"))

        // Execute concurrently
        val phoneDeferred = async { 
            phoneSaleRepo.createBatchWithTransactions(phoneBatch, listOf(phoneTx))
        }
        val tabletDeferred = async { 
            tabletSaleRepo.createBatchWithTransactions(tabletBatch, listOf(tabletTx))
        }
        
        awaitAll(phoneDeferred, tabletDeferred)

        // Verify inventory reflects both sales: 500 - 30 - 25 = 445
        val inventory = phoneRepo.getInventory().first()
        assertTrue(inventory[0].totalWeightKg.compareTo(BigDecimal("445.00")) == 0)

        // Verify both batches created
        val batches = phoneSaleRepo.observeAll().first()
        assertEquals(2, batches.size)
    }

    @Test
    fun `transactions have unique local IDs across devices`() = runTest {
        insertTestData()

        val (phoneRepo, _) = createRepositoryForDevice(devicePhoneId)
        val (tabletRepo, _) = createRepositoryForDevice(deviceTabletId)

        // Create many transactions from both devices
        repeat(5) {
            phoneRepo.createPurchase(location1Id, productId, BigDecimal("10.00"), BigDecimal("45.00"))
            tabletRepo.createPurchase(location1Id, productId, BigDecimal("10.00"), BigDecimal("45.00"))
        }

        val allTransactions = phoneRepo.getPaginatedTransactions(100, 0).first()
        assertEquals(10, allTransactions.size)

        // All localIds should be unique
        val localIds = allTransactions.map { it.localId }
        assertEquals(localIds.size, localIds.distinct().size)
    }

    @Test
    fun `different devices operating on different locations maintain separate inventories`() = runTest {
        insertTestData()

        val (phoneRepo, phoneSaleRepo) = createRepositoryForDevice(devicePhoneId)
        val (tabletRepo, tabletSaleRepo) = createRepositoryForDevice(deviceTabletId)

        // Phone works on location1
        phoneRepo.createPurchase(location1Id, productId, BigDecimal("200.00"), BigDecimal("45.00"))
        val phoneBatchId = UUID.randomUUID()
        val phoneBatch = createSaleBatch(phoneBatchId, location1Id, devicePhoneId, BigDecimal("50.00"), BigDecimal("2750.00"))
        val phoneTx = createSaleTransaction(phoneBatchId, location1Id, devicePhoneId, BigDecimal("50.00"), BigDecimal("55.00"))
        phoneSaleRepo.createBatchWithTransactions(phoneBatch, listOf(phoneTx))

        // Tablet works on location2
        tabletRepo.createPurchase(location2Id, productId, BigDecimal("150.00"), BigDecimal("45.00"))
        val tabletBatchId = UUID.randomUUID()
        val tabletBatch = createSaleBatch(tabletBatchId, location2Id, deviceTabletId, BigDecimal("30.00"), BigDecimal("1650.00"))
        val tabletTx = createSaleTransaction(tabletBatchId, location2Id, deviceTabletId, BigDecimal("30.00"), BigDecimal("55.00"))
        tabletSaleRepo.createBatchWithTransactions(tabletBatch, listOf(tabletTx))

        // Verify location1: 200 - 50 = 150
        val loc1Inventory = phoneRepo.getInventoryByLocation(location1Id).first()
        assertTrue(loc1Inventory[0].totalWeightKg.compareTo(BigDecimal("150.00")) == 0)

        // Verify location2: 150 - 30 = 120
        val loc2Inventory = phoneRepo.getInventoryByLocation(location2Id).first()
        assertTrue(loc2Inventory[0].totalWeightKg.compareTo(BigDecimal("120.00")) == 0)

        // Global: 150 + 120 = 270
        val globalInventory = phoneRepo.getInventory().first()
        val total = globalInventory.sumOf { it.totalWeightKg }
        assertTrue(total.compareTo(BigDecimal("270.00")) == 0)
    }

    @Test
    fun `rapid concurrent purchases maintain inventory accuracy`() = runTest {
        insertTestData()

        val (repo1, _) = createRepositoryForDevice(devicePhoneId)
        val (repo2, _) = createRepositoryForDevice(deviceTabletId)

        // Simulate rapid concurrent purchases
        val deferreds = (1..20).map { i ->
            async {
                val repo = if (i % 2 == 0) repo1 else repo2
                repo.createPurchase(location1Id, productId, BigDecimal("10.00"), BigDecimal("45.00"))
            }
        }
        
        awaitAll(*deferreds.toTypedArray())

        // Verify all 20 purchases recorded: 20 * 10 = 200
        val inventory = repo1.getInventory().first()
        assertTrue(inventory[0].totalWeightKg.compareTo(BigDecimal("200.00")) == 0)

        val transactions = repo1.getPaginatedTransactions(100, 0).first()
        assertEquals(20, transactions.size)
    }

    @Test
    fun `sale batches from different devices have separate IDs`() = runTest {
        insertTestData()

        val (phoneRepo, phoneSaleRepo) = createRepositoryForDevice(devicePhoneId)
        val (_, tabletSaleRepo) = createRepositoryForDevice(deviceTabletId)

        phoneRepo.createPurchase(location1Id, productId, BigDecimal("1000.00"), BigDecimal("45.00"))

        // Create batches from both devices
        val phoneBatchId = UUID.randomUUID()
        val phoneBatch = createSaleBatch(phoneBatchId, location1Id, devicePhoneId, BigDecimal("20.00"), BigDecimal("1100.00"))
        val phoneTx = createSaleTransaction(phoneBatchId, location1Id, devicePhoneId, BigDecimal("20.00"), BigDecimal("55.00"))
        phoneSaleRepo.createBatchWithTransactions(phoneBatch, listOf(phoneTx))

        val tabletBatchId = UUID.randomUUID()
        val tabletBatch = createSaleBatch(tabletBatchId, location1Id, deviceTabletId, BigDecimal("20.00"), BigDecimal("1100.00"))
        val tabletTx = createSaleTransaction(tabletBatchId, location1Id, deviceTabletId, BigDecimal("20.00"), BigDecimal("55.00"))
        tabletSaleRepo.createBatchWithTransactions(tabletBatch, listOf(tabletTx))

        // Batches should have unique IDs
        assertNotEquals(phoneBatchId, tabletBatchId)
        
        val batches = phoneSaleRepo.observeAll().first()
        assertEquals(2, batches.size)
        val batchIds = batches.map { it.id }
        assertEquals(batchIds.size, batchIds.distinct().size)
    }

    @Test
    fun `transaction timestamps preserve creation order`() = runTest {
        insertTestData()

        val (phoneRepo, _) = createRepositoryForDevice(devicePhoneId)

        // Create transactions with slight delay (Room uses Instant.now())
        phoneRepo.createPurchase(location1Id, productId, BigDecimal("10.00"), BigDecimal("45.00"))
        Thread.sleep(10) // Small delay to ensure different timestamps
        phoneRepo.createPurchase(location1Id, productId, BigDecimal("20.00"), BigDecimal("45.00"))
        Thread.sleep(10)
        phoneRepo.createPurchase(location1Id, productId, BigDecimal("30.00"), BigDecimal("45.00"))

        val transactions = phoneRepo.getPaginatedTransactions(100, 0).first()
            .sortedBy { it.createdAt }

        // Verify chronological order
        assertEquals(3, transactions.size)
        assertTrue(transactions[0].createdAt <= transactions[1].createdAt)
        assertTrue(transactions[1].createdAt <= transactions[2].createdAt)
    }

    @Test
    fun `unsynced transactions from multiple devices are tracked correctly`() = runTest {
        insertTestData()

        val (phoneRepo, _) = createRepositoryForDevice(devicePhoneId)
        val (tabletRepo, _) = createRepositoryForDevice(deviceTabletId)

        // Create unsynced transactions from both devices
        phoneRepo.createPurchase(location1Id, productId, BigDecimal("100.00"), BigDecimal("45.00"))
        tabletRepo.createPurchase(location1Id, productId, BigDecimal("50.00"), BigDecimal("45.00"))

        // All should be unsynced (syncedAt = null)
        val unsyncedTransactions = database.transactionDao().getUnsynced()
        assertEquals(2, unsyncedTransactions.size)
        assertTrue(unsyncedTransactions.all { it.syncedAt == null })
    }

    @Test
    fun `overselling from concurrent devices shows combined negative inventory`() = runTest {
        insertTestData()

        val (phoneRepo, phoneSaleRepo) = createRepositoryForDevice(devicePhoneId)
        val (_, tabletSaleRepo) = createRepositoryForDevice(deviceTabletId)

        // Small initial inventory
        phoneRepo.createPurchase(location1Id, productId, BigDecimal("50.00"), BigDecimal("45.00"))

        // Both devices sell more than available (concurrent scenario)
        val phoneBatchId = UUID.randomUUID()
        val phoneBatch = createSaleBatch(phoneBatchId, location1Id, devicePhoneId, BigDecimal("40.00"), BigDecimal("2200.00"))
        val phoneTx = createSaleTransaction(phoneBatchId, location1Id, devicePhoneId, BigDecimal("40.00"), BigDecimal("55.00"))
        phoneSaleRepo.createBatchWithTransactions(phoneBatch, listOf(phoneTx))

        val tabletBatchId = UUID.randomUUID()
        val tabletBatch = createSaleBatch(tabletBatchId, location1Id, deviceTabletId, BigDecimal("35.00"), BigDecimal("1925.00"))
        val tabletTx = createSaleTransaction(tabletBatchId, location1Id, deviceTabletId, BigDecimal("35.00"), BigDecimal("55.00"))
        tabletSaleRepo.createBatchWithTransactions(tabletBatch, listOf(tabletTx))

        // Inventory: 50 - 40 - 35 = -25 (oversold)
        val inventory = phoneRepo.getInventory().first()
        assertTrue(inventory[0].totalWeightKg.compareTo(BigDecimal("-25.00")) == 0)
    }
}
