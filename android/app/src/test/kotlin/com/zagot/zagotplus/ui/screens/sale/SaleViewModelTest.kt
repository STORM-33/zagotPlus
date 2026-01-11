package com.zagot.zagotplus.ui.screens.sale

import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionFilter
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import io.mockk.coEvery
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
class SaleViewModelTest {

    private lateinit var productRepository: ProductRepository
    private lateinit var transactionRepository: TransactionRepository
    private lateinit var devicePreferences: DevicePreferences
    private lateinit var viewModel: SaleViewModel
    private val testDispatcher = StandardTestDispatcher()

    private val testProductId = UUID.randomUUID()
    private val testLocationId = UUID.randomUUID()

    private val testProduct = Product(
        id = testProductId,
        name = "Горіх білий",
        defaultBuyPrice = BigDecimal("45.00"),
        defaultSellPrice = BigDecimal("55.00"),
        isActive = true,
        createdAt = Instant.now()
    )

    private val testTransaction = Transaction(
        id = UUID.randomUUID(),
        localId = "test-local-id",
        locationId = testLocationId,
        type = TransactionType.SALE,
        transferLocationId = null,
        productId = testProductId,
        weightKg = BigDecimal("-30.0"),
        pricePerKg = BigDecimal("55.00"),
        totalAmount = BigDecimal("1650.00"),
        notes = null,
        deviceId = null,
        createdAt = Instant.now(),
        syncedAt = null
    )

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        productRepository = mockk()
        transactionRepository = mockk()
        devicePreferences = mockk()

        every { productRepository.getActiveProducts() } returns flowOf(listOf(testProduct))
        every { devicePreferences.getSelectedLocationId() } returns testLocationId
        coEvery { 
            transactionRepository.getFilteredTransactions(any<TransactionFilter>(), any(), any()) 
        } returns listOf(testTransaction)
    }

    private fun createViewModel(): SaleViewModel {
        return SaleViewModel(transactionRepository, productRepository, devicePreferences)
    }

    @Test
    fun `loads today sales on init`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertEquals(1, state.todaysSales.size)
        assertEquals(testProduct.name, state.todaysSales[0].productName)
    }

    @Test
    fun `displays unknown product name when product not found`() = runTest {
        val transactionWithUnknownProduct = testTransaction.copy(
            productId = UUID.randomUUID()
        )
        coEvery { 
            transactionRepository.getFilteredTransactions(any<TransactionFilter>(), any(), any()) 
        } returns listOf(transactionWithUnknownProduct)

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals("Невідомий товар", state.todaysSales[0].productName)
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
        coEvery { 
            transactionRepository.getFilteredTransactions(any<TransactionFilter>(), any(), any()) 
        } throws RuntimeException("Database error")

        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertFalse(state.isLoading)
        assertNotNull(state.error)
        assertEquals("Database error", state.error)
    }

    @Test
    fun `dismissError clears error`() = runTest {
        coEvery { 
            transactionRepository.getFilteredTransactions(any<TransactionFilter>(), any(), any()) 
        } throws RuntimeException("Database error")

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.error)
    }

    @Test
    fun `refresh reloads data`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val newTransaction = testTransaction.copy(id = UUID.randomUUID())
        coEvery { 
            transactionRepository.getFilteredTransactions(any<TransactionFilter>(), any(), any()) 
        } returns listOf(testTransaction, newTransaction)

        viewModel.refresh()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.todaysSales.size)
    }
}
