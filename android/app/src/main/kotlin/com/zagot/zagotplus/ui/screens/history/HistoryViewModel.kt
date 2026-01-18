package com.zagot.zagotplus.ui.screens.history

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.ui.components.DateRange
import com.zagot.zagotplus.ui.components.DateRangePreset
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
import java.util.UUID
import javax.inject.Inject

data class HistoryUiState(
    // Batch list
    val batches: List<HistoryBatchDisplayItem> = emptyList(),
    // Note: expandedBatchIds, expandedBatchTransactions, isLoadingBatchDetails moved to separate flows
    // for better recomposition performance

    // Pagination
    val totalCount: Int = 0,
    val currentPage: Int = 0,
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMorePages: Boolean = false,
    val error: String? = null,
    val successMessage: String? = null,

    // Filter state
    val selectedTypes: Set<BatchType> = emptySet(),
    val dateRange: DateRange? = null, // null means ALL time
    val selectedLocationId: UUID? = null,
    val searchQuery: String = "",

    // Reference data for filters
    val locations: List<Location> = emptyList()
) {
    val hasActiveFilters: Boolean
        get() = selectedTypes.isNotEmpty() ||
                dateRange != null ||
                selectedLocationId != null ||
                searchQuery.isNotBlank()
}

data class HistoryDisplayItem(
    val id: UUID,
    val type: TransactionType,
    val productName: String,
    val locationName: String,
    val transferLocationName: String? = null,
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
    private val locationRepository: LocationRepository,
    private val devicePreferences: com.zagot.zagotplus.data.preferences.DevicePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(HistoryUiState())
    val uiState: StateFlow<HistoryUiState> = _uiState.asStateFlow()
    
    // Restricted mode flag - locks location filter
    private var isRestrictedModeEnabled = false

    // Separate flows for frequently-changing state to avoid full screen recomposition
    private val _expandedBatchIds = MutableStateFlow<Set<String>>(emptySet())
    val expandedBatchIds: StateFlow<Set<String>> = _expandedBatchIds.asStateFlow()

    private val _expandedBatchTransactions = MutableStateFlow<Map<String, List<HistoryDisplayItem>>>(emptyMap())
    val expandedBatchTransactions: StateFlow<Map<String, List<HistoryDisplayItem>>> = _expandedBatchTransactions.asStateFlow()

    private val _isLoadingBatchDetails = MutableStateFlow<Set<String>>(emptySet())
    val isLoadingBatchDetails: StateFlow<Set<String>> = _isLoadingBatchDetails.asStateFlow()

    private var products: Map<UUID, Product> = emptyMap()
    private var locationsMap: Map<UUID, Location> = emptyMap()
    private var searchJob: Job? = null
    private var lastKnownPurchaseBatchCount: Int = -1
    private var lastKnownSaleBatchCount: Int = -1

    init {
        loadInitialData()
        observeBatchChanges()
        observeLocationChangesForRestrictedMode()
    }
    
    /**
     * Observe device location changes to update filter when in restricted mode.
     */
    private fun observeLocationChangesForRestrictedMode() {
        viewModelScope.launch {
            devicePreferences.selectedLocationIdFlow
                .collect { newLocationId ->
                    if (isRestrictedModeEnabled && newLocationId != null) {
                        _uiState.update { it.copy(selectedLocationId = newLocationId) }
                        reloadWithFilter()
                    }
                }
        }
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
        val (startDate, endDate) = getDateRangeInstants(state.dateRange)

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

            // Convert purchase batches to display items, filtering by location and date
            val purchaseBatchDisplayItems = realPurchaseBatches
                .filter { batch ->
                    // Apply date filter
                    (startDate == null || !batch.createdAt.isBefore(startDate)) &&
                    (endDate == null || !batch.createdAt.isAfter(endDate))
                }
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
                        notes = batch.notes,
                        isVoided = batch.isVoided,
                        isCorrection = batch.correctsBatchId != null,
                        correctionReason = batch.correctionReason,
                        correctsBatchId = batch.correctsBatchId
                    )
                }

            // Load real sale batches
            val realSaleBatches = saleBatchRepository.getAllBatchesPaginated(PAGE_SIZE, if (resetPage) 0 else offset)

            // Convert sale batches to display items, filtering by location and date
            val saleBatchDisplayItems = realSaleBatches
                .filter { batch ->
                    // Apply date filter
                    (startDate == null || !batch.createdAt.isBefore(startDate)) &&
                    (endDate == null || !batch.createdAt.isAfter(endDate))
                }
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
                        notes = batch.notes,
                        isVoided = batch.isVoided,
                        isCorrection = batch.correctsBatchId != null,
                        correctionReason = batch.correctionReason,
                        correctsBatchId = batch.correctsBatchId
                    )
                }

            // Load unbatched transactions for virtual batches (transfers and adjustments)
            val unbatchedTransactions = transactionRepository.getFilteredTransactions(
                filter = transactionFilter.copy(
                    // Get transfer and adjustment transactions for virtual batches
                    types = state.selectedTypes.flatMap { it.toTransactionTypes() }
                        .filter { it == TransactionType.TRANSFER_IN || it == TransactionType.TRANSFER_OUT || it == TransactionType.ADJUSTMENT }
                        .toSet()
                        .ifEmpty { setOf(TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT, TransactionType.ADJUSTMENT) }
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
     * Groups unbatched transactions into virtual batches and transfer batches.
     * Transfers are paired by their localId prefix to show as single "from → to" operations.
     * Adjustments are grouped by type, location, and hour.
     */
    private fun groupIntoVirtualBatches(
        transactions: List<Transaction>
    ): List<HistoryBatchDisplayItem> {
        val result = mutableListOf<HistoryBatchDisplayItem>()
        
        // Separate transfers from other transactions
        val transfers = transactions.filter { 
            it.type == TransactionType.TRANSFER_IN || it.type == TransactionType.TRANSFER_OUT 
        }
        val otherTransactions = transactions.filter { 
            it.type != TransactionType.TRANSFER_IN && it.type != TransactionType.TRANSFER_OUT 
        }
        
        // Group transfers by their localId prefix (e.g., "abc-out" and "abc-in" share prefix "abc")
        val transfersByPrefix = transfers.groupBy { tx ->
            tx.localId.removeSuffix("-out").removeSuffix("-in")
        }
        
        // Create TransferBatch items for paired transfers
        transfersByPrefix.forEach { (_, txPair) ->
            val outTx = txPair.find { it.type == TransactionType.TRANSFER_OUT }
            val inTx = txPair.find { it.type == TransactionType.TRANSFER_IN }
            
            if (outTx != null && inTx != null) {
                // Paired transfer - show as single operation
                val fromLocationName = locationsMap[outTx.locationId]?.name ?: "Невідома локація"
                val toLocationName = locationsMap[inTx.locationId]?.name ?: "Невідома локація"
                
                result.add(HistoryBatchDisplayItem.TransferBatch(
                    transactionIds = listOf(outTx.id, inTx.id),
                    fromLocationName = fromLocationName,
                    toLocationName = toLocationName,
                    createdAt = outTx.createdAt,
                    totalWeightKg = inTx.weightKg.abs(), // Use positive weight from TRANSFER_IN
                    itemCount = 1, // One transfer operation
                    isSynced = outTx.syncedAt != null && inTx.syncedAt != null
                ))
            } else {
                // Orphan transfer (shouldn't happen normally, but handle gracefully)
                txPair.forEach { tx ->
                    val locationName = locationsMap[tx.locationId]?.name ?: "Невідома локація"
                    val targetName = tx.transferLocationId?.let { locationsMap[it]?.name } ?: "?"
                    
                    val (from, to) = if (tx.type == TransactionType.TRANSFER_OUT) {
                        locationName to targetName
                    } else {
                        targetName to locationName
                    }
                    
                    result.add(HistoryBatchDisplayItem.TransferBatch(
                        transactionIds = listOf(tx.id),
                        fromLocationName = from,
                        toLocationName = to,
                        createdAt = tx.createdAt,
                        totalWeightKg = tx.weightKg.abs(),
                        itemCount = 1,
                        isSynced = tx.syncedAt != null
                    ))
                }
            }
        }
        
        // Group other transactions (adjustments) by type, location, and hour
        val virtualBatches = otherTransactions
            .groupBy { tx ->
                val hourStart = tx.createdAt.truncatedTo(ChronoUnit.HOURS)
                val batchType = tx.type.toBatchType()
                Triple(batchType, tx.locationId, hourStart)
            }
            .map { (key, txList) ->
                val (batchType, locationId, hourStart) = key
                // For adjustments, sum signed values
                val totalWeight = if (batchType == BatchType.ADJUSTMENT) {
                    txList.sumOf { it.weightKg }
                } else {
                    txList.sumOf { it.weightKg.abs() }
                }
                HistoryBatchDisplayItem.VirtualBatch(
                    transactionIds = txList.map { it.id },
                    timeWindowStart = hourStart,
                    batchType = batchType,
                    createdAt = txList.maxOf { it.createdAt },
                    totalWeightKg = totalWeight,
                    totalAmount = txList.mapNotNull { it.totalAmount?.abs() }
                        .takeIf { it.isNotEmpty() }
                        ?.reduce { acc, amount -> acc + amount },
                    itemCount = txList.size,
                    locationName = locationsMap[locationId]?.name ?: "Невідома локація",
                    isSynced = txList.all { it.syncedAt != null }
                )
            }
        
        result.addAll(virtualBatches)
        return result
    }

    private fun TransactionType.toBatchType(): BatchType = when (this) {
        TransactionType.PURCHASE -> BatchType.PURCHASE
        TransactionType.SALE -> BatchType.SALE
        TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT -> BatchType.TRANSFER
        TransactionType.ADJUSTMENT -> BatchType.ADJUSTMENT
    }

    private fun BatchType.toTransactionTypes(): Set<TransactionType> = when (this) {
        BatchType.PURCHASE -> setOf(TransactionType.PURCHASE)
        BatchType.SALE -> setOf(TransactionType.SALE)
        BatchType.TRANSFER -> setOf(TransactionType.TRANSFER_IN, TransactionType.TRANSFER_OUT)
        BatchType.ADJUSTMENT -> setOf(TransactionType.ADJUSTMENT)
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

    /**
     * Toggle batch expansion on long-press.
     */
    fun toggleBatchExpansion(batchId: String) {
        viewModelScope.launch {
            val isCurrentlyExpanded = _expandedBatchIds.value.contains(batchId)

            if (isCurrentlyExpanded) {
                // Collapse
                _expandedBatchIds.update { it - batchId }
            } else {
                // Expand - load transactions if not already loaded
                if (!_expandedBatchTransactions.value.containsKey(batchId)) {
                    loadBatchTransactions(batchId)
                }
                _expandedBatchIds.update { it + batchId }
            }
        }
    }

    /**
     * Load transactions for a specific batch (lazy loading).
     */
    private suspend fun loadBatchTransactions(batchId: String) {
        _isLoadingBatchDetails.update { it + batchId }

        try {
            val transactions: List<Transaction> = when {
                batchId.startsWith("batch_") -> {
                    // Real purchase batch
                    val uuid = UUID.fromString(batchId.removePrefix("batch_"))
                    Log.d("HistoryViewModel", "Loading transactions for purchase batch: $uuid")
                    val result = purchaseBatchRepository.getTransactionsForBatch(uuid)
                    Log.d("HistoryViewModel", "Found ${result.size} transactions for batch $uuid")
                    result
                }
                batchId.startsWith("sale_batch_") -> {
                    // Real sale batch
                    val uuid = UUID.fromString(batchId.removePrefix("sale_batch_"))
                    Log.d("HistoryViewModel", "Loading transactions for sale batch: $uuid")
                    val result = saleBatchRepository.getTransactionsForBatch(uuid)
                    Log.d("HistoryViewModel", "Found ${result.size} transactions for sale batch $uuid")
                    result
                }
                batchId.startsWith("transfer_") -> {
                    // Transfer batch - get transaction IDs from the batch item
                    // Only show TRANSFER_IN transactions (positive weight) to avoid duplicates
                    val batch = _uiState.value.batches.find { it.id == batchId }
                    if (batch is HistoryBatchDisplayItem.TransferBatch) {
                        transactionRepository.getTransactionsByIds(batch.transactionIds)
                            .filter { it.type == TransactionType.TRANSFER_IN }
                    } else {
                        emptyList()
                    }
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
                    transferLocationName = tx.transferLocationId?.let { locationsMap[it]?.name },
                    weightKg = tx.weightKg,
                    totalAmount = tx.totalAmount,
                    createdAt = tx.createdAt,
                    isSynced = tx.syncedAt != null
                )
            }

            _expandedBatchTransactions.update { it + (batchId to displayItems) }
            _isLoadingBatchDetails.update { it - batchId }
        } catch (e: Exception) {
            _uiState.update { it.copy(error = e.message) }
            _isLoadingBatchDetails.update { it - batchId }
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

    /**
     * Sets the date range filter. Pass null to show all dates.
     */
    fun setDateRange(dateRange: DateRange?) {
        _uiState.update { it.copy(dateRange = dateRange) }
        reloadWithFilter()
    }

    fun setLocationFilter(locationId: UUID?) {
        // In restricted mode, don't allow changing location filter
        if (isRestrictedModeEnabled) return
        _uiState.update { it.copy(selectedLocationId = locationId) }
        reloadWithFilter()
    }
    
    /**
     * Enable or disable restricted mode.
     * When enabled: locks location filter to current device location.
     * When disabled: resets location filter to show all locations.
     */
    fun setRestrictedMode(enabled: Boolean) {
        val wasRestricted = isRestrictedModeEnabled
        isRestrictedModeEnabled = enabled
        
        if (enabled && !wasRestricted) {
            // Entering restricted mode - set location filter to current device location
            val currentLocationId = devicePreferences.getSelectedLocationId()
            if (currentLocationId != null) {
                _uiState.update { it.copy(selectedLocationId = currentLocationId) }
                reloadWithFilter()
            }
        } else if (!enabled && wasRestricted) {
            // Exiting restricted mode - reset to show all locations
            _uiState.update { it.copy(selectedLocationId = null) }
            reloadWithFilter()
        }
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
                dateRange = null,
                // In restricted mode, keep the location filter locked
                selectedLocationId = if (isRestrictedModeEnabled) it.selectedLocationId else null,
                searchQuery = ""
            )
        }
        reloadWithFilter()
    }

    private fun reloadWithFilter() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            // Clear expansion state on filter change
            _expandedBatchIds.value = emptySet()
            _expandedBatchTransactions.value = emptyMap()
            loadBatches(resetPage = true)
        }
    }

    /**
     * Void a batch (soft delete).
     */
    fun voidBatch(batchId: UUID, batchType: BatchType) {
        viewModelScope.launch {
            try {
                when (batchType) {
                    BatchType.PURCHASE -> purchaseBatchRepository.markVoided(batchId)
                    BatchType.SALE -> saleBatchRepository.markVoided(batchId)
                    BatchType.TRANSFER -> { /* Transfers can't be voided */ }
                    BatchType.ADJUSTMENT -> { /* Adjustments can't be voided - they are individual transactions */ }
                }
                _uiState.update { it.copy(successMessage = "✅ Партію анульовано") }
                reloadWithFilter()
            } catch (e: Exception) {
                _uiState.update { it.copy(error = e.message ?: "Помилка скасування") }
            }
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    fun dismissSuccess() {
        _uiState.update { it.copy(successMessage = null) }
    }

    companion object {
        private const val PAGE_SIZE = 50
    }
}
