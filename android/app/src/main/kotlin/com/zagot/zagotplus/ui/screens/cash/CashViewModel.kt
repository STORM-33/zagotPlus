package com.zagot.zagotplus.ui.screens.cash

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.IoDispatcher
import com.zagot.zagotplus.domain.model.CashHistoryItem
import com.zagot.zagotplus.domain.model.CashHistoryItemType
import com.zagot.zagotplus.domain.model.DayCashGroup
import com.zagot.zagotplus.domain.model.ExpenseCategory
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.repository.CashRepository
import com.zagot.zagotplus.domain.repository.LocationRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.math.BigDecimal
import java.time.LocalDate
import java.time.ZoneId
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
    TRANSFER,
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
    val dailyAddition: BigDecimal = BigDecimal.ZERO, // Today's deposits only (for color logic)
    val historyItems: List<CashHistoryItem> = emptyList(),
    val dayGroups: List<DayCashGroup> = emptyList(), // Grouped by day for collapsible panels
    val expandedDays: Set<LocalDate> = emptySet(), // Which days are expanded
    val categories: List<ExpenseCategory> = emptyList(),
    val locations: List<Location> = emptyList(),
    val selectedLocationId: UUID? = null, // null = totals view (all locations)
    val selectedDate: LocalDate = LocalDate.now(),
    val dialogType: CashDialogType = CashDialogType.NONE,
    val dialogAmount: String = "",
    val dialogNotes: String = "",
    val dialogCategoryId: UUID? = null,
    val dialogTransferDestinationId: UUID? = null, // For transfer dialog
    val newCategoryName: String = "",
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,
    val hasMoreItems: Boolean = true,
    val totalItemsCount: Int = 0,
    val editingOperationId: UUID? = null,
    val editingOperationType: CashHistoryItemType? = null
) {
    val canConfirmDeposit: Boolean
        get() = dialogAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

    // Allow negative balance - no balance check
    val canConfirmWithdraw: Boolean
        get() = dialogAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

    // Allow negative balance - no balance check
    val canConfirmPayment: Boolean
        get() = dialogAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

    // Allow negative balance - no balance check
    val canConfirmTransfer: Boolean
        get() = dialogAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true 
            && dialogTransferDestinationId != null

    val isTotalsView: Boolean
        get() = selectedLocationId == null

    val selectedLocationName: String?
        get() = selectedLocationId?.let { id -> locations.find { it.id == id }?.name }
    
    /** Locations available as transfer destinations (excludes current location) */
    val transferDestinations: List<Location>
        get() = locations.filter { it.id != selectedLocationId }
}

/**
 * Helper data class for debounced balance updates.
 * Enables distinctUntilChanged() to work properly with combined flow emissions.
 */
private data class BalanceUpdate(
    val balance: BigDecimal,
    val dailyChange: BigDecimal,
    val dailyAddition: BigDecimal,
    val categories: List<ExpenseCategory>
)

