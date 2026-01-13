package com.zagot.zagotplus.integration

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.repository.CashRepositoryImpl
import com.zagot.zagotplus.domain.model.CashOperationType
import com.zagot.zagotplus.domain.model.LocationType
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
import java.time.LocalDate
import java.util.UUID

/**
 * Integration tests for cash flow operations.
 * Tests the full stack: Repository → DAO → Database
 *
 * Uses Robolectric for in-memory Room database.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28], manifest = Config.NONE)
class CashFlowIntegrationTest {

    private lateinit var database: ZagotDatabase
    private lateinit var cashRepository: CashRepositoryImpl
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var context: Context

    // Test data IDs
    private val locationId = UUID.fromString("11111111-1111-1111-1111-111111111111")
    private val categoryId = UUID.fromString("22222222-2222-2222-2222-222222222222")
    private val testInstant = Instant.parse("2024-01-15T10:00:00Z")

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()

        database = Room.inMemoryDatabaseBuilder(context, ZagotDatabase::class.java)
            .allowMainThreadQueries()
            .build()

        devicePreferences = mockk(relaxed = true)
        every { devicePreferences.getDeviceId() } returns "test-device-id"

        cashRepository = CashRepositoryImpl(
            cashOperationDao = database.cashOperationDao(),
            expenseCategoryDao = database.expenseCategoryDao(),
            devicePreferences = devicePreferences
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

    private suspend fun insertTestCategory() {
        database.expenseCategoryDao().insert(
            ExpenseCategoryEntity(
                id = categoryId,
                localId = "local-category-1",
                name = "Транспорт",
                isActive = true,
                createdAt = testInstant,
                syncedAt = null
            )
        )
    }

    // ==================== Expense Category Tests ====================

    @Test
    fun `createCategory persists category to database`() = runTest {
        val category = cashRepository.createCategory("Пальне")

        assertNotNull(category.id)
        assertEquals("Пальне", category.name)
        assertTrue(category.isActive)

        // Verify persisted
        val allCategories = cashRepository.getAllCategories().first()
        assertEquals(1, allCategories.size)
        assertEquals("Пальне", allCategories[0].name)
    }

    @Test
    fun `getActiveCategories excludes deactivated categories`() = runTest {
        // Create two categories
        val cat1 = cashRepository.createCategory("Транспорт")
        cashRepository.createCategory("Зарплата")

        // Deactivate first
        cashRepository.deactivateCategory(cat1.id)

        // Verify active excludes deactivated
        val active = cashRepository.getActiveCategories().first()
        assertEquals(1, active.size)
        assertEquals("Зарплата", active[0].name)
    }

    @Test
    fun `updateCategory changes category name`() = runTest {
        val category = cashRepository.createCategory("Тест")

        cashRepository.updateCategory(category.id, "Оновлена назва")

        val updated = cashRepository.getAllCategories().first().find { it.id == category.id }
        assertEquals("Оновлена назва", updated?.name)
    }

    // ==================== Cash Operation Tests ====================

    @Test
    fun `deposit increases balance`() = runTest {
        insertTestLocation()

        cashRepository.deposit(locationId, BigDecimal("5000.00"), "Початкова каса")

        val balance = cashRepository.getBalance(locationId).first()
        assertTrue(balance.compareTo(BigDecimal("5000.00")) == 0)
    }

    @Test
    fun `withdraw decreases balance`() = runTest {
        insertTestLocation()

        // Start with deposit
        cashRepository.deposit(locationId, BigDecimal("10000.00"), null)

        // Withdraw some
        cashRepository.withdraw(locationId, BigDecimal("3000.00"), "Інкасація")

        val balance = cashRepository.getBalance(locationId).first()
        // 10000 - 3000 = 7000
        assertTrue(balance.compareTo(BigDecimal("7000.00")) == 0)
    }

    @Test
    fun `payment with category decreases balance`() = runTest {
        insertTestLocation()
        insertTestCategory()

        // Start with deposit
        cashRepository.deposit(locationId, BigDecimal("5000.00"), null)

        // Make payment
        cashRepository.payment(locationId, BigDecimal("500.00"), categoryId, "Оплата за бензин")

        val balance = cashRepository.getBalance(locationId).first()
        // 5000 - 500 = 4500
        assertTrue(balance.compareTo(BigDecimal("4500.00")) == 0)
    }

    @Test
    fun `multiple operations calculate correct balance`() = runTest {
        insertTestLocation()
        insertTestCategory()

        // Sequence of operations
        cashRepository.deposit(locationId, BigDecimal("10000.00"), "Початок дня")
        cashRepository.payment(locationId, BigDecimal("200.00"), categoryId, "Бензин")
        cashRepository.deposit(locationId, BigDecimal("5000.00"), "Додаткова каса")
        cashRepository.withdraw(locationId, BigDecimal("1000.00"), "Інкасація")
        cashRepository.payment(locationId, BigDecimal("150.00"), null, "Обід")

        val balance = cashRepository.getBalance(locationId).first()
        // 10000 - 200 + 5000 - 1000 - 150 = 13650
        assertTrue(balance.compareTo(BigDecimal("13650.00")) == 0)
    }

    @Test
    fun `getTotalBalance sums all locations`() = runTest {
        insertTestLocation()

        // Create second location
        val location2Id = UUID.fromString("33333333-3333-3333-3333-333333333333")
        database.locationDao().insert(
            LocationEntity(
                id = location2Id,
                name = "Склад Київ",
                type = LocationType.MOBILE.name,
                createdAt = testInstant
            )
        )

        // Deposits to both locations
        cashRepository.deposit(locationId, BigDecimal("5000.00"), null)
        cashRepository.deposit(location2Id, BigDecimal("3000.00"), null)

        val totalBalance = cashRepository.getTotalBalance().first()
        // 5000 + 3000 = 8000
        assertTrue(totalBalance.compareTo(BigDecimal("8000.00")) == 0)
    }

    // ==================== Operations Retrieval Tests ====================

    @Test
    fun `getOperationsByLocation returns only location operations`() = runTest {
        insertTestLocation()

        // Create second location
        val location2Id = UUID.fromString("33333333-3333-3333-3333-333333333333")
        database.locationDao().insert(
            LocationEntity(
                id = location2Id,
                name = "Склад Київ",
                type = LocationType.MOBILE.name,
                createdAt = testInstant
            )
        )

        // Operations to both locations
        cashRepository.deposit(locationId, BigDecimal("1000.00"), "Loc1 deposit")
        cashRepository.deposit(location2Id, BigDecimal("2000.00"), "Loc2 deposit")

        val loc1Ops = cashRepository.getOperationsByLocation(locationId).first()
        assertEquals(1, loc1Ops.size)
        assertEquals("Loc1 deposit", loc1Ops[0].notes)
    }

    @Test
    fun `getRecentOperations respects limit`() = runTest {
        insertTestLocation()

        // Create 5 operations
        repeat(5) { i ->
            cashRepository.deposit(locationId, BigDecimal("${(i + 1) * 100}.00"), "Op $i")
        }

        val recent = cashRepository.getRecentOperations(locationId, limit = 3).first()
        assertEquals(3, recent.size)
    }

    @Test
    fun `getOperationsPaged returns correct page`() = runTest {
        insertTestLocation()

        // Create 10 operations
        repeat(10) { i ->
            cashRepository.deposit(locationId, BigDecimal("${(i + 1) * 100}.00"), "Op $i")
        }

        // Get first page
        val page1 = cashRepository.getOperationsPaged(limit = 3, offset = 0)
        assertEquals(3, page1.size)

        // Get second page
        val page2 = cashRepository.getOperationsPaged(limit = 3, offset = 3)
        assertEquals(3, page2.size)

        // Verify no overlap
        val page1Ids = page1.map { it.id }.toSet()
        val page2Ids = page2.map { it.id }.toSet()
        assertTrue(page1Ids.intersect(page2Ids).isEmpty())
    }

    @Test
    fun `getTotalOperationsCount returns correct count`() = runTest {
        insertTestLocation()

        assertEquals(0, cashRepository.getTotalOperationsCount())

        repeat(7) {
            cashRepository.deposit(locationId, BigDecimal("100.00"), null)
        }

        assertEquals(7, cashRepository.getTotalOperationsCount())
    }

    // ==================== Operation with Category Tests ====================

    @Test
    fun `payment enriches with category name`() = runTest {
        insertTestLocation()
        insertTestCategory()

        cashRepository.payment(locationId, BigDecimal("250.00"), categoryId, "Витрати на транспорт")

        val operations = cashRepository.getOperationsByLocation(locationId).first()
        assertEquals(1, operations.size)
        assertEquals("Транспорт", operations[0].categoryName)
        assertEquals(CashOperationType.PAYMENT, operations[0].type)
    }

    // ==================== Signed Amount Tests ====================

    @Test
    fun `signedAmount is positive for deposit`() = runTest {
        insertTestLocation()

        cashRepository.deposit(locationId, BigDecimal("1000.00"), null)

        val operations = cashRepository.getOperationsByLocation(locationId).first()
        assertTrue(operations[0].signedAmount.compareTo(BigDecimal("1000.00")) == 0)
    }

    @Test
    fun `signedAmount is negative for withdrawal`() = runTest {
        insertTestLocation()

        // First deposit to have balance
        cashRepository.deposit(locationId, BigDecimal("5000.00"), null)
        cashRepository.withdraw(locationId, BigDecimal("1000.00"), null)

        val operations = cashRepository.getOperationsByLocation(locationId).first()
        val withdrawal = operations.find { it.type == CashOperationType.WITHDRAWAL }!!
        assertTrue(withdrawal.signedAmount.compareTo(BigDecimal("-1000.00")) == 0)
    }

    @Test
    fun `signedAmount is negative for payment`() = runTest {
        insertTestLocation()

        // First deposit to have balance
        cashRepository.deposit(locationId, BigDecimal("5000.00"), null)
        cashRepository.payment(locationId, BigDecimal("500.00"), null, null)

        val operations = cashRepository.getOperationsByLocation(locationId).first()
        val payment = operations.find { it.type == CashOperationType.PAYMENT }!!
        assertTrue(payment.signedAmount.compareTo(BigDecimal("-500.00")) == 0)
    }

    // ==================== Global Operations Tests ====================

    @Test
    fun `getAllOperations returns operations from all locations`() = runTest {
        insertTestLocation()

        val location2Id = UUID.fromString("33333333-3333-3333-3333-333333333333")
        database.locationDao().insert(
            LocationEntity(
                id = location2Id,
                name = "Склад Київ",
                type = LocationType.MOBILE.name,
                createdAt = testInstant
            )
        )

        cashRepository.deposit(locationId, BigDecimal("1000.00"), "Loc1")
        cashRepository.deposit(location2Id, BigDecimal("2000.00"), "Loc2")

        val allOps = cashRepository.getAllOperations().first()
        assertEquals(2, allOps.size)
    }

    @Test
    fun `operations without location work correctly`() = runTest {
        // Null location (global operation)
        cashRepository.deposit(null, BigDecimal("5000.00"), "Глобальна каса")

        val totalBalance = cashRepository.getTotalBalance().first()
        assertTrue(totalBalance.compareTo(BigDecimal("5000.00")) == 0)

        val allOps = cashRepository.getAllOperations().first()
        assertEquals(1, allOps.size)
        assertNull(allOps[0].locationId)
    }
}
