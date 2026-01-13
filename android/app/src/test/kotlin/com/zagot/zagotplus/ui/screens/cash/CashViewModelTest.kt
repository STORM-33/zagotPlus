package com.zagot.zagotplus.ui.screens.cash

import com.zagot.zagotplus.domain.model.CashOperation
import com.zagot.zagotplus.domain.model.CashOperationType
import com.zagot.zagotplus.domain.model.ExpenseCategory
import com.zagot.zagotplus.domain.repository.CashRepository
import com.zagot.zagotplus.testutil.MainDispatcherRule
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
import java.time.Instant
import java.time.LocalDate
import java.util.UUID

@OptIn(ExperimentalCoroutinesApi::class)
class CashViewModelTest {

    @get:Rule
    val mainDispatcherRule = MainDispatcherRule()

    private lateinit var cashRepository: CashRepository
    private lateinit var viewModel: CashViewModel

    private val testCategoryId = UUID.randomUUID()
    private val testCategories = listOf(
        ExpenseCategory(
            id = testCategoryId,
            localId = "cat-1",
            name = "Fuel",
            isActive = true,
            createdAt = Instant.now(),
            syncedAt = null
        )
    )

    private val testOperations = listOf(
        CashOperation(
            id = UUID.randomUUID(),
            localId = "op-1",
            locationId = null,
            type = CashOperationType.DEPOSIT,
            amount = BigDecimal("1000.00"),
            categoryId = null,
            categoryName = null,
            batchId = null,
            notes = null,
            deviceId = "device-1",
            createdAt = Instant.now(),
            syncedAt = null
        )
    )

    @Before
    fun setup() {
        cashRepository = mockk()
        setupDefaultMocks()
    }

    private fun setupDefaultMocks() {
        every { cashRepository.getTotalBalance() } returns flowOf(BigDecimal("5000.00"))
        every { cashRepository.getDailyChangeGlobal(any()) } returns flowOf(BigDecimal("500.00"))
        every { cashRepository.getActiveCategories() } returns flowOf(testCategories)
        coEvery { cashRepository.getTotalOperationsCount() } returns 1
        coEvery { cashRepository.getOperationsPaged(any(), any()) } returns testOperations
    }

    private fun createViewModel(): CashViewModel {
        return CashViewModel(cashRepository)
    }

    // ==================== Initial State Tests ====================

    @Test
    fun `initial state has default values`() = runTest {
        val initialState = CashUiState()
        
        assertEquals(BigDecimal.ZERO, initialState.balance)
        assertEquals(BigDecimal.ZERO, initialState.dailyChange)
        assertTrue(initialState.operations.isEmpty())
        assertTrue(initialState.categories.isEmpty())
        assertEquals(CashDialogType.NONE, initialState.dialogType)
        assertFalse(initialState.isLoading)
    }

