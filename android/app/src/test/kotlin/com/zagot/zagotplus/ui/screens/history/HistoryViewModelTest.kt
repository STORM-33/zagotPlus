package com.zagot.zagotplus.ui.screens.history

import com.zagot.zagotplus.domain.model.DateRangePreset
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.PurchaseBatchRepository
import com.zagot.zagotplus.domain.repository.SaleBatchRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModelTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var purchaseBatchRepository: PurchaseBatchRepository
    private lateinit var saleBatchRepository: SaleBatchRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var viewModel: HistoryViewModel
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private val purchaseBatchCountFlow = MutableStateFlow(2)
    private val saleBatchCountFlow = MutableStateFlow(0)

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
        name = "Кіоск 1",
        type = LocationType.KIOSK,
        createdAt = Instant.now()
    )

    private val testBatch = PurchaseBatch(
        id = UUID.randomUUID(),
        localId = "test-batch-local-id",
        locationId = testLocation.id,
        deviceId = "test-device",
        totalWeightKg = BigDecimal("10.50"),
        totalAmount = BigDecimal("472.50"),
        itemCount = 1,
        notes = null,
        createdAt = Instant.now(),
        syncedAt = null
    )

    private val testBatch2 = testBatch.copy(
        id = UUID.randomUUID(),
        localId = "test-batch-local-id-2",
        totalWeightKg = BigDecimal("20.00"),
        totalAmount = BigDecimal("900.00")
    )

    private val testTransaction = Transaction(
        id = UUID.randomUUID(),
        localId = "test-local-id",
        locationId = testLocation.id,
        type = TransactionType.PURCHASE,
        transferLocationId = null,
        productId = testProduct.id,
        weightKg = BigDecimal("10.50"),
        pricePerKg = BigDecimal("45.00"),
        totalAmount = BigDecimal("472.50"),
        notes = null,
        deviceId = "test-device",
        createdAt = Instant.now(),
        syncedAt = null
    )

    private val saleTransaction = testTransaction.copy(
        id = UUID.randomUUID(),
        type = TransactionType.SALE,
        pricePerKg = BigDecimal("50.00"),
        totalAmount = BigDecimal("525.00")
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        transactionRepository = mockk()
        purchaseBatchRepository = mockk()
        saleBatchRepository = mockk()
        productRepository = mockk()
        locationRepository = mockk()

        every { productRepository.getActiveProducts() } returns flowOf(listOf(testProduct))
        every { locationRepository.getAllLocations() } returns flowOf(listOf(testLocation))
        every { purchaseBatchRepository.observeTotalBatchCount() } returns purchaseBatchCountFlow
        every { saleBatchRepository.observeTotalBatchCount() } returns saleBatchCountFlow
        coEvery { purchaseBatchRepository.getTotalBatchCount() } returns 2
        coEvery { saleBatchRepository.getTotalBatchCount() } returns 0
        coEvery { purchaseBatchRepository.getAllBatchesPaginated(any(), any()) } returns listOf(testBatch, testBatch2)
        coEvery { saleBatchRepository.getAllBatchesPaginated(any(), any()) } returns emptyList()
        // Return empty list for unbatched transactions by default to avoid virtual batches
        coEvery { transactionRepository.getFilteredTransactions(any(), any(), any()) } returns emptyList()
    }

    @After
    fun tearDown() {
        // Cancel any running coroutines in the test scope (including ViewModel's viewModelScope)
        testScope.cancel()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HistoryViewModel {
        return HistoryViewModel(transactionRepository, purchaseBatchRepository, saleBatchRepository, productRepository, locationRepository)
    }

    @Test
    fun `initial state loads all batches`() = testScope.runTest {
        viewModel = createViewModel()
        

        val state = viewModel.uiState.value
        assertEquals(2, state.batches.size)
        assertEquals(2, state.totalCount)
        assertFalse(state.isLoading)
        assertFalse(state.hasActiveFilters)
    }

    @Test
    fun `batches are mapped to display items correctly`() = testScope.runTest {
        viewModel = createViewModel()
        

        val state = viewModel.uiState.value
        val firstItem = state.batches.first()
        assertTrue(firstItem is HistoryBatchDisplayItem.RealBatch)
        assertEquals(testLocation.name, firstItem.locationName)
        assertEquals(testBatch.totalWeightKg, firstItem.totalWeightKg)
        assertEquals(BatchType.PURCHASE, firstItem.batchType)
    }

    @Test
    fun `toggleTypeFilter adds type to filter`() = testScope.runTest {
        coEvery { purchaseBatchRepository.getAllBatchesPaginated(any(), any()) } returns listOf(testBatch)
        coEvery { purchaseBatchRepository.getTotalBatchCount() } returns 1

        viewModel = createViewModel()
        

        viewModel.toggleTypeFilter(BatchType.PURCHASE)
        

        val state = viewModel.uiState.value
        assertTrue(state.selectedTypes.contains(BatchType.PURCHASE))
        assertTrue(state.hasActiveFilters)
    }

    @Test
    fun `toggleTypeFilter removes type when already selected`() = testScope.runTest {
        viewModel = createViewModel()
        

        viewModel.toggleTypeFilter(BatchType.PURCHASE)
        
        assertTrue(viewModel.uiState.value.selectedTypes.contains(BatchType.PURCHASE))

        viewModel.toggleTypeFilter(BatchType.PURCHASE)
        
        assertFalse(viewModel.uiState.value.selectedTypes.contains(BatchType.PURCHASE))
    }

    @Test
    fun `setDateRangePreset updates filter`() = testScope.runTest {
        viewModel = createViewModel()
        

        viewModel.setDateRangePreset(DateRangePreset.TODAY)
        

        val state = viewModel.uiState.value
        assertEquals(DateRangePreset.TODAY, state.dateRangePreset)
        assertTrue(state.hasActiveFilters)
    }

    @Test
    fun `setLocationFilter filters by location`() = testScope.runTest {
        viewModel = createViewModel()
        

        viewModel.setLocationFilter(testLocation.id)
        

        val state = viewModel.uiState.value
        assertEquals(testLocation.id, state.selectedLocationId)
        assertTrue(state.hasActiveFilters)
    }

    @Test
    fun `setSearchQuery filters by product name`() = testScope.runTest {
        viewModel = createViewModel()
        

        viewModel.setSearchQuery("Горіх")
        testDispatcher.scheduler.advanceTimeBy(301) // Advance past debounce delay

        val state = viewModel.uiState.value
        assertEquals("Горіх", state.searchQuery)
        assertTrue(state.hasActiveFilters)
    }

    @Test
    fun `clearFilters resets all filters`() = testScope.runTest {
        viewModel = createViewModel()
        

        // Set some filters
        viewModel.toggleTypeFilter(BatchType.PURCHASE)
        viewModel.setLocationFilter(testLocation.id)
        viewModel.setSearchQuery("test")
        
        assertTrue(viewModel.uiState.value.hasActiveFilters)

        // Clear all
        viewModel.clearFilters()
        

        val state = viewModel.uiState.value
        assertFalse(state.hasActiveFilters)
        assertTrue(state.selectedTypes.isEmpty())
        assertNull(state.selectedLocationId)
        assertEquals("", state.searchQuery)
        assertEquals(DateRangePreset.ALL, state.dateRangePreset)
    }

    @Test
    fun `dismissError clears error`() = testScope.runTest {
        coEvery { transactionRepository.getFilteredTransactions(any(), any(), any()) } throws RuntimeException("Test error")
        coEvery { transactionRepository.getFilteredTransactionCount(any()) } throws RuntimeException("Test error")

        viewModel = createViewModel()
        

        assertNotNull(viewModel.uiState.value.error)

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `loadMoreBatches appends to existing list when hasMorePages is true`() = testScope.runTest {
        // Create enough batches to simulate pagination
        val batches = (1..50).map { 
            testBatch.copy(id = UUID.randomUUID(), localId = "batch-$it")
        }
        val additionalBatches = listOf(testBatch.copy(id = UUID.randomUUID(), localId = "batch-51"))
        
        var callCount = 0
        coEvery { purchaseBatchRepository.getAllBatchesPaginated(any(), any()) } answers {
            callCount++
            if (callCount == 1) batches else additionalBatches
        }
        coEvery { purchaseBatchRepository.getTotalBatchCount() } returns 51

        viewModel = createViewModel()
        
        assertEquals(50, viewModel.uiState.value.batches.size)
        assertTrue(viewModel.uiState.value.hasMorePages)

        viewModel.loadMoreBatches()
        

        assertEquals(51, viewModel.uiState.value.batches.size)
    }

    @Test
    fun `voidBatch voids purchase batch and refreshes list`() = testScope.runTest {
        val batchToVoid = testBatch.id
        coEvery { purchaseBatchRepository.markVoided(batchToVoid) } returns Unit
        
        // After voiding, the batch is still in the list but marked as voided
        viewModel = createViewModel()
        assertEquals(2, viewModel.uiState.value.batches.size)

        viewModel.voidBatch(batchToVoid, BatchType.PURCHASE)

        io.mockk.coVerify { purchaseBatchRepository.markVoided(batchToVoid) }
    }

    @Test
    fun `voidBatch voids sale batch and refreshes list`() = testScope.runTest {
        val saleBatchId = UUID.randomUUID()
        val testSaleBatch = com.zagot.zagotplus.domain.model.SaleBatch(
            id = saleBatchId,
            localId = "sale-batch-1",
            locationId = testLocation.id,
            deviceId = "test-device",
            totalWeightKg = BigDecimal("5.00"),
            totalAmount = BigDecimal("250.00"),
            itemCount = 1,
            notes = null,
            createdAt = Instant.now(),
            syncedAt = null
        )
        
        coEvery { saleBatchRepository.markVoided(saleBatchId) } returns Unit
        coEvery { saleBatchRepository.getAllBatchesPaginated(any(), any()) } returns listOf(testSaleBatch)
        coEvery { saleBatchRepository.getTotalBatchCount() } returns 1
        coEvery { purchaseBatchRepository.getAllBatchesPaginated(any(), any()) } returns emptyList()
        coEvery { purchaseBatchRepository.getTotalBatchCount() } returns 0

        viewModel = createViewModel()
        assertEquals(1, viewModel.uiState.value.batches.size)

        viewModel.voidBatch(saleBatchId, BatchType.SALE)

        io.mockk.coVerify { saleBatchRepository.markVoided(saleBatchId) }
    }

    @Test
    fun `voidBatch sets error on failure`() = testScope.runTest {
        val batchToVoid = testBatch.id
        coEvery { purchaseBatchRepository.markVoided(batchToVoid) } throws RuntimeException("Void failed")

        viewModel = createViewModel()
        assertNull(viewModel.uiState.value.error)

        viewModel.voidBatch(batchToVoid, BatchType.PURCHASE)

        assertNotNull(viewModel.uiState.value.error)
        assertEquals("Void failed", viewModel.uiState.value.error)
    }
}
