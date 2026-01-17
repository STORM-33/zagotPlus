package com.zagot.zagotplus.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.local.entity.PurchaseBatchEntity
import com.zagot.zagotplus.data.local.entity.SaleBatchEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.data.remote.dto.CashOperationDto
import com.zagot.zagotplus.data.remote.dto.ExpenseCategoryDto
import com.zagot.zagotplus.data.remote.dto.LocationDto
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import com.zagot.zagotplus.data.remote.dto.SaleBatchDto
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.sync.SyncDataSource
import com.zagot.zagotplus.sync.SyncPreferences
import com.zagot.zagotplus.sync.SyncResult
import com.zagot.zagotplus.sync.SyncService
import io.mockk.every
import io.mockk.mockk
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
 * Integration tests for full sync flow.
 * 
 * Tests the complete sync cycle:
 * 1. Populate local Room database with test data
 * 2. Execute sync (push to fake remote, pull from fake remote)
 * 3. Verify local and "remote" databases have identical data
 * 
 * Uses Robolectric for in-memory Room database and a FakeSyncDataSource
 * that simulates Supabase storage in memory.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SyncFlowIntegrationTest {

    private lateinit var database: ZagotDatabase
    private lateinit var fakeSyncDataSource: FakeSyncDataSource
    private lateinit var syncPreferences: SyncPreferences
    private lateinit var syncService: SyncService
    private lateinit var context: Context

    // Test data IDs
    private val locationId1 = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val locationId2 = UUID.fromString("11111111-1111-1111-1111-222222222222")
    private val productId1 = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val productId2 = UUID.fromString("22222222-2222-2222-2222-333333333333")
    private val batchId1 = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val saleBatchId1 = UUID.fromString("44444444-4444-4444-4444-444444444444")
    private val categoryId1 = UUID.fromString("55555555-5555-5555-5555-555555555555")
    private val testInstant = Instant.parse("2024-01-15T10:00:00Z")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        // Create real in-memory database
        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        // Create fake sync data source (simulates Supabase)
        fakeSyncDataSource = FakeSyncDataSource()

        // Mock SyncPreferences
        syncPreferences = mockk(relaxed = true)
        every { syncPreferences.getLastSyncTimestamp() } returns Instant.EPOCH

        // Create real SyncService with fake data source
        syncService = SyncService(
            database = database,
            syncDataSource = fakeSyncDataSource,
            transactionDao = database.transactionDao(),
            purchaseBatchDao = database.purchaseBatchDao(),
            saleBatchDao = database.saleBatchDao(),
            locationDao = database.locationDao(),
            productDao = database.productDao(),
            expenseCategoryDao = database.expenseCategoryDao(),
            cashOperationDao = database.cashOperationDao(),
            syncPreferences = syncPreferences
        )
    }

    @After
    fun tearDown() {
        database.close()
    }

    // ==================== Helper Methods ====================

    private suspend fun insertTestLocations() {
        database.locationDao().insert(
            LocationEntity(
                id = locationId1,
                name = "Склад Рівне",
                type = LocationType.KIOSK.name,
                createdAt = testInstant
            )
        )
        database.locationDao().insert(
            LocationEntity(
                id = locationId2,
                name = "Склад Київ",
                type = LocationType.MOBILE.name,
                createdAt = testInstant
            )
        )
    }

    private suspend fun insertTestProducts() {
        database.productDao().insert(
            ProductEntity(
                id = productId1,
                localId = "local-product-1",
                name = "Горіх білий",
                defaultBuyPrice = BigDecimal("45.00"),
                defaultSellPrice = BigDecimal("55.00"),
                isActive = true,
                createdAt = testInstant,
                syncedAt = null
            )
        )
        database.productDao().insert(
            ProductEntity(
                id = productId2,
                localId = "local-product-2",
                name = "Горіх червоний",
                defaultBuyPrice = BigDecimal("50.00"),
                defaultSellPrice = BigDecimal("65.00"),
                isActive = true,
                createdAt = testInstant,
                syncedAt = null
            )
        )
    }

    private suspend fun insertTestExpenseCategory() {
        database.expenseCategoryDao().insert(
            ExpenseCategoryEntity(
                id = categoryId1,
                localId = "local-category-1",
                name = "Транспорт",
                isActive = true,
                createdAt = testInstant,
                syncedAt = null
            )
        )
    }

    private suspend fun insertTestPurchaseBatch() {
        database.purchaseBatchDao().insert(
            PurchaseBatchEntity(
                id = batchId1,
                localId = "local-batch-1",
                locationId = locationId1,
                notes = "Закупка від Петра",
                totalWeightKg = BigDecimal("100.00"),
                totalAmount = BigDecimal("4500.00"),
                itemCount = 2,
                deviceId = "test-device",
                createdAt = testInstant,
                syncedAt = null
            )
        )
    }

    private suspend fun insertTestSaleBatch() {
        database.saleBatchDao().insert(
            SaleBatchEntity(
                id = saleBatchId1,
                localId = "local-sale-batch-1",
                locationId = locationId1,
                notes = "Продаж оптом",
                totalWeightKg = BigDecimal("50.00"),
                totalAmount = BigDecimal("2750.00"),
                itemCount = 1,
                deviceId = "test-device",
                createdAt = testInstant,
                syncedAt = null
            )
        )
    }

    private suspend fun insertTestTransactions() {
        // Purchase transaction
        database.transactionDao().insert(
            TransactionEntity(
                id = UUID.randomUUID(),
                localId = "local-tx-1",
                locationId = locationId1,
                type = "purchase",
                transferLocationId = null,
                productId = productId1,
                weightKg = BigDecimal("50.00"),
                pricePerKg = BigDecimal("45.00"),
                totalAmount = BigDecimal("2250.00"),
                notes = null,
                deviceId = "test-device",
                createdAt = testInstant,
                syncedAt = null,
                batchId = batchId1
            )
        )
        // Sale transaction
        database.transactionDao().insert(
            TransactionEntity(
                id = UUID.randomUUID(),
                localId = "local-tx-2",
                locationId = locationId1,
                type = "sale",
                transferLocationId = null,
                productId = productId1,
                weightKg = BigDecimal("20.00"),
                pricePerKg = BigDecimal("55.00"),
                totalAmount = BigDecimal("1100.00"),
                notes = "Продаж клієнту",
                deviceId = "test-device",
                createdAt = testInstant.plusSeconds(60),
                syncedAt = null,
                saleBatchId = saleBatchId1
            )
        )
    }

    private suspend fun insertTestCashOperations() {
        // Deposit
        database.cashOperationDao().insert(
            CashOperationEntity(
                id = UUID.randomUUID(),
                localId = "local-cash-1",
                locationId = locationId1,
                type = "deposit",
                amount = BigDecimal("5000.00"),
                categoryId = null,
                batchId = null,
                notes = "Початкова каса",
                deviceId = "test-device",
                createdAt = testInstant,
                syncedAt = null
            )
        )
        // Payment with category
        database.cashOperationDao().insert(
            CashOperationEntity(
                id = UUID.randomUUID(),
                localId = "local-cash-2",
                locationId = locationId1,
                type = "payment",
                amount = BigDecimal("200.00"),
                categoryId = categoryId1,
                batchId = null,
                notes = "Оплата за бензин",
                deviceId = "test-device",
                createdAt = testInstant.plusSeconds(120),
                syncedAt = null
            )
        )
    }

    // ==================== Full Sync Flow Tests ====================

    @Test
    fun `full sync pushes all local data to remote`() = runTest {
        // Given: Populate all local repositories
        insertTestLocations()
        insertTestProducts()
        insertTestExpenseCategory()
        insertTestPurchaseBatch()
        insertTestSaleBatch()
        insertTestTransactions()
        insertTestCashOperations()

        // Seed remote with locations (reference data pulled, not pushed)
        fakeSyncDataSource.seedLocations(listOf(
            LocationDto(
                id = locationId1.toString(),
                name = "Склад Рівне",
                type = LocationType.KIOSK.name,
                createdAt = testInstant.toString()
            ),
            LocationDto(
                id = locationId2.toString(),
                name = "Склад Київ",
                type = LocationType.MOBILE.name,
                createdAt = testInstant.toString()
            )
        ))

        // When: Perform sync
        val result = syncService.sync()

        // Then: Sync should succeed
        assertTrue("Sync should succeed", result is SyncResult.Success)
        val success = result as SyncResult.Success

        // Verify pushed counts
        assertTrue("Should push products", success.pushed >= 2)

        // Verify remote has all data
        assertEquals("Remote should have 2 products", 2, fakeSyncDataSource.products.size)
        assertEquals("Remote should have 1 purchase batch", 1, fakeSyncDataSource.purchaseBatches.size)
        assertEquals("Remote should have 1 sale batch", 1, fakeSyncDataSource.saleBatches.size)
        assertEquals("Remote should have 2 transactions", 2, fakeSyncDataSource.transactions.size)
        assertEquals("Remote should have 1 expense category", 1, fakeSyncDataSource.expenseCategories.size)
        assertEquals("Remote should have 2 cash operations", 2, fakeSyncDataSource.cashOperations.size)
    }

    @Test
    fun `sync pulls remote data to local database`() = runTest {
        // Given: Seed local with required reference data (locations must exist for FK)
        insertTestLocations()
        insertTestProducts()

        // Seed remote with locations
        fakeSyncDataSource.seedLocations(listOf(
            LocationDto(
                id = locationId1.toString(),
                name = "Склад Рівне",
                type = LocationType.KIOSK.name,
                createdAt = testInstant.toString()
            )
        ))

        // Seed remote with data to pull
        val remoteTransaction = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "remote-tx-1",
            locationId = locationId1.toString(),
            type = "purchase",
            transferLocationId = null,
            productId = productId1.toString(),
            weightKg = 75.0,
            pricePerKg = 48.0,
            totalAmount = 3600.0,
            notes = "From another device",
            deviceId = "other-device",
            createdAt = testInstant.plusSeconds(300).toString(),
            syncedAt = testInstant.plusSeconds(300).toString(),
            batchId = null,
            saleBatchId = null
        )
        fakeSyncDataSource.seedTransactions(listOf(remoteTransaction))

        val remoteBatch = PurchaseBatchDto(
            id = UUID.randomUUID().toString(),
            localId = "remote-batch-1",
            locationId = locationId1.toString(),
            notes = "Remote batch",
            totalWeightKg = 75.0,
            totalAmount = 3600.0,
            itemCount = 1,
            deviceId = "other-device",
            createdAt = testInstant.plusSeconds(300).toString(),
            syncedAt = testInstant.plusSeconds(300).toString()
        )
        fakeSyncDataSource.seedPurchaseBatches(listOf(remoteBatch))

        // When: Perform sync
        val result = syncService.sync()

        // Then: Sync should succeed and pull data
        assertTrue("Sync should succeed", result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertTrue("Should pull transactions", success.pulled >= 1)

        // Verify local has remote data
        val localTransactions = database.transactionDao().getAllPaginated(limit = 1000, offset = 0)
        assertTrue(
            "Local should have remote transaction",
            localTransactions.any { it.localId == "remote-tx-1" }
        )

        val localBatches = database.purchaseBatchDao().getAllPaginated(limit = 100, offset = 0)
        assertTrue(
            "Local should have remote batch",
            localBatches.any { it.localId == "remote-batch-1" }
        )
    }

    @Test
    fun `sync achieves data consistency between local and remote`() = runTest {
        // Given: Set up local data
        insertTestLocations()
        insertTestProducts()
        insertTestExpenseCategory()
        insertTestPurchaseBatch()
        insertTestSaleBatch()
        insertTestTransactions()
        insertTestCashOperations()

        // Seed remote with locations
        fakeSyncDataSource.seedLocations(listOf(
            LocationDto(
                id = locationId1.toString(),
                name = "Склад Рівне",
                type = LocationType.KIOSK.name,
                createdAt = testInstant.toString()
            ),
            LocationDto(
                id = locationId2.toString(),
                name = "Склад Київ",
                type = LocationType.MOBILE.name,
                createdAt = testInstant.toString()
            )
        ))

        // When: Perform sync
        val result = syncService.sync()

        // Then: Sync should succeed
        assertTrue("Sync should succeed", result is SyncResult.Success)

        // Verify data consistency: local == remote
        val localProducts = database.productDao().getAll()
        val remoteProducts = fakeSyncDataSource.products

        assertEquals(
            "Product count should match",
            localProducts.size,
            remoteProducts.size
        )

        // Verify each local product exists in remote with same data
        for (localProduct in localProducts) {
            val remoteProduct = remoteProducts.find { it.localId == localProduct.localId }
            assertNotNull("Remote should have product ${localProduct.localId}", remoteProduct)
            assertEquals("Product name should match", localProduct.name, remoteProduct!!.name)
            assertEquals(
                "Product buy price should match",
                localProduct.defaultBuyPrice?.toDouble(),
                remoteProduct.defaultBuyPrice
            )
        }

        // Verify transactions match
        val localTransactions = database.transactionDao().getAllPaginated(limit = 1000, offset = 0)
        val remoteTransactions = fakeSyncDataSource.transactions

        assertEquals(
            "Transaction count should match",
            localTransactions.size,
            remoteTransactions.size
        )

        for (localTx in localTransactions) {
            val remoteTx = remoteTransactions.find { it.localId == localTx.localId }
            assertNotNull("Remote should have transaction ${localTx.localId}", remoteTx)
            assertEquals("Transaction type should match", localTx.type, remoteTx!!.type)
            assertEquals(
                "Transaction weight should match",
                localTx.weightKg.toDouble(),
                remoteTx.weightKg,
                0.001
            )
        }

        // Verify cash operations match
        val localCashOps = database.cashOperationDao().getOperationsPaged(limit = 100, offset = 0)
        val remoteCashOps = fakeSyncDataSource.cashOperations

        assertEquals(
            "Cash operation count should match",
            localCashOps.size,
            remoteCashOps.size
        )
    }

    @Test
    fun `sync is idempotent - second sync does not duplicate data`() = runTest {
        // Given: Populate local data
        insertTestLocations()
        insertTestProducts()
        insertTestPurchaseBatch()
        insertTestSaleBatch()
        insertTestTransactions()

        fakeSyncDataSource.seedLocations(listOf(
            LocationDto(
                id = locationId1.toString(),
                name = "Склад Рівне",
                type = LocationType.KIOSK.name,
                createdAt = testInstant.toString()
            )
        ))

        // When: Perform first sync
        val result1 = syncService.sync()
        assertTrue("First sync should succeed", result1 is SyncResult.Success)

        val productCountAfterFirstSync = fakeSyncDataSource.products.size
        val transactionCountAfterFirstSync = fakeSyncDataSource.transactions.size

        // When: Perform second sync (should be no-op since data is synced)
        val result2 = syncService.sync()
        assertTrue("Second sync should succeed", result2 is SyncResult.Success)

        // Then: Data should not be duplicated
        assertEquals(
            "Product count should not change after second sync",
            productCountAfterFirstSync,
            fakeSyncDataSource.products.size
        )
        assertEquals(
            "Transaction count should not change after second sync",
            transactionCountAfterFirstSync,
            fakeSyncDataSource.transactions.size
        )
    }

    @Test
    fun `sync marks local entities as synced after push`() = runTest {
        // Given: Insert unsynced product
        insertTestLocations()
        insertTestProducts()

        fakeSyncDataSource.seedLocations(listOf(
            LocationDto(
                id = locationId1.toString(),
                name = "Склад Рівне",
                type = LocationType.KIOSK.name,
                createdAt = testInstant.toString()
            )
        ))

        // Verify products are unsynced before sync
        val unsyncedBefore = database.productDao().getUnsynced()
        assertEquals("Should have 2 unsynced products", 2, unsyncedBefore.size)

        // When: Perform sync
        val result = syncService.sync()
        assertTrue("Sync should succeed", result is SyncResult.Success)

        // Then: Products should be marked as synced
        val unsyncedAfter = database.productDao().getUnsynced()
        assertEquals("Should have no unsynced products", 0, unsyncedAfter.size)

        // Verify syncedAt is set
        val allProducts = database.productDao().getAll()
        assertTrue(
            "All products should have syncedAt set",
            allProducts.all { it.syncedAt != null }
        )
    }

    @Test
    fun `sync handles mixed push and pull scenario`() = runTest {
        // Given: Local has some data, remote has other data
        insertTestLocations()
        insertTestProducts()
        insertTestPurchaseBatch()

        // Local transaction
        val localTxId = UUID.randomUUID()
        database.transactionDao().insert(
            TransactionEntity(
                id = localTxId,
                localId = "local-only-tx",
                locationId = locationId1,
                type = "purchase",
                transferLocationId = null,
                productId = productId1,
                weightKg = BigDecimal("30.00"),
                pricePerKg = BigDecimal("45.00"),
                totalAmount = BigDecimal("1350.00"),
                notes = null,
                deviceId = "device-A",
                createdAt = testInstant,
                syncedAt = null,
                batchId = batchId1
            )
        )

        // Seed remote with locations and products (required for FK)
        fakeSyncDataSource.seedLocations(listOf(
            LocationDto(
                id = locationId1.toString(),
                name = "Склад Рівне",
                type = LocationType.KIOSK.name,
                createdAt = testInstant.toString()
            )
        ))
        fakeSyncDataSource.seedProducts(listOf(
            ProductDto(
                id = productId1.toString(),
                localId = "local-product-1",
                name = "Горіх білий",
                defaultBuyPrice = 45.0,
                defaultSellPrice = 55.0,
                isActive = true,
                createdAt = testInstant.toString()
            )
        ))

        // Remote transaction (from another device)
        val remoteTx = TransactionDto(
            id = UUID.randomUUID().toString(),
            localId = "remote-only-tx",
            locationId = locationId1.toString(),
            type = "sale",
            transferLocationId = null,
            productId = productId1.toString(),
            weightKg = 15.0,
            pricePerKg = 55.0,
            totalAmount = 825.0,
            notes = null,
            deviceId = "device-B",
            createdAt = testInstant.plusSeconds(60).toString(),
            syncedAt = testInstant.plusSeconds(60).toString(),
            batchId = null,
            saleBatchId = null
        )
        fakeSyncDataSource.seedTransactions(listOf(remoteTx))

        // When: Perform sync
        val result = syncService.sync()

        // Then: Sync should succeed
        assertTrue("Sync should succeed", result is SyncResult.Success)
        val success = result as SyncResult.Success
        assertTrue("Should push local data", success.pushed >= 1)
        assertTrue("Should pull remote data", success.pulled >= 1)

        // Local should have both transactions
        val localTransactions = database.transactionDao().getAllPaginated(limit = 1000, offset = 0)
        assertTrue(
            "Local should have local-only-tx",
            localTransactions.any { it.localId == "local-only-tx" }
        )
        assertTrue(
            "Local should have remote-only-tx",
            localTransactions.any { it.localId == "remote-only-tx" }
        )

        // Remote should have both transactions
        assertEquals(
            "Remote should have 2 transactions",
            2,
            fakeSyncDataSource.transactions.size
        )
    }
}