@OptIn(FlowPreview::class)
@HiltViewModel
class CashViewModel @Inject constructor(
    private val cashRepository: CashRepository,
    private val locationRepository: LocationRepository,
    @IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : ViewModel() {

    private val _uiState = MutableStateFlow(CashUiState())
    val uiState: StateFlow<CashUiState> = _uiState.asStateFlow()

    /** Job for balance/categories flow collection - cancelled when location changes */
    private var balanceCollectionJob: Job? = null

    /** Last known cash operation count for change detection */
    private var lastKnownCashSignal: String = ""

    companion object {
        private const val TAG = "CashViewModel"
        private const val PAGE_SIZE = 20
        private val MAX_AMOUNT = java.math.BigDecimal("999999999.99")
        // Pre-compiled regex patterns for input validation (avoid recompilation on each keystroke)
        private val AMOUNT_PATTERN = Regex("^\\d+\\.?\\d*$")
        private val DECIMAL_START_PATTERN = Regex("^0\\.\\d*$")
    }

    init {
        Log.d(TAG, "CashViewModel init")
        loadData()
        observeCashChanges()
    }

    /**
     * Observe cash operation count changes to auto-refresh when sync writes new operations.
     * Follows the same pattern as HistoryViewModel.observeBatchChanges().
     */
    private fun observeCashChanges() {
        viewModelScope.launch {
            cashRepository.observeCashChangeSignal()
                .distinctUntilChanged()
                .collect { signal ->
                    if (lastKnownCashSignal.isNotEmpty() && signal != lastKnownCashSignal) {
                        refreshOperations()
                    }
                    lastKnownCashSignal = signal
                }
        }
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
        // Cancel any existing balance collection job to prevent race conditions
        balanceCollectionJob?.cancel()

        val selectedLocationId = _uiState.value.selectedLocationId
        Log.d(TAG, "loadHistoryAndBalance called, selectedLocationId=$selectedLocationId")

        viewModelScope.launch {
            try {
                // Load history items on IO thread to avoid blocking main thread
                val (totalCount, items, dayGroups) = withContext(ioDispatcher) {
                    val count: Int
                    val historyItems: List<CashHistoryItem>

                    if (selectedLocationId == null) {
                        // Totals view - all locations, transfers are excluded in SQL query
                        count = cashRepository.getTotalHistoryCount()
                        Log.d(TAG, "TOTALS VIEW: totalCount=$count")
                        historyItems = cashRepository.getCashHistoryPaged(PAGE_SIZE, 0)
                        Log.d(TAG, "TOTALS VIEW: fetched ${historyItems.size} items from repository")
                    } else {
                        // Specific location - show all including transfers
                        count = cashRepository.getTotalHistoryCountByLocation(selectedLocationId)
                        historyItems = cashRepository.getCashHistoryByLocationPaged(selectedLocationId, PAGE_SIZE, 0)
                        Log.d(TAG, "LOCATION VIEW: locationId=$selectedLocationId, totalCount=$count, fetched ${historyItems.size} items")
                    }

                    // Group items by day (also on IO thread since it's CPU work)
                    val groups = groupItemsByDay(historyItems)
                    Log.d(TAG, "Grouped into ${groups.size} day groups")
                    
                    Triple(count, historyItems, groups)
                }

                // Update state with history items on main thread
                _uiState.update { currentState ->
                    Log.d(TAG, "Updating state with ${items.size} history items")
                    currentState.copy(
                        historyItems = items,
                        dayGroups = dayGroups,
                        totalItemsCount = totalCount,
                        hasMoreItems = items.size < totalCount,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error loading history", e)
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка завантаження",
                        isLoading = false
                    )
                }
            }
        }

        // Start balance/categories collection in a separate tracked job
        balanceCollectionJob = viewModelScope.launch {
            try {
                if (selectedLocationId == null) {
                    combine(
                        cashRepository.getTotalBalance(),
                        cashRepository.getDailyChangeGlobal(LocalDate.now()),
                        cashRepository.getDailyDepositsGlobal(LocalDate.now()),
                        cashRepository.getActiveCategories()
                    ) { balance, dailyChange, dailyAddition, categories ->
                        BalanceUpdate(balance, dailyChange, dailyAddition, categories)
                    }
                        // Debounce disabled for now - causes test timing issues
                        // TODO: Re-enable with proper test infrastructure
                        // .debounce(50) // Coalesce rapid emissions during sync
                        .distinctUntilChanged()
                        .collect { update ->
                            _uiState.update { currentState ->
                                currentState.copy(
                                    balance = update.balance,
                                    dailyChange = update.dailyChange,
                                    dailyAddition = update.dailyAddition,
                                    categories = update.categories
                                )
                            }
                        }
                } else {
                    combine(
                        cashRepository.getBalance(selectedLocationId),
                        cashRepository.getDailyChange(selectedLocationId, LocalDate.now()),
                        cashRepository.getDailyDeposits(selectedLocationId, LocalDate.now()),
                        cashRepository.getActiveCategories()
                    ) { balance, dailyChange, dailyAddition, categories ->
                        BalanceUpdate(balance, dailyChange, dailyAddition, categories)
                    }
                        // Debounce disabled for now - causes test timing issues
                        // TODO: Re-enable with proper test infrastructure
                        // .debounce(50) // Coalesce rapid emissions during sync
                        .distinctUntilChanged()
                        .collect { update ->
                            _uiState.update { currentState ->
                                currentState.copy(
                                    balance = update.balance,
                                    dailyChange = update.dailyChange,
                                    dailyAddition = update.dailyAddition,
                                    categories = update.categories
                                )
                            }
                        }
                }
            } catch (e: CancellationException) {
                // Expected when switching locations, don't treat as error
                throw e
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Помилка завантаження балансу") }
            }
        }
    }
    
    /** Group history items by day for collapsible panels */
    private fun groupItemsByDay(items: List<CashHistoryItem>): List<DayCashGroup> {
        val zone = ZoneId.systemDefault()
        return items
            .groupBy { it.createdAt.atZone(zone).toLocalDate() }
            .map { (date, dayItems) ->
                DayCashGroup(
                    date = date,
                    items = dayItems,
                    totalAmount = dayItems.sumOf { it.signedAmount }
                )
            }
            .sortedByDescending { it.date }
    }
    
    /** Toggle a day's expanded state */
    fun toggleDayExpansion(date: LocalDate) {
        _uiState.update { state ->
            val newExpanded = if (date in state.expandedDays) {
                state.expandedDays - date
            } else {
                state.expandedDays + date
            }
            state.copy(expandedDays = newExpanded)
        }
    }

    fun loadMoreOperations() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMoreItems) return
        Log.d(TAG, "loadMoreOperations called, offset=${state.historyItems.size}")

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            try {
                val offset = state.historyItems.size
                val moreItems = withContext(ioDispatcher) {
                    if (state.selectedLocationId == null) {
                        // Totals view - transfers are excluded in SQL query
                        cashRepository.getCashHistoryPaged(PAGE_SIZE, offset)
                    } else {
                        cashRepository.getCashHistoryByLocationPaged(state.selectedLocationId, PAGE_SIZE, offset)
                    }
                }
                Log.d(TAG, "loadMoreOperations: loaded ${moreItems.size} more items")
                _uiState.update { currentState ->
                    val newItems = currentState.historyItems + moreItems
                    val newDayGroups = groupItemsByDay(newItems)
                    currentState.copy(
                        historyItems = newItems,
                        dayGroups = newDayGroups,
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
        Log.d(TAG, "refreshOperations called")
        viewModelScope.launch {
            try {
                val state = _uiState.value
                val currentCount = state.historyItems.size.coerceAtLeast(PAGE_SIZE)
                Log.d(TAG, "refreshOperations: selectedLocationId=${state.selectedLocationId}, currentCount=$currentCount")

                val (totalCount, items, dayGroups) = withContext(ioDispatcher) {
                    val count: Int
                    val historyItems: List<CashHistoryItem>
                    
                    if (state.selectedLocationId == null) {
                        count = cashRepository.getTotalHistoryCount()
                        // Totals view - transfers are excluded in SQL query
                        historyItems = cashRepository.getCashHistoryPaged(currentCount, 0)
                        Log.d(TAG, "refreshOperations TOTALS: totalCount=$count, items=${historyItems.size}")
                    } else {
                        count = cashRepository.getTotalHistoryCountByLocation(state.selectedLocationId)
                        historyItems = cashRepository.getCashHistoryByLocationPaged(state.selectedLocationId, currentCount, 0)
                        Log.d(TAG, "refreshOperations LOCATION: totalCount=$count, items=${historyItems.size}")
                    }
                    
                    Triple(count, historyItems, groupItemsByDay(historyItems))
                }
                
                _uiState.update { currentState ->
                    currentState.copy(
                        historyItems = items,
                        dayGroups = dayGroups,
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
    
    fun showTransferDialog() {
        _uiState.update {
            it.copy(
                dialogType = CashDialogType.TRANSFER,
                dialogAmount = "",
                dialogNotes = "",
                dialogTransferDestinationId = null
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
                dialogTransferDestinationId = null,
                newCategoryName = "",
                editingOperationId = null,
                editingOperationType = null
            )
        }
    }

    fun onAmountChange(amount: String) {
        // Allow empty input or valid positive decimal format
        // Rejects: "." alone, leading zeros like "00.5", negative values
        // Uses pre-compiled regex patterns from companion object
        val isValidFormat = amount.isEmpty() || 
            (AMOUNT_PATTERN.matches(amount) && !amount.startsWith("0") || amount == "0" || DECIMAL_START_PATTERN.matches(amount))
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
    
    fun onTransferDestinationSelect(locationId: UUID?) {
        _uiState.update { it.copy(dialogTransferDestinationId = locationId) }
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
                _uiState.update { it.copy(isSaving = false, successMessage = "✅ Поповнення на ${amount}₴ збережено") }
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

        // Negative balance allowed - no check for insufficient funds

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                cashRepository.withdraw(
                    locationId = state.selectedLocationId,
                    amount = amount,
                    notes = state.dialogNotes.takeIf { it.isNotBlank() }
                )
                dismissDialog()
                _uiState.update { it.copy(isSaving = false, successMessage = "✅ Видача ${amount}₴ збережена") }
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

        // Negative balance allowed - no check for insufficient funds

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
                _uiState.update { it.copy(isSaving = false, successMessage = "✅ Витрату ${amount}₴ збережено") }
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
    
    fun confirmTransfer() {
        val state = _uiState.value
        val amount = state.dialogAmount.toBigDecimalOrNull() ?: return
        val sourceLocationId = state.selectedLocationId ?: return
        val destinationLocationId = state.dialogTransferDestinationId ?: return

        // Negative balance allowed - no check for insufficient funds

        val destName = state.locations.find { it.id == destinationLocationId }?.name ?: ""

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                cashRepository.transfer(
                    sourceLocationId = sourceLocationId,
                    destinationLocationId = destinationLocationId,
                    amount = amount,
                    notes = state.dialogNotes.takeIf { it.isNotBlank() }
                )
                dismissDialog()
                _uiState.update { it.copy(isSaving = false, successMessage = "✅ Переказ ${amount}₴ → $destName виконано") }
                refreshOperations()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка переказу",
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
                _uiState.update { it.copy(successMessage = "✅ Категорію видалено") }
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Помилка видалення категорії") }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    /**
     * Select a specific location to filter cash history and balance.
     */
    fun selectLocation(locationId: UUID) {
        Log.d(TAG, "selectLocation called: locationId=$locationId")
        _uiState.update { it.copy(selectedLocationId = locationId, isLoading = true) }
        loadHistoryAndBalance()
    }

    /**
     * Switch to totals view showing all locations.
     */
    fun selectTotalView() {
        Log.d(TAG, "selectTotalView called")
        _uiState.update { it.copy(selectedLocationId = null, isLoading = true) }
        loadHistoryAndBalance()
    }

    /**
     * Show edit dialog for a cash operation.
     * Only manual operations (deposit, withdrawal, payment) can be edited.
     */
    fun showEditDialog(item: CashHistoryItem) {
        // Only allow editing manual cash operations (not purchases/sales/transfers)
        if (item.type == CashHistoryItemType.PURCHASE || 
            item.type == CashHistoryItemType.SALE ||
            item.type == CashHistoryItemType.TRANSFER) {
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
                _uiState.update { it.copy(isSaving = false, successMessage = "✅ Операцію оновлено") }
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
     * Transfers, purchases, and sales cannot be edited.
     */
    fun canModifyItem(item: CashHistoryItem): Boolean {
        return item.type != CashHistoryItemType.PURCHASE && 
               item.type != CashHistoryItemType.SALE &&
               item.type != CashHistoryItemType.TRANSFER
    }
}
