package com.zagot.zagotplus.ui.screens.cash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.domain.model.CashHistoryItem
import com.zagot.zagotplus.domain.model.ExpenseCategory
import com.zagot.zagotplus.domain.repository.CashRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
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
    CATEGORIES
}

/**
 * UI state for the cash register screen.
 */
data class CashUiState(
    val balance: BigDecimal = BigDecimal.ZERO,
    val dailyChange: BigDecimal = BigDecimal.ZERO,
    val historyItems: List<CashHistoryItem> = emptyList(),
    val categories: List<ExpenseCategory> = emptyList(),
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
    val totalItemsCount: Int = 0
) {
    val canConfirmDeposit: Boolean
        get() = dialogAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

    val canConfirmWithdraw: Boolean
        get() = dialogAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO && it <= balance } == true

    val canConfirmPayment: Boolean
        get() = dialogAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO && it <= balance } == true
}

@HiltViewModel
class CashViewModel @Inject constructor(
    private val cashRepository: CashRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashUiState())
    val uiState: StateFlow<CashUiState> = _uiState.asStateFlow()

    companion object {
        private const val PAGE_SIZE = 20
    }

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Load initial page of history items (cash operations + purchases + sales)
                val totalCount = cashRepository.getTotalHistoryCount()
                val initialItems = cashRepository.getCashHistoryPaged(PAGE_SIZE, 0)
                
                // Collect balance and categories as flows
                combine(
                    cashRepository.getTotalBalance(),
                    cashRepository.getDailyChangeGlobal(LocalDate.now()),
                    cashRepository.getActiveCategories()
                ) { balance, dailyChange, categories ->
                    _uiState.update { state ->
                        state.copy(
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
                val moreItems = cashRepository.getCashHistoryPaged(PAGE_SIZE, offset)
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
                val totalCount = cashRepository.getTotalHistoryCount()
                val currentCount = _uiState.value.historyItems.size.coerceAtLeast(PAGE_SIZE)
                val items = cashRepository.getCashHistoryPaged(currentCount, 0)
                _uiState.update { state ->
                    state.copy(
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
                newCategoryName = ""
            )
        }
    }

    fun onAmountChange(amount: String) {
        if (amount.isEmpty() || amount.matches(Regex("^\\d*\\.?\\d*$"))) {
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
                    locationId = null,
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
                    locationId = null,
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
                    locationId = null,
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
}
