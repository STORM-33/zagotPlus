package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.CashOperationType
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

class CashRepositoryImplTest {

    private lateinit var cashOperationDao: CashOperationDao
    private lateinit var expenseCategoryDao: ExpenseCategoryDao
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var repository: CashRepositoryImpl

    private val testLocationId = UUID.randomUUID()
    private val testCategoryId = UUID.randomUUID()
    private val now = Instant.now()

    @Before
    fun setup() {
        cashOperationDao = mockk()
        expenseCategoryDao = mockk()
        devicePreferences = mockk()
        
        every { devicePreferences.getDeviceId() } returns "test-device"
        every { expenseCategoryDao.getAllCategories() } returns flowOf(emptyList())
        
        repository = CashRepositoryImpl(cashOperationDao, expenseCategoryDao, devicePreferences)
    }

    private fun createCategoryEntity(
        id: UUID = testCategoryId,
        name: String = "Test Category",
        isActive: Boolean = true
    ) = ExpenseCategoryEntity(
        id = id,
        localId = UUID.randomUUID().toString(),
        name = name,
        isActive = isActive,
        createdAt = now,
        syncedAt = null
    )

    private fun createOperationEntity(
        id: UUID = UUID.randomUUID(),
        locationId: UUID? = testLocationId,
        type: String = CashOperationType.DEPOSIT.toDbValue(),
        amount: BigDecimal = BigDecimal("100.00"),
        categoryId: UUID? = null,
        batchId: UUID? = null,
        notes: String? = null
    ) = CashOperationEntity(
        id = id,
        localId = UUID.randomUUID().toString(),
        locationId = locationId,
        type = type,
        amount = amount,
        categoryId = categoryId,
        batchId = batchId,
        notes = notes,
        deviceId = "test-device",
        createdAt = now,
        syncedAt = null
    )

    // ==================== Category Tests ====================

    @Test
    fun `getActiveCategories returns active categories only`() = runTest {
        val activeCategory = createCategoryEntity(isActive = true)
        every { expenseCategoryDao.getActiveCategories() } returns flowOf(listOf(activeCategory))

        val categories = repository.getActiveCategories().first()

        assertEquals(1, categories.size)
        assertTrue(categories[0].isActive)
    }

    @Test
    fun `getAllCategories returns all categories`() = runTest {
        val categories = listOf(
            createCategoryEntity(id = UUID.randomUUID(), isActive = true),
            createCategoryEntity(id = UUID.randomUUID(), isActive = false)
        )
        every { expenseCategoryDao.getAllCategories() } returns flowOf(categories)

        val result = repository.getAllCategories().first()

        assertEquals(2, result.size)
    }

    @Test
    fun `createCategory inserts entity and returns domain model`() = runTest {
        val slot = slot<ExpenseCategoryEntity>()
        coEvery { expenseCategoryDao.insert(capture(slot)) } returns Unit

        val result = repository.createCategory("New Category")

        assertEquals("New Category", result.name)
        assertTrue(result.isActive)
        assertEquals("New Category", slot.captured.name)
    }

    @Test
    fun `updateCategory updates existing category`() = runTest {
        val existing = createCategoryEntity(name = "Old Name")
        coEvery { expenseCategoryDao.getById(testCategoryId) } returns existing
        coEvery { expenseCategoryDao.update(any()) } returns Unit

        repository.updateCategory(testCategoryId, "New Name")

        coVerify { expenseCategoryDao.update(match { it.name == "New Name" }) }
    }

    @Test
    fun `updateCategory does nothing if category not found`() = runTest {
        coEvery { expenseCategoryDao.getById(testCategoryId) } returns null

        repository.updateCategory(testCategoryId, "New Name")

        coVerify(exactly = 0) { expenseCategoryDao.update(any()) }
    }

    @Test
    fun `deactivateCategory calls dao`() = runTest {
        coEvery { expenseCategoryDao.deactivate(testCategoryId) } returns Unit

        repository.deactivateCategory(testCategoryId)

        coVerify { expenseCategoryDao.deactivate(testCategoryId) }
    }

    // ==================== Operation Query Tests ====================

    @Test
    fun `getOperationsByLocation returns operations for location`() = runTest {
        val operations = listOf(createOperationEntity(locationId = testLocationId))
        every { cashOperationDao.getByLocation(testLocationId) } returns flowOf(operations)

        val result = repository.getOperationsByLocation(testLocationId).first()

        assertEquals(1, result.size)
        assertEquals(testLocationId, result[0].locationId)
    }

    @Test
    fun `getRecentOperations returns limited operations`() = runTest {
        val operations = listOf(
            createOperationEntity(),
            createOperationEntity()
        )
        every { cashOperationDao.getRecentByLocation(testLocationId, 5) } returns flowOf(operations)

        val result = repository.getRecentOperations(testLocationId, 5).first()

        assertEquals(2, result.size)
    }

