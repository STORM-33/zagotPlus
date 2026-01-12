package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.dao.InventoryAggregateResult
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.sync.SyncManager
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
import java.util.UUID

class TransactionRepositoryImplTest {

    private lateinit var database: ZagotDatabase
    private lateinit var transactionDao: TransactionDao
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var syncManager: SyncManager
    private lateinit var repository: TransactionRepositoryImpl

    private val locationA = UUID.randomUUID()
    private val locationB = UUID.randomUUID()
    private val productApples = UUID.randomUUID()
    private val productOranges = UUID.randomUUID()

    @Before
    fun setup() {
        database = mockk(relaxed = true)
        transactionDao = mockk()
        devicePreferences = mockk()
        syncManager = mockk(relaxed = true)
        every { devicePreferences.getDeviceId() } returns "test-device-id"
        
        repository = TransactionRepositoryImpl(database, transactionDao, devicePreferences, syncManager)
    }

    @Test
    fun `getInventory returns empty list when no transactions`() = runTest {
        every { transactionDao.getInventoryAggregatedFlow() } returns flowOf(emptyList())

        val inventory = repository.getInventory().first()

        assertTrue(inventory.isEmpty())
    }

    @Test
    fun `getInventory returns positive weight for single purchase`() = runTest {
        val aggregateResult = InventoryAggregateResult(
            locationId = locationA.toString(),
            productId = productApples.toString(),
            totalWeightKg = "100.50"
        )
        every { transactionDao.getInventoryAggregatedFlow() } returns flowOf(listOf(aggregateResult))

        val inventory = repository.getInventory().first()

        assertEquals(1, inventory.size)
        assertEquals(locationA, inventory[0].locationId)
        assertEquals(productApples, inventory[0].productId)
        assertEquals(BigDecimal("100.50"), inventory[0].totalWeightKg)
    }

    @Test
    fun `getInventory calculates net weight for purchase and sale`() = runTest {
        // SQL aggregation already computes net weight (100 - 30 = 70)
        val aggregateResult = InventoryAggregateResult(
            locationId = locationA.toString(),
            productId = productApples.toString(),
            totalWeightKg = "70.00"
        )
        every { transactionDao.getInventoryAggregatedFlow() } returns flowOf(listOf(aggregateResult))

        val inventory = repository.getInventory().first()

        assertEquals(1, inventory.size)
        assertEquals(BigDecimal("70.00"), inventory[0].totalWeightKg)
    }

    @Test
    fun `getInventory groups by location and product`() = runTest {
        val applesAtA = InventoryAggregateResult(locationA.toString(), productApples.toString(), "50.00")
        val orangesAtA = InventoryAggregateResult(locationA.toString(), productOranges.toString(), "30.00")
        val applesAtB = InventoryAggregateResult(locationB.toString(), productApples.toString(), "25.00")
        every { transactionDao.getInventoryAggregatedFlow() } returns flowOf(listOf(applesAtA, orangesAtA, applesAtB))

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
        // After transfer: A has 80kg (100-20), B has 20kg
        val atA = InventoryAggregateResult(locationA.toString(), productApples.toString(), "80.00")
        val atB = InventoryAggregateResult(locationB.toString(), productApples.toString(), "20.00")
        every { transactionDao.getInventoryAggregatedFlow() } returns flowOf(listOf(atA, atB))

        val inventory = repository.getInventory().first()

        assertEquals(2, inventory.size)

        val inventoryAtA = inventory.find { it.locationId == locationA }
        val inventoryAtB = inventory.find { it.locationId == locationB }

        assertEquals(BigDecimal("80.00"), inventoryAtA?.totalWeightKg)
        assertEquals(BigDecimal("20.00"), inventoryAtB?.totalWeightKg)
    }

    @Test
    fun `getInventoryByLocation returns only transactions for specified location`() = runTest {
        val atLocationA = InventoryAggregateResult(locationA.toString(), productApples.toString(), "100.00")
        every { transactionDao.getInventoryByLocationAggregatedFlow(locationA) } returns flowOf(listOf(atLocationA))

        val inventory = repository.getInventoryByLocation(locationA).first()

        assertEquals(1, inventory.size)
        assertEquals(locationA, inventory[0].locationId)
        assertEquals(BigDecimal("100.00"), inventory[0].totalWeightKg)
    }

    @Test
    fun `getInventory sums multiple transactions of same product at same location`() = runTest {
        // SQL already aggregates: 50 + 30 + 20 = 100
        val aggregateResult = InventoryAggregateResult(locationA.toString(), productApples.toString(), "100.00")
        every { transactionDao.getInventoryAggregatedFlow() } returns flowOf(listOf(aggregateResult))

        val inventory = repository.getInventory().first()

        assertEquals(1, inventory.size)
        assertEquals(BigDecimal("100.00"), inventory[0].totalWeightKg)
    }
}