/**
 * Fake SyncDataSource that stores data in memory.
 * Simulates Supabase storage for integration testing.
 */
class FakeSyncDataSource : SyncDataSource {

    // In-memory "remote" storage
    val products = mutableListOf<ProductDto>()
    val transactions = mutableListOf<TransactionDto>()
    val purchaseBatches = mutableListOf<PurchaseBatchDto>()
    val saleBatches = mutableListOf<SaleBatchDto>()
    val expenseCategories = mutableListOf<ExpenseCategoryDto>()
    val cashOperations = mutableListOf<CashOperationDto>()
    private val locations = mutableListOf<LocationDto>()

    // Seed methods for setting up test scenarios
    fun seedLocations(items: List<LocationDto>) {
        locations.clear()
        locations.addAll(items)
    }

    fun seedProducts(items: List<ProductDto>) {
        products.clear()
        products.addAll(items)
    }

    fun seedTransactions(items: List<TransactionDto>) {
        transactions.addAll(items)
    }

    fun seedPurchaseBatches(items: List<PurchaseBatchDto>) {
        purchaseBatches.addAll(items)
    }

    // Push operations - batch upsert by localId
    override suspend fun pushTransactions(dtos: List<TransactionDto>) {
        dtos.forEach { dto ->
            val existing = transactions.indexOfFirst { it.localId == dto.localId }
            if (existing >= 0) {
                transactions[existing] = dto
            } else {
                transactions.add(dto)
            }
        }
    }

