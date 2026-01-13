package com.zagot.zagotplus.ui.screens.transfer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.util.UUID
import javax.inject.Inject

/**
 * Represents a single transfer position (line item).
 */
data class TransferPosition(
    val id: String = UUID.randomUUID().toString(),
    val product: Product,
    val weightKg: BigDecimal,
    val availableStock: BigDecimal
)

/**
 * Screen state for the transfer entry flow.
 */
enum class TransferScreenState {
    PRODUCT_GRID,    // Selecting product from inventory
    WEIGHT_ENTRY,    // Entering weight for selected product
    POSITIONS_LIST,  // Viewing/editing positions before finalizing
    DESTINATION,     // Selecting destination location
    SUMMARY          // Showing summary overlay before saving
}

/**
 * UI state for the transfer entry flow.
 */
data class TransferUiState(
    val sourceLocation: Location? = null,
    val destinationLocation: Location? = null,
    val allLocations: List<Location> = emptyList(),
    val availableDestinations: List<Location> = emptyList(),
    val inventoryItems: List<InventoryWithProduct> = emptyList(),
    val selectedProduct: Product? = null,
    val selectedAvailableStock: BigDecimal = BigDecimal.ZERO,
    val currentWeight: String = "",
    val positions: List<TransferPosition> = emptyList(),
    val notes: String = "",
    val screenState: TransferScreenState = TransferScreenState.PRODUCT_GRID,
    val scaleWeight: BigDecimal? = null, // null = not connected (placeholder)
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val navigateBack: Boolean = false
) {
    val canAddPosition: Boolean
        get() = selectedProduct != null &&
                currentWeight.toBigDecimalOrNull()?.let { 
                    it > BigDecimal.ZERO && it <= selectedAvailableStock 
                } == true

    val canFinalize: Boolean
        get() = positions.isNotEmpty()

    val canConfirmTransfer: Boolean
        get() = positions.isNotEmpty() && destinationLocation != null

    val totalWeight: BigDecimal
        get() = positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.weightKg) }

    val exceedsAvailableStock: Boolean
        get() {
            val weight = currentWeight.toBigDecimalOrNull() ?: BigDecimal.ZERO
            return weight > selectedAvailableStock
        }
}

/**
 * Inventory item with product details for display.
 */
data class InventoryWithProduct(
    val inventory: InventoryItem,
    val product: Product
)

