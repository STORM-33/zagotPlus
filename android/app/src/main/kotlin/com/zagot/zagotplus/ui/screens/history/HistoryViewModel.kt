package com.zagot.zagotplus.ui.screens.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.domain.model.DateRangePreset
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionFilter
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class HistoryUiState(
    val transactions: List<HistoryDisplayItem> = emptyList(),
    val totalCount: Int = 0,
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = false,
    val error: String? = null,
    // Filter state
    val selectedTypes: Set<TransactionType> = emptySet(),
    val dateRangePreset: DateRangePreset = DateRangePreset.ALL,
    val customStartDate: Instant? = null,
    val customEndDate: Instant? = null,
    val selectedLocationId: UUID? = null,
    val searchQuery: String = "",
    // Reference data for filters
    val locations: List<Location> = emptyList()
) {
    val hasActiveFilters: Boolean
        get() = selectedTypes.isNotEmpty() ||
                dateRangePreset != DateRangePreset.ALL ||
                selectedLocationId != null ||
                searchQuery.isNotBlank()
}

data class HistoryDisplayItem(
    val id: UUID,
    val type: TransactionType,
    val productName: String,
    val locationName: String,
    val weightKg: BigDecimal,
    val totalAmount: BigDecimal?,
    val createdAt: Instant,
    val isSynced: Boolean
)

@HiltViewModel
class HistoryViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val productRepository: ProductRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var products: Map<UUID, Product> = emptyMap()
    private var locationsMap: Map<UUID, Location> = emptyMap()
    private var searchJob: Job? = null

    init {
        loadInitialData()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Load reference data
                products = productRepository.getActiveProducts().first().associateBy { it.id }
                val locationsList = locationRepository.getAllLocations().first()
                locationsMap = locationsList.associateBy { it.id }

                // Load first page with current filter
                loadFilteredTransactions(resetPage = true, locationsList = locationsList)
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

    private suspend fun loadFilteredTransactions(
        resetPage: Boolean = false,
        locationsList: List<Location>? = null
    ) {
        val state = _uiState.value
        val filter = buildFilter(state)

        try {
            val totalCount = transactionRepository.getFilteredTransactionCount(filter)
            val offset = if (resetPage) 0 else (state.currentPage + 1) * PAGE_SIZE
            val transactions = transactionRepository.getFilteredTransactions(
                filter = filter,
                limit = PAGE_SIZE,
                offset = if (resetPage) 0 else offset
            )

            _uiState.update {
                it.copy(
                    transactions = if (resetPage) {
                        transactions.map { tx -> tx.toDisplayItem() }
                    } else {
                        it.transactions + transactions.map { tx -> tx.toDisplayItem() }
                    },
                    totalCount = totalCount,
                    currentPage = if (resetPage) 0 else state.currentPage + 1,
                    hasMorePages = transactions.size >= PAGE_SIZE,
                    isLoading = false,
                    isLoadingMore = false,
                    locations = locationsList ?: it.locations
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    error = e.message ?: "Помилка завантаження",
                    isLoading = false,
                    isLoadingMore = false
                )
            }
        }
    }

    private fun buildFilter(state: HistoryUiState): TransactionFilter {
        val (startDate, endDate) = getDateRange(state.dateRangePreset, state.customStartDate, state.customEndDate)
        return TransactionFilter(
            types = state.selectedTypes,
            locationId = state.selectedLocationId,
            startDate = startDate,
            endDate = endDate,
            productNameSearch = state.searchQuery.takeIf { it.isNotBlank() }
        )
    }

    private fun getDateRange(
        preset: DateRangePreset,
        customStart: Instant?,
        customEnd: Instant?
    ): Pair<Instant?, Instant?> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)

        return when (preset) {
            DateRangePreset.TODAY -> {
                val start = today.atStartOfDay(zone).toInstant()
                val end = today.atTime(LocalTime.MAX).atZone(zone).toInstant()
                start to end
            }
            DateRangePreset.THIS_WEEK -> {
                val weekFields = WeekFields.of(Locale.getDefault())
                val firstDayOfWeek = today.with(weekFields.dayOfWeek(), 1)
                val start = firstDayOfWeek.atStartOfDay(zone).toInstant()
                val end = today.atTime(LocalTime.MAX).atZone(zone).toInstant()
                start to end
            }
            DateRangePreset.THIS_MONTH -> {
                val firstDayOfMonth = today.withDayOfMonth(1)
                val start = firstDayOfMonth.atStartOfDay(zone).toInstant()
                val end = today.atTime(LocalTime.MAX).atZone(zone).toInstant()
                start to end
            }
            DateRangePreset.CUSTOM -> customStart to customEnd
            DateRangePreset.ALL -> null to null
        }
    }

    fun loadMoreTransactions() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMorePages) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            loadFilteredTransactions(resetPage = false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            loadFilteredTransactions(resetPage = true)
        }
    }

    // Filter methods

    fun toggleTypeFilter(type: TransactionType) {
        _uiState.update { state ->
            val newTypes = if (type in state.selectedTypes) {
                state.selectedTypes - type
            } else {
                state.selectedTypes + type
            }
            state.copy(selectedTypes = newTypes)
        }
        reloadWithFilter()
    }

    fun setDateRangePreset(preset: DateRangePreset) {
        _uiState.update { it.copy(dateRangePreset = preset) }
        if (preset != DateRangePreset.CUSTOM) {
            reloadWithFilter()
        }
    }

    fun setCustomDateRange(start: Instant?, end: Instant?) {
        _uiState.update {
            it.copy(
                dateRangePreset = DateRangePreset.CUSTOM,
                customStartDate = start,
                customEndDate = end
            )
        }
        reloadWithFilter()
    }

    fun setLocationFilter(locationId: UUID?) {
        _uiState.update { it.copy(selectedLocationId = locationId) }
        reloadWithFilter()
    }

    fun setSearchQuery(query: String) {
        _uiState.update { it.copy(searchQuery = query) }
        // Debounce search
        searchJob?.cancel()
        searchJob = viewModelScope.launch {
            delay(300)
            reloadWithFilter()
        }
    }

    fun clearFilters() {
        _uiState.update {
            it.copy(
                selectedTypes = emptySet(),
                dateRangePreset = DateRangePreset.ALL,
                customStartDate = null,
                customEndDate = null,
                selectedLocationId = null,
                searchQuery = ""
            )
        }
        reloadWithFilter()
    }

    private fun reloadWithFilter() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            loadFilteredTransactions(resetPage = true)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    private fun Transaction.toDisplayItem() = HistoryDisplayItem(
        id = id,
        type = type,
        productName = productId?.let { products[it]?.name } ?: "Невідомий товар",
        locationName = locationId?.let { locationsMap[it]?.name } ?: "Невідома локація",
        weightKg = weightKg,
        totalAmount = totalAmount,
        createdAt = createdAt,
        isSynced = syncedAt != null
    )

    companion object {
        private const val PAGE_SIZE = 50
    }
}
