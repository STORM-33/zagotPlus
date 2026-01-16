package com.zagot.zagotplus.ui.screens.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionFilter
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.CashRepository
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import com.zagot.zagotplus.ui.components.DateRange
import com.zagot.zagotplus.ui.components.DateRangePreset
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject

/**
 * UI state for the Reports screen.
 * 
 * Summary panels:
 * - Витрати (Spendings): закупки + оплата + виведення
 * - Прибуток (Earnings): sum of sale amounts
 */
data class ReportsUiState(
    val dateRange: DateRange? = null, // null = all time (default)
    val isLoading: Boolean = false,
    
    // Summary panels
    val totalSpendings: BigDecimal = BigDecimal.ZERO,
    val totalEarnings: BigDecimal = BigDecimal.ZERO,
    
    // Location selection
    val locations: List<Location> = emptyList(),
    val selectedLocationId: UUID? = null, // null = all locations ("всього")
    
    // Product list (sorted by spending descending)
    val productItems: List<ProductReportItem> = emptyList(),
    
    val hasData: Boolean = false,
    val error: String? = null
) {
    val isTotalsView: Boolean
        get() = selectedLocationId == null
    
    val selectedLocationName: String?
        get() = selectedLocationId?.let { id -> locations.find { it.id == id }?.name }
}

/**
 * Product report item showing purchase stats for a product.
 */
data class ProductReportItem(
    val productId: UUID,
    val productName: String,
    val imageUri: String?,
    val totalSpent: BigDecimal,
    val totalWeightKg: BigDecimal
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val productRepository: ProductRepository,
    private val locationRepository: LocationRepository,
    private val cashRepository: CashRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private var products: Map<UUID, Product> = emptyMap()
    private var locations: Map<UUID, Location> = emptyMap()
    private var lastKnownCount: Int = -1

    init {
        loadReferencesAndData()
        observeTransactionChanges()
    }

    private fun observeTransactionChanges() {
        viewModelScope.launch {
            transactionRepository.getTotalTransactionCount()
                .distinctUntilChanged()
                .collect { count ->
                    if (lastKnownCount >= 0 && count != lastKnownCount) {
                        loadReportData()
                    }
                    lastKnownCount = count
                }
        }
    }

    private fun loadReferencesAndData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                products = productRepository.getActiveProducts().first().associateBy { it.id }
                val locationsList = locationRepository.getAllLocations().first()
                locations = locationsList.associateBy { it.id }
                _uiState.update { it.copy(locations = locationsList) }
                loadReportData()
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Помилка завантаження даних"
                    )
                }
            }
        }
    }

    fun setDateRange(dateRange: DateRange?) {
        if (dateRange == _uiState.value.dateRange) return
        _uiState.update { it.copy(dateRange = dateRange) }
        loadReportData()
    }

    fun selectLocation(locationId: UUID) {
        _uiState.update { it.copy(selectedLocationId = locationId) }
        loadReportData()
    }

    fun selectTotalView() {
        _uiState.update { it.copy(selectedLocationId = null) }
        loadReportData()
    }

    /**
     * Converts DateRange to Instant pair for filtering.
     * Returns (null, null) if dateRange is null (meaning ALL time).
     */
    private fun getDateRangeInstants(dateRange: DateRange?): Pair<Instant?, Instant?> {
        if (dateRange == null) return null to null

        val zone = ZoneId.systemDefault()
        val start = dateRange.startDate.atStartOfDay(zone).toInstant()
        val end = dateRange.endDate.atTime(LocalTime.MAX).atZone(zone).toInstant()
        return start to end
    }

    private fun loadReportData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val state = _uiState.value
                val (startInstant, endInstant) = getDateRangeInstants(state.dateRange)

                // Build filter
                val filter = TransactionFilter(
                    startDate = startInstant,
                    endDate = endInstant,
                    locationId = state.selectedLocationId
                )

                val transactions = transactionRepository.getFilteredTransactions(
                    filter = filter,
                    limit = 10000,
                    offset = 0
                )

                // Calculate spendings (purchases only from transactions)
                val purchases = transactions.filter { it.type == TransactionType.PURCHASE }
                val purchaseTotal = purchases.sumOf { it.totalAmount ?: BigDecimal.ZERO }

                // Get cash operations (payments + withdrawals) for the period
                val cashSpendings = calculateCashSpendings(startInstant, endInstant, state.selectedLocationId)

                val totalSpendings = purchaseTotal.add(cashSpendings)

                // Calculate earnings (sales)
                val sales = transactions.filter { it.type == TransactionType.SALE }
                val totalEarnings = sales.sumOf { it.totalAmount ?: BigDecimal.ZERO }

                // Build product list from purchases
                val productItems = computeProductItems(purchases)

                val hasData = transactions.isNotEmpty() || totalSpendings > BigDecimal.ZERO || totalEarnings > BigDecimal.ZERO

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totalSpendings = totalSpendings,
                        totalEarnings = totalEarnings,
                        productItems = productItems,
                        hasData = hasData
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Помилка завантаження звіту"
                    )
                }
            }
        }
    }

    /**
     * Calculate cash spendings (payments + withdrawals) for the period.
     * This matches the cash screen calculation logic.
     * If dates are null, includes all time.
     */
    private suspend fun calculateCashSpendings(
        startDate: Instant?,
        endDate: Instant?,
        locationId: UUID?
    ): BigDecimal {
        return try {
            val operations = if (locationId != null) {
                cashRepository.getCashHistoryByLocationPaged(locationId, 10000, 0)
            } else {
                cashRepository.getCashHistoryPaged(10000, 0)
            }
            
            operations
                .filter { 
                    (startDate == null || it.createdAt >= startDate) && 
                    (endDate == null || it.createdAt <= endDate) 
                }
                .filter { 
                    it.type == com.zagot.zagotplus.domain.model.CashHistoryItemType.PAYMENT ||
                    it.type == com.zagot.zagotplus.domain.model.CashHistoryItemType.WITHDRAWAL
                }
                .sumOf { it.amount }
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }

    /**
     * Compute product items from purchases, sorted by total spent descending.
     */
    private fun computeProductItems(purchases: List<Transaction>): List<ProductReportItem> {
        val productIds = purchases.mapNotNull { it.productId }.toSet()

        return productIds.mapNotNull { productId ->
            val product = products[productId] ?: return@mapNotNull null
            val productPurchases = purchases.filter { it.productId == productId }

            ProductReportItem(
                productId = productId,
                productName = product.name,
                imageUri = product.imageUri,
                totalSpent = productPurchases.sumOf { it.totalAmount ?: BigDecimal.ZERO },
                totalWeightKg = productPurchases.sumOf { it.weightKg }
            )
        }.sortedByDescending { it.totalSpent }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
