package com.zagot.zagotplus.ui.screens.inventory

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

data class InventoryUiState(
    val locations: List<Location> = emptyList(),
    val selectedLocation: Location? = null,
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
    val isNegative: Boolean
)

@HiltViewModel
class InventoryViewModel @Inject constructor(
    private val locationRepository: LocationRepository,
    private val productRepository: ProductRepository,
    private val transactionRepository: TransactionRepository,
    private val syncStatusRepository: SyncStatusRepository,
    private val syncManager: SyncManager
) : ViewModel() {

    private val _uiState = MutableStateFlow(InventoryUiState())
    val uiState: StateFlow<InventoryUiState> = _uiState.asStateFlow()

    val syncStatus: Flow<SyncStatus> = syncStatusRepository.syncStatus

    /**
     * Derived StateFlow that computes display items only when state changes.
     * Avoids creating new lists on every recomposition.
     */
    val displayItems: StateFlow<List<InventoryDisplayItem>> = _uiState
        .map { state ->
            val inventoryMap = state.inventory.associateBy { it.productId }
            state.products.map { product ->
                val weight = inventoryMap[product.id]?.totalWeightKg ?: BigDecimal.ZERO
                InventoryDisplayItem(
                    productId = product.id,
                    productName = product.name,
                    weightKg = weight,
                    isNegative = weight < BigDecimal.ZERO
                )
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    init {
        loadInitialData()
        observeSyncStatus()
    }

    private fun loadInitialData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val locations = locationRepository.getAllLocations().first()
                val products = productRepository.getActiveProducts().first()
                val firstLocation = locations.firstOrNull()
                
                val inventory = if (firstLocation != null) {
                    transactionRepository.getInventoryByLocation(firstLocation.id).first()
                } else {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        locations = locations,
                        selectedLocation = firstLocation,
                        products = products,
                        inventory = inventory,
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

    private fun observeSyncStatus() {
        viewModelScope.launch {
            syncStatusRepository.syncStatus.collect { status ->
                _uiState.update { it.copy(lastSyncTime = status.lastSyncTime) }
            }
        }
    }

    fun selectLocation(location: Location) {
        if (location == _uiState.value.selectedLocation) return
        
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true, selectedLocation = location) }
            try {
                val inventory = transactionRepository.getInventoryByLocation(location.id).first()
                _uiState.update {
                    it.copy(inventory = inventory, isLoading = false)
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка завантаження залишків",
                        isLoading = false
                    )
                }
            }
        }
    }

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(isRefreshing = true) }
            try {
                syncManager.triggerManualSync()
                
                // Reload inventory data
                val location = _uiState.value.selectedLocation
                if (location != null) {
                    val inventory = transactionRepository.getInventoryByLocation(location.id).first()
                    _uiState.update {
                        it.copy(inventory = inventory, isRefreshing = false)
                    }
                } else {
                    _uiState.update { it.copy(isRefreshing = false) }
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        error = e.message ?: "Помилка оновлення",
                        isRefreshing = false
                    )
                }
            }
        }
    }

    fun triggerSync() {
        syncManager.triggerManualSync()
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
