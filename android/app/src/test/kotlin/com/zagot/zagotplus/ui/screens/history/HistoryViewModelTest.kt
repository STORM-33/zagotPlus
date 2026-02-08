package com.zagot.zagotplus.ui.screens.history

import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.ui.components.DateRange
import com.zagot.zagotplus.ui.components.DateRangePreset
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
import com.zagot.zagotplus.sync.SyncStatus
import com.zagot.zagotplus.sync.SyncStatusRepository
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
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var syncStatusRepository: SyncStatusRepository
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

    private val testLocation2 = Location(
        id = UUID.randomUUID(),
        name = "Кіоск 2",
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
        syncStatusRepository = mockk()
        devicePreferences = mockk()

        every { productRepository.getAllProducts() } returns flowOf(listOf(testProduct))
        every { locationRepository.getAllLocations() } returns flowOf(listOf(testLocation, testLocation2))
        every { purchaseBatchRepository.observeTotalBatchCount() } returns purchaseBatchCountFlow
        every { saleBatchRepository.observeTotalBatchCount() } returns saleBatchCountFlow
        coEvery { purchaseBatchRepository.getTotalBatchCount() } returns 2
        coEvery { saleBatchRepository.getTotalBatchCount() } returns 0
        coEvery { purchaseBatchRepository.getAllBatchesPaginated(any(), any()) } returns listOf(testBatch, testBatch2)
        coEvery { saleBatchRepository.getAllBatchesPaginated(any(), any()) } returns emptyList()
        // Return empty list for unbatched transactions by default to avoid virtual batches
        coEvery { transactionRepository.getFilteredTransactions(any(), any(), any()) } returns emptyList()
        // Mock device preferences for restricted mode
        every { devicePreferences.getSelectedLocationId() } returns testLocation.id
        every { devicePreferences.selectedLocationIdFlow } returns MutableStateFlow(testLocation.id)
        // Mock sync status repository to return idle state
        every { syncStatusRepository.syncStatus } returns MutableStateFlow(SyncStatus.idle())
    }

    @After
    fun tearDown() {
        // Cancel any running coroutines in the test scope (including ViewModel's viewModelScope)
        testScope.cancel()
        Dispatchers.resetMain()
    }

    private fun createViewModel(): HistoryViewModel {
        return HistoryViewModel(transactionRepository, purchaseBatchRepository, saleBatchRepository, productRepository, locationRepository, devicePreferences, syncStatusRepository)
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
    fun `setDateRange updates filter`() = testScope.runTest {
        viewModel = createViewModel()

        val todayRange = DateRange.today()
        viewModel.setDateRange(todayRange)

        val state = viewModel.uiState.value
        assertEquals(todayRange, state.dateRange)
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
        assertNull(state.dateRange) // null means ALL time
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

    // === Restricted Mode Tests ===

    @Test
    fun `setRestrictedMode sets location filter to device location`() = testScope.runTest {
        every { devicePreferences.getSelectedLocationId() } returns testLocation.id
        every { devicePreferences.selectedLocationIdFlow } returns MutableStateFlow(testLocation.id)
        
        viewModel = createViewModel()
        
        // Initially location filter is null (all locations)
        assertNull(viewModel.uiState.value.selectedLocationId)
        
        // Enable restricted mode
        viewModel.setRestrictedMode(true)
        
        // Location filter should be set to device location
        assertEquals(testLocation.id, viewModel.uiState.value.selectedLocationId)
    }

    @Test
    fun `location change in restricted mode updates filter and reloads`() = testScope.runTest {
        val locationFlow = MutableStateFlow(testLocation.id)
        every { devicePreferences.selectedLocationIdFlow } returns locationFlow
        every { devicePreferences.getSelectedLocationId() } returns testLocation.id
        
        viewModel = createViewModel()
        
        // Enable restricted mode
        viewModel.setRestrictedMode(true)
        assertEquals(testLocation.id, viewModel.uiState.value.selectedLocationId)
        
        // Change location in preferences
        every { devicePreferences.getSelectedLocationId() } returns testLocation2.id
        locationFlow.value = testLocation2.id
        
        // Wait for the flow to be collected
        testScheduler.advanceUntilIdle()
        
        // Should update filter to new location
        assertEquals(testLocation2.id, viewModel.uiState.value.selectedLocationId)
    }

    @Test
    fun `location change when not in restricted mode does not affect filter`() = testScope.runTest {
        val locationFlow = MutableStateFlow(testLocation.id)
        every { devicePreferences.selectedLocationIdFlow } returns locationFlow
        
        viewModel = createViewModel()
        
        // Not in restricted mode, set a specific location filter
        viewModel.setLocationFilter(testLocation2.id)
        assertEquals(testLocation2.id, viewModel.uiState.value.selectedLocationId)
        
        // Change device location - should NOT affect filter since not in restricted mode
        locationFlow.value = testLocation.id
        testScheduler.advanceUntilIdle()
        
        // Should still be on location2
        assertEquals(testLocation2.id, viewModel.uiState.value.selectedLocationId)
    }

    @Test
    fun `exiting restricted mode resets location filter to all locations`() = testScope.runTest {
        every { devicePreferences.getSelectedLocationId() } returns testLocation.id
        every { devicePreferences.selectedLocationIdFlow } returns MutableStateFlow(testLocation.id)
        
        viewModel = createViewModel()
        
        // Initially location filter is null (all locations)
        assertNull(viewModel.uiState.value.selectedLocationId)
        
        // Enable restricted mode
        viewModel.setRestrictedMode(true)
        assertEquals(testLocation.id, viewModel.uiState.value.selectedLocationId)
        
        // Exit restricted mode
        viewModel.setRestrictedMode(false)
        
        // Location filter should be reset to null (all locations)
        assertNull(viewModel.uiState.value.selectedLocationId)
    }
}