@HiltViewModel
class TransferViewModel @Inject constructor(
    private val transactionRepository: TransactionRepository,
    private val productRepository: ProductRepository,
    private val locationRepository: LocationRepository,
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(TransferUiState())
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()
    
    private var allLocations: List<Location> = emptyList()
    private var allProducts: List<Product> = emptyList()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val locationId = devicePreferences.getSelectedLocationId()
                
                // Load source location
                val sourceLocation = locationId?.let { locationRepository.getLocationById(it) }
                
                // Load all locations
                allLocations = locationRepository.getAllLocations().first()
                val destinations = allLocations.filter { it.id != locationId }
                
                // Store products for later use
                allProducts = productRepository.getActiveProducts().first()
                
                // Load inventory for current location with product details
                loadInventoryForLocation(locationId ?: UUID.randomUUID())
                
                _uiState.update {
                    it.copy(
                        sourceLocation = sourceLocation,
                        allLocations = allLocations,
                        availableDestinations = destinations,
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
    
    private fun loadInventoryForLocation(locationId: UUID) {
        viewModelScope.launch {
            combine(
                transactionRepository.getInventoryByLocation(locationId),
                productRepository.getActiveProducts()
            ) { inventory, products ->
                val productMap = products.associateBy { it.id }
                inventory
                    .filter { it.totalWeightKg > BigDecimal.ZERO }
                    .mapNotNull { item ->
                        productMap[item.productId]?.let { product ->
                            InventoryWithProduct(item, product)
                        }
                    }
            }.collect { inventoryWithProducts ->
                _uiState.update {
                    it.copy(inventoryItems = inventoryWithProducts)
                }
            }
        }
    }
    
    fun selectSourceLocation(location: Location) {
        val currentSource = _uiState.value.sourceLocation
        if (location.id == currentSource?.id) return
        
        // Clear positions when changing source location
        val destinations = allLocations.filter { it.id != location.id }
        
        _uiState.update {
            it.copy(
                sourceLocation = location,
                availableDestinations = destinations,
                positions = emptyList(),
                selectedProduct = null,
                currentWeight = "",
                selectedAvailableStock = BigDecimal.ZERO,
                screenState = TransferScreenState.PRODUCT_GRID
            )
        }
        
        // Reload inventory for new source location
        loadInventoryForLocation(location.id)
    }

    fun selectProduct(inventoryWithProduct: InventoryWithProduct) {
        // Calculate already added weight for this product
        val alreadyAdded = _uiState.value.positions
            .filter { it.product.id == inventoryWithProduct.product.id }
            .fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.weightKg) }
        
        val remainingStock = inventoryWithProduct.inventory.totalWeightKg.subtract(alreadyAdded)
        
        _uiState.update {
            it.copy(
                selectedProduct = inventoryWithProduct.product,
                selectedAvailableStock = remainingStock,
                currentWeight = "",
                screenState = TransferScreenState.WEIGHT_ENTRY
            )
        }
    }

    fun onWeightChange(weight: String) {
        // Allow only valid decimal input
        if (weight.isEmpty() || weight.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.update { it.copy(currentWeight = weight) }
        }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun addPosition() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val weight = state.currentWeight.toBigDecimalOrNull() ?: return

        if (weight <= BigDecimal.ZERO || weight > state.selectedAvailableStock) return

        val position = TransferPosition(
            product = product,
            weightKg = weight,
            availableStock = state.selectedAvailableStock
        )

        _uiState.update {
            it.copy(
                positions = it.positions + position,
                selectedProduct = null,
                currentWeight = "",
                selectedAvailableStock = BigDecimal.ZERO,
                screenState = TransferScreenState.POSITIONS_LIST
            )
        }
    }

    fun removePosition(positionId: String) {
        _uiState.update { state ->
            val newPositions = state.positions.filter { it.id != positionId }
            state.copy(
                positions = newPositions,
                screenState = if (newPositions.isEmpty()) 
                    TransferScreenState.PRODUCT_GRID 
                else 
                    state.screenState
            )
        }
    }

    fun backToGrid() {
        _uiState.update {
            it.copy(
                selectedProduct = null,
                currentWeight = "",
                selectedAvailableStock = BigDecimal.ZERO,
                screenState = if (it.positions.isNotEmpty()) 
                    TransferScreenState.POSITIONS_LIST 
                else 
                    TransferScreenState.PRODUCT_GRID
            )
        }
    }

    fun addAnotherProduct() {
        _uiState.update {
            it.copy(screenState = TransferScreenState.PRODUCT_GRID)
        }
    }

    fun proceedToDestination() {
        val state = _uiState.value
        if (state.positions.isEmpty()) return

        _uiState.update {
            it.copy(screenState = TransferScreenState.DESTINATION)
        }
    }

    fun selectDestination(location: Location) {
        _uiState.update {
            it.copy(
                destinationLocation = location,
                screenState = TransferScreenState.SUMMARY
            )
        }
    }

    fun backToPositions() {
        _uiState.update {
            it.copy(screenState = TransferScreenState.POSITIONS_LIST)
        }
    }

    fun confirmSave() {
        val state = _uiState.value
        if (state.positions.isEmpty() || state.destinationLocation == null) return
        val sourceLocationId = state.sourceLocation?.id ?: return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Create transfers for each position
                state.positions.forEach { position ->
                    transactionRepository.createTransfer(
                        fromLocationId = sourceLocationId,
                        toLocationId = state.destinationLocation.id,
                        productId = position.product.id,
                        weightKg = position.weightKg
                    )
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        navigateBack = true
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        screenState = TransferScreenState.POSITIONS_LIST,
                        error = e.message ?: "Помилка збереження"
                    )
                }
            }
        }
    }

    fun dismissSummary() {
        _uiState.update {
            it.copy(screenState = TransferScreenState.DESTINATION)
        }
    }

    fun cancel() {
        _uiState.update { it.copy(navigateBack = true) }
    }

    fun onNavigationHandled() {
        _uiState.update {
            it.copy(navigateBack = false)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
    
    /**
     * Apply prefilled data from navigation (e.g., from inventory screen).
     * Selects the product and optionally pre-selects destination location.
     */
    fun applyPrefilledData(productIdStr: String?, destinationLocationIdStr: String?) {
        if (productIdStr == null && destinationLocationIdStr == null) return
        
        viewModelScope.launch {
            // Wait for initial data to load
            val currentState = _uiState.value
            if (currentState.isLoading) {
                // Wait for loading to complete
                _uiState.first { !it.isLoading }
            }
            
            val state = _uiState.value
            
            // Find and select the product
            productIdStr?.let { idStr ->
                try {
                    val productId = UUID.fromString(idStr)
                    val inventoryWithProduct = state.inventoryItems.find { it.product.id == productId }
                    inventoryWithProduct?.let { selectProduct(it) }
                } catch (_: IllegalArgumentException) {
                    // Invalid UUID, ignore
                }
            }
            
            // Pre-select destination location
            destinationLocationIdStr?.let { idStr ->
                try {
                    val locationId = UUID.fromString(idStr)
                    val destination = state.availableDestinations.find { it.id == locationId }
                    destination?.let { 
                        _uiState.update { current -> 
                            current.copy(destinationLocation = it) 
                        }
                    }
                } catch (_: IllegalArgumentException) {
                    // Invalid UUID, ignore
                }
            }
        }
    }
}
