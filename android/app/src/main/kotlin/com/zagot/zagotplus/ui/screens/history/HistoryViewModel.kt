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
import com.zagot.zagotplus.domain.repository.PurchaseBatchRepository
import com.zagot.zagotplus.domain.repository.SaleBatchRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
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
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

data class HistoryUiState(
    // Batch list
    val batches: List<HistoryBatchDisplayItem> = emptyList(),
    val expandedBatchIds: Set<String> = emptySet(),
    val expandedBatchTransactions: Map<String, List<HistoryDisplayItem>> = emptyMap(),
    val isLoadingBatchDetails: Set<String> = emptySet(),

    // Pagination
    val totalCount: Int = 0,
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = false,
    val error: String? = null,

    // Filter state
    val selectedTypes: Set<BatchType> = emptySet(),
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
    private val purchaseBatchRepository: PurchaseBatchRepository,
    private val saleBatchRepository: SaleBatchRepository,
    private val productRepository: ProductRepository,
    private val locationRepository: LocationRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()

    private var products: Map<UUID, Product> = emptyMap()
    private var locationsMap: Map<UUID, Location> = emptyMap()
    private var searchJob: Job? = null
    private var lastKnownPurchaseBatchCount: Int = -1
    private var lastKnownSaleBatchCount: Int = -1

    init {
        loadInitialData()
        observeBatchChanges()
    }

    /**
     * Observe batch count changes to auto-refresh when new batches are added.
     */
    private fun observeBatchChanges() {
        viewModelScope.launch {
            purchaseBatchRepository.observeTotalBatchCount()
                .distinctUntilChanged()
                .collect { count ->
                    if (lastKnownPurchaseBatchCount >= 0 && count != lastKnownPurchaseBatchCount) {
                        reloadWithFilter()
                    }
                    lastKnownPurchaseBatchCount = count
                }
        }
        viewModelScope.launch {
            saleBatchRepository.observeTotalBatchCount()
                .distinctUntilChanged()
                .collect { count ->
                    if (lastKnownSaleBatchCount >= 0 && count != lastKnownSaleBatchCount) {
                        reloadWithFilter()
                    }
                    lastKnownSaleBatchCount = count
                }
        }
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                // Load reference data
                products = productRepository.getActiveProducts().first().associateBy { it.id }
                val locationsList = locationRepository.getAllLocations().first()
                locationsMap = locationsList.associateBy { it.id }

                // Load batches with current filter
                loadBatches(resetPage = true, locationsList = locationsList)
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

    private suspend fun loadBatches(
        resetPage: Boolean = false,
        locationsList: List<Location>? = null
    ) {
        val state = _uiState.value
        val (startDate, endDate) = getDateRange(state.dateRangePreset, state.customStartDate, state.customEndDate)

        try {
            // Build transaction filter for getting unbatched transactions
            val transactionFilter = TransactionFilter(
                types = state.selectedTypes.flatMap { it.toTransactionTypes() }.toSet(),
                locationId = state.selectedLocationId,
                startDate = startDate,
                endDate = endDate,
                productNameSearch = state.searchQuery.takeIf { it.isNotBlank() }
            )

            // Load real purchase batches
            val offset = if (resetPage) 0 else (state.currentPage + 1) * PAGE_SIZE
            val realPurchaseBatches = purchaseBatchRepository.getAllBatchesPaginated(PAGE_SIZE, if (resetPage) 0 else offset)

            // Convert purchase batches to display items, filtering by location if needed
            val purchaseBatchDisplayItems = realPurchaseBatches
                .filter { batch ->
                    // Apply location filter
                    state.selectedLocationId == null || batch.locationId == state.selectedLocationId
                }
                .filter { _ ->
                    // Apply type filter - only show purchase batches if PURCHASE is selected or no filter
                    state.selectedTypes.isEmpty() || BatchType.PURCHASE in state.selectedTypes
                }
                .map { batch ->
                    HistoryBatchDisplayItem.RealBatch(
                        batchId = batch.id,
                        createdAt = batch.createdAt,
                        totalWeightKg = batch.totalWeightKg ?: BigDecimal.ZERO,
                        totalAmount = batch.totalAmount,
                        itemCount = batch.itemCount ?: 0,
                        locationName = locationsMap[batch.locationId]?.name ?: "Невідома локація",
                        isSynced = batch.syncedAt != null,
                        notes = batch.notes
                    )
                }

            // Load real sale batches
            val realSaleBatches = saleBatchRepository.getAllBatchesPaginated(PAGE_SIZE, if (resetPage) 0 else offset)

            // Convert sale batches to display items, filtering by location if needed
            val saleBatchDisplayItems = realSaleBatches
                .filter { batch ->
                    // Apply location filter
                    state.selectedLocationId == null || batch.locationId == state.selectedLocationId
                }
                .filter { _ ->
                    // Apply type filter - only show sale batches if SALE is selected or no filter
                    state.selectedTypes.isEmpty() || BatchType.SALE in state.selectedTypes
                }
                .map { batch ->
                    HistoryBatchDisplayItem.RealSaleBatch(
                        batchId = batch.id,
                        createdAt = batch.createdAt,
                        totalWeightKg = batch.totalWeightKg ?: BigDecimal.ZERO,
                        totalAmount = batch.totalAmount,
                        itemCount = batch.itemCount ?: 0,
                        locationName = locationsMap[batch.locationId]?.name ?: "Невідома локація",
                        isSynced = batch.syncedAt != null,
                        notes = batch.notes
                    )
                }

            // Load unbatched transactions for virtual batches (transfers only now)
            val unbatchedTransactions = transactionRepository.getFilteredTransactions(
                filter = transactionFilter.copy(
                    // Only get transfer transactions for virtual batches
                    types = state.selectedTypes.flatMap { it.toTransactionTypes() }
                        .filter { it == TransactionType.TRANSFER_IN || it == TransactionType.TRANSFER_OUT }
                        .toSet()
                        .ifEmpty { setOf(TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT) }
                ),
                limit = PAGE_SIZE * 10, // Get more transactions to group them
                offset = 0
            )

            // Group unbatched transactions into virtual batches
            val virtualBatches = groupIntoVirtualBatches(unbatchedTransactions)
                .filter { batch ->
                    // Apply type filter
                    state.selectedTypes.isEmpty() || batch.batchType in state.selectedTypes
                }

            // Merge and sort all batches by createdAt DESC
            val allBatches = (purchaseBatchDisplayItems + saleBatchDisplayItems + virtualBatches)
                .sortedByDescending { it.createdAt }
                .take(PAGE_SIZE)

            val totalBatchCount = purchaseBatchRepository.getTotalBatchCount() + 
                                  saleBatchRepository.getTotalBatchCount() + 
                                  virtualBatches.size

            _uiState.update {
                it.copy(
                    batches = if (resetPage) {
                        allBatches
                    } else {
                        it.batches + allBatches
                    },
                    totalCount = totalBatchCount,
                    currentPage = if (resetPage) 0 else state.currentPage + 1,
                    hasMorePages = allBatches.size >= PAGE_SIZE,
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

    /**
     * Groups unbatched transactions into virtual batches by type, location, and hour.
     */
    private fun groupIntoVirtualBatches(
        transactions: List<Transaction>
    ): List<HistoryBatchDisplayItem.VirtualBatch> {
        return transactions
            .groupBy { tx ->
                // Group key: type category + location + hour-aligned timestamp
                val hourStart = tx.createdAt.truncatedTo(ChronoUnit.HOURS)
                val batchType = tx.type.toBatchType()
                Triple(batchType, tx.locationId, hourStart)
            }
            .map { (key, txList) ->
                val (batchType, locationId, hourStart) = key
                HistoryBatchDisplayItem.VirtualBatch(
                    transactionIds = txList.map { it.id },
                    timeWindowStart = hourStart,
                    batchType = batchType,
                    createdAt = txList.maxOf { it.createdAt },
                    totalWeightKg = txList.sumOf { it.weightKg.abs() },
                    totalAmount = txList.mapNotNull { it.totalAmount?.abs() }
                        .takeIf { it.isNotEmpty() }
                        ?.reduce { acc, amount -> acc + amount },
                    itemCount = txList.size,
                    locationName = locationsMap[locationId]?.name ?: "Невідома локація",
                    isSynced = txList.all { it.syncedAt != null }
                )
            }
    }

    private fun TransactionType.toBatchType(): BatchType = when (this) {
        TransactionType.PURCHASE -> BatchType.PURCHASE
        TransactionType.SALE -> BatchType.SALE
        TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT -> BatchType.TRANSFER
    }

    private fun BatchType.toTransactionTypes(): Set<TransactionType> = when (this) {
        BatchType.PURCHASE -> setOf(TransactionType.PURCHASE)
        BatchType.SALE -> setOf(TransactionType.SALE)
        BatchType.TRANSFER -> setOf(TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT)
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

    /**
     * Toggle batch expansion on long-press.
     */
    fun toggleBatchExpansion(batchId: String) {
        viewModelScope.launch {
            val isCurrentlyExpanded = _uiState.value.expandedBatchIds.contains(batchId)

            if (isCurrentlyExpanded) {
                // Collapse
                _uiState.update { state ->
                    state.copy(
                        expandedBatchIds = state.expandedBatchIds - batchId
                    )
                }
            } else {
                // Expand - load transactions if not already loaded
                if (!_uiState.value.expandedBatchTransactions.containsKey(batchId)) {
                    loadBatchTransactions(batchId)
                }
                _uiState.update { state ->
                    state.copy(
                        expandedBatchIds = state.expandedBatchIds + batchId
                    )
                }
            }
        }
    }

    /**
     * Load transactions for a specific batch (lazy loading).
     */
    private suspend fun loadBatchTransactions(batchId: String) {
        _uiState.update { it.copy(isLoadingBatchDetails = it.isLoadingBatchDetails + batchId) }

        try {
            val transactions: List<Transaction> = when {
                batchId.startsWith("batch_") -> {
                    // Real purchase batch
                    val uuid = UUID.fromString(batchId.removePrefix("batch_"))
                    purchaseBatchRepository.getTransactionsForBatch(uuid)
                }
                batchId.startsWith("sale_batch_") -> {
                    // Real sale batch
                    val uuid = UUID.fromString(batchId.removePrefix("sale_batch_"))
                    saleBatchRepository.getTransactionsForBatch(uuid)
                }
                batchId.startsWith("virtual_") -> {
                    // Virtual batch - get transaction IDs from the batch item
                    val batch = _uiState.value.batches.find { it.id == batchId }
                    if (batch is HistoryBatchDisplayItem.VirtualBatch) {
                        transactionRepository.getTransactionsByIds(batch.transactionIds)
                    } else {
                        emptyList()
                    }
                }
                else -> emptyList()
            }

            val displayItems = transactions.map { tx ->
                HistoryDisplayItem(
                    id = tx.id,
                    type = tx.type,
                    productName = tx.productId?.let { products[it]?.name } ?: "Невідомий товар",
                    locationName = tx.locationId?.let { locationsMap[it]?.name } ?: "Невідома локація",
                    weightKg = tx.weightKg,
                    totalAmount = tx.totalAmount,
                    createdAt = tx.createdAt,
                    isSynced = tx.syncedAt != null
                )
            }

            _uiState.update { state ->
                state.copy(
                    expandedBatchTransactions = state.expandedBatchTransactions + (batchId to displayItems),
                    isLoadingBatchDetails = state.isLoadingBatchDetails - batchId
                )
            }
        } catch (e: Exception) {
            _uiState.update {
                it.copy(
                    error = e.message,
                    isLoadingBatchDetails = it.isLoadingBatchDetails - batchId
                )
            }
        }
    }

    fun loadMoreBatches() {
        val state = _uiState.value
        if (state.isLoadingMore || !state.hasMorePages) return

        viewModelScope.launch {
            _uiState.update { it.copy(isLoadingMore = true) }
            loadBatches(resetPage = false)
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            loadBatches(resetPage = true)
        }
    }

    // Filter methods

    fun toggleTypeFilter(type: BatchType) {
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
            _uiState.update {
                it.copy(
                    isLoading = true,
                    expandedBatchIds = emptySet(),
                    expandedBatchTransactions = emptyMap()
                )
            }
            loadBatches(resetPage = true)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    companion object {
        private const val PAGE_SIZE = 50
    }
}
