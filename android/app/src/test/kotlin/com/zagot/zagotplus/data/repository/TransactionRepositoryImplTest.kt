package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.TransactionType
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

class TransactionRepositoryImplTest {

    private lateinit var transactionDao: TransactionDao
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var repository: TransactionRepositoryImpl

    private val locationA = UUID.randomUUID()
    private val locationB = UUID.randomUUID()
    private val productApples = UUID.randomUUID()
    private val productOranges = UUID.randomUUID()

    @Before
    fun setup() {
        transactionDao = mockk()
        devicePreferences = mockk()
        every { devicePreferences.getDeviceId() } returns "test-device-id"
        repository = TransactionRepositoryImpl(transactionDao, devicePreferences)
    }

    // Helper to create transaction entities
    private fun createTransaction(
        locationId: UUID?,
        productId: UUID?,
        type: TransactionType,
        weightKg: BigDecimal,
        transferLocationId: UUID? = null
    ) = TransactionEntity(
        id = UUID.randomUUID(),
        localId = UUID.randomUUID().toString(),
        locationId = locationId,
        type = type.toDbValue(),
        transferLocationId = transferLocationId,
        productId = productId,
        weightKg = weightKg,
        pricePerKg = BigDecimal("10.00"),
        totalAmount = weightKg.abs() * BigDecimal("10.00"),
        notes = null,
        deviceId = "test-device",
        createdAt = Instant.now(),
        syncedAt = null
    )

    @Test
    fun `getInventory returns empty list when no transactions`() = runTest {
        every { transactionDao.getAllFlow() } returns flowOf(emptyList())

        val inventory = repository.getInventory().first()

        assertTrue(inventory.isEmpty())
    }

    @Test
    fun `getInventory returns positive weight for single purchase`() = runTest {
        val purchase = createTransaction(
            locationId = locationA,
            productId = productApples,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("100.50")
        )
        every { transactionDao.getAllFlow() } returns flowOf(listOf(purchase))

        val inventory = repository.getInventory().first()

        assertEquals(1, inventory.size)
        assertEquals(locationA, inventory[0].locationId)
        assertEquals(productApples, inventory[0].productId)
        assertEquals(BigDecimal("100.50"), inventory[0].totalWeightKg)
    }

    @Test
    fun `getInventory calculates net weight for purchase and sale`() = runTest {
        val purchase = createTransaction(
            locationId = locationA,
            productId = productApples,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("100.00")
        )
        val sale = createTransaction(
            locationId = locationA,
            productId = productApples,
            type = TransactionType.SALE,
            weightKg = BigDecimal("-30.00") // Sales are stored as negative
        )
        every { transactionDao.getAllFlow() } returns flowOf(listOf(purchase, sale))

        val inventory = repository.getInventory().first()

        assertEquals(1, inventory.size)
        assertEquals(BigDecimal("70.00"), inventory[0].totalWeightKg)
    }

    @Test
    fun `getInventory groups by location and product`() = runTest {
        val applesAtA = createTransaction(
            locationId = locationA,
            productId = productApples,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("50.00")
        )
        val orangesAtA = createTransaction(
            locationId = locationA,
            productId = productOranges,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("30.00")
        )
        val applesAtB = createTransaction(
            locationId = locationB,
            productId = productApples,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("25.00")
        )
        every { transactionDao.getAllFlow() } returns flowOf(listOf(applesAtA, orangesAtA, applesAtB))

        val inventory = repository.getInventory().first()

        assertEquals(3, inventory.size)

        val applesA = inventory.find { it.locationId == locationA && it.productId == productApples }
        val orangesA = inventory.find { it.locationId == locationA && it.productId == productOranges }
        val applesB = inventory.find { it.locationId == locationB && it.productId == productApples }

        assertEquals(BigDecimal("50.00"), applesA?.totalWeightKg)
        assertEquals(BigDecimal("30.00"), orangesA?.totalWeightKg)
        assertEquals(BigDecimal("25.00"), applesB?.totalWeightKg)
    }

