package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.sync.SyncManager
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class ProductRepositoryImplTest {

    private lateinit var productDao: ProductDao
    private lateinit var syncManager: SyncManager
    private lateinit var repository: ProductRepositoryImpl

    private val productId1 = UUID.randomUUID()
    private val productId2 = UUID.randomUUID()
    private val now = Instant.now()

    @Before
    fun setup() {
        productDao = mockk()
        syncManager = mockk(relaxed = true)
        repository = ProductRepositoryImpl(productDao, syncManager)
    }

    private fun createProductEntity(
        id: UUID,
        name: String,
        buyPrice: BigDecimal? = BigDecimal("50.00"),
        sellPrice: BigDecimal? = BigDecimal("55.00"),
        isActive: Boolean = true,
        imageUri: String? = null
    ) = ProductEntity(
        id = id,
        localId = id.toString(),
        name = name,
        defaultBuyPrice = buyPrice,
        defaultSellPrice = sellPrice,
        isActive = isActive,
        createdAt = now,
        syncedAt = now,
        imageUri = imageUri
    )

    @Test
    fun `getAllProducts returns mapped products`() = runTest {
        val entities = listOf(
            createProductEntity(productId1, "Горіх білий"),
            createProductEntity(productId2, "Насіння чорне", isActive = false)
        )
        every { productDao.getAllFlow() } returns flowOf(entities)

        val products = repository.getAllProducts().first()

        assertEquals(2, products.size)
        assertEquals(productId1, products[0].id)
        assertEquals("Горіх білий", products[0].name)
        assertTrue(products[0].isActive)
        assertEquals(productId2, products[1].id)
        assertFalse(products[1].isActive)
    }

    @Test
    fun `getAllProducts returns empty list when no products`() = runTest {
        every { productDao.getAllFlow() } returns flowOf(emptyList())

        val products = repository.getAllProducts().first()

        assertEquals(0, products.size)
    }

    @Test
    fun `getActiveProducts returns only active products`() = runTest {
        val activeEntities = listOf(
            createProductEntity(productId1, "Горіх білий", isActive = true)
        )
        every { productDao.getActiveFlow() } returns flowOf(activeEntities)

        val products = repository.getActiveProducts().first()

        assertEquals(1, products.size)
        assertTrue(products[0].isActive)
    }

    @Test
    fun `getProductById returns mapped product when found`() = runTest {
        val entity = createProductEntity(
            productId1,
            "Горіх білий",
            buyPrice = BigDecimal("45.50"),
            sellPrice = BigDecimal("52.00"),
            imageUri = "content://image.jpg"
        )
        coEvery { productDao.getById(productId1) } returns entity

        val product = repository.getProductById(productId1)

        assertEquals(productId1, product?.id)
        assertEquals("Горіх білий", product?.name)
        assertEquals(BigDecimal("45.50"), product?.defaultBuyPrice)
        assertEquals(BigDecimal("52.00"), product?.defaultSellPrice)
        assertTrue(product?.isActive ?: false)
        assertEquals("content://image.jpg", product?.imageUri)
    }

    @Test
    fun `getProductById returns null when not found`() = runTest {
        coEvery { productDao.getById(productId1) } returns null

        val product = repository.getProductById(productId1)

        assertNull(product)
    }

    @Test
    fun `createProduct inserts entity and returns domain model`() = runTest {
        val entitySlot = slot<ProductEntity>()
        coEvery { productDao.insert(capture(entitySlot)) } returns Unit

        val product = repository.createProduct(
            name = "Новий продукт",
            defaultBuyPrice = BigDecimal("40.00"),
            defaultSellPrice = BigDecimal("48.00"),
            imageUri = "content://new.jpg"
        )

        assertEquals("Новий продукт", product.name)
        assertEquals(BigDecimal("40.00"), product.defaultBuyPrice)
        assertEquals(BigDecimal("48.00"), product.defaultSellPrice)
        assertTrue(product.isActive)
        assertEquals("content://new.jpg", product.imageUri)
        
        // Verify entity was inserted
        coVerify { productDao.insert(any()) }
        assertEquals("Новий продукт", entitySlot.captured.name)
    }

    @Test
    fun `updateProduct updates entity correctly`() = runTest {
        val existingEntity = createProductEntity(productId1, "Original", isActive = true)
        val entitySlot = slot<ProductEntity>()
        coEvery { productDao.getById(productId1) } returns existingEntity
        coEvery { productDao.update(capture(entitySlot)) } returns Unit

        val product = com.zagot.zagotplus.domain.model.Product(
            id = productId1,
            name = "Оновлений",
            defaultBuyPrice = BigDecimal("60.00"),
            defaultSellPrice = BigDecimal("70.00"),
            isActive = false,
            createdAt = now,
            imageUri = "content://updated.jpg"
        )

        repository.updateProduct(product)

        coVerify { productDao.update(any()) }
        assertEquals(productId1, entitySlot.captured.id)
        assertEquals("Оновлений", entitySlot.captured.name)
        assertEquals(BigDecimal("60.00"), entitySlot.captured.defaultBuyPrice)
        assertFalse(entitySlot.captured.isActive)
        assertNull(entitySlot.captured.syncedAt) // Should be null to mark as unsynced
    }

    @Test
    fun `toggleProductActive toggles isActive from true to false`() = runTest {
        val entity = createProductEntity(productId1, "Горіх", isActive = true)
        val entitySlot = slot<ProductEntity>()
        coEvery { productDao.getById(productId1) } returns entity
        coEvery { productDao.update(capture(entitySlot)) } returns Unit

        repository.toggleProductActive(productId1)

        coVerify { productDao.update(any()) }
        assertFalse(entitySlot.captured.isActive)
    }

    @Test
    fun `toggleProductActive toggles isActive from false to true`() = runTest {
        val entity = createProductEntity(productId1, "Горіх", isActive = false)
        val entitySlot = slot<ProductEntity>()
        coEvery { productDao.getById(productId1) } returns entity
        coEvery { productDao.update(capture(entitySlot)) } returns Unit

        repository.toggleProductActive(productId1)

        coVerify { productDao.update(any()) }
        assertTrue(entitySlot.captured.isActive)
    }

    @Test
    fun `toggleProductActive does nothing when product not found`() = runTest {
        coEvery { productDao.getById(productId1) } returns null

        repository.toggleProductActive(productId1)

        coVerify(exactly = 0) { productDao.update(any()) }
    }

    @Test
    fun `getAllProducts maps null prices correctly`() = runTest {
        val entities = listOf(
            createProductEntity(productId1, "Без цін", buyPrice = null, sellPrice = null)
        )
        every { productDao.getAllFlow() } returns flowOf(entities)

        val products = repository.getAllProducts().first()

        assertEquals(1, products.size)
        assertNull(products[0].defaultBuyPrice)
        assertNull(products[0].defaultSellPrice)
    }
}
