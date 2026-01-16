package com.zagot.zagotplus.ui.screens.cash

import com.zagot.zagotplus.domain.model.CashHistoryItem
import com.zagot.zagotplus.domain.model.CashHistoryItemType
import com.zagot.zagotplus.domain.model.CashOperation
import com.zagot.zagotplus.domain.model.CashOperationType
import com.zagot.zagotplus.domain.model.ExpenseCategory
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.repository.CashRepository
import com.zagot.zagotplus.domain.repository.LocationRepository
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
    private lateinit var locationRepository: LocationRepository
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

    private val testHistoryItems = listOf(
        CashHistoryItem(
            id = UUID.randomUUID().toString(),
            type = CashHistoryItemType.DEPOSIT,
            amount = BigDecimal("1000.00"),
            notes = null,
            categoryName = null,
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )
    )

    @Before
    fun setup() {
        cashRepository = mockk()
        locationRepository = mockk()
        setupDefaultMocks()
    }

    private fun setupDefaultMocks() {
        every { cashRepository.getTotalBalance() } returns flowOf(BigDecimal("5000.00"))
        every { cashRepository.getDailyChangeGlobal(any()) } returns flowOf(BigDecimal("500.00"))
        every { cashRepository.getDailyDepositsGlobal(any()) } returns flowOf(BigDecimal("200.00"))
        every { cashRepository.getActiveCategories() } returns flowOf(testCategories)
        coEvery { cashRepository.getTotalHistoryCount() } returns 1
        coEvery { cashRepository.getCashHistoryPaged(any(), any()) } returns testHistoryItems
        every { locationRepository.getAllLocations() } returns flowOf(emptyList())
    }

    private fun createViewModel(): CashViewModel {
        return CashViewModel(cashRepository, locationRepository)
    }

    // ==================== Initial State Tests ====================

    @Test
    fun `initial state has default values`() = runTest {
        val initialState = CashUiState()
        
        assertEquals(BigDecimal.ZERO, initialState.balance)
        assertEquals(BigDecimal.ZERO, initialState.dailyChange)
        assertTrue(initialState.historyItems.isEmpty())
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
    fun `loadData loads history items`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(1, state.historyItems.size)
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
        val moreItems = listOf(
            CashHistoryItem(
                id = UUID.randomUUID().toString(),
                type = CashHistoryItemType.WITHDRAWAL,
                amount = BigDecimal("500.00"),
                notes = null,
                categoryName = null,
                itemCount = null,
                weightKg = null,
                createdAt = Instant.now(),
                batchCount = null
            )
        )
        coEvery { cashRepository.getTotalHistoryCount() } returns 2
        coEvery { cashRepository.getCashHistoryPaged(20, 0) } returns testHistoryItems
        coEvery { cashRepository.getCashHistoryPaged(20, 1) } returns moreItems

        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.loadMoreOperations()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.historyItems.size)
    }

    @Test
    fun `loadMoreOperations does nothing when no more items`() = runTest {
        coEvery { cashRepository.getTotalHistoryCount() } returns 1
        
        viewModel = createViewModel()
        advanceUntilIdle()

        // hasMoreItems should be false since totalCount == historyItems.size
        viewModel.loadMoreOperations()
        advanceUntilIdle()

        // Should only have initial items
        assertEquals(1, viewModel.uiState.value.historyItems.size)
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

    // ==================== Edit Operation Tests ====================

    @Test
    fun `canModifyItem returns true for deposit`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val depositItem = CashHistoryItem(
            id = UUID.randomUUID().toString(),
            type = CashHistoryItemType.DEPOSIT,
            amount = BigDecimal("100"),
            notes = null,
            categoryName = null,
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )

        assertTrue(viewModel.canModifyItem(depositItem))
    }

    @Test
    fun `canModifyItem returns true for withdrawal`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val withdrawalItem = CashHistoryItem(
            id = UUID.randomUUID().toString(),
            type = CashHistoryItemType.WITHDRAWAL,
            amount = BigDecimal("100"),
            notes = null,
            categoryName = null,
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )

        assertTrue(viewModel.canModifyItem(withdrawalItem))
    }

    @Test
    fun `canModifyItem returns true for payment`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val paymentItem = CashHistoryItem(
            id = UUID.randomUUID().toString(),
            type = CashHistoryItemType.PAYMENT,
            amount = BigDecimal("100"),
            notes = null,
            categoryName = "Fuel",
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )

        assertTrue(viewModel.canModifyItem(paymentItem))
    }

    @Test
    fun `canModifyItem returns false for purchase`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val purchaseItem = CashHistoryItem(
            id = "purchase_loc_2024-01-01",
            type = CashHistoryItemType.PURCHASE,
            amount = BigDecimal("500"),
            notes = null,
            categoryName = null,
            itemCount = 10,
            weightKg = BigDecimal("50"),
            createdAt = Instant.now(),
            batchCount = 2
        )

        assertFalse(viewModel.canModifyItem(purchaseItem))
    }

    @Test
    fun `canModifyItem returns false for sale`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val saleItem = CashHistoryItem(
            id = "sale_loc_2024-01-01",
            type = CashHistoryItemType.SALE,
            amount = BigDecimal("800"),
            notes = null,
            categoryName = null,
            itemCount = 15,
            weightKg = BigDecimal("75"),
            createdAt = Instant.now(),
            batchCount = 3
        )

        assertFalse(viewModel.canModifyItem(saleItem))
    }

    @Test
    fun `showEditDialog sets EDIT_DEPOSIT for deposit item`() = runTest {
        val operationId = UUID.randomUUID()
        val testOperation = CashOperation(
            id = operationId,
            localId = "local-1",
            locationId = null,
            type = CashOperationType.DEPOSIT,
            amount = BigDecimal("250.00"),
            categoryId = null,
            categoryName = null,
            notes = "Test deposit note",
            deviceId = null,
            createdAt = Instant.now(),
            syncedAt = null
        )
        coEvery { cashRepository.getOperationById(operationId) } returns testOperation

        viewModel = createViewModel()
        advanceUntilIdle()

        val depositItem = CashHistoryItem(
            id = operationId.toString(),
            type = CashHistoryItemType.DEPOSIT,
            amount = BigDecimal("250.00"),
            notes = "Test deposit note",
            categoryName = null,
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )

        viewModel.showEditDialog(depositItem)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CashDialogType.EDIT_DEPOSIT, state.dialogType)
        assertEquals("250.00", state.dialogAmount)
        assertEquals("Test deposit note", state.dialogNotes)
        assertEquals(operationId, state.editingOperationId)
    }

    @Test
    fun `showEditDialog sets EDIT_WITHDRAW for withdrawal item`() = runTest {
        val operationId = UUID.randomUUID()
        val testOperation = CashOperation(
            id = operationId,
            localId = "local-2",
            locationId = null,
            type = CashOperationType.WITHDRAWAL,
            amount = BigDecimal("150.00"),
            categoryId = null,
            categoryName = null,
            notes = "Test withdrawal",
            deviceId = null,
            createdAt = Instant.now(),
            syncedAt = null
        )
        coEvery { cashRepository.getOperationById(operationId) } returns testOperation

        viewModel = createViewModel()
        advanceUntilIdle()

        val withdrawalItem = CashHistoryItem(
            id = operationId.toString(),
            type = CashHistoryItemType.WITHDRAWAL,
            amount = BigDecimal("150.00"),
            notes = "Test withdrawal",
            categoryName = null,
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )

        viewModel.showEditDialog(withdrawalItem)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CashDialogType.EDIT_WITHDRAW, state.dialogType)
        assertEquals("150.00", state.dialogAmount)
        assertEquals("Test withdrawal", state.dialogNotes)
    }

    @Test
    fun `showEditDialog sets EDIT_PAYMENT for payment item with category`() = runTest {
        val operationId = UUID.randomUUID()
        val testOperation = CashOperation(
            id = operationId,
            localId = "local-3",
            locationId = null,
            type = CashOperationType.PAYMENT,
            amount = BigDecimal("75.50"),
            categoryId = testCategoryId,
            categoryName = "Fuel",
            notes = "Gas station",
            deviceId = null,
            createdAt = Instant.now(),
            syncedAt = null
        )
        coEvery { cashRepository.getOperationById(operationId) } returns testOperation

        viewModel = createViewModel()
        advanceUntilIdle()

        val paymentItem = CashHistoryItem(
            id = operationId.toString(),
            type = CashHistoryItemType.PAYMENT,
            amount = BigDecimal("75.50"),
            notes = "Gas station",
            categoryName = "Fuel",
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )

        viewModel.showEditDialog(paymentItem)
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CashDialogType.EDIT_PAYMENT, state.dialogType)
        assertEquals("75.50", state.dialogAmount)
        assertEquals("Gas station", state.dialogNotes)
        assertEquals(testCategoryId, state.dialogCategoryId)
    }

    @Test
    fun `showEditDialog does nothing for purchase item`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val purchaseItem = CashHistoryItem(
            id = "purchase_loc_2024-01-01",
            type = CashHistoryItemType.PURCHASE,
            amount = BigDecimal("500"),
            notes = null,
            categoryName = null,
            itemCount = 10,
            weightKg = BigDecimal("50"),
            createdAt = Instant.now(),
            batchCount = 2
        )

        viewModel.showEditDialog(purchaseItem)
        advanceUntilIdle()

        assertEquals(CashDialogType.NONE, viewModel.uiState.value.dialogType)
    }

    @Test
    fun `showEditDialog does nothing for sale item`() = runTest {
        viewModel = createViewModel()
        advanceUntilIdle()

        val saleItem = CashHistoryItem(
            id = "sale_loc_2024-01-01",
            type = CashHistoryItemType.SALE,
            amount = BigDecimal("800"),
            notes = null,
            categoryName = null,
            itemCount = 15,
            weightKg = BigDecimal("75"),
            createdAt = Instant.now(),
            batchCount = 3
        )

        viewModel.showEditDialog(saleItem)
        advanceUntilIdle()

        assertEquals(CashDialogType.NONE, viewModel.uiState.value.dialogType)
    }

    @Test
    fun `confirmEdit calls updateOperation with correct parameters`() = runTest {
        val operationId = UUID.randomUUID()
        val testOperation = CashOperation(
            id = operationId,
            localId = "local-1",
            locationId = null,
            type = CashOperationType.DEPOSIT,
            amount = BigDecimal("100.00"),
            categoryId = null,
            categoryName = null,
            notes = "Original note",
            deviceId = null,
            createdAt = Instant.now(),
            syncedAt = null
        )
        coEvery { cashRepository.getOperationById(operationId) } returns testOperation
        coEvery { cashRepository.updateOperation(any(), any(), any(), any()) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        val depositItem = CashHistoryItem(
            id = operationId.toString(),
            type = CashHistoryItemType.DEPOSIT,
            amount = BigDecimal("100.00"),
            notes = "Original note",
            categoryName = null,
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )

        viewModel.showEditDialog(depositItem)
        advanceUntilIdle()

        // Modify the values
        viewModel.onAmountChange("200")
        viewModel.onNotesChange("Updated note")
        viewModel.confirmEdit()
        advanceUntilIdle()

        coVerify { 
            cashRepository.updateOperation(
                id = operationId,
                amount = BigDecimal("200"),
                categoryId = null,
                notes = "Updated note"
            )
        }
    }

    @Test
    fun `confirmEdit dismisses dialog on success`() = runTest {
        val operationId = UUID.randomUUID()
        val testOperation = CashOperation(
            id = operationId,
            localId = "local-1",
            locationId = null,
            type = CashOperationType.DEPOSIT,
            amount = BigDecimal("100.00"),
            categoryId = null,
            categoryName = null,
            notes = null,
            deviceId = null,
            createdAt = Instant.now(),
            syncedAt = null
        )
        coEvery { cashRepository.getOperationById(operationId) } returns testOperation
        coEvery { cashRepository.updateOperation(any(), any(), any(), any()) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        val depositItem = CashHistoryItem(
            id = operationId.toString(),
            type = CashHistoryItemType.DEPOSIT,
            amount = BigDecimal("100.00"),
            notes = null,
            categoryName = null,
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )

        viewModel.showEditDialog(depositItem)
        advanceUntilIdle()
        viewModel.onAmountChange("150")
        viewModel.confirmEdit()
        advanceUntilIdle()

        val state = viewModel.uiState.value
        assertEquals(CashDialogType.NONE, state.dialogType)
        assertNull(state.editingOperationId)
    }

    @Test
    fun `confirmEdit sets error on failure`() = runTest {
        val operationId = UUID.randomUUID()
        val testOperation = CashOperation(
            id = operationId,
            localId = "local-1",
            locationId = null,
            type = CashOperationType.DEPOSIT,
            amount = BigDecimal("100.00"),
            categoryId = null,
            categoryName = null,
            notes = null,
            deviceId = null,
            createdAt = Instant.now(),
            syncedAt = null
        )
        coEvery { cashRepository.getOperationById(operationId) } returns testOperation
        coEvery { cashRepository.updateOperation(any(), any(), any(), any()) } throws RuntimeException("Update failed")

        viewModel = createViewModel()
        advanceUntilIdle()

        val depositItem = CashHistoryItem(
            id = operationId.toString(),
            type = CashHistoryItemType.DEPOSIT,
            amount = BigDecimal("100.00"),
            notes = null,
            categoryName = null,
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )

        viewModel.showEditDialog(depositItem)
        advanceUntilIdle()
        viewModel.onAmountChange("150")
        viewModel.confirmEdit()
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.error)
    }

    @Test
    fun `confirmEdit updates payment with new category`() = runTest {
        val operationId = UUID.randomUUID()
        val newCategoryId = UUID.randomUUID()
        val testOperation = CashOperation(
            id = operationId,
            localId = "local-3",
            locationId = null,
            type = CashOperationType.PAYMENT,
            amount = BigDecimal("50.00"),
            categoryId = testCategoryId,
            categoryName = "Fuel",
            notes = null,
            deviceId = null,
            createdAt = Instant.now(),
            syncedAt = null
        )
        coEvery { cashRepository.getOperationById(operationId) } returns testOperation
        coEvery { cashRepository.updateOperation(any(), any(), any(), any()) } returns Unit

        viewModel = createViewModel()
        advanceUntilIdle()

        val paymentItem = CashHistoryItem(
            id = operationId.toString(),
            type = CashHistoryItemType.PAYMENT,
            amount = BigDecimal("50.00"),
            notes = null,
            categoryName = "Fuel",
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )

        viewModel.showEditDialog(paymentItem)
        advanceUntilIdle()

        // Change category
        viewModel.onCategorySelect(newCategoryId)
        viewModel.onAmountChange("75")
        viewModel.confirmEdit()
        advanceUntilIdle()

        coVerify { 
            cashRepository.updateOperation(
                id = operationId,
                amount = BigDecimal("75"),
                categoryId = newCategoryId,
                notes = null
            )
        }
    }

    @Test
    fun `dismissDialog clears editing state`() = runTest {
        val operationId = UUID.randomUUID()
        val testOperation = CashOperation(
            id = operationId,
            localId = "local-1",
            locationId = null,
            type = CashOperationType.DEPOSIT,
            amount = BigDecimal("100.00"),
            categoryId = null,
            categoryName = null,
            notes = "Test",
            deviceId = null,
            createdAt = Instant.now(),
            syncedAt = null
        )
        coEvery { cashRepository.getOperationById(operationId) } returns testOperation

        viewModel = createViewModel()
        advanceUntilIdle()

        val depositItem = CashHistoryItem(
            id = operationId.toString(),
            type = CashHistoryItemType.DEPOSIT,
            amount = BigDecimal("100.00"),
            notes = "Test",
            categoryName = null,
            itemCount = null,
            weightKg = null,
            createdAt = Instant.now(),
            batchCount = null
        )

        viewModel.showEditDialog(depositItem)
        advanceUntilIdle()

        assertNotNull(viewModel.uiState.value.editingOperationId)

        viewModel.dismissDialog()

        val state = viewModel.uiState.value
        assertEquals(CashDialogType.NONE, state.dialogType)
        assertNull(state.editingOperationId)
        assertNull(state.editingOperationType)
        assertEquals("", state.dialogAmount)
        assertEquals("", state.dialogNotes)
    }

    // ==================== Location/Tab Switching Tests ====================

    private val testLocationId1 = UUID.randomUUID()
    private val testLocationId2 = UUID.randomUUID()
    private val testLocations = listOf(
        Location(
            id = testLocationId1,
            name = "Location 1",
            type = com.zagot.zagotplus.domain.model.LocationType.KIOSK,
            createdAt = Instant.now()
        ),
        Location(
            id = testLocationId2,
            name = "Location 2",
            type = com.zagot.zagotplus.domain.model.LocationType.MOBILE,
            createdAt = Instant.now()
        )
    )

    private fun setupLocationMocks() {
        every { locationRepository.getAllLocations() } returns flowOf(testLocations)
        every { cashRepository.getBalance(any()) } returns flowOf(BigDecimal("1000.00"))
        every { cashRepository.getDailyChange(any(), any()) } returns flowOf(BigDecimal("100.00"))
        every { cashRepository.getDailyDeposits(any(), any()) } returns flowOf(BigDecimal("50.00"))
        coEvery { cashRepository.getTotalHistoryCountByLocation(any()) } returns 0
        coEvery { cashRepository.getCashHistoryByLocationPaged(any(), any(), any()) } returns emptyList()
    }

    @Test
    fun `selectLocation updates selectedLocationId`() = runTest {
        setupLocationMocks()
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId2)
        advanceUntilIdle()

        assertEquals(testLocationId2, viewModel.uiState.value.selectedLocationId)
    }

    @Test
    fun `selectLocation sets isLoading to true initially`() = runTest {
        setupLocationMocks()
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId2)
        // Before advanceUntilIdle, isLoading should be true
        assertTrue(viewModel.uiState.value.isLoading)
    }

    @Test
    fun `selectTotalView sets selectedLocationId to null`() = runTest {
        setupLocationMocks()
        viewModel = createViewModel()
        advanceUntilIdle()

        // First select a location
        viewModel.selectLocation(testLocationId1)
        advanceUntilIdle()
        
        // Then switch to total view
        viewModel.selectTotalView()
        advanceUntilIdle()

        assertNull(viewModel.uiState.value.selectedLocationId)
    }

    @Test
    fun `isTotalsView is true when selectedLocationId is null`() = runTest {
        setupLocationMocks()
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectTotalView()
        advanceUntilIdle()

        assertTrue(viewModel.uiState.value.isTotalsView)
    }

    @Test
    fun `isTotalsView is false when location is selected`() = runTest {
        setupLocationMocks()
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId1)
        advanceUntilIdle()

        assertFalse(viewModel.uiState.value.isTotalsView)
    }

    @Test
    fun `selectLocation loads location-specific balance`() = runTest {
        setupLocationMocks()
        every { cashRepository.getBalance(testLocationId1) } returns flowOf(BigDecimal("2500.00"))
        
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId1)
        advanceUntilIdle()

        assertEquals(BigDecimal("2500.00"), viewModel.uiState.value.balance)
    }

    @Test
    fun `locations are loaded on initialization`() = runTest {
        setupLocationMocks()
        viewModel = createViewModel()
        advanceUntilIdle()

        assertEquals(2, viewModel.uiState.value.locations.size)
        assertEquals("Location 1", viewModel.uiState.value.locations[0].name)
        assertEquals("Location 2", viewModel.uiState.value.locations[1].name)
    }

    @Test
    fun `transferDestinations excludes currently selected location`() = runTest {
        setupLocationMocks()
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId1)
        advanceUntilIdle()

        val destinations = viewModel.uiState.value.transferDestinations
        assertEquals(1, destinations.size)
        assertEquals(testLocationId2, destinations[0].id)
    }

    // ==================== Transfer Tests ====================

    @Test
    fun `showTransferDialog sets dialog type`() = runTest {
        setupLocationMocks()
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId1)
        advanceUntilIdle()
        viewModel.showTransferDialog()

        assertEquals(CashDialogType.TRANSFER, viewModel.uiState.value.dialogType)
    }

    @Test
    fun `onTransferDestinationSelect updates destination`() = runTest {
        setupLocationMocks()
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId1)
        advanceUntilIdle()
        viewModel.showTransferDialog()
        viewModel.onTransferDestinationSelect(testLocationId2)

        assertEquals(testLocationId2, viewModel.uiState.value.dialogTransferDestinationId)
    }

    @Test
    fun `canConfirmTransfer is true when amount and destination are valid`() = runTest {
        setupLocationMocks()
        every { cashRepository.getBalance(testLocationId1) } returns flowOf(BigDecimal("1000.00"))
        
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId1)
        advanceUntilIdle()
        viewModel.showTransferDialog()
        viewModel.onAmountChange("500")
        viewModel.onTransferDestinationSelect(testLocationId2)

        assertTrue(viewModel.uiState.value.canConfirmTransfer)
    }

    @Test
    fun `canConfirmTransfer is false when amount exceeds balance`() = runTest {
        setupLocationMocks()
        every { cashRepository.getBalance(testLocationId1) } returns flowOf(BigDecimal("100.00"))
        
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId1)
        advanceUntilIdle()
        viewModel.showTransferDialog()
        viewModel.onAmountChange("500")
        viewModel.onTransferDestinationSelect(testLocationId2)

        assertFalse(viewModel.uiState.value.canConfirmTransfer)
    }

    @Test
    fun `canConfirmTransfer is false when destination not selected`() = runTest {
        setupLocationMocks()
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId1)
        advanceUntilIdle()
        viewModel.showTransferDialog()
        viewModel.onAmountChange("100")

        assertFalse(viewModel.uiState.value.canConfirmTransfer)
    }

    @Test
    fun `confirmTransfer calls repository`() = runTest {
        setupLocationMocks()
        every { cashRepository.getBalance(testLocationId1) } returns flowOf(BigDecimal("1000.00"))
        coEvery { cashRepository.transfer(any(), any(), any(), any()) } returns Unit
        
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId1)
        advanceUntilIdle()
        viewModel.showTransferDialog()
        viewModel.onAmountChange("250")
        viewModel.onTransferDestinationSelect(testLocationId2)
        viewModel.onNotesChange("Transfer notes")
        viewModel.confirmTransfer()
        advanceUntilIdle()

        coVerify { 
            cashRepository.transfer(
                sourceLocationId = testLocationId1,
                destinationLocationId = testLocationId2,
                amount = BigDecimal("250"),
                notes = "Transfer notes"
            )
        }
    }

    @Test
    fun `confirmTransfer dismisses dialog on success`() = runTest {
        setupLocationMocks()
        every { cashRepository.getBalance(testLocationId1) } returns flowOf(BigDecimal("1000.00"))
        coEvery { cashRepository.transfer(any(), any(), any(), any()) } returns Unit
        
        viewModel = createViewModel()
        advanceUntilIdle()

        viewModel.selectLocation(testLocationId1)
        advanceUntilIdle()
        viewModel.showTransferDialog()
        viewModel.onAmountChange("100")
        viewModel.onTransferDestinationSelect(testLocationId2)
        viewModel.confirmTransfer()
        advanceUntilIdle()

        assertEquals(CashDialogType.NONE, viewModel.uiState.value.dialogType)
    }
}
