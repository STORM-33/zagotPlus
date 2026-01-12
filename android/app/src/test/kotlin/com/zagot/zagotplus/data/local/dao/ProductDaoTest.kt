package com.zagot.zagotplus.data.local.dao

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.entity.ProductEntity
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
 * DAO tests using Robolectric for fast in-memory database testing.
 * These tests verify SQL queries work correctly without mocking.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class ProductDaoTest {

    private lateinit var database: ZagotDatabase
    private lateinit var productDao: ProductDao

    private val testInstant = Instant.parse("2024-01-15T10:00:00Z")

    @Before
    fun setup() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        productDao = database.productDao()
    }

    @After
    fun tearDown() {
        database.close()
    }

    private fun createProduct(
        id: UUID = UUID.randomUUID(),
        name: String = "Горіх білий",
        isActive: Boolean = true,
        syncedAt: Instant? = null
    ) = ProductEntity(
        id = id,
        localId = "local-$id",
        name = name,
        defaultBuyPrice = BigDecimal("45.00"),
        defaultSellPrice = BigDecimal("55.00"),
        isActive = isActive,
        createdAt = testInstant,
        syncedAt = syncedAt
    )

    // ==================== Insert Tests ====================

    @Test
    fun `insert product stores it in database`() = runTest {
        val product = createProduct(name = "Горіх червоний")
        
        productDao.insert(product)
        
        val retrieved = productDao.getById(product.id)
        assertNotNull(retrieved)
        assertEquals("Горіх червоний", retrieved?.name)
    }

    @Test
    fun `insert with conflict replaces existing product`() = runTest {
        val id = UUID.randomUUID()
        val original = createProduct(id = id, name = "Original")
        val updated = createProduct(id = id, name = "Updated")
        
        productDao.insert(original)
        productDao.insert(updated)
        
        val retrieved = productDao.getById(id)
        assertEquals("Updated", retrieved?.name)
    }

    @Test
    fun `insertAll stores multiple products`() = runTest {
        val products = listOf(
            createProduct(name = "Горіх 1"),
            createProduct(name = "Горіх 2"),
            createProduct(name = "Горіх 3")
        )
        
        productDao.insertAll(products)
        
        val all = productDao.getAll()
        assertEquals(3, all.size)
    }

    // ==================== Query Tests ====================

    @Test
    fun `getAll returns all products ordered by name`() = runTest {
        productDao.insertAll(listOf(
            createProduct(name = "Ccc"),
            createProduct(name = "Aaa"),
            createProduct(name = "Bbb")
        ))
        
        val all = productDao.getAll()
        
        assertEquals(3, all.size)
        // SQLite orders by ASCII/UTF8 bytes - verify ordering is consistent
        assertEquals("Aaa", all[0].name)
        assertEquals("Bbb", all[1].name)
        assertEquals("Ccc", all[2].name)
    }

    @Test
    fun `getAllFlow emits updates reactively`() = runTest {
        val product1 = createProduct(name = "Горіх 1")
        productDao.insert(product1)
        
        val initial = productDao.getAllFlow().first()
        assertEquals(1, initial.size)
        
        productDao.insert(createProduct(name = "Горіх 2"))
        
        val updated = productDao.getAllFlow().first()
        assertEquals(2, updated.size)
    }

    @Test
    fun `getById returns null for non-existent product`() = runTest {
        val result = productDao.getById(UUID.randomUUID())
        
        assertNull(result)
    }

    @Test
    fun `getByLocalId finds product by local id`() = runTest {
        val product = createProduct()
        productDao.insert(product)
        
        val retrieved = productDao.getByLocalId(product.localId)
        
        assertNotNull(retrieved)
        assertEquals(product.id, retrieved?.id)
    }

    // ==================== Active Products Tests ====================

    @Test
    fun `getActive returns only active products`() = runTest {
        productDao.insertAll(listOf(
            createProduct(name = "Активний 1", isActive = true),
            createProduct(name = "Неактивний", isActive = false),
            createProduct(name = "Активний 2", isActive = true)
        ))
        
        val active = productDao.getActive()
        
        assertEquals(2, active.size)
        assertTrue(active.all { it.isActive })
    }

    @Test
    fun `getActiveFlow returns only active products reactively`() = runTest {
        productDao.insertAll(listOf(
            createProduct(name = "Активний", isActive = true),
            createProduct(name = "Неактивний", isActive = false)
        ))
        
        val active = productDao.getActiveFlow().first()
        
        assertEquals(1, active.size)
        assertEquals("Активний", active[0].name)
    }

    // ==================== Sync Status Tests ====================

    @Test
    fun `getUnsynced returns products with null syncedAt`() = runTest {
        productDao.insertAll(listOf(
            createProduct(name = "Несинхронізований 1", syncedAt = null),
            createProduct(name = "Синхронізований", syncedAt = testInstant),
            createProduct(name = "Несинхронізований 2", syncedAt = null)
        ))
        
        val unsynced = productDao.getUnsynced()
        
        assertEquals(2, unsynced.size)
        assertTrue(unsynced.all { it.syncedAt == null })
    }

    @Test
    fun `markSynced updates syncedAt timestamp`() = runTest {
        val product = createProduct(syncedAt = null)
        productDao.insert(product)
        
        val syncTime = Instant.now()
        productDao.markSynced(product.id, syncTime)
        
        val retrieved = productDao.getById(product.id)
        assertEquals(syncTime.toEpochMilli(), retrieved?.syncedAt?.toEpochMilli())
    }

    // ==================== Update & Delete Tests ====================

    @Test
    fun `update modifies existing product`() = runTest {
        val product = createProduct(name = "Original")
        productDao.insert(product)
        
        val updated = product.copy(name = "Modified")
        productDao.update(updated)
        
        val retrieved = productDao.getById(product.id)
        assertEquals("Modified", retrieved?.name)
    }

    @Test
    fun `deleteById removes product`() = runTest {
        val product = createProduct()
        productDao.insert(product)
        
        productDao.deleteById(product.id)
        
        val retrieved = productDao.getById(product.id)
        assertNull(retrieved)
    }

    @Test
    fun `deleteAll clears all products`() = runTest {
        productDao.insertAll(listOf(
            createProduct(name = "1"),
            createProduct(name = "2"),
            createProduct(name = "3")
        ))
        
        productDao.deleteAll()
        
        val all = productDao.getAll()
        assertTrue(all.isEmpty())
    }

    // ==================== BigDecimal Precision Tests ====================

    @Test
    fun `BigDecimal prices are stored and retrieved with correct precision`() = runTest {
        val product = ProductEntity(
            id = UUID.randomUUID(),
            localId = "test-local",
            name = "Точність ціни",
            defaultBuyPrice = BigDecimal("45.55"),
            defaultSellPrice = BigDecimal("99.99"),
            isActive = true,
            createdAt = testInstant
        )
        
        productDao.insert(product)
        
        val retrieved = productDao.getById(product.id)
        assertEquals(BigDecimal("45.55"), retrieved?.defaultBuyPrice)
        assertEquals(BigDecimal("99.99"), retrieved?.defaultSellPrice)
    }

    @Test
    fun `high precision BigDecimal values are preserved`() = runTest {
        val product = ProductEntity(
            id = UUID.randomUUID(),
            localId = "test-local-2",
            name = "Висока точність",
            defaultBuyPrice = BigDecimal("123.456789"),
            defaultSellPrice = BigDecimal("0.0001"),
            isActive = true,
            createdAt = testInstant
        )
        
        productDao.insert(product)
        
        val retrieved = productDao.getById(product.id)
        assertEquals(BigDecimal("123.456789"), retrieved?.defaultBuyPrice)
        assertEquals(BigDecimal("0.0001"), retrieved?.defaultSellPrice)
    }
}
