package com.zagot.zagotplus.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
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
 * DAO tests for TransactionDao using Robolectric.
 * Tests SQL queries, inventory aggregation, and sync-related operations.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class TransactionDaoTest {

    private lateinit var database: ZagotDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var locationDao: LocationDao
    private lateinit var productDao: ProductDao

    private val testInstant = Instant.parse("2024-01-15T10:00:00Z")
    private lateinit var testLocationId: UUID
    private lateinit var testProductId: UUID

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        transactionDao = database.transactionDao()
        locationDao = database.locationDao()
        productDao = database.productDao()

        // Create required foreign key entities
        testLocationId = UUID.randomUUID()
        testProductId = UUID.randomUUID()

        kotlinx.coroutines.runBlocking {
            locationDao.insert(LocationEntity(testLocationId, "Test Location", "kiosk", testInstant))
            productDao.insert(ProductEntity(
                id = testProductId,
                localId = "product-local-id",
                name = "Test Product",
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

    private fun createTransaction(
        id: UUID = UUID.randomUUID(),
        localId: String = "local-${UUID.randomUUID()}",
        locationId: UUID? = testLocationId,
        type: String = "purchase",
        productId: UUID? = testProductId,
        weightKg: BigDecimal = BigDecimal("100.00"),
        pricePerKg: BigDecimal = BigDecimal("45.00"),
        syncedAt: Instant? = null,
        createdAt: Instant = testInstant
    ) = TransactionEntity(
        id = id,
        localId = localId,
        locationId = locationId,
        type = type,
        transferLocationId = null,
        productId = productId,
        weightKg = weightKg,
        pricePerKg = pricePerKg,
        totalAmount = weightKg.multiply(pricePerKg),
        notes = null,
        deviceId = "test-device",
        createdAt = createdAt,
        syncedAt = syncedAt
    )

    // ==================== Insert Tests ====================

    @Test
    fun `insert transaction stores it in database`() = runTest {
        val transaction = createTransaction()
        
        transactionDao.insert(transaction)
        
        val retrieved = transactionDao.getById(transaction.id)
        assertNotNull(retrieved)
        assertEquals(transaction.localId, retrieved?.localId)
    }

    @Test
    fun `insertAll stores multiple transactions`() = runTest {
        val transactions = listOf(
            createTransaction(),
            createTransaction(),
            createTransaction()
        )
        
        transactionDao.insertAll(transactions)
        
        val all = transactionDao.getAll()
        assertEquals(3, all.size)
    }

    // ==================== Query Tests ====================

    @Test
    fun `getAll returns transactions ordered by created_at DESC`() = runTest {
        val earlier = Instant.parse("2024-01-01T10:00:00Z")
        val later = Instant.parse("2024-01-15T10:00:00Z")
        
        transactionDao.insert(createTransaction(createdAt = earlier))
        transactionDao.insert(createTransaction(createdAt = later))
        
        val all = transactionDao.getAll()
        
        assertEquals(2, all.size)
        assertTrue(all[0].createdAt >= all[1].createdAt)
    }

    @Test
    fun `getById returns null for non-existent transaction`() = runTest {
        val result = transactionDao.getById(UUID.randomUUID())
        
        assertNull(result)
    }

    @Test
    fun `getByLocalId finds transaction by local id`() = runTest {
        val transaction = createTransaction(localId = "unique-local-id")
        transactionDao.insert(transaction)
        
        val retrieved = transactionDao.getByLocalId("unique-local-id")
        
        assertNotNull(retrieved)
        assertEquals(transaction.id, retrieved?.id)
    }

    // ==================== Pagination Tests ====================

    @Test
    fun `getAllPaginated returns correct page`() = runTest {
        // Insert 10 transactions
        repeat(10) { i ->
            transactionDao.insert(createTransaction(
                createdAt = testInstant.plusSeconds(i.toLong())
            ))
        }
        
        val page1 = transactionDao.getAllPaginated(limit = 3, offset = 0)
        val page2 = transactionDao.getAllPaginated(limit = 3, offset = 3)
        
        assertEquals(3, page1.size)
        assertEquals(3, page2.size)
        assertNotEquals(page1[0].id, page2[0].id)
    }

    @Test
    fun `getTotalCount returns correct count`() = runTest {
        repeat(5) {
            transactionDao.insert(createTransaction())
        }
        
        val count = transactionDao.getTotalCount()
        
        assertEquals(5, count)
    }

    // ==================== Location Filter Tests ====================

    @Test
    fun `getByLocationFlow returns only transactions for location`() = runTest {
        // Create second location
        val secondLocationId = UUID.randomUUID()
        locationDao.insert(LocationEntity(secondLocationId, "Second Location", "mobile", testInstant))
        
        transactionDao.insert(createTransaction(locationId = testLocationId))
        transactionDao.insert(createTransaction(locationId = testLocationId))
        transactionDao.insert(createTransaction(locationId = secondLocationId))
        
        val forTestLocation = transactionDao.getByLocationFlow(testLocationId).first()
        
        assertEquals(2, forTestLocation.size)
        assertTrue(forTestLocation.all { it.locationId == testLocationId })
    }

    // ==================== Sync Tests ====================

    @Test
    fun `getUnsynced returns only transactions with null syncedAt`() = runTest {
        transactionDao.insertAll(listOf(
            createTransaction(syncedAt = null),
            createTransaction(syncedAt = testInstant),
            createTransaction(syncedAt = null)
        ))
        
        val unsynced = transactionDao.getUnsynced()
        
        assertEquals(2, unsynced.size)
        assertTrue(unsynced.all { it.syncedAt == null })
    }

    @Test
    fun `getUnsyncedCountFlow returns correct count reactively`() = runTest {
        transactionDao.insertAll(listOf(
            createTransaction(syncedAt = null),
            createTransaction(syncedAt = testInstant),
            createTransaction(syncedAt = null)
        ))
        
        val count = transactionDao.getUnsyncedCountFlow().first()
        
        assertEquals(2, count)
    }

    @Test
    fun `markAsSynced updates syncedAt by localId`() = runTest {
        val transaction = createTransaction(localId = "sync-test-id", syncedAt = null)
        transactionDao.insert(transaction)
        
        val syncTime = Instant.now()
        transactionDao.markAsSynced("sync-test-id", syncTime)
        
        val retrieved = transactionDao.getByLocalId("sync-test-id")
        assertNotNull(retrieved?.syncedAt)
        assertEquals(syncTime.toEpochMilli(), retrieved?.syncedAt?.toEpochMilli())
    }

    @Test
    fun `getCreatedAfter returns transactions after timestamp`() = runTest {
        val cutoff = Instant.parse("2024-01-10T00:00:00Z")
        val before = Instant.parse("2024-01-05T00:00:00Z")
        val after = Instant.parse("2024-01-15T00:00:00Z")
        
        transactionDao.insert(createTransaction(createdAt = before))
        transactionDao.insert(createTransaction(createdAt = after))
        
        val result = transactionDao.getCreatedAfter(cutoff)
        
        assertEquals(1, result.size)
        assertTrue(result[0].createdAt.isAfter(cutoff))
    }

    // ==================== Inventory Aggregation Tests ====================

    @Test
    fun `getInventoryAggregatedFlow sums weights by location and product`() = runTest {
        // Add multiple transactions for same product at same location
        transactionDao.insert(createTransaction(weightKg = BigDecimal("100.00")))
        transactionDao.insert(createTransaction(weightKg = BigDecimal("50.00")))
        transactionDao.insert(createTransaction(weightKg = BigDecimal("-30.00"))) // Sale
        
        val inventory = transactionDao.getInventoryAggregatedFlow().first()
        
        assertEquals(1, inventory.size)
        assertEquals(testLocationId.toString(), inventory[0].locationId)
        assertEquals(testProductId.toString(), inventory[0].productId)
        // 100 + 50 - 30 = 120 - compare numeric value, not scale
        assertEquals(0, BigDecimal("120").compareTo(BigDecimal(inventory[0].totalWeightKg)))
    }

    @Test
    fun `getInventoryByLocationAggregatedFlow filters by location`() = runTest {
        // Create second location
        val secondLocationId = UUID.randomUUID()
        locationDao.insert(LocationEntity(secondLocationId, "Second", "mobile", testInstant))
        
        transactionDao.insert(createTransaction(locationId = testLocationId, weightKg = BigDecimal("100")))
        transactionDao.insert(createTransaction(locationId = secondLocationId, weightKg = BigDecimal("200")))
        
        val inventory = transactionDao.getInventoryByLocationAggregatedFlow(testLocationId).first()
        
        assertEquals(1, inventory.size)
        assertEquals(BigDecimal("100"), BigDecimal(inventory[0].totalWeightKg))
    }

    // ==================== Delete Tests ====================

    @Test
    fun `deleteAll clears all transactions`() = runTest {
        transactionDao.insertAll(listOf(
            createTransaction(),
            createTransaction(),
            createTransaction()
        ))
        
        transactionDao.deleteAll()
        
        val all = transactionDao.getAll()
        assertTrue(all.isEmpty())
    }

    // ==================== BigDecimal Precision Tests ====================

    @Test
    fun `BigDecimal weights are stored with correct precision`() = runTest {
        val transaction = createTransaction(
            weightKg = BigDecimal("123.456"),
            pricePerKg = BigDecimal("45.99")
        )
        
        transactionDao.insert(transaction)
        
        val retrieved = transactionDao.getById(transaction.id)
        assertEquals(BigDecimal("123.456"), retrieved?.weightKg)
        assertEquals(BigDecimal("45.99"), retrieved?.pricePerKg)
    }

    @Test
    fun `negative weights are stored correctly for sales`() = runTest {
        val transaction = createTransaction(
            type = "sale",
            weightKg = BigDecimal("-50.00")
        )
        
        transactionDao.insert(transaction)
        
        val retrieved = transactionDao.getById(transaction.id)
        assertEquals(BigDecimal("-50.00"), retrieved?.weightKg)
    }
}
