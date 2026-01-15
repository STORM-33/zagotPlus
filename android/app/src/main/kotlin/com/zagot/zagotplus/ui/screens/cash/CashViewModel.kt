package com.zagot.zagotplus.ui.screens.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.domain.model.CashHistoryItem
import com.zagot.zagotplus.domain.model.CashHistoryItemType
import com.zagot.zagotplus.domain.model.ExpenseCategory
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.repository.CashRepository
import com.zagot.zagotplus.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID
import javax.inject.Inject

/**
 * Dialog type for cash operations.
 */
enum class CashDialogType {
    NONE,
    DEPOSIT,
    WITHDRAW,
    PAYMENT,
    CATEGORIES,
    EDIT_DEPOSIT,
    EDIT_WITHDRAW,
    EDIT_PAYMENT
}

/**
 * UI state for the cash register screen.
 */
data class CashUiState(
    val balance: BigDecimal = BigDecimal.ZERO,
    val dailyChange: BigDecimal = BigDecimal.ZERO,
    val historyItems: List<CashHistoryItem> = emptyList(),
    val categories: List<ExpenseCategory> = emptyList(),
    val locations: List<Location> = emptyList(),
    val selectedLocationId: UUID? = null, // null = totals view (all locations)
    val selectedDate: LocalDate = LocalDate.now(),
    val dialogType: CashDialogType = CashDialogType.NONE,
    val dialogAmount: String = "",
    val dialogNotes: String = "",
    val dialogCategoryId: UUID? = null,
    val newCategoryName: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val hasMoreItems: Boolean = true,
    val totalItemsCount: Int = 0,
    val editingOperationId: UUID? = null,
    val editingOperationType: CashHistoryItemType? = null
) {
    val canConfirmDeposit: Boolean
        get() = dialogAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

    val canConfirmWithdraw: Boolean
        get() = dialogAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO && it <= balance } == true

    val canConfirmPayment: Boolean
        get() = dialogAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO && it <= balance } == true

    val isTotalsView: Boolean
        get() = selectedLocationId == null

    val selectedLocationName: String?
        get() = selectedLocationId?.let { id -> locations.find { it.id == id }?.name }
}

