package com.zagot.zagotplus.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.dao.TransactionQueryBuilder
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.repository.TransactionRepositoryImpl
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.domain.model.TransactionFilter
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
import java.time.temporal.ChronoUnit
import java.util.UUID

/**
 * Integration tests for transaction history and filtering.
 * Tests the full stack: Repository → QueryBuilder → DAO → Database
 *
 * Uses Robolectric for in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class HistoryFilterIntegrationTest {

    private lateinit var database: ZagotDatabase
    private lateinit var transactionRepository: TransactionRepositoryImpl
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var syncManager: SyncManager
    private lateinit var context: Context

    // Test data IDs
    private val locationRivne = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val locationKyiv = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val productId1 = UUID.fromString("33333333-3333-3333-3333-333333333333")
    private val productId2 = UUID.fromString("44444444-4444-4444-4444-444444444444")
    
    private val baseInstant = Instant.parse("2024-01-15T10:00:00Z")

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
                createdAt = baseInstant
            )
        )
        database.locationDao().insert(
            LocationEntity(
                id = locationKyiv,
                name = "Склад Київ",
                type = LocationType.MOBILE.name,
                createdAt = baseInstant
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
                createdAt = baseInstant,
                syncedAt = null
            )
        )
        database.productDao().insert(
            ProductEntity(
                id = productId2,
                localId = "local-product-2",
                name = "Насіння соняшникове",
                defaultBuyPrice = BigDecimal("30.00"),
                defaultSellPrice = BigDecimal("40.00"),
                isActive = true,
                createdAt = baseInstant,
                syncedAt = null
            )
        )
    }

    private suspend fun insertTransactionAt(
        type: String,
        locationId: UUID,
        productId: UUID,
        weight: BigDecimal,
        createdAt: Instant
    ): TransactionEntity {
        val entity = TransactionEntity(
            id = UUID.randomUUID(),
            localId = UUID.randomUUID().toString(),
            locationId = locationId,
            type = type,
            transferLocationId = null,
            productId = productId,
            weightKg = weight,
            pricePerKg = BigDecimal("50.00"),
            totalAmount = weight.multiply(BigDecimal("50.00")),
            notes = null,
            deviceId = "test-device",
            createdAt = createdAt,
            syncedAt = null
        )
        database.transactionDao().insert(entity)
        return entity
    }

    // ==================== Filter by Type Tests ====================

    @Test
    fun `filter by single type returns only matching transactions`() = runTest {
        insertTestLocations()
        insertTestProducts()

        // Create mixed transactions
        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("10.0"), baseInstant)
        insertTransactionAt("sale", locationRivne, productId1, BigDecimal("5.0"), baseInstant.plusSeconds(60))
        insertTransactionAt("purchase", locationKyiv, productId2, BigDecimal("20.0"), baseInstant.plusSeconds(120))

        // Filter by purchase only
        val query = TransactionQueryBuilder()
            .withTypes(listOf("purchase"))
            .build()
        
        val results = database.transactionDao().getFiltered(query)
        
        assertEquals(2, results.size)
        assertTrue(results.all { it.type == "purchase" })
    }

    @Test
    fun `filter by multiple types returns matching transactions`() = runTest {
        insertTestLocations()
        insertTestProducts()

        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("10.0"), baseInstant)
        insertTransactionAt("sale", locationRivne, productId1, BigDecimal("5.0"), baseInstant.plusSeconds(60))
        insertTransactionAt("transfer_out", locationRivne, productId1, BigDecimal("3.0"), baseInstant.plusSeconds(120))
        insertTransactionAt("transfer_in", locationKyiv, productId1, BigDecimal("3.0"), baseInstant.plusSeconds(120))

        // Filter by purchase and sale only
        val query = TransactionQueryBuilder()
            .withTypes(listOf("purchase", "sale"))
            .build()
        
        val results = database.transactionDao().getFiltered(query)
        
        assertEquals(2, results.size)
        val types = results.map { it.type }.toSet()
        assertTrue(types.contains("purchase"))
        assertTrue(types.contains("sale"))
        assertFalse(types.contains("transfer_out"))
    }

    // ==================== Filter by Location Tests ====================

    @Test
    fun `filter by location returns only location transactions`() = runTest {
        insertTestLocations()
        insertTestProducts()

        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("10.0"), baseInstant)
        insertTransactionAt("purchase", locationKyiv, productId1, BigDecimal("20.0"), baseInstant.plusSeconds(60))
        insertTransactionAt("sale", locationRivne, productId1, BigDecimal("5.0"), baseInstant.plusSeconds(120))

        val query = TransactionQueryBuilder()
            .withLocation(locationRivne)
            .build()
        
        val results = database.transactionDao().getFiltered(query)
        
        assertEquals(2, results.size)
        assertTrue(results.all { it.locationId == locationRivne })
    }

    // ==================== Filter by Date Range Tests ====================

    @Test
    fun `filter by date range returns transactions in range`() = runTest {
        insertTestLocations()
        insertTestProducts()

        val jan10 = Instant.parse("2024-01-10T10:00:00Z")
        val jan15 = Instant.parse("2024-01-15T10:00:00Z")
        val jan20 = Instant.parse("2024-01-20T10:00:00Z")

        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("10.0"), jan10)
        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("15.0"), jan15)
        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("20.0"), jan20)

        // Filter Jan 12 - Jan 18
        val startDate = Instant.parse("2024-01-12T00:00:00Z")
        val endDate = Instant.parse("2024-01-18T23:59:59Z")
        
        val query = TransactionQueryBuilder()
            .withDateRange(startDate, endDate)
            .build()
        
        val results = database.transactionDao().getFiltered(query)
        
        assertEquals(1, results.size)
        assertTrue(results[0].weightKg.compareTo(BigDecimal("15.0")) == 0)
    }

    @Test
    fun `filter by start date only returns transactions after date`() = runTest {
        insertTestLocations()
        insertTestProducts()

        val jan10 = Instant.parse("2024-01-10T10:00:00Z")
        val jan15 = Instant.parse("2024-01-15T10:00:00Z")
        val jan20 = Instant.parse("2024-01-20T10:00:00Z")

        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("10.0"), jan10)
        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("15.0"), jan15)
        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("20.0"), jan20)

        val startDate = Instant.parse("2024-01-14T00:00:00Z")
        
        val query = TransactionQueryBuilder()
            .withDateRange(startDate, null)
            .build()
        
        val results = database.transactionDao().getFiltered(query)
        
        assertEquals(2, results.size)
    }

    // ==================== Filter by Product Name Tests ====================

    @Test
    fun `filter by product name search finds matching products`() = runTest {
        insertTestLocations()
        insertTestProducts()

        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("10.0"), baseInstant)
        insertTransactionAt("purchase", locationRivne, productId2, BigDecimal("20.0"), baseInstant.plusSeconds(60))

        // Search for "Горіх"
        val query = TransactionQueryBuilder()
            .withProductNameSearch("Горіх")
            .build()
        
        val results = database.transactionDao().getFiltered(query)
        
        assertEquals(1, results.size)
        assertEquals(productId1, results[0].productId)
    }

    @Test
    fun `filter by product name matches with same case`() = runTest {
        insertTestLocations()
        insertTestProducts()

        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("10.0"), baseInstant)
        insertTransactionAt("purchase", locationRivne, productId2, BigDecimal("20.0"), baseInstant.plusSeconds(60))

        // Search with proper case (SQLite LIKE is case-sensitive for Unicode)
        val query = TransactionQueryBuilder()
            .withProductNameSearch("Насіння")
            .build()
        
        val results = database.transactionDao().getFiltered(query)
        
        assertEquals(1, results.size)
        assertEquals(productId2, results[0].productId)
    }

    @Test
    fun `filter by partial product name matches`() = runTest {
        insertTestLocations()
        insertTestProducts()

        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("10.0"), baseInstant)
        insertTransactionAt("purchase", locationRivne, productId2, BigDecimal("20.0"), baseInstant.plusSeconds(60))

        // Search partial name
        val query = TransactionQueryBuilder()
            .withProductNameSearch("білий")
            .build()
        
        val results = database.transactionDao().getFiltered(query)
        
        assertEquals(1, results.size)
        assertEquals(productId1, results[0].productId)
    }

    // ==================== Combined Filters Tests ====================

    @Test
    fun `multiple filters are combined with AND logic`() = runTest {
        insertTestLocations()
        insertTestProducts()

        val jan15 = Instant.parse("2024-01-15T10:00:00Z")
        val jan16 = Instant.parse("2024-01-16T10:00:00Z")

        // Rivne, purchase, Jan 15
        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("10.0"), jan15)
        // Rivne, sale, Jan 15
        insertTransactionAt("sale", locationRivne, productId1, BigDecimal("5.0"), jan15.plusSeconds(60))
        // Kyiv, purchase, Jan 15
        insertTransactionAt("purchase", locationKyiv, productId1, BigDecimal("20.0"), jan15.plusSeconds(120))
        // Rivne, purchase, Jan 16
        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("15.0"), jan16)

        // Filter: Rivne + purchase + Jan 15 only
        val startDate = Instant.parse("2024-01-15T00:00:00Z")
        val endDate = Instant.parse("2024-01-15T23:59:59Z")
        
        val query = TransactionQueryBuilder()
            .withTypes(listOf("purchase"))
            .withLocation(locationRivne)
            .withDateRange(startDate, endDate)
            .build()
        
        val results = database.transactionDao().getFiltered(query)
        
        assertEquals(1, results.size)
        assertTrue(results[0].weightKg.compareTo(BigDecimal("10.0")) == 0)
    }

    // ==================== Pagination Tests ====================

    @Test
    fun `pagination returns correct slice of results`() = runTest {
        insertTestLocations()
        insertTestProducts()

        // Create 10 transactions
        repeat(10) { i ->
            insertTransactionAt(
                "purchase",
                locationRivne,
                productId1,
                BigDecimal("${(i + 1) * 5}.0"),
                baseInstant.plusSeconds(i.toLong() * 60)
            )
        }

        // Get page 2 (3 items per page)
        val query = TransactionQueryBuilder()
            .withPagination(limit = 3, offset = 3)
            .build()
        
        val results = database.transactionDao().getFiltered(query)
        
        assertEquals(3, results.size)
    }

    @Test
    fun `count query returns total matching records`() = runTest {
        insertTestLocations()
        insertTestProducts()

        // Create 7 transactions
        repeat(7) { i ->
            insertTransactionAt(
                if (i % 2 == 0) "purchase" else "sale",
                locationRivne,
                productId1,
                BigDecimal("${(i + 1) * 5}.0"),
                baseInstant.plusSeconds(i.toLong() * 60)
            )
        }

        // Count all purchases
        val countQuery = TransactionQueryBuilder()
            .withTypes(listOf("purchase"))
            .buildCount()
        
        val count = database.transactionDao().getFilteredCount(countQuery)
        
        assertEquals(4, count) // 0, 2, 4, 6 are purchases
    }

    // ==================== Order Tests ====================

    @Test
    fun `results are ordered by created_at descending`() = runTest {
        insertTestLocations()
        insertTestProducts()

        val times = listOf(
            baseInstant,
            baseInstant.plusSeconds(120),
            baseInstant.plusSeconds(60)
        )

        times.forEachIndexed { i, time ->
            insertTransactionAt(
                "purchase",
                locationRivne,
                productId1,
                BigDecimal("${(i + 1) * 10}.0"),
                time
            )
        }

        val query = TransactionQueryBuilder().build()
        val results = database.transactionDao().getFiltered(query)

        // Should be ordered: +120s, +60s, 0s
        assertTrue(results[0].createdAt.isAfter(results[1].createdAt))
        assertTrue(results[1].createdAt.isAfter(results[2].createdAt))
    }

    // ==================== Empty Results Tests ====================

    @Test
    fun `no matches returns empty list`() = runTest {
        insertTestLocations()
        insertTestProducts()

        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("10.0"), baseInstant)

        // Filter for non-existent type
        val query = TransactionQueryBuilder()
            .withTypes(listOf("nonexistent"))
            .build()
        
        val results = database.transactionDao().getFiltered(query)
        
        assertTrue(results.isEmpty())
    }

    @Test
    fun `filter with no matching date range returns empty`() = runTest {
        insertTestLocations()
        insertTestProducts()

        insertTransactionAt("purchase", locationRivne, productId1, BigDecimal("10.0"), baseInstant)

        // Filter for future date
        val futureStart = Instant.parse("2025-01-01T00:00:00Z")
        val futureEnd = Instant.parse("2025-12-31T23:59:59Z")
        
        val query = TransactionQueryBuilder()
            .withDateRange(futureStart, futureEnd)
            .build()
        
        val results = database.transactionDao().getFiltered(query)
        
        assertTrue(results.isEmpty())
    }
}
