package com.zagot.zagotplus.ui.screens.purchase

import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.repository.PurchaseBatchRepository
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

    private lateinit var purchaseBatchRepository: PurchaseBatchRepository
    private lateinit var viewModel: PurchaseViewModel
    private val testDispatcher = StandardTestDispatcher()

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

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        purchaseBatchRepository = mockk()
    }

    private fun createViewModel(): PurchaseViewModel {
        return PurchaseViewModel(purchaseBatchRepository)
    }

    @Test
    fun `loads today's batches on init`() = runTest {
        every { purchaseBatchRepository.observeTodaysBatches() } returns flowOf(listOf(testBatch))

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.todaysBatches.size)
        assertEquals(testBatch, state.todaysBatches[0])
        assertFalse(state.isLoading)
    }

    @Test
    fun `shows empty state when no batches today`() = runTest {
        every { purchaseBatchRepository.observeTodaysBatches() } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertTrue(state.todaysBatches.isEmpty())
        assertFalse(state.isLoading)
    }

    @Test
    fun `weight placeholder shows default value`() = runTest {
        every { purchaseBatchRepository.observeTodaysBatches() } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals("-- кг", viewModel.uiState.value.weightPlaceholder)
    }

    @Test
    fun `onNewClientClick sets navigate flag`() = runTest {
        every { purchaseBatchRepository.observeTodaysBatches() } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNewClientClick()

        assertTrue(viewModel.uiState.value.navigateToNewClient)
    }

    @Test
    fun `onNavigationHandled clears navigate flag`() = runTest {
        every { purchaseBatchRepository.observeTodaysBatches() } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNewClientClick()
        assertTrue(viewModel.uiState.value.navigateToNewClient)

        viewModel.onNavigationHandled()
        assertFalse(viewModel.uiState.value.navigateToNewClient)
    }

    @Test
    fun `dismissError clears error`() = runTest {
        every { purchaseBatchRepository.observeTodaysBatches() } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dismissError()
        assertNull(viewModel.uiState.value.error)
    }
}
