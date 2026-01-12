package com.zagot.zagotplus.ui.screens.history

import com.zagot.zagotplus.domain.model.DateRangePreset
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionFilter
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
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
class HistoryViewModelTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var viewModel: HistoryViewModel
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
        name = "Кіоск 1",
        type = LocationType.KIOSK,
        createdAt = Instant.now()
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
        productRepository = mockk()
        locationRepository = mockk()

        every { productRepository.getActiveProducts() } returns flowOf(listOf(testProduct))
        every { locationRepository.getAllLocations() } returns flowOf(listOf(testLocation))
        every { transactionRepository.getTotalTransactionCount() } returns flowOf(2)
        coEvery { transactionRepository.getFilteredTransactionCount(any()) } returns 2
        coEvery { transactionRepository.getFilteredTransactions(any(), any(), any()) } returns listOf(testTransaction, saleTransaction)
    }

    private fun createViewModel(): HistoryViewModel {
        return HistoryViewModel(transactionRepository, productRepository, locationRepository)
    }

    @Test
    fun `initial state loads all transactions`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.transactions.size)
        assertEquals(2, state.totalCount)
        assertFalse(state.isLoading)
        assertFalse(state.hasActiveFilters)
    }

    @Test
    fun `transactions are mapped to display items correctly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val firstItem = state.transactions.first()
        assertEquals(testProduct.name, firstItem.productName)
        assertEquals(testLocation.name, firstItem.locationName)
        assertEquals(testTransaction.weightKg, firstItem.weightKg)
        assertEquals(testTransaction.type, firstItem.type)
    }

    @Test
    fun `toggleTypeFilter adds type to filter`() = runTest {
        val filterSlot = slot<TransactionFilter>()
        coEvery { transactionRepository.getFilteredTransactions(capture(filterSlot), any(), any()) } returns listOf(testTransaction)
        coEvery { transactionRepository.getFilteredTransactionCount(any()) } returns 1

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleTypeFilter(TransactionType.PURCHASE)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.selectedTypes.contains(TransactionType.PURCHASE))
        assertTrue(state.hasActiveFilters)
        assertEquals(setOf(TransactionType.PURCHASE), filterSlot.captured.types)
    }

    @Test
    fun `toggleTypeFilter removes type when already selected`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.toggleTypeFilter(TransactionType.PURCHASE)
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.selectedTypes.contains(TransactionType.PURCHASE))

        viewModel.toggleTypeFilter(TransactionType.PURCHASE)
        advanceUntilIdle()
        assertFalse(viewModel.uiState.value.selectedTypes.contains(TransactionType.PURCHASE))
    }

    @Test
    fun `setDateRangePreset updates filter`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setDateRangePreset(DateRangePreset.TODAY)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(DateRangePreset.TODAY, state.dateRangePreset)
        assertTrue(state.hasActiveFilters)
    }

    @Test
    fun `setLocationFilter filters by location`() = runTest {
        val filterSlot = slot<TransactionFilter>()
        coEvery { transactionRepository.getFilteredTransactions(capture(filterSlot), any(), any()) } returns listOf(testTransaction)
        coEvery { transactionRepository.getFilteredTransactionCount(any()) } returns 1

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setLocationFilter(testLocation.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(testLocation.id, state.selectedLocationId)
        assertTrue(state.hasActiveFilters)
        assertEquals(testLocation.id, filterSlot.captured.locationId)
    }

    @Test
    fun `setSearchQuery filters by product name`() = runTest {
        val filterSlot = slot<TransactionFilter>()
        coEvery { transactionRepository.getFilteredTransactions(capture(filterSlot), any(), any()) } returns listOf(testTransaction)
        coEvery { transactionRepository.getFilteredTransactionCount(any()) } returns 1

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.setSearchQuery("Горіх")
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Горіх", state.searchQuery)
        assertTrue(state.hasActiveFilters)
        assertEquals("Горіх", filterSlot.captured.productNameSearch)
    }

    @Test
    fun `clearFilters resets all filters`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Set some filters
        viewModel.toggleTypeFilter(TransactionType.PURCHASE)
        viewModel.setLocationFilter(testLocation.id)
        viewModel.setSearchQuery("test")
        advanceUntilIdle()
        assertTrue(viewModel.uiState.value.hasActiveFilters)

        // Clear all
        viewModel.clearFilters()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasActiveFilters)
        assertTrue(state.selectedTypes.isEmpty())
        assertNull(state.selectedLocationId)
        assertEquals("", state.searchQuery)
        assertEquals(DateRangePreset.ALL, state.dateRangePreset)
    }

    @Test
    fun `dismissError clears error`() = runTest {
        coEvery { transactionRepository.getFilteredTransactions(any(), any(), any()) } throws RuntimeException("Test error")
        coEvery { transactionRepository.getFilteredTransactionCount(any()) } throws RuntimeException("Test error")

        viewModel = createViewModel()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `loadMoreTransactions appends to existing list when hasMorePages is true`() = runTest {
        // Create enough transactions to simulate pagination
        val transactions = (1..50).map { 
            testTransaction.copy(id = UUID.randomUUID(), localId = "tx-$it")
        }
        val additionalTransactions = listOf(testTransaction.copy(id = UUID.randomUUID(), localId = "tx-51"))
        
        var callCount = 0
        coEvery { transactionRepository.getFilteredTransactions(any(), any(), any()) } answers {
            callCount++
            if (callCount == 1) transactions else additionalTransactions
        }
        coEvery { transactionRepository.getFilteredTransactionCount(any()) } returns 51

        viewModel = createViewModel()
        advanceUntilIdle()
        assertEquals(50, viewModel.uiState.value.transactions.size)
        assertTrue(viewModel.uiState.value.hasMorePages)

        viewModel.loadMoreTransactions()
        advanceUntilIdle()

        assertEquals(51, viewModel.uiState.value.transactions.size)
    }
}
