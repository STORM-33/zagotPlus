package com.zagot.zagotplus.ui.screens.reports

import com.zagot.zagotplus.domain.model.CashHistoryItem
import com.zagot.zagotplus.domain.model.CashHistoryItemType
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionFilter
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.CashRepository
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import com.zagot.zagotplus.ui.components.DateRange
import com.zagot.zagotplus.ui.components.DateRangePreset
import io.mockk.coEvery
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
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class ReportsViewModelTest {

    private lateinit var transactionRepository: TransactionRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var locationRepository: LocationRepository
    private lateinit var cashRepository: CashRepository
    private lateinit var viewModel: ReportsViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testProduct = Product(
        id = UUID.randomUUID(),
        name = "Горіх білий",
        defaultBuyPrice = BigDecimal("45.00"),
        defaultSellPrice = BigDecimal("50.00"),
        isActive = true,
        createdAt = Instant.now(),
        imageUri = "content://image/1"
    )

    private val testProduct2 = Product(
        id = UUID.randomUUID(),
        name = "Горіх чорний",
        defaultBuyPrice = BigDecimal("40.00"),
        defaultSellPrice = BigDecimal("48.00"),
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
        name = "Склад",
        type = LocationType.MOBILE,
        createdAt = Instant.now()
    )

    private val purchaseTransaction = Transaction(
        id = UUID.randomUUID(),
        localId = "purchase-1",
        locationId = testLocation.id,
        type = TransactionType.PURCHASE,
        transferLocationId = null,
        productId = testProduct.id,
        weightKg = BigDecimal("10.00"),
        pricePerKg = BigDecimal("45.00"),
        totalAmount = BigDecimal("450.00"),
        notes = null,
        deviceId = "test-device",
        createdAt = Instant.now(),
        syncedAt = null
    )

    private val saleTransaction = Transaction(
        id = UUID.randomUUID(),
        localId = "sale-1",
        locationId = testLocation.id,
        type = TransactionType.SALE,
        transferLocationId = null,
        productId = testProduct.id,
        weightKg = BigDecimal("5.00"),
        pricePerKg = BigDecimal("50.00"),
        totalAmount = BigDecimal("250.00"),
        notes = null,
        deviceId = "test-device",
        createdAt = Instant.now(),
        syncedAt = null
    )

    private val paymentHistoryItem = CashHistoryItem(
        id = UUID.randomUUID().toString(),
        type = CashHistoryItemType.PAYMENT,
        amount = BigDecimal("100.00"),
        notes = "Test payment",
        categoryName = null,
        itemCount = null,
        weightKg = null,
        createdAt = Instant.now()
    )

    private val withdrawalHistoryItem = CashHistoryItem(
        id = UUID.randomUUID().toString(),
        type = CashHistoryItemType.WITHDRAWAL,
        amount = BigDecimal("50.00"),
        notes = "Test withdrawal",
        categoryName = null,
        itemCount = null,
        weightKg = null,
        createdAt = Instant.now()
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        transactionRepository = mockk()
        productRepository = mockk()
        locationRepository = mockk()
        cashRepository = mockk()

        every { productRepository.getActiveProducts() } returns flowOf(listOf(testProduct, testProduct2))
        every { locationRepository.getAllLocations() } returns flowOf(listOf(testLocation, testLocation2))
        every { transactionRepository.getTotalTransactionCount() } returns flowOf(3)
        coEvery { transactionRepository.getFilteredTransactions(any(), any(), any()) } returns 
            listOf(purchaseTransaction, saleTransaction)
        coEvery { cashRepository.getCashHistoryPaged(any(), any()) } returns 
            listOf(paymentHistoryItem, withdrawalHistoryItem)
        coEvery { cashRepository.getCashHistoryByLocationPaged(any(), any(), any()) } returns 
            listOf(paymentHistoryItem)
    }

    private fun createViewModel(): ReportsViewModel {
        return ReportsViewModel(transactionRepository, productRepository, locationRepository, cashRepository)
    }

    @Test
    fun `initial state has all time date range (null)`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.dateRange)
    }

    @Test
    fun `initial state shows all locations (totals view)`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedLocationId)
        assertTrue(state.isTotalsView)
    }

    @Test
    fun `loads transactions and computes summaries on init`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.hasData)
    }

    @Test
    fun `setDateRange changes date range and reloads data`() = runTest {
        val filterSlot = slot<TransactionFilter>()
        coEvery { transactionRepository.getFilteredTransactions(capture(filterSlot), any(), any()) } returns 
            listOf(purchaseTransaction)

        viewModel = createViewModel()
        advanceUntilIdle()

        val newDateRange = DateRange.last7Days()
        viewModel.setDateRange(newDateRange)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNotNull(state.dateRange)
        assertEquals(newDateRange.startDate, state.dateRange!!.startDate)
        assertEquals(newDateRange.endDate, state.dateRange!!.endDate)
        
        val capturedFilter = filterSlot.captured
        assertNotNull(capturedFilter.startDate)
        assertNotNull(capturedFilter.endDate)
    }

    @Test
    fun `setDateRange to null shows all time`() = runTest {
        val filterSlot = slot<TransactionFilter>()
        coEvery { transactionRepository.getFilteredTransactions(capture(filterSlot), any(), any()) } returns 
            listOf(purchaseTransaction)

        viewModel = createViewModel()
        advanceUntilIdle()

        // First set a date range
        viewModel.setDateRange(DateRange.today())
        advanceUntilIdle()
        assertNotNull(viewModel.uiState.value.dateRange)

        // Then set to null (all time)
        viewModel.setDateRange(null)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.dateRange)
        
        // Filter should have null dates for all time
        val capturedFilter = filterSlot.captured
        assertNull(capturedFilter.startDate)
        assertNull(capturedFilter.endDate)
    }

    @Test
    fun `selectLocation filters by location`() = runTest {
        val filterSlot = slot<TransactionFilter>()
        coEvery { transactionRepository.getFilteredTransactions(capture(filterSlot), any(), any()) } returns 
            listOf(purchaseTransaction)

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocation.id)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(testLocation.id, state.selectedLocationId)
        assertFalse(state.isTotalsView)
        
        val capturedFilter = filterSlot.captured
        assertEquals(testLocation.id, capturedFilter.locationId)
    }

    @Test
    fun `selectTotalView clears location filter`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocation.id)
        advanceUntilIdle()
        
        viewModel.selectTotalView()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertNull(state.selectedLocationId)
        assertTrue(state.isTotalsView)
    }

    @Test
    fun `earnings calculated from sales correctly`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BigDecimal("250.00"), state.totalEarnings)
    }

    @Test
    fun `spendings include purchases and payments but exclude withdrawals`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        // Purchases: 450 + Payments: 100 = 550 (withdrawals excluded)
        assertEquals(BigDecimal("550.00"), state.totalSpendings)
    }

    @Test
    fun `product items sorted by spending descending`() = runTest {
        val purchase1 = purchaseTransaction.copy(
            id = UUID.randomUUID(),
            productId = testProduct.id,
            totalAmount = BigDecimal("450.00")
        )
        val purchase2 = purchaseTransaction.copy(
            id = UUID.randomUUID(),
            productId = testProduct2.id,
            totalAmount = BigDecimal("600.00")
        )
        
        coEvery { transactionRepository.getFilteredTransactions(any(), any(), any()) } returns 
            listOf(purchase1, purchase2)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.productItems.size)
        // Product2 has higher spending, should be first
        assertEquals(testProduct2.id, state.productItems[0].productId)
        assertEquals(testProduct.id, state.productItems[1].productId)
    }

    @Test
    fun `product items include image uri`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        val productItem = state.productItems.find { it.productId == testProduct.id }
        assertNotNull(productItem)
        assertEquals("content://image/1", productItem?.imageUri)
    }

    @Test
    fun `empty transactions shows hasData false`() = runTest {
        coEvery { transactionRepository.getFilteredTransactions(any(), any(), any()) } returns emptyList()
        coEvery { cashRepository.getCashHistoryPaged(any(), any()) } returns emptyList()

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.hasData)
        assertEquals(BigDecimal.ZERO, state.totalSpendings)
        assertEquals(BigDecimal.ZERO, state.totalEarnings)
    }

    @Test
    fun `dismissError clears error`() = runTest {
        coEvery { transactionRepository.getFilteredTransactions(any(), any(), any()) } throws RuntimeException("Test error")

        viewModel = createViewModel()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `locations list is populated`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(2, state.locations.size)
        assertTrue(state.locations.any { it.id == testLocation.id })
        assertTrue(state.locations.any { it.id == testLocation2.id })
    }
}
