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
 * - Витрати (Spendings): закупки + оплата (excluding виведення)
 * - Прибуток (Earnings): sum of sale amounts
 */
data class ReportsUiState(
    val dateRange: DateRange? = null, // null = all time (default)
    val isLoading: Boolean = false,
    
    // Summary panels
    val totalSpendings: BigDecimal = BigDecimal.ZERO,
    val totalEarnings: BigDecimal = BigDecimal.ZERO,
    val totalWeightKg: BigDecimal = BigDecimal.ZERO,
    val inventoryValue: BigDecimal = BigDecimal.ZERO,
    
    // Spendings breakdown
    val purchaseTotal: BigDecimal = BigDecimal.ZERO,
    val paymentsByCategory: List<PaymentCategoryItem> = emptyList(),
    
    // Sales breakdown (for earnings detail)
    val salesItems: List<SalesReportItem> = emptyList(),
    
    // Location selection
    val locations: List<Location> = emptyList(),
    val selectedLocationId: UUID? = null, // null = all locations ("всього")
    
    // Product list (sorted by spending descending)
    val productItems: List<ProductReportItem> = emptyList(),
    
    val hasData: Boolean = false,
    val error: String? = null
){
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

/**
 * Payment category item for spendings breakdown.
 */
data class PaymentCategoryItem(
    val categoryName: String,
    val amount: BigDecimal
)

/**
 * Sales item for earnings breakdown by product.
 */
data class SalesReportItem(
    val productId: UUID,
    val productName: String,
    val imageUri: String?,
    val totalEarned: BigDecimal,
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

                // Get cash operations (payments) for the period with categories
                val paymentsByCategory = calculatePaymentsByCategory(startInstant, endInstant, state.selectedLocationId)
                val paymentsTotal = paymentsByCategory.sumOf { it.amount }

                val totalSpendings = purchaseTotal.add(paymentsTotal)

                // Calculate earnings (sales)
                val sales = transactions.filter { it.type == TransactionType.SALE }
                val totalEarnings = sales.sumOf { it.totalAmount ?: BigDecimal.ZERO }
                
                // Build sales items for earnings breakdown
                val salesItems = computeSalesItems(sales)

                // Build product list from purchases
                val productItems = computeProductItems(purchases)
                
                val totalWeightKg = productItems.sumOf { it.totalWeightKg }

                // Calculate inventory value (current stock potential revenue)
                // This is NOT affected by date range, only by location
                val inventoryItems = if (state.selectedLocationId != null) {
                    transactionRepository.getInventoryByLocation(state.selectedLocationId).first()
                } else {
                    transactionRepository.getInventory().first()
                }

                val inventoryValue = inventoryItems.sumOf { item ->
                    val product = products[item.productId]
                    val price = product?.defaultSellPrice ?: BigDecimal.ZERO
                    if (item.totalWeightKg > BigDecimal.ZERO) {
                        item.totalWeightKg.multiply(price)
                    } else {
                        BigDecimal.ZERO
                    }
                }

                val hasData = transactions.isNotEmpty() || totalSpendings > BigDecimal.ZERO || totalEarnings > BigDecimal.ZERO

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        totalSpendings = totalSpendings,
                        totalEarnings = totalEarnings,
                        totalWeightKg = totalWeightKg,
                        inventoryValue = inventoryValue,
                        purchaseTotal = purchaseTotal,
                        paymentsByCategory = paymentsByCategory,
                        salesItems = salesItems,
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
     * Calculate payments grouped by category for the period.
     * Note: Withdrawals (виведення коштів) are excluded from spendings.
     * If dates are null, includes all time.
     */
    private suspend fun calculatePaymentsByCategory(
        startDate: Instant?,
        endDate: Instant?,
        locationId: UUID?
    ): List<PaymentCategoryItem> {
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
                    it.type == com.zagot.zagotplus.domain.model.CashHistoryItemType.PAYMENT
                }
                .groupBy { it.categoryName ?: "Без категорії" }
                .map { (category, items) ->
                    PaymentCategoryItem(
                        categoryName = category,
                        amount = items.sumOf { it.amount }
                    )
                }
                .sortedByDescending { it.amount }
        } catch (e: Exception) {
            emptyList()
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
    
    /**
     * Compute sales items from sales transactions, sorted by total earned descending.
     */
    private fun computeSalesItems(sales: List<Transaction>): List<SalesReportItem> {
        val productIds = sales.mapNotNull { it.productId }.toSet()

        return productIds.mapNotNull { productId ->
            val product = products[productId] ?: return@mapNotNull null
            val productSales = sales.filter { it.productId == productId }

            SalesReportItem(
                productId = productId,
                productName = product.name,
                imageUri = product.imageUri,
                totalEarned = productSales.sumOf { it.totalAmount ?: BigDecimal.ZERO },
                totalWeightKg = productSales.sumOf { it.weightKg }
            )
        }.sortedByDescending { it.totalEarned }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
