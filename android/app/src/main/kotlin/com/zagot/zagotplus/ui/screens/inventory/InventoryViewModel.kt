package com.zagot.zagotplus.ui.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import com.zagot.zagotplus.sync.SyncManager
import com.zagot.zagotplus.sync.SyncStatus
import com.zagot.zagotplus.sync.SyncStatusRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Represents the view mode for inventory display.
 */
enum class InventoryViewMode {
    BY_LOCATION,  // Show inventory for specific location
    TOTAL         // Show total inventory across all locations
}

data class InventoryUiState(
    val locations: List<Location> = emptyList(),
    val selectedLocation: Location? = null,
    val viewMode: InventoryViewMode = InventoryViewMode.BY_LOCATION,
    val products: List<Product> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    val isLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val lastSyncTime: Instant? = null,
    val error: String? = null
)

data class InventoryDisplayItem(
    val productId: UUID,
    val productName: String,
    val weightKg: BigDecimal,
    val isNegative: Boolean,
    val locationId: UUID? = null  // null for total view
)

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val productRepository: ProductRepository,
    private val transactionRepository: TransactionRepository,
    private val syncStatusRepository: SyncStatusRepository,
    private val syncManager: SyncManager,
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    // Track user's manual location selection (overrides device preference)
    private val _manualLocationSelection = MutableStateFlow<UUID?>(null)
    
    // Track view mode (BY_LOCATION or TOTAL)
    private val _viewMode = MutableStateFlow(InventoryViewMode.BY_LOCATION)

    val syncStatus: Flow<SyncStatus> = syncStatusRepository.syncStatus

    /**
     * Derived StateFlow that computes display items only when state changes.
     * Avoids creating new lists on every recomposition.
     */
    val displayItems: StateFlow<List<InventoryDisplayItem>> = _uiState
        .map { state ->
            if (state.viewMode == InventoryViewMode.TOTAL) {
                // For total view, aggregate inventory across all locations
                val aggregatedInventory = state.inventory
                    .groupBy { it.productId }
                    .mapValues { (_, items) -> items.sumOf { it.totalWeightKg } }
                
                state.products.mapNotNull { product ->
                    val weight = aggregatedInventory[product.id] ?: BigDecimal.ZERO
                    // Only include products with non-zero weight
                    if (weight.compareTo(BigDecimal.ZERO) == 0) return@mapNotNull null
                    InventoryDisplayItem(
                        productId = product.id,
                        productName = product.name,
                        weightKg = weight,
                        isNegative = weight < BigDecimal.ZERO,
                        locationId = null
                    )
                }
            } else {
                // For location view, show inventory for selected location
                val inventoryMap = state.inventory.associateBy { it.productId }
                state.products.mapNotNull { product ->
                    val inventoryItem = inventoryMap[product.id]
                    val weight = inventoryItem?.totalWeightKg ?: BigDecimal.ZERO
                    // Only include products with non-zero weight
                    if (weight.compareTo(BigDecimal.ZERO) == 0) return@mapNotNull null
                    InventoryDisplayItem(
                        productId = product.id,
                        productName = product.name,
                        weightKg = weight,
                        isNegative = weight < BigDecimal.ZERO,
                        locationId = state.selectedLocation?.id
                    )
                }
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private var locationsMap: Map<UUID, Location> = emptyMap()

    init {
        loadInitialData()
        observeSyncStatus()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val locations = locationRepository.getAllLocations().first()
                locationsMap = locations.associateBy { it.id }
                val products = productRepository.getActiveProducts().first()
                
                _uiState.update { it.copy(locations = locations, products = products) }
                
                // Observe view mode and location changes, reactively update inventory
                combine(_viewMode, _manualLocationSelection) { viewMode, manualSelection ->
                    viewMode to manualSelection
                }.flatMapLatest { (viewMode, manualSelection) ->
                    if (viewMode == InventoryViewMode.TOTAL) {
                        // Load all inventory for total view
                        transactionRepository.getInventory()
                            .map { inventory -> Triple(viewMode, null, inventory) }
                    } else {
                        // Load inventory for specific location
                        val locationId = manualSelection 
                            ?: devicePreferences.getSelectedLocationId()
                            ?: locationsMap.keys.firstOrNull()
                        
                        if (locationId != null) {
                            transactionRepository.getInventoryByLocation(locationId)
                                .map { inventory -> Triple(viewMode, locationId, inventory) }
                        } else {
                            flowOf(Triple(viewMode, null, emptyList<InventoryItem>()))
                        }
                    }
                }.collect { (viewMode, locationId, inventory) ->
                    val selectedLocation = locationId?.let { locationsMap[it] }
                    _uiState.update {
                        it.copy(
                            viewMode = viewMode,
                            selectedLocation = selectedLocation,
                            inventory = inventory,
                            isLoading = false
                        )
                    }
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

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncStatusRepository.syncStatus.collect { status ->
                _uiState.update { it.copy(lastSyncTime = status.lastSyncTime) }
            }
        }
    }

    fun selectLocation(location: Location) {
        if (_uiState.value.viewMode == InventoryViewMode.BY_LOCATION && 
            location == _uiState.value.selectedLocation) return
        // Switch to location view and select this location
        _viewMode.value = InventoryViewMode.BY_LOCATION
        _manualLocationSelection.value = location.id
    }
    
    fun selectTotalView() {
        if (_uiState.value.viewMode == InventoryViewMode.TOTAL) return
        _viewMode.value = InventoryViewMode.TOTAL
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            syncManager.triggerManualSync()
            // Inventory will auto-update via Flow observation
            // Just reset the refreshing flag after a short delay
            _uiState.update { it.copy(isRefreshing = false) }
        }
    }

    fun triggerSync() {
        syncManager.triggerManualSync()
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Create an inventory adjustment transaction.
     * 
     * @param locationId Location where adjustment is made
     * @param productId Product being adjusted
     * @param actualWeightKg The actual weight from physical count
     * @param currentWeightKg The current recorded weight
     * @param reason Optional reason for adjustment
     */
    fun createAdjustment(
        locationId: UUID,
        productId: UUID,
        actualWeightKg: BigDecimal,
        currentWeightKg: BigDecimal,
        reason: String?
    ) {
        viewModelScope.launch {
            try {
                val adjustmentKg = actualWeightKg - currentWeightKg
                transactionRepository.createAdjustment(
                    locationId = locationId,
                    productId = productId,
                    adjustmentKg = adjustmentKg,
                    reason = reason
                )
                // Inventory will auto-update via Flow observation
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(error = e.message ?: "Помилка при коригуванні залишків")
                }
            }
        }
    }
}
