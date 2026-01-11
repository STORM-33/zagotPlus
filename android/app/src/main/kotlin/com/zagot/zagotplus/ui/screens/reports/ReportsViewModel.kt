package com.zagot.zagotplus.ui.screens.reports

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionFilter
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID
import javax.inject.Inject

data class ReportsUiState(
    val selectedDate: LocalDate = LocalDate.now(),
    val isLoading: Boolean = false,
    val purchaseSummary: TransactionSummary = TransactionSummary(),
    val saleSummary: TransactionSummary = TransactionSummary(),
    val productBreakdown: List<ProductBreakdownItem> = emptyList(),
    val locationBreakdown: List<LocationBreakdownItem> = emptyList(),
    val transferSummary: List<TransferSummaryItem> = emptyList(),
    val hasData: Boolean = false,
    val error: String? = null,
    val copySuccess: Boolean = false
)

data class TransactionSummary(
    val totalWeightKg: BigDecimal = BigDecimal.ZERO,
    val totalAmount: BigDecimal = BigDecimal.ZERO
)

data class ProductBreakdownItem(
    val productId: UUID,
    val productName: String,
    val purchaseWeightKg: BigDecimal = BigDecimal.ZERO,
    val purchaseAmount: BigDecimal = BigDecimal.ZERO,
    val saleWeightKg: BigDecimal = BigDecimal.ZERO,
    val saleAmount: BigDecimal = BigDecimal.ZERO
)

data class LocationBreakdownItem(
    val locationId: UUID,
    val locationName: String,
    val purchaseWeightKg: BigDecimal = BigDecimal.ZERO,
    val purchaseAmount: BigDecimal = BigDecimal.ZERO,
    val saleWeightKg: BigDecimal = BigDecimal.ZERO,
    val saleAmount: BigDecimal = BigDecimal.ZERO
)

data class TransferSummaryItem(
    val fromLocationName: String,
    val toLocationName: String,
    val productName: String,
    val weightKg: BigDecimal
)