@HiltViewModel
class CashViewModel @Inject constructor(
    private val cashRepository: CashRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashUiState())
    val uiState: StateFlow<CashUiState> = _uiState.asStateFlow()

    companion object {
        private const val PAGE_SIZE = 20
        private val MAX_AMOUNT = java.math.BigDecimal("999999999.99")
    }

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Load locations first
                val locations = locationRepository.getAllLocations().first()
                _uiState.update { it.copy(locations = locations) }
                
                // Load history and balance based on selected location
                loadHistoryAndBalance()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка завантаження",
                        isLoading = false
                    )
                }
            }
        }
    }

    private fun loadHistoryAndBalance() {
        viewModelScope.launch {
            val state = _uiState.value
            val selectedLocationId = state.selectedLocationId
            
            try {
                // Load initial page of history items based on location
                val totalCount: Int
                val initialItems: List<CashHistoryItem>
                
                if (selectedLocationId == null) {
                    // Totals view - all locations
                    totalCount = cashRepository.getTotalHistoryCount()
                    initialItems = cashRepository.getCashHistoryPaged(PAGE_SIZE, 0)
                } else {
                    // Specific location
                    totalCount = cashRepository.getTotalHistoryCountByLocation(selectedLocationId)
                    initialItems = cashRepository.getCashHistoryByLocationPaged(selectedLocationId, PAGE_SIZE, 0)
                }
                
                // Collect balance and categories as flows based on location
                if (selectedLocationId == null) {
                    combine(
                        cashRepository.getTotalBalance(),
                        cashRepository.getDailyChangeGlobal(LocalDate.now()),
                        cashRepository.getActiveCategories()
                    ) { balance, dailyChange, categories ->
                        _uiState.update { currentState ->
                            currentState.copy(
                                balance = balance,
                                dailyChange = dailyChange,
                                historyItems = initialItems,
                                categories = categories,
                                isLoading = false,
                                totalItemsCount = totalCount,
                                hasMoreItems = initialItems.size < totalCount
                            )
                        }
                    }.collect { }
                } else {
                    combine(
                        cashRepository.getBalance(selectedLocationId),
                        cashRepository.getDailyChange(selectedLocationId, LocalDate.now()),
                        cashRepository.getActiveCategories()
                    ) { balance, dailyChange, categories ->
                        _uiState.update { currentState ->
                            currentState.copy(
                                balance = balance,
                                dailyChange = dailyChange,
                                historyItems = initialItems,
                                categories = categories,
                                isLoading = false,
                                totalItemsCount = totalCount,
                                hasMoreItems = initialItems.size < totalCount
                            )
                        }
                    }.collect { }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка завантаження",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun loadMoreOperations() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMoreItems) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val offset = state.historyItems.size
                val moreItems = if (state.selectedLocationId == null) {
                    cashRepository.getCashHistoryPaged(PAGE_SIZE, offset)
                } else {
                    cashRepository.getCashHistoryByLocationPaged(state.selectedLocationId, PAGE_SIZE, offset)
                }
                _uiState.update { currentState ->
                    val newItems = currentState.historyItems + moreItems
                    currentState.copy(
                        historyItems = newItems,
                        isLoadingMore = false,
                        hasMoreItems = newItems.size < currentState.totalItemsCount
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка завантаження",
                        isLoadingMore = false
                    )
                }
            }
        }
    }

    fun refreshOperations() {
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val totalCount: Int
                val items: List<CashHistoryItem>
                val currentCount = state.historyItems.size.coerceAtLeast(PAGE_SIZE)
                
                if (state.selectedLocationId == null) {
                    totalCount = cashRepository.getTotalHistoryCount()
                    items = cashRepository.getCashHistoryPaged(currentCount, 0)
                } else {
                    totalCount = cashRepository.getTotalHistoryCountByLocation(state.selectedLocationId)
                    items = cashRepository.getCashHistoryByLocationPaged(state.selectedLocationId, currentCount, 0)
                }
                
                _uiState.update { currentState ->
                    currentState.copy(
                        historyItems = items,
                        totalItemsCount = totalCount,
                        hasMoreItems = items.size < totalCount
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Помилка оновлення") }
            }
        }
    }

    fun showDepositDialog() {
        _uiState.update {
            it.copy(
                dialogType = CashDialogType.DEPOSIT,
                dialogAmount = "",
                dialogNotes = ""
            )
        }
    }

    fun showWithdrawDialog() {
        _uiState.update {
            it.copy(
                dialogType = CashDialogType.WITHDRAW,
                dialogAmount = "",
                dialogNotes = ""
            )
        }
    }

    fun showPaymentDialog() {
        _uiState.update {
            it.copy(
                dialogType = CashDialogType.PAYMENT,
                dialogAmount = "",
                dialogNotes = "",
                dialogCategoryId = null
            )
        }
    }

    fun showCategoriesDialog() {
        _uiState.update {
            it.copy(
                dialogType = CashDialogType.CATEGORIES,
                newCategoryName = ""
            )
        }
    }

    fun dismissDialog() {
        _uiState.update {
            it.copy(
                dialogType = CashDialogType.NONE,
                dialogAmount = "",
                dialogNotes = "",
                dialogCategoryId = null,
                newCategoryName = "",
                editingOperationId = null,
                editingOperationType = null
            )
        }
    }

    fun onAmountChange(amount: String) {
        // Allow empty input or valid positive decimal format
        // Rejects: "." alone, leading zeros like "00.5", negative values
        val isValidFormat = amount.isEmpty() || 
            (amount.matches(Regex("^\\d+\\.?\\d*$")) && !amount.startsWith("0") || amount == "0" || amount.matches(Regex("^0\\.\\d*$")))
        if (isValidFormat && (amount.toBigDecimalOrNull()?.let { it <= MAX_AMOUNT } ?: true)) {
            _uiState.update { it.copy(dialogAmount = amount) }
        }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(dialogNotes = notes) }
    }

    fun onCategorySelect(categoryId: UUID?) {
        _uiState.update { it.copy(dialogCategoryId = categoryId) }
    }

    fun onNewCategoryNameChange(name: String) {
        _uiState.update { it.copy(newCategoryName = name) }
    }

    fun confirmDeposit() {
        val state = _uiState.value
        val amount = state.dialogAmount.toBigDecimalOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                cashRepository.deposit(
                    locationId = state.selectedLocationId,
                    amount = amount,
                    notes = state.dialogNotes.takeIf { it.isNotBlank() }
                )
                dismissDialog()
                _uiState.update { it.copy(isSaving = false) }
                refreshOperations()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка поповнення",
                        isSaving = false
                    )
                }
            }
        }
    }

    fun confirmWithdraw() {
        val state = _uiState.value
        val amount = state.dialogAmount.toBigDecimalOrNull() ?: return

        if (amount > state.balance) {
            _uiState.update { it.copy(error = "Недостатньо коштів") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                cashRepository.withdraw(
                    locationId = state.selectedLocationId,
                    amount = amount,
                    notes = state.dialogNotes.takeIf { it.isNotBlank() }
                )
                dismissDialog()
                _uiState.update { it.copy(isSaving = false) }
                refreshOperations()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка виведення",
                        isSaving = false
                    )
                }
            }
        }
    }

    fun confirmPayment() {
        val state = _uiState.value
        val amount = state.dialogAmount.toBigDecimalOrNull() ?: return

        if (amount > state.balance) {
            _uiState.update { it.copy(error = "Недостатньо коштів") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                cashRepository.payment(
                    locationId = state.selectedLocationId,
                    amount = amount,
                    categoryId = state.dialogCategoryId,
                    notes = state.dialogNotes.takeIf { it.isNotBlank() }
                )
                dismissDialog()
                _uiState.update { it.copy(isSaving = false) }
                refreshOperations()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка оплати",
                        isSaving = false
                    )
                }
            }
        }
    }

    fun addCategory() {
        val name = _uiState.value.newCategoryName.trim()
        if (name.isBlank()) return

        viewModelScope.launch {
            try {
                cashRepository.createCategory(name)
                _uiState.update { it.copy(newCategoryName = "") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Помилка створення категорії") }
            }
        }
    }

    fun deactivateCategory(categoryId: UUID) {
        viewModelScope.launch {
            try {
                cashRepository.deactivateCategory(categoryId)
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Помилка видалення категорії") }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Select a specific location to filter cash history and balance.
     */
    fun selectLocation(locationId: UUID) {
        _uiState.update { it.copy(selectedLocationId = locationId, isLoading = true) }
        loadHistoryAndBalance()
    }

    /**
     * Switch to totals view showing all locations.
     */
    fun selectTotalView() {
        _uiState.update { it.copy(selectedLocationId = null, isLoading = true) }
        loadHistoryAndBalance()
    }

    /**
     * Show edit dialog for a cash operation.
     * Only manual operations (deposit, withdrawal, payment) can be edited.
     */
    fun showEditDialog(item: CashHistoryItem) {
        // Only allow editing manual cash operations (not purchases/sales)
        if (item.type == CashHistoryItemType.PURCHASE || item.type == CashHistoryItemType.SALE) {
            return
        }

        viewModelScope.launch {
            try {
                val operationId = UUID.fromString(item.id)
                val operation = cashRepository.getOperationById(operationId) ?: return@launch

                val dialogType = when (item.type) {
                    CashHistoryItemType.DEPOSIT -> CashDialogType.EDIT_DEPOSIT
                    CashHistoryItemType.WITHDRAWAL -> CashDialogType.EDIT_WITHDRAW
                    CashHistoryItemType.PAYMENT -> CashDialogType.EDIT_PAYMENT
                    else -> return@launch
                }

                _uiState.update {
                    it.copy(
                        dialogType = dialogType,
                        dialogAmount = operation.amount.toPlainString(),
                        dialogNotes = operation.notes ?: "",
                        dialogCategoryId = operation.categoryId,
                        editingOperationId = operationId,
                        editingOperationType = item.type
                    )
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Помилка завантаження операції") }
            }
        }
    }

    /**
     * Confirm edit of a cash operation.
     */
    fun confirmEdit() {
        val state = _uiState.value
        val operationId = state.editingOperationId ?: return
        val amount = state.dialogAmount.toBigDecimalOrNull() ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                cashRepository.updateOperation(
                    id = operationId,
                    amount = amount,
                    categoryId = state.dialogCategoryId,
                    notes = state.dialogNotes.takeIf { it.isNotBlank() }
                )
                dismissDialog()
                _uiState.update { it.copy(isSaving = false) }
                refreshOperations()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка оновлення",
                        isSaving = false
                    )
                }
            }
        }
    }

    /**
     * Check if a cash history item can be edited.
     * Only manual operations (deposit, withdrawal, payment) can be modified.
     */
    fun canModifyItem(item: CashHistoryItem): Boolean {
        return item.type != CashHistoryItemType.PURCHASE && item.type != CashHistoryItemType.SALE
    }
}
