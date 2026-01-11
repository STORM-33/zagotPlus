package com.zagot.zagotplus.ui.screens.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionFilter
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import javax.inject.Inject

/**
 * Represents a sale item for display in the dashboard.
 */
data class SaleDisplayItem(
    val transaction: Transaction,
    val productName: String
)

/**
 * UI state for the main sale screen showing today's sales.
 */
data class SaleUiState(
    val todaysSales: List<SaleDisplayItem> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToNewSale: Boolean = false
)

@HiltViewModel
class SaleViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val productRepository: ProductRepository,
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SaleUiState())
    val uiState: StateFlow<SaleUiState> = _uiState.asStateFlow()

    init {
        loadTodaysSales()
    }

    private fun loadTodaysSales() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Get start of today
                val todayStart = LocalDate.now()
                    .atStartOfDay(ZoneId.systemDefault())
                    .toInstant()
                
                val locationId = devicePreferences.getSelectedLocationId()
                
                val filter = TransactionFilter(
                    types = setOf(TransactionType.SALE),
                    locationId = locationId,
                    startDate = todayStart,
                    endDate = null
                )
                
                val transactions = transactionRepository.getFilteredTransactions(
                    filter = filter,
                    limit = 100,
                    offset = 0
                )
                
                // Load product names (use first() to get snapshot)
                val productList = productRepository.getActiveProducts().first()
                val productNames = productList.associateBy({ it.id }, { it.name })
                
                val salesWithNames = transactions.map { transaction ->
                    SaleDisplayItem(
                        transaction = transaction,
                        productName = transaction.productId?.let { productNames[it] } ?: "Невідомий товар"
                    )
                }.sortedByDescending { it.transaction.createdAt }
                
                _uiState.update {
                    it.copy(
                        todaysSales = salesWithNames,
                        isLoading = false
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка завантаження даних",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun onNewSaleClick() {
        _uiState.update { it.copy(navigateToNewSale = true) }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigateToNewSale = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        loadTodaysSales()
    }
}
