package com.zagot.zagotplus.ui.screens.purchase

import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.ProductDailyTotal
import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.repository.CashRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.PurchaseBatchRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
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

    private lateinit var purchaseBatchRepository: PurchaseBatchRepository
    private lateinit var cashRepository: CashRepository
    private lateinit var productRepository: ProductRepository
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var viewModel: PurchaseViewModel
    private val testDispatcher = StandardTestDispatcher()
    private val testLocationId = UUID.randomUUID()

    private val testBatch = PurchaseBatch(
        id = UUID.randomUUID(),
        localId = "test-local-id",
        locationId = UUID.randomUUID(),
        notes = null,
        totalWeightKg = BigDecimal("15.5"),
        totalAmount = BigDecimal("350.00"),
        itemCount = 3,
        deviceId = "test-device",
        createdAt = Instant.now(),
        syncedAt = null
    )

    private val testProductTotal = ProductDailyTotal(
        productId = UUID.randomUUID(),
        productName = "Яблука",
        totalWeightKg = BigDecimal("100.0"),
        totalAmount = BigDecimal("2500.00")
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        purchaseBatchRepository = mockk()
        cashRepository = mockk()
        productRepository = mockk()
        devicePreferences = mockk()
        every { productRepository.getActiveProducts() } returns flowOf(emptyList())
        every { devicePreferences.selectedLocationIdFlow } returns MutableStateFlow(testLocationId)
        every { devicePreferences.getSelectedLocationId() } returns testLocationId
    }

    private fun createViewModel(): PurchaseViewModel {
        return PurchaseViewModel(purchaseBatchRepository, cashRepository, productRepository, devicePreferences)
    }

    @Test
    fun `loads cash balance and product totals on init`() = runTest {
        every { cashRepository.getBalance(testLocationId) } returns flowOf(BigDecimal("5000.00"))
        every { purchaseBatchRepository.observeTodaysProductTotals(testLocationId) } returns flowOf(listOf(testProductTotal))
        every { purchaseBatchRepository.observeTodaysBatches(testLocationId) } returns flowOf(listOf(testBatch))

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BigDecimal("5000.00"), state.cashBalance)
        assertEquals(1, state.todaysProductTotals.size)
        assertEquals(testProductTotal, state.todaysProductTotals[0])
        assertEquals(1, state.todaysBatches.size)
        assertFalse(state.isLoading)
    }

    @Test
    fun `shows empty state when no purchases today`() = runTest {
        every { cashRepository.getBalance(testLocationId) } returns flowOf(BigDecimal("1000.00"))
        every { purchaseBatchRepository.observeTodaysProductTotals(testLocationId) } returns flowOf(emptyList())
        every { purchaseBatchRepository.observeTodaysBatches(testLocationId) } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.todaysProductTotals.isEmpty())
        assertTrue(state.todaysBatches.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `onNewClientClick sets navigate flag`() = runTest {
        every { cashRepository.getBalance(testLocationId) } returns flowOf(BigDecimal.ZERO)
        every { purchaseBatchRepository.observeTodaysProductTotals(testLocationId) } returns flowOf(emptyList())
        every { purchaseBatchRepository.observeTodaysBatches(testLocationId) } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNewClientClick()

        assertTrue(viewModel.uiState.value.navigateToNewClient)
    }

    @Test
    fun `onNavigationHandled clears navigate flag`() = runTest {
        every { cashRepository.getBalance(testLocationId) } returns flowOf(BigDecimal.ZERO)
        every { purchaseBatchRepository.observeTodaysProductTotals(testLocationId) } returns flowOf(emptyList())
        every { purchaseBatchRepository.observeTodaysBatches(testLocationId) } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNewClientClick()
        assertTrue(viewModel.uiState.value.navigateToNewClient)

        viewModel.onNavigationHandled()
        assertFalse(viewModel.uiState.value.navigateToNewClient)
    }

    @Test
    fun `dismissError clears error`() = runTest {
        every { cashRepository.getBalance(testLocationId) } returns flowOf(BigDecimal.ZERO)
        every { purchaseBatchRepository.observeTodaysProductTotals(testLocationId) } returns flowOf(emptyList())
        every { purchaseBatchRepository.observeTodaysBatches(testLocationId) } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }
}
