package com.zagot.zagotplus.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.local.entity.SaleBatchEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.repository.SaleBatchRepositoryImpl
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
 * Integration tests for sale flow with batches.
 * Tests the full stack: Repository → DAO → Database
 *
 * Uses Robolectric for in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class SaleFlowIntegrationTest {

    private lateinit var database: ZagotDatabase
    private lateinit var saleBatchRepository: SaleBatchRepositoryImpl
    private lateinit var syncManager: SyncManager
    private lateinit var context: Context

    // Test data IDs
    private val locationId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val productId1 = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val productId2 = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val testInstant = Instant.parse("2024-01-15T10:00:00Z")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        syncManager = mockk(relaxed = true)

        saleBatchRepository = SaleBatchRepositoryImpl(
            database = database,
            saleBatchDao = database.saleBatchDao(),
            transactionDao = database.transactionDao(),
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

    // ==================== Sale Batch Creation Tests ====================

    @Test
    fun `createBatchWithTransactions creates batch and transactions atomically`() = runTest {
        insertTestLocation()
        insertTestProducts()

        val batchId = UUID.randomUUID()
        val batch = SaleBatch(
            id = batchId,
            localId = "sale-batch-1",
            locationId = locationId,
            notes = "Wholesale sale",
            totalWeightKg = BigDecimal("50.0"),
            totalAmount = BigDecimal("2750.0"),
            itemCount = 2,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null
        )

        val transactions = listOf(
            Transaction(
                id = UUID.randomUUID(),
                localId = "sale-tx-1",
                locationId = locationId,
                type = TransactionType.SALE,
                transferLocationId = null,
                productId = productId1,
                weightKg = BigDecimal("30.0"),
                pricePerKg = BigDecimal("55.00"),
                totalAmount = BigDecimal("1650.0"),
                notes = null,
                deviceId = "test-device",
                createdAt = Instant.now(),
                syncedAt = null,
                batchId = null,
                saleBatchId = batchId
            ),
            Transaction(
                id = UUID.randomUUID(),
                localId = "sale-tx-2",
                locationId = locationId,
                type = TransactionType.SALE,
                transferLocationId = null,
                productId = productId2,
                weightKg = BigDecimal("20.0"),
                pricePerKg = BigDecimal("55.00"),
                totalAmount = BigDecimal("1100.0"),
                notes = null,
                deviceId = "test-device",
                createdAt = Instant.now(),
                syncedAt = null,
                batchId = null,
                saleBatchId = batchId
            )
        )

        saleBatchRepository.createBatchWithTransactions(batch, transactions)

        // Verify batch created
        val savedBatch = saleBatchRepository.getById(batchId)
        assertNotNull(savedBatch)
        assertEquals("sale-batch-1", savedBatch!!.localId)
        assertEquals(BigDecimal("50.0"), savedBatch.totalWeightKg)

        // Verify transactions created and linked
        val savedTransactions = saleBatchRepository.getTransactionsForBatch(batchId)
        assertEquals(2, savedTransactions.size)
        assertTrue(savedTransactions.all { it.saleBatchId == batchId })
    }

    @Test
    fun `getTransactionsForBatch returns only batch transactions`() = runTest {
        insertTestLocation()
        insertTestProducts()

        val batch1Id = UUID.randomUUID()
        val batch2Id = UUID.randomUUID()

        // Create first batch
        val batch1 = SaleBatch(
            id = batch1Id,
            localId = "sale-batch-1",
            locationId = locationId,
            notes = null,
            totalWeightKg = BigDecimal("10.0"),
            totalAmount = BigDecimal("550.0"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null
        )
        val tx1 = Transaction(
            id = UUID.randomUUID(),
            localId = "tx-batch1",
            locationId = locationId,
            type = TransactionType.SALE,
            transferLocationId = null,
            productId = productId1,
            weightKg = BigDecimal("10.0"),
            pricePerKg = BigDecimal("55.00"),
            totalAmount = BigDecimal("550.0"),
            notes = null,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null,
            batchId = null,
            saleBatchId = batch1Id
        )
        saleBatchRepository.createBatchWithTransactions(batch1, listOf(tx1))

        // Create second batch
        val batch2 = SaleBatch(
            id = batch2Id,
            localId = "sale-batch-2",
            locationId = locationId,
            notes = null,
            totalWeightKg = BigDecimal("20.0"),
            totalAmount = BigDecimal("1100.0"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = Instant.now().plusSeconds(60),
            syncedAt = null
        )
        val tx2 = Transaction(
            id = UUID.randomUUID(),
            localId = "tx-batch2",
            locationId = locationId,
            type = TransactionType.SALE,
            transferLocationId = null,
            productId = productId2,
            weightKg = BigDecimal("20.0"),
            pricePerKg = BigDecimal("55.00"),
            totalAmount = BigDecimal("1100.0"),
            notes = null,
            deviceId = "test-device",
            createdAt = Instant.now().plusSeconds(60),
            syncedAt = null,
            batchId = null,
            saleBatchId = batch2Id
        )
        saleBatchRepository.createBatchWithTransactions(batch2, listOf(tx2))

        // Verify each batch has its own transactions
        val batch1Txs = saleBatchRepository.getTransactionsForBatch(batch1Id)
        val batch2Txs = saleBatchRepository.getTransactionsForBatch(batch2Id)

        assertEquals(1, batch1Txs.size)
        assertEquals("tx-batch1", batch1Txs[0].localId)

        assertEquals(1, batch2Txs.size)
        assertEquals("tx-batch2", batch2Txs[0].localId)
    }

    // ==================== Pagination Tests ====================

    @Test
    fun `getAllBatchesPaginated returns correct page`() = runTest {
        insertTestLocation()
        insertTestProducts()

        // Create 5 batches
        repeat(5) { i ->
            val batchId = UUID.randomUUID()
            val batch = SaleBatch(
                id = batchId,
                localId = "batch-$i",
                locationId = locationId,
                notes = "Batch $i",
                totalWeightKg = BigDecimal("${(i + 1) * 10}.0"),
                totalAmount = BigDecimal("${(i + 1) * 550}.0"),
                itemCount = 1,
                deviceId = "test-device",
                createdAt = Instant.now().plusSeconds(i.toLong() * 60),
                syncedAt = null
            )
            val tx = Transaction(
                id = UUID.randomUUID(),
                localId = "tx-$i",
                locationId = locationId,
                type = TransactionType.SALE,
                transferLocationId = null,
                productId = productId1,
                weightKg = BigDecimal("${(i + 1) * 10}.0"),
                pricePerKg = BigDecimal("55.00"),
                totalAmount = BigDecimal("${(i + 1) * 550}.0"),
                notes = null,
                deviceId = "test-device",
                createdAt = Instant.now().plusSeconds(i.toLong() * 60),
                syncedAt = null,
                batchId = null,
                saleBatchId = batchId
            )
            saleBatchRepository.createBatchWithTransactions(batch, listOf(tx))
        }

        // Get first page
        val page1 = saleBatchRepository.getAllBatchesPaginated(limit = 2, offset = 0)
        assertEquals(2, page1.size)

        // Get second page
        val page2 = saleBatchRepository.getAllBatchesPaginated(limit = 2, offset = 2)
        assertEquals(2, page2.size)

        // Verify no overlap
        val page1Ids = page1.map { it.id }.toSet()
        val page2Ids = page2.map { it.id }.toSet()
        assertTrue(page1Ids.intersect(page2Ids).isEmpty())
    }

    @Test
    fun `getTotalBatchCount returns correct count`() = runTest {
        insertTestLocation()
        insertTestProducts()

        assertEquals(0, saleBatchRepository.getTotalBatchCount())

        // Create 3 batches
        repeat(3) { i ->
            val batchId = UUID.randomUUID()
            val batch = SaleBatch(
                id = batchId,
                localId = "batch-count-$i",
                locationId = locationId,
                notes = null,
                totalWeightKg = BigDecimal("10.0"),
                totalAmount = BigDecimal("550.0"),
                itemCount = 1,
                deviceId = "test-device",
                createdAt = Instant.now().plusSeconds(i.toLong()),
                syncedAt = null
            )
            val tx = Transaction(
                id = UUID.randomUUID(),
                localId = "tx-count-$i",
                locationId = locationId,
                type = TransactionType.SALE,
                transferLocationId = null,
                productId = productId1,
                weightKg = BigDecimal("10.0"),
                pricePerKg = BigDecimal("55.00"),
                totalAmount = BigDecimal("550.0"),
                notes = null,
                deviceId = "test-device",
                createdAt = Instant.now().plusSeconds(i.toLong()),
                syncedAt = null,
                batchId = null,
                saleBatchId = batchId
            )
            saleBatchRepository.createBatchWithTransactions(batch, listOf(tx))
        }

        assertEquals(3, saleBatchRepository.getTotalBatchCount())
    }

    // ==================== Unsynced Tests ====================

    @Test
    fun `getUnsynced returns only unsynced batches`() = runTest {
        insertTestLocation()
        insertTestProducts()

        // Create synced batch directly
        database.saleBatchDao().insert(
            SaleBatchEntity(
                id = UUID.randomUUID(),
                localId = "synced-batch",
                locationId = locationId,
                notes = null,
                totalWeightKg = BigDecimal("10.0"),
                totalAmount = BigDecimal("550.0"),
                itemCount = 1,
                deviceId = "test-device",
                createdAt = testInstant,
                syncedAt = testInstant // Already synced
            )
        )

        // Create unsynced batch
        val unsyncedId = UUID.randomUUID()
        val batch = SaleBatch(
            id = unsyncedId,
            localId = "unsynced-batch",
            locationId = locationId,
            notes = null,
            totalWeightKg = BigDecimal("20.0"),
            totalAmount = BigDecimal("1100.0"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null
        )
        val tx = Transaction(
            id = UUID.randomUUID(),
            localId = "tx-unsynced",
            locationId = locationId,
            type = TransactionType.SALE,
            transferLocationId = null,
            productId = productId1,
            weightKg = BigDecimal("20.0"),
            pricePerKg = BigDecimal("55.00"),
            totalAmount = BigDecimal("1100.0"),
            notes = null,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null,
            batchId = null,
            saleBatchId = unsyncedId
        )
        saleBatchRepository.createBatchWithTransactions(batch, listOf(tx))

        // Verify only unsynced is returned
        val unsynced = saleBatchRepository.getUnsynced()
        assertEquals(1, unsynced.size)
        assertEquals("unsynced-batch", unsynced[0].localId)
    }

    @Test
    fun `markSynced updates syncedAt timestamp`() = runTest {
        insertTestLocation()
        insertTestProducts()

        val batchId = UUID.randomUUID()
        val batch = SaleBatch(
            id = batchId,
            localId = "to-sync-batch",
            locationId = locationId,
            notes = null,
            totalWeightKg = BigDecimal("10.0"),
            totalAmount = BigDecimal("550.0"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null
        )
        val tx = Transaction(
            id = UUID.randomUUID(),
            localId = "tx-to-sync",
            locationId = locationId,
            type = TransactionType.SALE,
            transferLocationId = null,
            productId = productId1,
            weightKg = BigDecimal("10.0"),
            pricePerKg = BigDecimal("55.00"),
            totalAmount = BigDecimal("550.0"),
            notes = null,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null,
            batchId = null,
            saleBatchId = batchId
        )
        saleBatchRepository.createBatchWithTransactions(batch, listOf(tx))

        // Verify unsynced
        assertNull(saleBatchRepository.getById(batchId)?.syncedAt)

        // Mark synced
        saleBatchRepository.markSynced(batchId)

        // Verify synced
        val synced = saleBatchRepository.getById(batchId)
        assertNotNull(synced?.syncedAt)
    }

    // ==================== Delete Tests ====================

    @Test
    fun `delete removes batch from database`() = runTest {
        insertTestLocation()
        insertTestProducts()

        val batchId = UUID.randomUUID()
        val batch = SaleBatch(
            id = batchId,
            localId = "to-delete-batch",
            locationId = locationId,
            notes = null,
            totalWeightKg = BigDecimal("10.0"),
            totalAmount = BigDecimal("550.0"),
            itemCount = 1,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null
        )
        val tx = Transaction(
            id = UUID.randomUUID(),
            localId = "tx-to-delete",
            locationId = locationId,
            type = TransactionType.SALE,
            transferLocationId = null,
            productId = productId1,
            weightKg = BigDecimal("10.0"),
            pricePerKg = BigDecimal("55.00"),
            totalAmount = BigDecimal("550.0"),
            notes = null,
            deviceId = "test-device",
            createdAt = Instant.now(),
            syncedAt = null,
            batchId = null,
            saleBatchId = batchId
        )
        saleBatchRepository.createBatchWithTransactions(batch, listOf(tx))

        // Verify exists
        assertNotNull(saleBatchRepository.getById(batchId))

        // Delete
        saleBatchRepository.delete(batchId)

        // Verify deleted
        assertNull(saleBatchRepository.getById(batchId))
    }
}