    override suspend fun pushBatches(dtos: List<PurchaseBatchDto>) {
        dtos.forEach { dto ->
            val existing = purchaseBatches.indexOfFirst { it.localId == dto.localId }
            if (existing >= 0) {
                purchaseBatches[existing] = dto
            } else {
                purchaseBatches.add(dto)
            }
        }
    }

    override suspend fun pushSaleBatches(dtos: List<SaleBatchDto>) {
        dtos.forEach { dto ->
            val existing = saleBatches.indexOfFirst { it.localId == dto.localId }
            if (existing >= 0) {
                saleBatches[existing] = dto
            } else {
                saleBatches.add(dto)
            }
        }
    }

    override suspend fun pushProducts(dtos: List<ProductDto>) {
        dtos.forEach { dto ->
            val existing = products.indexOfFirst { it.localId == dto.localId }
            if (existing >= 0) {
                products[existing] = dto
            } else {
                products.add(dto)
            }
        }
    }

    override suspend fun deleteProduct(id: String) {
        products.removeIf { it.id == id }
    }

    override suspend fun pushExpenseCategories(dtos: List<ExpenseCategoryDto>) {
        dtos.forEach { dto ->
            val existing = expenseCategories.indexOfFirst { it.localId == dto.localId }
            if (existing >= 0) {
                expenseCategories[existing] = dto
            } else {
                expenseCategories.add(dto)
            }
        }
    }

