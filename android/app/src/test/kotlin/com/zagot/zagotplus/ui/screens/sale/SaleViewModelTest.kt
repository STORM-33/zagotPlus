package com.zagot.zagotplus.ui.screens.sale

import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class SaleViewModelTest {

    private lateinit var productRepository: ProductRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var viewModel: SaleViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testProductId = UUID.randomUUID()
    private val testLocationId = UUID.randomUUID()

    private val testProduct = Product(
        id = testProductId,
        name = "Горіх білий",
        defaultBuyPrice = BigDecimal("45.00"),
        defaultSellPrice = BigDecimal("55.00"),
        isActive = true,
        createdAt = Instant.now()
    )

    private val testLocation = Location(
        id = testLocationId,
        name = "Кіоск №1",
        type = LocationType.KIOSK,
        createdAt = Instant.now()
    )

    private val testInventory = InventoryItem(
        locationId = testLocationId,
        productId = testProductId,
        totalWeightKg = BigDecimal("50.00")
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        productRepository = mockk()
        transactionRepository = mockk()
        locationRepository = mockk()

        every { productRepository.getActiveProducts() } returns flowOf(listOf(testProduct))
        every { locationRepository.getAllLocations() } returns flowOf(listOf(testLocation))
        every { transactionRepository.getInventoryByLocation(testLocationId) } returns flowOf(listOf(testInventory))
    }

    private fun createViewModel(): SaleViewModel {
        return SaleViewModel(productRepository, transactionRepository, locationRepository)
    }

    @Test
    fun `loads products, location and inventory on init`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.products.size)
        assertEquals(testProduct, state.products[0])
        assertEquals(testLocation, state.currentLocation)
        assertEquals(1, state.inventory.size)
    }

    @Test
    fun `selecting product sets default sell price and available weight`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)

        val state = viewModel.uiState.value
        assertEquals(testProduct, state.selectedProduct)
        assertEquals("55.00", state.pricePerKg)
        assertEquals(0, BigDecimal("50.00").compareTo(state.availableWeight))
    }

    @Test
    fun `total calculates correctly as weight times price`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setWeight("30.0")
        viewModel.setPricePerKg("55.00")

        val state = viewModel.uiState.value
        assertNotNull(state.total)
        assertEquals(0, BigDecimal("1650.00").compareTo(state.total))
    }

    @Test
    fun `showInventoryWarning is true when weight exceeds available`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setWeight("60.0")

        assertTrue(viewModel.uiState.value.showInventoryWarning)
    }

    @Test
    fun `showInventoryWarning is false when weight is within available`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setWeight("30.0")

        assertFalse(viewModel.uiState.value.showInventoryWarning)
    }

    @Test
    fun `canSave is true when weight exceeds inventory - sale still allowed`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setWeight("60.0")
        viewModel.setPricePerKg("55.00")

        val state = viewModel.uiState.value
        assertTrue(state.showInventoryWarning)
        assertTrue(state.canSave)
    }

    @Test
    fun `canSave is false when no product selected`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setWeight("30.0")
        viewModel.setPricePerKg("55.00")

        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `saveSale creates transaction with type sale`() = runTest {
        val mockTransaction = Transaction(
            id = UUID.randomUUID(),
            localId = "test-local-id",
            locationId = testLocationId,
            type = TransactionType.SALE,
            transferLocationId = null,
            productId = testProductId,
            weightKg = BigDecimal("30.0"),
            pricePerKg = BigDecimal("55.00"),
            totalAmount = BigDecimal("1650.00"),
            notes = null,
            deviceId = null,
            createdAt = Instant.now(),
            syncedAt = null
        )

        coEvery {
            transactionRepository.createSale(any(), any(), any(), any(), any())
        } returns mockTransaction

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setWeight("30.0")
        viewModel.setPricePerKg("55.00")
        viewModel.saveSale()
        advanceUntilIdle()

        coVerify {
            transactionRepository.createSale(
                locationId = testLocationId,
                productId = testProductId,
                weightKg = BigDecimal("30.0"),
                pricePerKg = BigDecimal("55.00"),
                notes = null
            )
        }

        val state = viewModel.uiState.value
        assertTrue(state.showSuccess)
        assertEquals("", state.weight)
        assertEquals("", state.pricePerKg)
        assertNull(state.selectedProduct)
    }

    @Test
    fun `saveSale handles error`() = runTest {
        coEvery {
            transactionRepository.createSale(any(), any(), any(), any(), any())
        } throws RuntimeException("Database error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setWeight("30.0")
        viewModel.setPricePerKg("55.00")
        viewModel.saveSale()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showSuccess)
        assertNotNull(state.error)
        assertEquals("Database error", state.error)
    }
}
