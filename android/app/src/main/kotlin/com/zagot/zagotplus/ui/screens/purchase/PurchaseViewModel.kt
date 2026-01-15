package com.zagot.zagotplus.ui.screens.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.domain.model.ProductDailyTotal
import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.repository.CashRepository
import com.zagot.zagotplus.domain.repository.PurchaseBatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import javax.inject.Inject

/**
 * UI state for the main purchase screen showing cash balance and today's product totals.
 */
data class PurchaseUiState(
    val cashBalance: BigDecimal = BigDecimal.ZERO,
    val todaysProductTotals: List<ProductDailyTotal> = emptyList(),
    val todaysBatches: List<PurchaseBatch> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
    val navigateToNewClient: Boolean = false
)

@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val purchaseBatchRepository: PurchaseBatchRepository,
    private val cashRepository: CashRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseUiState())
    val uiState: StateFlow<PurchaseUiState> = _uiState.asStateFlow()

    init {
        observeData()
    }

    private fun observeData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                cashRepository.getTotalBalance(),
                purchaseBatchRepository.observeTodaysProductTotals(),
                purchaseBatchRepository.observeTodaysBatches()
            ) { balance, productTotals, batches ->
                Triple(balance, productTotals, batches)
            }
                .catch { e ->
                    _uiState.update {
                        it.copy(
                            error = e.message ?: "Помилка завантаження даних",
                            isLoading = false
                        )
                    }
                }
                .collect { (balance, productTotals, batches) ->
                    _uiState.update {
                        it.copy(
                            cashBalance = balance,
                            todaysProductTotals = productTotals,
                            todaysBatches = batches,
                            isLoading = false
                        )
                    }
                }
        }
    }

    fun onNewClientClick() {
        _uiState.update { it.copy(navigateToNewClient = true) }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigateToNewClient = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun refresh() {
        observeData()
    }
}