    override suspend fun pushCashOperations(dtos: List<CashOperationDto>) {
        dtos.forEach { dto ->
            val existing = cashOperations.indexOfFirst { it.localId == dto.localId }
            if (existing >= 0) {
                cashOperations[existing] = dto
            } else {
                cashOperations.add(dto)
            }
        }
    }

    // Pull operations - filter by created_at > since
    override suspend fun pullTransactions(since: Instant): List<TransactionDto> {
        return transactions.filter {
            Instant.parse(it.createdAt).isAfter(since)
        }
    }

    override suspend fun pullBatches(since: Instant): List<PurchaseBatchDto> {
        return purchaseBatches.filter {
            Instant.parse(it.createdAt).isAfter(since)
        }
    }

    override suspend fun pullSaleBatches(since: Instant): List<SaleBatchDto> {
        return saleBatches.filter {
            Instant.parse(it.createdAt).isAfter(since)
        }
    }

    override suspend fun pullExpenseCategories(since: Instant): List<ExpenseCategoryDto> {
        return expenseCategories.filter {
            Instant.parse(it.createdAt).isAfter(since)
        }
    }

    override suspend fun pullCashOperations(since: Instant): List<CashOperationDto> {
        return cashOperations.filter {
            Instant.parse(it.createdAt).isAfter(since)
        }
    }

    // Reference data pull - returns all
    override suspend fun pullLocations(): List<LocationDto> = locations.toList()

    override suspend fun pullProducts(): List<ProductDto> = products.toList()
}
