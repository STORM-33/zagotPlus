package com.zagot.zagotplus.ui.screens.purchase

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
class PurchaseViewModelTest {

    private lateinit var productRepository: ProductRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var viewModel: PurchaseViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testProduct = Product(
        id = UUID.randomUUID(),
        name = "Горіх білий",
        defaultBuyPrice = BigDecimal("45.00"),
        defaultSellPrice = BigDecimal("50.00"),
        isActive = true,
        createdAt = Instant.now()
    )

    private val testLocation = Location(
        id = UUID.randomUUID(),
        name = "Кіоск №1",
        type = LocationType.KIOSK,
        createdAt = Instant.now()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        productRepository = mockk()
        transactionRepository = mockk()
        locationRepository = mockk()

        every { productRepository.getActiveProducts() } returns flowOf(listOf(testProduct))
        every { locationRepository.getAllLocations() } returns flowOf(listOf(testLocation))
    }

    private fun createViewModel(): PurchaseViewModel {
        return PurchaseViewModel(productRepository, transactionRepository, locationRepository)
    }

    @Test
    fun `loads products and location on init`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.products.size)
        assertEquals(testProduct, state.products[0])
        assertEquals(testLocation, state.currentLocation)
    }

    @Test
    fun `selecting product sets default buy price`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)

        val state = viewModel.uiState.value
        assertEquals(testProduct, state.selectedProduct)
        assertEquals("45.00", state.pricePerKg)
    }

    @Test
    fun `total calculates correctly as weight times price`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setWeight("24.5")
        viewModel.setPricePerKg("45.00")

        val state = viewModel.uiState.value
        assertNotNull(state.total)
        assertEquals(0, BigDecimal("1102.50").compareTo(state.total))
    }

    @Test
    fun `total is null when weight is empty`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setPricePerKg("45.00")
        viewModel.setWeight("")

        assertNull(viewModel.uiState.value.total)
    }

    @Test
    fun `total is null when price is empty`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setWeight("24.5")
        viewModel.setPricePerKg("")

        assertNull(viewModel.uiState.value.total)
    }

    @Test
    fun `canSave is false when no product selected`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setWeight("24.5")
        viewModel.setPricePerKg("45.00")

        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `canSave is false when weight is zero`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setWeight("0")
        viewModel.setPricePerKg("45.00")

        assertFalse(viewModel.uiState.value.canSave)
    }

    @Test
    fun `canSave is true when all fields valid`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setWeight("24.5")
        viewModel.setPricePerKg("45.00")

        assertTrue(viewModel.uiState.value.canSave)
    }

    @Test
    fun `savePurchase creates transaction with correct type`() = runTest {
        val mockTransaction = Transaction(
            id = UUID.randomUUID(),
            localId = "test-local-id",
            locationId = testLocation.id,
            type = TransactionType.PURCHASE,
            transferLocationId = null,
            productId = testProduct.id,
            weightKg = BigDecimal("24.5"),
            pricePerKg = BigDecimal("45.00"),
            totalAmount = BigDecimal("1102.50"),
            notes = null,
            deviceId = null,
            createdAt = Instant.now(),
            syncedAt = null
        )

        coEvery {
            transactionRepository.createPurchase(any(), any(), any(), any(), any())
        } returns mockTransaction

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setWeight("24.5")
        viewModel.setPricePerKg("45.00")
        viewModel.savePurchase()
        advanceUntilIdle()

        coVerify {
            transactionRepository.createPurchase(
                locationId = testLocation.id,
                productId = testProduct.id,
                weightKg = BigDecimal("24.5"),
                pricePerKg = BigDecimal("45.00"),
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
    fun `savePurchase handles error`() = runTest {
        coEvery {
            transactionRepository.createPurchase(any(), any(), any(), any(), any())
        } throws RuntimeException("Database error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.setWeight("24.5")
        viewModel.setPricePerKg("45.00")
        viewModel.savePurchase()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.showSuccess)
        assertNotNull(state.error)
        assertEquals("Database error", state.error)
    }
}
