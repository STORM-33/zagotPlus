package com.zagot.zagotplus.ui.screens.sale

import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.SaleBatch
import com.zagot.zagotplus.domain.repository.SaleBatchRepository
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flow
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
class SaleViewModelTest {

    private lateinit var saleBatchRepository: SaleBatchRepository
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var viewModel: SaleViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testLocationId = UUID.randomUUID()

    private val testSaleBatch = SaleBatch(
        id = UUID.randomUUID(),
        localId = "test-local-id",
        locationId = testLocationId,
        notes = null,
        totalWeightKg = BigDecimal("30.0"),
        totalAmount = BigDecimal("1650.00"),
        itemCount = 2,
        deviceId = null,
        createdAt = Instant.now(),
        syncedAt = null
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        saleBatchRepository = mockk()
        devicePreferences = mockk()

        every { devicePreferences.selectedLocationIdFlow } returns MutableStateFlow(testLocationId)
        every { devicePreferences.getSelectedLocationId() } returns testLocationId
        every { saleBatchRepository.observeTodaysBatches(testLocationId) } returns flowOf(listOf(testSaleBatch))
    }

    private fun createViewModel(): SaleViewModel {
        return SaleViewModel(saleBatchRepository, devicePreferences)
    }

    @Test
    fun `loads today batches on init`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.todaysBatches.size)
        assertEquals(testSaleBatch.id, state.todaysBatches[0].id)
    }

    @Test
    fun `onNewSaleClick sets navigation flag`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNewSaleClick()

        assertTrue(viewModel.uiState.value.navigateToNewSale)
    }

    @Test
    fun `onNavigationHandled clears navigation flag`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNewSaleClick()
        viewModel.onNavigationHandled()

        assertFalse(viewModel.uiState.value.navigateToNewSale)
    }

    @Test
    fun `handles error during loading`() = runTest {
        // Mock with flow that throws on collection
        every { saleBatchRepository.observeTodaysBatches(testLocationId) } returns flow<List<SaleBatch>> {
            throw RuntimeException("Database error")
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertEquals("Database error", state.error)
    }

    @Test
    fun `dismissError clears error`() = runTest {
        every { saleBatchRepository.observeTodaysBatches(testLocationId) } returns flow<List<SaleBatch>> {
            throw RuntimeException("Database error")
        }

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `empty state when no batches`() = runTest {
        every { saleBatchRepository.observeTodaysBatches(testLocationId) } returns flowOf(emptyList())

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertTrue(state.todaysBatches.isEmpty())
    }
}
