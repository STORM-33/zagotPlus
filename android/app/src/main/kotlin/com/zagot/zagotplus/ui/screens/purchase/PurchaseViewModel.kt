package com.zagot.zagotplus.ui.screens.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.ProductDailyTotal
import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.repository.CashRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.PurchaseBatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
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

@OptIn(FlowPreview::class)
@HiltViewModel
class PurchaseViewModel @Inject constructor(
    private val purchaseBatchRepository: PurchaseBatchRepository,
    private val cashRepository: CashRepository,
    private val productRepository: ProductRepository,
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseUiState())
    val uiState: StateFlow<PurchaseUiState> = _uiState.asStateFlow()

    private var dataObservationJob: Job? = null

    init {
        observeLocationChanges()
    }

    /**
     * Observe location changes and restart data observation when location changes.
     */
    private fun observeLocationChanges() {
        viewModelScope.launch {
            devicePreferences.selectedLocationIdFlow.collect { locationId ->
                locationId?.let { observeData(it) }
            }
        }
    }

    private fun observeData(locationId: UUID) {
        // Cancel previous observation job
        dataObservationJob?.cancel()
        
        dataObservationJob = viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            combine(
                cashRepository.getBalance(locationId),
                purchaseBatchRepository.observeTodaysProductTotals(locationId),
                purchaseBatchRepository.observeTodaysBatches(locationId),
                productRepository.getActiveProducts()
            ) { balance, productTotals, batches, products ->
                val productMap = products.associateBy { it.id }
                val enrichedTotals = productTotals.map { total ->
                    val product = productMap[total.productId]
                    val profit = calculatePlannedProfit(
                        sellPrice = product?.defaultSellPrice,
                        avgPurchasePrice = total.avgPricePerKg,
                        weight = total.totalWeightKg
                    )
                    total.copy(plannedProfit = profit)
                }
                Triple(balance, enrichedTotals, batches)
            }
                .debounce(50) // Coalesce rapid emissions during sync
                .distinctUntilChanged() // Skip if values unchanged
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

    /**
     * Calculate planned profit: (sellPrice - avgPurchasePrice) × weight
     */
    private fun calculatePlannedProfit(
        sellPrice: BigDecimal?,
        avgPurchasePrice: BigDecimal?,
        weight: BigDecimal
    ): BigDecimal? {
        if (sellPrice == null || avgPurchasePrice == null) return null
        if (weight <= BigDecimal.ZERO) return null
        return (sellPrice - avgPurchasePrice).multiply(weight)
            .setScale(2, RoundingMode.HALF_UP)
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
        devicePreferences.getSelectedLocationId()?.let { observeData(it) }
    }
}