@HiltViewModel
class ReportsViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val productRepository: ProductRepository,
    private val locationRepository: LocationRepository,
    @ApplicationContext private val context: Context
) : ViewModel() {

    private val _uiState = MutableStateFlow(ReportsUiState())
    val uiState: StateFlow<ReportsUiState> = _uiState.asStateFlow()

    private var products: Map<UUID, Product> = emptyMap()
    private var locations: Map<UUID, Location> = emptyMap()
    private var lastKnownCount: Int = -1

    private val decimalFormat = DecimalFormat("#,##0.00")
    private val dateFormatter = DateTimeFormatter.ofPattern("dd.MM.yyyy")

    init {
        loadReferencesAndData()
        observeTransactionChanges()
    }

    /**
     * Observe transaction count changes to auto-refresh when new transactions are added.
     */
    private fun observeTransactionChanges() {
        viewModelScope.launch {
            transactionRepository.getTotalTransactionCount()
                .distinctUntilChanged()
                .collect { count ->
                    // Only refresh if count changed after initial load
                    if (lastKnownCount >= 0 && count != lastKnownCount) {
                        loadDayData(_uiState.value.selectedDate)
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
                locations = locationRepository.getAllLocations().first().associateBy { it.id }
                loadDayData(_uiState.value.selectedDate)
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

    fun selectDate(date: LocalDate) {
        if (date == _uiState.value.selectedDate) return
        _uiState.update { it.copy(selectedDate = date) }
        loadDayData(date)
    }

    private fun loadDayData(date: LocalDate) {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, error = null) }
            try {
                val zone = ZoneId.systemDefault()
                val startOfDay = date.atStartOfDay(zone).toInstant()
                val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()

                val filter = TransactionFilter(
                    startDate = startOfDay,
                    endDate = endOfDay
                )

                val transactions = transactionRepository.getFilteredTransactions(
                    filter = filter,
                    limit = 10000,
                    offset = 0
                )

                computeSummaries(transactions)
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

    private fun computeSummaries(transactions: List<Transaction>) {
        val purchases = transactions.filter { it.type == TransactionType.PURCHASE }
        val sales = transactions.filter { it.type == TransactionType.SALE }
        val transfersOut = transactions.filter { it.type == TransactionType.TRANSFER_OUT }

        val purchaseSummary = TransactionSummary(
            totalWeightKg = purchases.sumOf { it.weightKg },
            totalAmount = purchases.sumOf { it.totalAmount ?: BigDecimal.ZERO }
        )

        val saleSummary = TransactionSummary(
            totalWeightKg = sales.sumOf { it.weightKg },
            totalAmount = sales.sumOf { it.totalAmount ?: BigDecimal.ZERO }
        )

        // Product breakdown
        val productBreakdown = computeProductBreakdown(purchases, sales)

        // Location breakdown
        val locationBreakdown = computeLocationBreakdown(purchases, sales)

        // Transfer summary
        val transferSummary = transfersOut.mapNotNull { tx ->
            val fromLocation = tx.locationId?.let { locations[it]?.name } ?: return@mapNotNull null
            val toLocation = tx.transferLocationId?.let { locations[it]?.name } ?: return@mapNotNull null
            val productName = tx.productId?.let { products[it]?.name } ?: "Невідомо"
            TransferSummaryItem(
                fromLocationName = fromLocation,
                toLocationName = toLocation,
                productName = productName,
                weightKg = tx.weightKg
            )
        }

        val hasData = transactions.isNotEmpty()

        _uiState.update {
            it.copy(
                isLoading = false,
                purchaseSummary = purchaseSummary,
                saleSummary = saleSummary,
                productBreakdown = productBreakdown,
                locationBreakdown = locationBreakdown,
                transferSummary = transferSummary,
                hasData = hasData
            )
        }
    }

    private fun computeProductBreakdown(
        purchases: List<Transaction>,
        sales: List<Transaction>
    ): List<ProductBreakdownItem> {
        val productIds = (purchases.mapNotNull { it.productId } + sales.mapNotNull { it.productId }).toSet()

        return productIds.mapNotNull { productId ->
            val product = products[productId] ?: return@mapNotNull null
            val productPurchases = purchases.filter { it.productId == productId }
            val productSales = sales.filter { it.productId == productId }

            ProductBreakdownItem(
                productId = productId,
                productName = product.name,
                purchaseWeightKg = productPurchases.sumOf { it.weightKg },
                purchaseAmount = productPurchases.sumOf { it.totalAmount ?: BigDecimal.ZERO },
                saleWeightKg = productSales.sumOf { it.weightKg },
                saleAmount = productSales.sumOf { it.totalAmount ?: BigDecimal.ZERO }
            )
        }.sortedBy { it.productName }
    }

    private fun computeLocationBreakdown(
        purchases: List<Transaction>,
        sales: List<Transaction>
    ): List<LocationBreakdownItem> {
        val locationIds = (purchases.mapNotNull { it.locationId } + sales.mapNotNull { it.locationId }).toSet()

        return locationIds.mapNotNull { locationId ->
            val location = locations[locationId] ?: return@mapNotNull null
            val locationPurchases = purchases.filter { it.locationId == locationId }
            val locationSales = sales.filter { it.locationId == locationId }

            LocationBreakdownItem(
                locationId = locationId,
                locationName = location.name,
                purchaseWeightKg = locationPurchases.sumOf { it.weightKg },
                purchaseAmount = locationPurchases.sumOf { it.totalAmount ?: BigDecimal.ZERO },
                saleWeightKg = locationSales.sumOf { it.weightKg },
                saleAmount = locationSales.sumOf { it.totalAmount ?: BigDecimal.ZERO }
            )
        }.sortedBy { it.locationName }
    }

    fun copyReportToClipboard() {
        val report = generateReportText()
        val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("Звіт", report)
        clipboard.setPrimaryClip(clip)
        _uiState.update { it.copy(copySuccess = true) }
    }

    fun createShareIntent(): Intent {
        val report = generateReportText()
        return Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, report)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }

    private fun generateReportText(): String {
        val state = _uiState.value
        val dateStr = dateFormatter.format(state.selectedDate)

        return buildString {
            appendLine("Звіт за $dateStr")
            appendLine()

            if (!state.hasData) {
                appendLine("Немає даних за цей день")
                return@buildString
            }

            // Purchases section
            appendLine("ЗАКУПКИ")
            if (state.productBreakdown.any { it.purchaseWeightKg > BigDecimal.ZERO }) {
                state.productBreakdown
                    .filter { it.purchaseWeightKg > BigDecimal.ZERO }
                    .forEach { item ->
                        val pricePerKg = if (item.purchaseWeightKg > BigDecimal.ZERO) {
                            item.purchaseAmount.divide(item.purchaseWeightKg, 2, java.math.RoundingMode.HALF_UP)
                        } else BigDecimal.ZERO
                        appendLine("- ${item.productName}: ${decimalFormat.format(item.purchaseWeightKg)} кг × ${decimalFormat.format(pricePerKg)} = ${decimalFormat.format(item.purchaseAmount)} грн")
                    }
                appendLine("Разом: ${decimalFormat.format(state.purchaseSummary.totalWeightKg)} кг, ${decimalFormat.format(state.purchaseSummary.totalAmount)} грн")
            } else {
                appendLine("(немає)")
            }
            appendLine()

            // Sales section
            appendLine("ПРОДАЖІ")
            if (state.productBreakdown.any { it.saleWeightKg > BigDecimal.ZERO }) {
                state.productBreakdown
                    .filter { it.saleWeightKg > BigDecimal.ZERO }
                    .forEach { item ->
                        val pricePerKg = if (item.saleWeightKg > BigDecimal.ZERO) {
                            item.saleAmount.divide(item.saleWeightKg, 2, java.math.RoundingMode.HALF_UP)
                        } else BigDecimal.ZERO
                        appendLine("- ${item.productName}: ${decimalFormat.format(item.saleWeightKg)} кг × ${decimalFormat.format(pricePerKg)} = ${decimalFormat.format(item.saleAmount)} грн")
                    }
                appendLine("Разом: ${decimalFormat.format(state.saleSummary.totalWeightKg)} кг, ${decimalFormat.format(state.saleSummary.totalAmount)} грн")
            } else {
                appendLine("(немає)")
            }
            appendLine()

            // Transfers section
            if (state.transferSummary.isNotEmpty()) {
                appendLine("ПЕРЕМІЩЕННЯ")
                state.transferSummary.forEach { transfer ->
                    appendLine("- ${transfer.fromLocationName} → ${transfer.toLocationName}: ${transfer.productName} ${decimalFormat.format(transfer.weightKg)} кг")
                }
            }
        }
    }

    fun dismissCopySuccess() {
        _uiState.update { it.copy(copySuccess = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
