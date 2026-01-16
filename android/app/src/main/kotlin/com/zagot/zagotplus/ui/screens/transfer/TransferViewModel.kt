package com.zagot.zagotplus.ui.screens.transfer

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import com.zagot.zagotplus.ui.navigation.Destination
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
    val grossWeightKg: BigDecimal,
    val tareCount: Int = 0,
    val tareWeightPerUnit: BigDecimal = BigDecimal.ZERO,
    val availableStock: BigDecimal
) {
    val totalTareWeight: BigDecimal
        get() = tareWeightPerUnit.multiply(BigDecimal(tareCount))
    
    val netWeightKg: BigDecimal
        get() = (grossWeightKg - totalTareWeight).max(BigDecimal.ZERO)
}

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
    val currentTareCount: String = "",
    val tareWeightPerUnit: String = "0.1", // Default 100g per sack
    val positions: List<TransferPosition> = emptyList(),
    val notes: String = "",
    val screenState: TransferScreenState = TransferScreenState.PRODUCT_GRID,
    val scaleWeight: BigDecimal? = null, // null = not connected (placeholder)
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val navigateBack: Boolean = false
) {
    val currentGrossWeight: BigDecimal
        get() = currentWeight.toBigDecimalOrNull() ?: BigDecimal.ZERO
    
    val currentTareCountInt: Int
        get() = if (currentTareCount.isBlank()) 0 else currentTareCount.toIntOrNull() ?: 0
    
    val currentTareWeightPerUnitDecimal: BigDecimal
        get() = tareWeightPerUnit.toBigDecimalOrNull() ?: BigDecimal.ZERO
    
    val currentTotalTareWeight: BigDecimal
        get() = currentTareWeightPerUnitDecimal.multiply(BigDecimal(currentTareCountInt))
    
    val currentNetWeight: BigDecimal
        get() = (currentGrossWeight - currentTotalTareWeight).max(BigDecimal.ZERO)

    val canAddPosition: Boolean
        get() = selectedProduct != null &&
                currentNetWeight > BigDecimal.ZERO &&
                currentNetWeight <= selectedAvailableStock &&
                (currentTareCount.isBlank() || currentTareCount.toIntOrNull()?.let { it >= 0 } == true)

    val canFinalize: Boolean
        get() = positions.isNotEmpty()

    val canConfirmTransfer: Boolean
        get() = positions.isNotEmpty() && destinationLocation != null

    val totalWeight: BigDecimal
        get() = positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.netWeightKg) }

    val exceedsAvailableStock: Boolean
        get() = currentNetWeight > selectedAvailableStock
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
    private val devicePreferences: DevicePreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    // Prefilled data from navigation arguments
    private val prefilledProductId: String? = savedStateHandle[Destination.Transfer.ARG_PRODUCT_ID]
    private val prefilledSourceLocationId: String? = savedStateHandle[Destination.Transfer.ARG_SOURCE_LOCATION_ID]
    private val prefilledDestinationLocationId: String? = savedStateHandle[Destination.Transfer.ARG_DESTINATION_LOCATION_ID]
    private val hasPrefill = prefilledProductId != null || prefilledSourceLocationId != null || prefilledDestinationLocationId != null

    // Start with loading=true to prevent flash when we have prefilled data
    private val _uiState = MutableStateFlow(TransferUiState(isLoading = true))
    val uiState: StateFlow<TransferUiState> = _uiState.asStateFlow()
    
    private var allLocations: List<Location> = emptyList()
    private var allProducts: List<Product> = emptyList()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                // Determine source location - prefer prefilled, then device preference
                val defaultLocationId = devicePreferences.getSelectedLocationId()
                val sourceLocationId = prefilledSourceLocationId?.let { 
                    try { UUID.fromString(it) } catch (_: Exception) { null }
                } ?: defaultLocationId
                
                // Load source location
                val sourceLocation = sourceLocationId?.let { locationRepository.getLocationById(it) }
                
                // Load all locations
                allLocations = locationRepository.getAllLocations().first()
                val destinations = allLocations.filter { it.id != sourceLocationId }
                
                // Pre-select destination if provided
                val destinationLocation = prefilledDestinationLocationId?.let { idStr ->
                    try {
                        val destId = UUID.fromString(idStr)
                        destinations.find { it.id == destId }
                    } catch (_: Exception) { null }
                }
                
                // Store products for later use
                allProducts = productRepository.getActiveProducts().first()
                
                // Load inventory for source location with product details
                loadInventoryForLocation(sourceLocationId ?: UUID.randomUUID())
                
                // Wait for inventory to be loaded
                val currentState = _uiState.value
                
                // Find prefilled product if specified
                var selectedProduct: Product? = null
                var selectedAvailableStock = BigDecimal.ZERO
                var screenState = TransferScreenState.PRODUCT_GRID
                
                prefilledProductId?.let { idStr ->
                    try {
                        val productId = UUID.fromString(idStr)
                        val inventoryWithProduct = currentState.inventoryItems.find { it.product.id == productId }
                            ?: _uiState.value.inventoryItems.find { it.product.id == productId }
                        inventoryWithProduct?.let {
                            selectedProduct = it.product
                            selectedAvailableStock = it.inventory.totalWeightKg
                            screenState = TransferScreenState.WEIGHT_ENTRY
                        }
                    } catch (_: Exception) { /* Invalid UUID */ }
                }
                
                _uiState.update {
                    it.copy(
                        sourceLocation = sourceLocation,
                        allLocations = allLocations,
                        availableDestinations = destinations,
                        destinationLocation = destinationLocation,
                        selectedProduct = selectedProduct ?: it.selectedProduct,
                        selectedAvailableStock = if (selectedProduct != null) selectedAvailableStock else it.selectedAvailableStock,
                        screenState = if (selectedProduct != null) screenState else it.screenState,
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
            .fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.netWeightKg) }

        val remainingStock = inventoryWithProduct.inventory.totalWeightKg.subtract(alreadyAdded)

        _uiState.update {
            it.copy(
                selectedProduct = inventoryWithProduct.product,
                selectedAvailableStock = remainingStock,
                currentWeight = "",
                currentTareCount = "",
                tareWeightPerUnit = "0.1",
                screenState = TransferScreenState.WEIGHT_ENTRY
            )
        }
    }

    fun selectProductById(productId: UUID) {
        val inventoryWithProduct = _uiState.value.inventoryItems.find { it.product.id == productId }
        inventoryWithProduct?.let { selectProduct(it) }
    }

    fun onWeightChange(weight: String) {
        // Allow only valid decimal input
        if (weight.isEmpty() || weight.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.update { it.copy(currentWeight = weight) }
        }
    }

    fun onTareCountChange(count: String) {
        if (count.isEmpty() || count.matches(Regex("^\\d+$"))) {
            _uiState.update { it.copy(currentTareCount = count) }
        }
    }

    fun onTareWeightPerUnitChange(weight: String) {
        if (weight.isEmpty() || weight.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.update { it.copy(tareWeightPerUnit = weight) }
        }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun addPosition() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val grossWeight = state.currentGrossWeight
        val netWeight = state.currentNetWeight
        val tareCount = state.currentTareCountInt
        val tareWeightPerUnit = state.currentTareWeightPerUnitDecimal

        if (netWeight <= BigDecimal.ZERO || netWeight > state.selectedAvailableStock) return

        val position = TransferPosition(
            product = product,
            grossWeightKg = grossWeight,
            tareCount = tareCount,
            tareWeightPerUnit = tareWeightPerUnit,
            availableStock = state.selectedAvailableStock
        )

        _uiState.update {
            it.copy(
                positions = it.positions + position,
                selectedProduct = null,
                currentWeight = "",
                currentTareCount = "",
                tareWeightPerUnit = "0.1",
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
                currentTareCount = "",
                tareWeightPerUnit = "0.1",
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

    /**
     * Add all inventory items as positions (transfer everything).
     * This takes all items with positive stock and adds them as positions
     * with their full available weight (no tare deduction for bulk transfer).
     */
    fun transferAll() {
        val state = _uiState.value
        if (state.inventoryItems.isEmpty()) return

        // Calculate already added weights per product
        val alreadyAddedByProduct = state.positions
            .groupBy { it.product.id }
            .mapValues { (_, positions) ->
                positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.netWeightKg) }
            }

        // Create positions for all items with remaining stock
        val newPositions = state.inventoryItems.mapNotNull { item ->
            val alreadyAdded = alreadyAddedByProduct[item.product.id] ?: BigDecimal.ZERO
            val remainingStock = item.inventory.totalWeightKg.subtract(alreadyAdded)

            if (remainingStock > BigDecimal.ZERO) {
                TransferPosition(
                    product = item.product,
                    grossWeightKg = remainingStock,
                    tareCount = 0,
                    tareWeightPerUnit = BigDecimal.ZERO,
                    availableStock = remainingStock
                )
            } else {
                null
            }
        }

        if (newPositions.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    positions = it.positions + newPositions,
                    screenState = TransferScreenState.DESTINATION
                )
            }
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
                // Create transfers for each position (use net weight after tare deduction)
                state.positions.forEach { position ->
                    transactionRepository.createTransfer(
                        fromLocationId = sourceLocationId,
                        toLocationId = state.destinationLocation.id,
                        productId = position.product.id,
                        weightKg = position.netWeightKg
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
     * Sets source location, selects the product, and pre-selects destination location.
     * When all three are provided, navigates directly to weight entry for the specific product.
     */
    fun applyPrefilledData(
        productIdStr: String?, 
        sourceLocationIdStr: String?,
        destinationLocationIdStr: String?
    ) {
        if (productIdStr == null && sourceLocationIdStr == null && destinationLocationIdStr == null) return
        
        viewModelScope.launch {
            // Wait for initial data to load
            val currentState = _uiState.value
            if (currentState.isLoading) {
                // Wait for loading to complete
                _uiState.first { !it.isLoading }
            }
            
            // First, set source location if provided (this will reload inventory)
            sourceLocationIdStr?.let { idStr ->
                try {
                    val locationId = UUID.fromString(idStr)
                    val sourceLocation = allLocations.find { it.id == locationId }
                    sourceLocation?.let { selectSourceLocation(it) }
                    
                    // Wait for inventory to reload after source location change
                    kotlinx.coroutines.delay(100)
                } catch (_: IllegalArgumentException) {
                    // Invalid UUID, ignore
                }
            }
            
            // Re-fetch state after source location change
            val stateAfterSource = _uiState.value
            
            // Find and select the product
            productIdStr?.let { idStr ->
                try {
                    val productId = UUID.fromString(idStr)
                    val inventoryWithProduct = stateAfterSource.inventoryItems.find { it.product.id == productId }
                    inventoryWithProduct?.let { selectProduct(it) }
                } catch (_: IllegalArgumentException) {
                    // Invalid UUID, ignore
                }
            }
            
            // Pre-select destination location
            destinationLocationIdStr?.let { idStr ->
                try {
                    val locationId = UUID.fromString(idStr)
                    val destination = stateAfterSource.availableDestinations.find { it.id == locationId }
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