    @Test
    fun `loadData loads balance and categories`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(BigDecimal("5000.00"), state.balance)
        assertEquals(BigDecimal("500.00"), state.dailyChange)
        assertEquals(1, state.categories.size)
    }

    @Test
    fun `loadData loads operations`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.operations.size)
    }

    // ==================== Dialog Tests ====================

    @Test
    fun `showDepositDialog sets dialog type`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showDepositDialog()

        assertEquals(CashDialogType.DEPOSIT, viewModel.uiState.value.dialogType)
    }

    @Test
    fun `showWithdrawDialog sets dialog type`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showWithdrawDialog()

        assertEquals(CashDialogType.WITHDRAW, viewModel.uiState.value.dialogType)
    }

    @Test
    fun `showPaymentDialog sets dialog type`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showPaymentDialog()

        assertEquals(CashDialogType.PAYMENT, viewModel.uiState.value.dialogType)
    }

    @Test
    fun `showCategoriesDialog sets dialog type`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showCategoriesDialog()

        assertEquals(CashDialogType.CATEGORIES, viewModel.uiState.value.dialogType)
    }

    @Test
    fun `dismissDialog resets dialog state`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showDepositDialog()
        viewModel.onAmountChange("100")
        viewModel.onNotesChange("test note")
        viewModel.dismissDialog()

        val state = viewModel.uiState.value
        assertEquals(CashDialogType.NONE, state.dialogType)
        assertEquals("", state.dialogAmount)
        assertEquals("", state.dialogNotes)
    }

    // ==================== Input Validation Tests ====================

    @Test
    fun `onAmountChange accepts valid decimal`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChange("123.45")

        assertEquals("123.45", viewModel.uiState.value.dialogAmount)
    }

    @Test
    fun `onAmountChange rejects invalid input`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChange("abc")

        assertEquals("", viewModel.uiState.value.dialogAmount)
    }

    @Test
    fun `onAmountChange accepts empty string`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChange("100")
        viewModel.onAmountChange("")

        assertEquals("", viewModel.uiState.value.dialogAmount)
    }

    @Test
    fun `onNotesChange updates notes`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onNotesChange("Test notes")

        assertEquals("Test notes", viewModel.uiState.value.dialogNotes)
    }

    @Test
    fun `onCategorySelect updates selected category`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onCategorySelect(testCategoryId)

        assertEquals(testCategoryId, viewModel.uiState.value.dialogCategoryId)
    }

    // ==================== Validation Property Tests ====================

    @Test
    fun `canConfirmDeposit is true for positive amount`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChange("100")

        assertTrue(viewModel.uiState.value.canConfirmDeposit)
    }

    @Test
    fun `canConfirmDeposit is false for zero amount`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChange("0")

        assertFalse(viewModel.uiState.value.canConfirmDeposit)
    }

    @Test
    fun `canConfirmDeposit is false for empty amount`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.canConfirmDeposit)
    }

    @Test
    fun `canConfirmWithdraw is true when amount is positive and within balance`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChange("1000")

        assertTrue(viewModel.uiState.value.canConfirmWithdraw)
    }

    @Test
    fun `canConfirmWithdraw is false when amount exceeds balance`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.onAmountChange("10000")

        assertFalse(viewModel.uiState.value.canConfirmWithdraw)
    }

    // ==================== Deposit Tests ====================

    @Test
    fun `confirmDeposit calls repository`() = runTest {
        coEvery { cashRepository.deposit(any(), any(), any()) } returns Unit
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showDepositDialog()
        viewModel.onAmountChange("100")
        viewModel.onNotesChange("Test deposit")
        viewModel.confirmDeposit()
        advanceUntilIdle()

        coVerify { cashRepository.deposit(null, BigDecimal("100"), "Test deposit") }
    }

    @Test
    fun `confirmDeposit dismisses dialog on success`() = runTest {
        coEvery { cashRepository.deposit(any(), any(), any()) } returns Unit
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showDepositDialog()
        viewModel.onAmountChange("100")
        viewModel.confirmDeposit()
        advanceUntilIdle()

        assertEquals(CashDialogType.NONE, viewModel.uiState.value.dialogType)
    }

    // ==================== Withdraw Tests ====================

    @Test
    fun `confirmWithdraw calls repository`() = runTest {
        coEvery { cashRepository.withdraw(any(), any(), any()) } returns Unit
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showWithdrawDialog()
        viewModel.onAmountChange("100")
        viewModel.confirmWithdraw()
        advanceUntilIdle()

        coVerify { cashRepository.withdraw(null, BigDecimal("100"), null) }
    }

    @Test
    fun `confirmWithdraw sets error when amount exceeds balance`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showWithdrawDialog()
        viewModel.onAmountChange("10000")
        viewModel.confirmWithdraw()

        assertNotNull(viewModel.uiState.value.error)
    }

    // ==================== Payment Tests ====================

    @Test
    fun `confirmPayment calls repository with category`() = runTest {
        coEvery { cashRepository.payment(any(), any(), any(), any()) } returns Unit
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showPaymentDialog()
        viewModel.onAmountChange("200")
        viewModel.onCategorySelect(testCategoryId)
        viewModel.onNotesChange("Payment notes")
        viewModel.confirmPayment()
        advanceUntilIdle()

        coVerify { cashRepository.payment(null, BigDecimal("200"), testCategoryId, "Payment notes") }
    }

    // ==================== Category Management Tests ====================

    @Test
    fun `addCategory calls repository`() = runTest {
        coEvery { cashRepository.createCategory(any()) } returns testCategories[0]
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showCategoriesDialog()
        viewModel.onNewCategoryNameChange("New Category")
        viewModel.addCategory()
        advanceUntilIdle()

        coVerify { cashRepository.createCategory("New Category") }
    }

    @Test
    fun `addCategory clears input on success`() = runTest {
        coEvery { cashRepository.createCategory(any()) } returns testCategories[0]
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showCategoriesDialog()
        viewModel.onNewCategoryNameChange("New Category")
        viewModel.addCategory()
        advanceUntilIdle()

        assertEquals("", viewModel.uiState.value.newCategoryName)
    }

    @Test
    fun `addCategory does nothing for blank name`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.showCategoriesDialog()
        viewModel.onNewCategoryNameChange("   ")
        viewModel.addCategory()
        advanceUntilIdle()

        coVerify(exactly = 0) { cashRepository.createCategory(any()) }
    }

    @Test
    fun `deactivateCategory calls repository`() = runTest {
        coEvery { cashRepository.deactivateCategory(any()) } returns Unit
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.deactivateCategory(testCategoryId)
        advanceUntilIdle()

        coVerify { cashRepository.deactivateCategory(testCategoryId) }
    }

    // ==================== Pagination Tests ====================

    @Test
    fun `loadMoreOperations loads next page`() = runTest {
        val moreOperations = listOf(
            CashOperation(
                id = UUID.randomUUID(),
                localId = "op-2",
                locationId = null,
                type = CashOperationType.WITHDRAWAL,
                amount = BigDecimal("500.00"),
                categoryId = null,
                categoryName = null,
                batchId = null,
                notes = null,
                deviceId = "device-1",
                createdAt = Instant.now(),
                syncedAt = null
            )
        )
        coEvery { cashRepository.getTotalOperationsCount() } returns 2
        coEvery { cashRepository.getOperationsPaged(20, 0) } returns testOperations
        coEvery { cashRepository.getOperationsPaged(20, 1) } returns moreOperations

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMoreOperations()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.operations.size)
    }

    @Test
    fun `loadMoreOperations does nothing when no more operations`() = runTest {
        coEvery { cashRepository.getTotalOperationsCount() } returns 1
        
        viewModel = createViewModel()
        advanceUntilIdle()

        // hasMoreOperations should be false since totalCount == operations.size
        viewModel.loadMoreOperations()
        advanceUntilIdle()

        // Should only have initial operations
        assertEquals(1, viewModel.uiState.value.operations.size)
    }

    // ==================== Error Handling Tests ====================

    @Test
    fun `dismissError clears error`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        // Trigger an error
        viewModel.showWithdrawDialog()
        viewModel.onAmountChange("10000")
        viewModel.confirmWithdraw()

        assertNotNull(viewModel.uiState.value.error)

        viewModel.dismissError()

        assertNull(viewModel.uiState.value.error)
    }
}
