package com.zagot.zagotplus.ui.screens.transfer

import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import com.zagot.zagotplus.testutil.MainDispatcherRule
import com.zagot.zagotplus.testutil.TestData
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import java.math.BigDecimal

@OptIn(ExperimentalCoroutinesApi::class)
class TransferViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var viewModel: TransferViewModel

    private val testProduct = TestData.PRODUCT_WHITE_WALNUT
    private val testSourceLocation = TestData.LOCATION_WAREHOUSE_1
    private val testDestLocation = TestData.LOCATION_WAREHOUSE_2
    private val testInventoryItem = TestData.createInventoryItem(
        productId = testProduct.id,
        locationId = testSourceLocation.id,
        totalWeightKg = BigDecimal("100.00")
    )

    @Before
    fun setup() {
        transactionRepository = mockk()
        productRepository = mockk()
        locationRepository = mockk()
        devicePreferences = mockk()

        every { devicePreferences.getSelectedLocationId() } returns testSourceLocation.id
        coEvery { locationRepository.getLocationById(testSourceLocation.id) } returns testSourceLocation
        every { locationRepository.getAllLocations() } returns flowOf(listOf(testSourceLocation, testDestLocation))
        every { productRepository.getActiveProducts() } returns flowOf(listOf(testProduct))
        every { transactionRepository.getInventoryByLocation(testSourceLocation.id) } returns flowOf(listOf(testInventoryItem))
    }

    private fun createViewModel(): TransferViewModel {
        return TransferViewModel(
            transactionRepository = transactionRepository,
            productRepository = productRepository,
            locationRepository = locationRepository,
            devicePreferences = devicePreferences
        )
    }

    // ==================== Initialization Tests ====================

    @Test
    fun `loads source location and inventory on init`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(testSourceLocation, state.sourceLocation)
        assertEquals(1, state.inventoryItems.size)
        assertEquals(testProduct.id, state.inventoryItems[0].product.id)
    }

    @Test
    fun `filters out current location from destinations`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.availableDestinations.size)
        assertEquals(testDestLocation.id, state.availableDestinations[0].id)
        assertFalse(state.availableDestinations.any { it.id == testSourceLocation.id })
    }

    @Test
    fun `handles error during loading`() = runTest {
        coEvery { locationRepository.getLocationById(any()) } throws RuntimeException("Network error")
        
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
    }

    // ==================== Product Selection Tests ====================

    @Test
    fun `selectProduct navigates to weight entry`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val inventoryWithProduct = viewModel.uiState.value.inventoryItems[0]
        viewModel.selectProduct(inventoryWithProduct)

        val state = viewModel.uiState.value
        assertEquals(TransferScreenState.WEIGHT_ENTRY, state.screenState)
        assertEquals(testProduct, state.selectedProduct)
        assertEquals(testInventoryItem.totalWeightKg, state.selectedAvailableStock)
    }

    @Test
    fun `selectProduct calculates remaining stock after existing positions`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val inventoryWithProduct = viewModel.uiState.value.inventoryItems[0]
        viewModel.selectProduct(inventoryWithProduct)
        viewModel.onWeightChange("30")
        viewModel.addPosition()

        // Select same product again
        viewModel.addAnotherProduct()
        viewModel.selectProduct(inventoryWithProduct)

        val state = viewModel.uiState.value
        // Original: 100, minus 30 already added = 70 remaining
        assertEquals(BigDecimal("70.00"), state.selectedAvailableStock)
    }

    // ==================== Weight Input Tests ====================

    @Test
    fun `onWeightChange updates current weight`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.onWeightChange("50.5")

        assertEquals("50.5", viewModel.uiState.value.currentWeight)
    }

    @Test
    fun `onWeightChange rejects invalid input`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.onWeightChange("abc")

        assertEquals("", viewModel.uiState.value.currentWeight)
    }

    @Test
    fun `canAddPosition is true with valid weight within available stock`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.onWeightChange("50")

        assertTrue(viewModel.uiState.value.canAddPosition)
    }

    @Test
    fun `canAddPosition is false when weight exceeds available stock`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.onWeightChange("150") // More than 100 available

        assertFalse(viewModel.uiState.value.canAddPosition)
    }

    // ==================== Position Management Tests ====================

    @Test
    fun `addPosition creates position and navigates to list`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.onWeightChange("30")
        viewModel.addPosition()

        val state = viewModel.uiState.value
        assertEquals(1, state.positions.size)
        assertEquals(BigDecimal("30"), state.positions[0].weightKg)
        assertEquals(TransferScreenState.POSITIONS_LIST, state.screenState)
        assertNull(state.selectedProduct)
    }

    @Test
    fun `removePosition removes position and returns to grid if empty`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.onWeightChange("30")
        viewModel.addPosition()

        val positionId = viewModel.uiState.value.positions[0].id
        viewModel.removePosition(positionId)

        val state = viewModel.uiState.value
        assertTrue(state.positions.isEmpty())
        assertEquals(TransferScreenState.PRODUCT_GRID, state.screenState)
    }

    @Test
    fun `totalWeight calculates sum of all positions`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Add first position
        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.onWeightChange("30")
        viewModel.addPosition()

        // Add second position
        viewModel.addAnotherProduct()
        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.onWeightChange("20")
        viewModel.addPosition()

        assertEquals(BigDecimal("50"), viewModel.uiState.value.totalWeight)
    }

    // ==================== Destination & Confirmation Tests ====================

    @Test
    fun `proceedToDestination navigates to destination selection`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.onWeightChange("30")
        viewModel.addPosition()
        viewModel.proceedToDestination()

        assertEquals(TransferScreenState.DESTINATION, viewModel.uiState.value.screenState)
    }

    @Test
    fun `selectDestination navigates to summary`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.onWeightChange("30")
        viewModel.addPosition()
        viewModel.proceedToDestination()
        viewModel.selectDestination(testDestLocation)

        val state = viewModel.uiState.value
        assertEquals(testDestLocation, state.destinationLocation)
        assertEquals(TransferScreenState.SUMMARY, state.screenState)
    }

    @Test
    fun `confirmSave creates transfers and navigates back`() = runTest {
        val mockTransferPair = Pair(
            TestData.createTransferTransaction(),
            TestData.createTransferTransaction()
        )
        coEvery { 
            transactionRepository.createTransfer(any(), any(), any(), any()) 
        } returns mockTransferPair

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.onWeightChange("30")
        viewModel.addPosition()
        viewModel.proceedToDestination()
        viewModel.selectDestination(testDestLocation)
        viewModel.confirmSave()
        advanceUntilIdle()

        coVerify { 
            transactionRepository.createTransfer(
                fromLocationId = testSourceLocation.id,
                toLocationId = testDestLocation.id,
                productId = testProduct.id,
                weightKg = BigDecimal("30")
            )
        }
        assertTrue(viewModel.uiState.value.navigateBack)
    }

    @Test
    fun `confirmSave handles error`() = runTest {
        coEvery { 
            transactionRepository.createTransfer(any(), any(), any(), any()) 
        } throws RuntimeException("Save failed")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.onWeightChange("30")
        viewModel.addPosition()
        viewModel.proceedToDestination()
        viewModel.selectDestination(testDestLocation)
        viewModel.confirmSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertFalse(state.isSaving)
        assertEquals(TransferScreenState.POSITIONS_LIST, state.screenState)
    }

    // ==================== Navigation Tests ====================

    @Test
    fun `backToGrid clears selection`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(viewModel.uiState.value.inventoryItems[0])
        viewModel.backToGrid()

        val state = viewModel.uiState.value
        assertNull(state.selectedProduct)
        assertEquals("", state.currentWeight)
        assertEquals(TransferScreenState.PRODUCT_GRID, state.screenState)
    }

    @Test
    fun `cancel sets navigateBack`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.cancel()

        assertTrue(viewModel.uiState.value.navigateBack)
    }

    @Test
    fun `onNavigationHandled clears navigateBack`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.cancel()
        viewModel.onNavigationHandled()

        assertFalse(viewModel.uiState.value.navigateBack)
    }

    @Test
    fun `dismissError clears error`() = runTest {
        coEvery { locationRepository.getLocationById(any()) } throws RuntimeException("Error")
        
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.error)
    }
}
