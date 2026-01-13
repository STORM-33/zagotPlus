package com.zagot.zagotplus.ui.screens.purchase

import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.preferences.ProductOrderPreferences
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.PurchaseBatchRepository
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
class PurchaseEntryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var productRepository: ProductRepository
    private lateinit var purchaseBatchRepository: PurchaseBatchRepository
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var productOrderPreferences: ProductOrderPreferences
    private lateinit var viewModel: PurchaseEntryViewModel

    private val testProduct = TestData.PRODUCT_WHITE_WALNUT
    private val testLocation = TestData.LOCATION_WAREHOUSE_1

    @Before
    fun setup() {
        productRepository = mockk()
        purchaseBatchRepository = mockk()
        devicePreferences = mockk()
        productOrderPreferences = mockk(relaxed = true)

        every { productRepository.getActiveProducts() } returns flowOf(listOf(testProduct))
        every { devicePreferences.getSelectedLocationId() } returns testLocation.id
        every { devicePreferences.getDeviceId() } returns "test-device"
        every { productOrderPreferences.getProductOrder() } returns emptyList()
        every { productOrderPreferences.applyOrder(any<List<Any>>(), any()) } answers { firstArg() }
    }

    private fun createViewModel(): PurchaseEntryViewModel {
        return PurchaseEntryViewModel(
            productRepository = productRepository,
            purchaseBatchRepository = purchaseBatchRepository,
            devicePreferences = devicePreferences,
            productOrderPreferences = productOrderPreferences
        )
    }

    // ==================== Initialization Tests ====================

    @Test
    fun `loads products on init`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.products.size)
        assertEquals(testProduct.name, state.products[0].name)
    }

    @Test
    fun `initial screen state is product grid`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(PurchaseEntryScreenState.PRODUCT_GRID, viewModel.uiState.value.screenState)
    }

    // ==================== Product Selection Tests ====================

    @Test
    fun `selectProduct sets product and navigates to weight entry`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)

        val state = viewModel.uiState.value
        assertEquals(testProduct, state.selectedProduct)
        assertEquals(testProduct.defaultBuyPrice?.toPlainString(), state.currentPrice)
        assertEquals(PurchaseEntryScreenState.WEIGHT_ENTRY, state.screenState)
    }

    @Test
    fun `selectProduct clears previous weight input`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("50")
        viewModel.backToGrid()
        viewModel.selectProduct(testProduct)

        assertEquals("", viewModel.uiState.value.currentWeight)
    }

    // ==================== Weight & Price Input Tests ====================

    @Test
    fun `onWeightChange updates current weight`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)

        viewModel.onWeightChange("100.5")

        assertEquals("100.5", viewModel.uiState.value.currentWeight)
    }

    @Test
    fun `onWeightChange rejects invalid input`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)

        viewModel.onWeightChange("abc")

        assertEquals("", viewModel.uiState.value.currentWeight)
    }

    @Test
    fun `onPriceChange updates current price`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)

        viewModel.onPriceChange("50.00")

        assertEquals("50.00", viewModel.uiState.value.currentPrice)
    }

    @Test
    fun `currentTotal calculates weight times price`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")

        val total = viewModel.uiState.value.currentTotal
        assertEquals(BigDecimal("4500.00"), total)
    }

    @Test
    fun `currentTotal is null with invalid inputs`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("")
        viewModel.onPriceChange("45")

        assertNull(viewModel.uiState.value.currentTotal)
    }

    // ==================== Position Management Tests ====================

    @Test
    fun `addPosition creates position and navigates to list`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")

        viewModel.addPosition()

        val state = viewModel.uiState.value
        assertEquals(1, state.positions.size)
        assertEquals(testProduct, state.positions[0].product)
        assertEquals(BigDecimal("100"), state.positions[0].weightKg)
        assertEquals(BigDecimal("45"), state.positions[0].pricePerKg)
        assertEquals(PurchaseEntryScreenState.POSITIONS_LIST, state.screenState)
    }

    @Test
    fun `addPosition clears current selection`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")

        viewModel.addPosition()

        val state = viewModel.uiState.value
        assertNull(state.selectedProduct)
        assertEquals("", state.currentWeight)
        assertEquals("", state.currentPrice)
    }

    @Test
    fun `canAddPosition is true with valid weight and price`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")

        assertTrue(viewModel.uiState.value.canAddPosition)
    }

    @Test
    fun `canAddPosition is false with zero weight`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("0")
        viewModel.onPriceChange("45")

        assertFalse(viewModel.uiState.value.canAddPosition)
    }

    @Test
    fun `canAddPosition is false with zero price`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("0")

        assertFalse(viewModel.uiState.value.canAddPosition)
    }

    @Test
    fun `removePosition removes position by id`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.addPosition()

        val positionId = viewModel.uiState.value.positions[0].id
        viewModel.removePosition(positionId)

        assertTrue(viewModel.uiState.value.positions.isEmpty())
    }

    @Test
    fun `removePosition returns to grid when all positions removed`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.addPosition()

        val positionId = viewModel.uiState.value.positions[0].id
        viewModel.removePosition(positionId)

        assertEquals(PurchaseEntryScreenState.PRODUCT_GRID, viewModel.uiState.value.screenState)
    }

    // ==================== Totals Tests ====================

    @Test
    fun `totalWeight sums all positions`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Add first position
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.addPosition()

        // Add second position
        viewModel.addAnotherProduct()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("50")
        viewModel.onPriceChange("45")
        viewModel.addPosition()

        assertEquals(BigDecimal("150"), viewModel.uiState.value.totalWeight)
    }

    @Test
    fun `totalAmount sums all position totals`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Add position: 100kg × 45 = 4500
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.addPosition()

        // Add position: 50kg × 50 = 2500
        viewModel.addAnotherProduct()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("50")
        viewModel.onPriceChange("50")
        viewModel.addPosition()

        assertEquals(BigDecimal("7000.00"), viewModel.uiState.value.totalAmount)
    }

    // ==================== Finalization Tests ====================

    @Test
    fun `finalize navigates to summary`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.addPosition()

        viewModel.finalize()

        assertEquals(PurchaseEntryScreenState.SUMMARY, viewModel.uiState.value.screenState)
    }

    @Test
    fun `finalize does nothing without positions`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.finalize()

        assertEquals(PurchaseEntryScreenState.PRODUCT_GRID, viewModel.uiState.value.screenState)
    }

    @Test
    fun `canFinalize is true when positions exist`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.addPosition()

        assertTrue(viewModel.uiState.value.canFinalize)
    }

    @Test
    fun `canFinalize is false without positions`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canFinalize)
    }

    // ==================== Save Tests ====================

    @Test
    fun `confirmSave creates batch with transactions and navigates back`() = runTest {
        coEvery { purchaseBatchRepository.createBatchWithTransactions(any(), any()) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.addPosition()
        viewModel.finalize()

        viewModel.confirmSave()
        advanceUntilIdle()

        coVerify { purchaseBatchRepository.createBatchWithTransactions(any(), any()) }
        assertTrue(viewModel.uiState.value.navigateBack)
    }

    @Test
    fun `confirmSave handles error`() = runTest {
        coEvery { 
            purchaseBatchRepository.createBatchWithTransactions(any(), any()) 
        } throws RuntimeException("Database error")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.addPosition()
        viewModel.finalize()

        viewModel.confirmSave()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertFalse(state.isSaving)
        assertEquals(PurchaseEntryScreenState.POSITIONS_LIST, state.screenState)
    }

    @Test
    fun `notes are included in batch`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.onNotesChange("Test notes")
        viewModel.addPosition()

        assertEquals("Test notes", viewModel.uiState.value.notes)
    }

    // ==================== Navigation Tests ====================

    @Test
    fun `backToGrid clears selection and returns to appropriate screen`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")

        viewModel.backToGrid()

        val state = viewModel.uiState.value
        assertNull(state.selectedProduct)
        assertEquals("", state.currentWeight)
        assertEquals("", state.currentPrice)
        assertEquals(PurchaseEntryScreenState.PRODUCT_GRID, state.screenState)
    }

    @Test
    fun `backToGrid returns to positions list if positions exist`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.addPosition()
        viewModel.addAnotherProduct()
        viewModel.selectProduct(testProduct)

        viewModel.backToGrid()

        assertEquals(PurchaseEntryScreenState.POSITIONS_LIST, viewModel.uiState.value.screenState)
    }

    @Test
    fun `addAnotherProduct navigates to product grid`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.addPosition()

        viewModel.addAnotherProduct()

        assertEquals(PurchaseEntryScreenState.PRODUCT_GRID, viewModel.uiState.value.screenState)
    }

    @Test
    fun `dismissSummary returns to positions list`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.addPosition()
        viewModel.finalize()

        viewModel.dismissSummary()

        assertEquals(PurchaseEntryScreenState.POSITIONS_LIST, viewModel.uiState.value.screenState)
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
        coEvery { 
            purchaseBatchRepository.createBatchWithTransactions(any(), any()) 
        } throws RuntimeException("Error")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("100")
        viewModel.onPriceChange("45")
        viewModel.addPosition()
        viewModel.finalize()
        viewModel.confirmSave()
        advanceUntilIdle()

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.error)
    }
}