    @Test
    fun `getInventory handles transfer out and in correctly`() = runTest {
        // Transfer 20kg from A to B
        val transferOut = createTransaction(
            locationId = locationA,
            productId = productApples,
            type = TransactionType.TRANSFER_OUT,
            weightKg = BigDecimal("-20.00"), // Out is negative
            transferLocationId = locationB
        )
        val transferIn = createTransaction(
            locationId = locationB,
            productId = productApples,
            type = TransactionType.TRANSFER_IN,
            weightKg = BigDecimal("20.00"), // In is positive
            transferLocationId = locationA
        )
        // Start with 100kg at A
        val initialStock = createTransaction(
            locationId = locationA,
            productId = productApples,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("100.00")
        )
        every { transactionDao.getAllFlow() } returns flowOf(listOf(initialStock, transferOut, transferIn))

        val inventory = repository.getInventory().first()

        assertEquals(2, inventory.size)

        val atA = inventory.find { it.locationId == locationA }
        val atB = inventory.find { it.locationId == locationB }

        assertEquals(BigDecimal("80.00"), atA?.totalWeightKg) // 100 - 20
        assertEquals(BigDecimal("20.00"), atB?.totalWeightKg) // 0 + 20
    }

    @Test
    fun `getInventory filters out transactions with null locationId`() = runTest {
        val validTransaction = createTransaction(
            locationId = locationA,
            productId = productApples,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("50.00")
        )
        val nullLocationTransaction = createTransaction(
            locationId = null,
            productId = productApples,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("100.00")
        )
        every { transactionDao.getAllFlow() } returns flowOf(listOf(validTransaction, nullLocationTransaction))

        val inventory = repository.getInventory().first()

        assertEquals(1, inventory.size)
        assertEquals(BigDecimal("50.00"), inventory[0].totalWeightKg)
    }

    @Test
    fun `getInventory filters out transactions with null productId`() = runTest {
        val validTransaction = createTransaction(
            locationId = locationA,
            productId = productApples,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("50.00")
        )
        val nullProductTransaction = createTransaction(
            locationId = locationA,
            productId = null,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("100.00")
        )
        every { transactionDao.getAllFlow() } returns flowOf(listOf(validTransaction, nullProductTransaction))

        val inventory = repository.getInventory().first()

        assertEquals(1, inventory.size)
        assertEquals(BigDecimal("50.00"), inventory[0].totalWeightKg)
    }

    @Test
    fun `getInventoryByLocation returns only transactions for specified location`() = runTest {
        val atLocationA = createTransaction(
            locationId = locationA,
            productId = productApples,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("100.00")
        )
        every { transactionDao.getByLocationFlow(locationA) } returns flowOf(listOf(atLocationA))

        val inventory = repository.getInventoryByLocation(locationA).first()

        assertEquals(1, inventory.size)
        assertEquals(locationA, inventory[0].locationId)
        assertEquals(BigDecimal("100.00"), inventory[0].totalWeightKg)
    }

    @Test
    fun `getInventory sums multiple transactions of same product at same location`() = runTest {
        val purchase1 = createTransaction(
            locationId = locationA,
            productId = productApples,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("50.00")
        )
        val purchase2 = createTransaction(
            locationId = locationA,
            productId = productApples,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("30.00")
        )
        val purchase3 = createTransaction(
            locationId = locationA,
            productId = productApples,
            type = TransactionType.PURCHASE,
            weightKg = BigDecimal("20.00")
        )
        every { transactionDao.getAllFlow() } returns flowOf(listOf(purchase1, purchase2, purchase3))

        val inventory = repository.getInventory().first()

        assertEquals(1, inventory.size)
        assertEquals(BigDecimal("100.00"), inventory[0].totalWeightKg)
    }
}
