package com.zagot.zagotplus.ui.screens.sale

import androidx.lifecycle.SavedStateHandle
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.preferences.ProductOrderPreferences
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.SaleBatchRepository
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
class SaleEntryViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var productRepository: ProductRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var saleBatchRepository: SaleBatchRepository
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var productOrderPreferences: ProductOrderPreferences
    private lateinit var viewModel: SaleEntryViewModel

    private val testProduct = TestData.PRODUCT_WHITE_WALNUT
    private val testLocation = TestData.LOCATION_WAREHOUSE_1
    private val testInventoryItem = TestData.createInventoryItem(
        productId = testProduct.id,
        locationId = testLocation.id,
        totalWeightKg = BigDecimal("500.00")
    )

    @Before
    fun setup() {
        productRepository = mockk()
        transactionRepository = mockk()
        saleBatchRepository = mockk()
        devicePreferences = mockk()
        productOrderPreferences = mockk(relaxed = true)

        every { productRepository.getActiveProducts() } returns flowOf(listOf(testProduct))
        every { devicePreferences.getSelectedLocationId() } returns testLocation.id
        every { devicePreferences.getDeviceId() } returns "test-device"
        every { transactionRepository.getInventoryByLocation(testLocation.id) } returns flowOf(listOf(testInventoryItem))
        every { productOrderPreferences.getProductOrder() } returns emptyList()
        every { productOrderPreferences.applyOrder(any<List<Any>>(), any()) } answers { firstArg() }
        coEvery { saleBatchRepository.createBatchWithTransactions(any(), any()) } returns Unit
    }

    private fun createViewModel(): SaleEntryViewModel {
        return SaleEntryViewModel(
            productRepository = productRepository,
            transactionRepository = transactionRepository,
            saleBatchRepository = saleBatchRepository,
            devicePreferences = devicePreferences,
            productOrderPreferences = productOrderPreferences,
            savedStateHandle = SavedStateHandle()
        )
    }

    // ==================== Initialization Tests ====================

    @Test
    fun `loads products and inventory on init`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.products.size)
        assertEquals(testProduct.name, state.products[0].name)
        assertEquals(1, state.inventory.size)
    }

    @Test
    fun `handles error during loading`() = runTest {
        every { productRepository.getActiveProducts() } throws RuntimeException("Load error")
        
        viewModel = createViewModel()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
    }

    // ==================== Product Selection Tests ====================

    @Test
    fun `selectProduct sets product and navigates to weighing`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)

        val state = viewModel.uiState.value
        assertEquals(testProduct, state.selectedProduct)
        assertEquals(testProduct.defaultSellPrice?.toPlainString(), state.pricePerKg)
        assertEquals(SaleEntryScreenState.WEIGHING, state.screenState)
    }

    @Test
    fun `selectProduct loads available weight from inventory`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectProduct(testProduct)

        assertEquals(testInventoryItem.totalWeightKg, viewModel.uiState.value.availableWeight)
    }

    // ==================== Weighing Input Tests ====================

    @Test
    fun `onWeightChange updates current weight`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)

        viewModel.onWeightChange("25.5")

        assertEquals("25.5", viewModel.uiState.value.currentWeight)
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
    fun `onTareCountChange updates tare count`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)

        viewModel.onTareCountChange("5")

        assertEquals("5", viewModel.uiState.value.currentTareCount)
    }

    @Test
    fun `onPriceChange updates price per kg`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)

        viewModel.onPriceChange("60.00")

        assertEquals("60.00", viewModel.uiState.value.pricePerKg)
    }

    // ==================== Batch Management Tests ====================

    @Test
    fun `addBatch creates weighing batch`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30")
        viewModel.onTareCountChange("3")

        viewModel.addBatch()

        val state = viewModel.uiState.value
        assertEquals(1, state.currentBatches.size)
        assertEquals(BigDecimal("30"), state.currentBatches[0].grossWeightKg)
        assertEquals(3, state.currentBatches[0].tareCount)
        assertEquals("", state.currentWeight) // Reset after adding
        assertEquals("", state.currentTareCount) // Reset to default
    }

    @Test
    fun `removeBatch removes batch by id`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30")
        viewModel.addBatch()

        val batchId = viewModel.uiState.value.currentBatches[0].id
        viewModel.removeBatch(batchId)

        assertTrue(viewModel.uiState.value.currentBatches.isEmpty())
    }

    @Test
    fun `canAddBatch is true with valid weight and tare count`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30")
        viewModel.onTareCountChange("3")

        assertTrue(viewModel.uiState.value.canAddBatch)
    }

    @Test
    fun `canAddBatch is false with zero weight`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("0")

        assertFalse(viewModel.uiState.value.canAddBatch)
    }

    // ==================== Tare Calculation Tests ====================

    @Test
    fun `currentNetWeight calculates gross minus tare`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        
        // Add batch: 30kg gross, 3 sacks × 0.1kg tare = 29.7kg net
        viewModel.onWeightChange("30")
        viewModel.onTareCountChange("3")
        viewModel.addBatch()

        // Tare per unit default is 0.1
        val expectedNet = BigDecimal("30").subtract(BigDecimal("0.1").multiply(BigDecimal("3")))
        assertEquals(expectedNet, viewModel.uiState.value.currentNetWeight)
    }

    @Test
    fun `onTareWeightPerUnitChange updates tare weight`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30")
        viewModel.addBatch()
        viewModel.proceedToReview()

        viewModel.onTareWeightPerUnitChange("0.2")

        assertEquals("0.2", viewModel.uiState.value.tareWeightPerUnit)
    }

    // ==================== Position Management Tests ====================

    @Test
    fun `addPositionAndContinue creates position with calculated values`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30")
        viewModel.onTareCountChange("3")
        viewModel.addBatch()
        viewModel.proceedToReview()
        viewModel.onPriceChange("55.00")

        viewModel.addPositionAndContinue()

        val state = viewModel.uiState.value
        assertEquals(1, state.positions.size)
        assertEquals(testProduct, state.positions[0].product)
        assertEquals(SaleEntryScreenState.POSITIONS_LIST, state.screenState)
    }

    @Test
    fun `canAddPosition requires batches and valid price`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30")
        viewModel.addBatch()
        viewModel.proceedToReview()

        viewModel.onPriceChange("55.00")
        assertTrue(viewModel.uiState.value.canAddPosition)

        viewModel.onPriceChange("0")
        assertFalse(viewModel.uiState.value.canAddPosition)
    }

    @Test
    fun `removePosition removes position and returns to grid if empty`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30")
        viewModel.addBatch()
        viewModel.proceedToReview()
        viewModel.onPriceChange("55.00")
        viewModel.addPositionAndContinue()

        val positionId = viewModel.uiState.value.positions[0].id
        viewModel.removePosition(positionId)

        val state = viewModel.uiState.value
        assertTrue(state.positions.isEmpty())
        assertEquals(SaleEntryScreenState.PRODUCT_GRID, state.screenState)
    }

    // ==================== Totals Tests ====================

    @Test
    fun `totalWeight sums all positions net weights`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Add first position
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30")
        viewModel.addBatch()
        viewModel.proceedToReview()
        viewModel.onPriceChange("55")
        viewModel.addPositionAndContinue()

        // Add second position
        viewModel.addAnotherProduct()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("20")
        viewModel.addBatch()
        viewModel.proceedToReview()
        viewModel.onPriceChange("55")
        viewModel.addPositionAndContinue()

        // Total should be sum of net weights (gross - tare)
        assertTrue(viewModel.uiState.value.totalWeight > BigDecimal.ZERO)
    }

    // ==================== Finalization Tests ====================

    @Test
    fun `finalize saves and navigates to summary when positions exist`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30")
        viewModel.addBatch()
        viewModel.proceedToReview()
        viewModel.onPriceChange("55.00")
        viewModel.addPositionAndContinue()

        viewModel.finalize()
        advanceUntilIdle()

        assertEquals(SaleEntryScreenState.SUMMARY, viewModel.uiState.value.screenState)
    }

    @Test
    fun `finalize does nothing when no positions`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.finalize()
        advanceUntilIdle()

        assertEquals(SaleEntryScreenState.PRODUCT_GRID, viewModel.uiState.value.screenState)
    }

    @Test
    fun `finalize saves batch immediately and shows summary`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30")
        viewModel.addBatch()
        viewModel.proceedToReview()
        viewModel.onPriceChange("55.00")
        viewModel.addPositionAndContinue()
        viewModel.finalize()
        advanceUntilIdle()

        coVerify { saleBatchRepository.createBatchWithTransactions(any(), any()) }
        assertEquals(SaleEntryScreenState.SUMMARY, viewModel.uiState.value.screenState)
    }

    @Test
    fun `exitFromSummary navigates back`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30")
        viewModel.addBatch()
        viewModel.proceedToReview()
        viewModel.onPriceChange("55.00")
        viewModel.addPositionAndContinue()
        viewModel.finalize()
        advanceUntilIdle()

        viewModel.exitFromSummary()

        assertTrue(viewModel.uiState.value.navigateBack)
    }

    @Test
    fun `finalize handles error and stays in positions list`() = runTest {
        coEvery { saleBatchRepository.createBatchWithTransactions(any(), any()) } throws RuntimeException("Save failed")

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30")
        viewModel.addBatch()
        viewModel.proceedToReview()
        viewModel.onPriceChange("55.00")
        viewModel.addPositionAndContinue()
        viewModel.finalize()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.error)
        assertFalse(state.isSaving)
        assertEquals(SaleEntryScreenState.POSITIONS_LIST, state.screenState)
    }

    // ==================== Navigation Tests ====================

    @Test
    fun `backToGrid clears current product state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)

        viewModel.backToGrid()

        val state = viewModel.uiState.value
        assertNull(state.selectedProduct)
        assertTrue(state.currentBatches.isEmpty())
    }

    @Test
    fun `cancel sets navigateBack`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.cancel()

        assertTrue(viewModel.uiState.value.navigateBack)
    }

    @Test
    fun `dismissError clears error`() = runTest {
        every { productRepository.getActiveProducts() } throws RuntimeException("Error")
        
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.error)
    }

    // ==================== Inventory Warning Tests ====================

    @Test
    fun `showInventoryWarning true when net weight exceeds available`() = runTest {
        val smallInventory = TestData.createInventoryItem(
            productId = testProduct.id,
            totalWeightKg = BigDecimal("10.00")
        )
        every { transactionRepository.getInventoryByLocation(any()) } returns flowOf(listOf(smallInventory))

        viewModel = createViewModel()
        advanceUntilIdle()
        viewModel.selectProduct(testProduct)
        viewModel.onWeightChange("30") // More than 10 available
        viewModel.addBatch()

        assertTrue(viewModel.uiState.value.showInventoryWarning)
    }

    // ==================== Editing/Correction Mode Tests ====================

    @Test
    fun `loadBatchForEditing fetches products from repository when uiState products not loaded`() = runTest {
        val batchId = java.util.UUID.randomUUID()
        val existingBatch = TestData.createSaleBatch(id = batchId, notes = "Test sale notes")
        val existingTransactions = listOf(
            TestData.createSaleTransaction(
                id = java.util.UUID.randomUUID(),
                productId = testProduct.id,
                weightKg = BigDecimal("-30.00"),
                pricePerKg = BigDecimal("55.00")
            ).copy(saleBatchId = batchId)
        )

        coEvery { saleBatchRepository.getById(batchId) } returns existingBatch
        coEvery { saleBatchRepository.getTransactionsForBatch(batchId) } returns existingTransactions

        // Create viewModel but don't wait for init to complete
        viewModel = createViewModel()
        
        // Immediately call loadBatchForEditing before products are loaded
        viewModel.loadBatchForEditing(batchId.toString())
        advanceUntilIdle()

        // Verify that loadBatchForEditing fetched products directly from repository
        val state = viewModel.uiState.value
        assertEquals(batchId, state.editingBatchId)
        assertEquals("Test sale notes", state.notes)
        assertEquals(1, state.positions.size)
        assertEquals(testProduct, state.positions[0].product)
        assertEquals(BigDecimal("30.00"), state.positions[0].netWeight)
        assertEquals(BigDecimal("55.00"), state.positions[0].pricePerKg)
    }
}