    @Test
    fun `getAllOperations returns all operations`() = runTest {
        val operations = listOf(
            createOperationEntity(locationId = testLocationId),
            createOperationEntity(locationId = null)
        )
        every { cashOperationDao.getAllOperations() } returns flowOf(operations)

        val result = repository.getAllOperations().first()

        assertEquals(2, result.size)
    }

    @Test
    fun `getTotalBalance returns balance from dao`() = runTest {
        every { cashOperationDao.getTotalBalance() } returns flowOf(BigDecimal("500.00"))

        val balance = repository.getTotalBalance().first()

        assertEquals(BigDecimal("500.00"), balance)
    }

    @Test
    fun `getBalance returns location balance from dao`() = runTest {
        every { cashOperationDao.getBalanceByLocation(testLocationId) } returns flowOf(BigDecimal("250.00"))

        val balance = repository.getBalance(testLocationId).first()

        assertEquals(BigDecimal("250.00"), balance)
    }

    @Test
    fun `getOperationsPaged calls dao with correct parameters`() = runTest {
        val operations = listOf(createOperationEntity())
        coEvery { cashOperationDao.getOperationsPaged(10, 20) } returns operations

        val result = repository.getOperationsPaged(10, 20)

        assertEquals(1, result.size)
        coVerify { cashOperationDao.getOperationsPaged(10, 20) }
    }

    @Test
    fun `getTotalOperationsCount returns count from dao`() = runTest {
        coEvery { cashOperationDao.getTotalOperationsCount() } returns 42

        val count = repository.getTotalOperationsCount()

        assertEquals(42, count)
    }

    // ==================== Operation Creation Tests ====================

    @Test
    fun `deposit creates deposit operation`() = runTest {
        val slot = slot<CashOperationEntity>()
        coEvery { cashOperationDao.insert(capture(slot)) } returns Unit

        repository.deposit(testLocationId, BigDecimal("100.00"), "Deposit notes")

        assertEquals(CashOperationType.DEPOSIT.toDbValue(), slot.captured.type)
        assertEquals(BigDecimal("100.00"), slot.captured.amount)
        assertEquals(testLocationId, slot.captured.locationId)
        assertEquals("Deposit notes", slot.captured.notes)
    }

    @Test
    fun `deposit with null location creates global operation`() = runTest {
        val slot = slot<CashOperationEntity>()
        coEvery { cashOperationDao.insert(capture(slot)) } returns Unit

        repository.deposit(null, BigDecimal("50.00"), null)

        assertNull(slot.captured.locationId)
    }

    @Test
    fun `withdraw creates withdrawal operation`() = runTest {
        val slot = slot<CashOperationEntity>()
        coEvery { cashOperationDao.insert(capture(slot)) } returns Unit

        repository.withdraw(testLocationId, BigDecimal("75.00"), "Withdraw notes")

        assertEquals(CashOperationType.WITHDRAWAL.toDbValue(), slot.captured.type)
        assertEquals(BigDecimal("75.00"), slot.captured.amount)
        assertEquals("Withdraw notes", slot.captured.notes)
    }

    @Test
    fun `payment creates payment operation with category`() = runTest {
        val slot = slot<CashOperationEntity>()
        coEvery { cashOperationDao.insert(capture(slot)) } returns Unit

        repository.payment(testLocationId, BigDecimal("200.00"), testCategoryId, "Payment notes")

        assertEquals(CashOperationType.PAYMENT.toDbValue(), slot.captured.type)
        assertEquals(BigDecimal("200.00"), slot.captured.amount)
        assertEquals(testCategoryId, slot.captured.categoryId)
        assertEquals("Payment notes", slot.captured.notes)
    }

    @Test
    fun `payment without category sets categoryId to null`() = runTest {
        val slot = slot<CashOperationEntity>()
        coEvery { cashOperationDao.insert(capture(slot)) } returns Unit

        repository.payment(testLocationId, BigDecimal("150.00"), null, null)

        assertNull(slot.captured.categoryId)
        assertNull(slot.captured.notes)
    }

    // ==================== Category Enrichment Tests ====================

    @Test
    fun `operations are enriched with category names`() = runTest {
        val category = createCategoryEntity(id = testCategoryId, name = "Fuel")
        val operation = createOperationEntity(
            type = CashOperationType.PAYMENT.toDbValue(),
            categoryId = testCategoryId
        )
        
        every { expenseCategoryDao.getAllCategories() } returns flowOf(listOf(category))
        every { cashOperationDao.getAllOperations() } returns flowOf(listOf(operation))

        val result = repository.getAllOperations().first()

        assertEquals(1, result.size)
        assertEquals("Fuel", result[0].categoryName)
    }

    @Test
    fun `operations without category have null categoryName`() = runTest {
        val operation = createOperationEntity(categoryId = null)
        
        every { cashOperationDao.getAllOperations() } returns flowOf(listOf(operation))

        val result = repository.getAllOperations().first()

        assertNull(result[0].categoryName)
    }
}
