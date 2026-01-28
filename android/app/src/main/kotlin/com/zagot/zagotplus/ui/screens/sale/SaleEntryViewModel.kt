package com.zagot.zagotplus.ui.screens.sale

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.preferences.ProductOrderPreferences
import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.SaleBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.SaleBatchRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import com.zagot.zagotplus.ui.navigation.Destination
import com.zagot.zagotplus.ui.navigation.SaleMode
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

/**
 * Represents a single weighing batch in a sale (e.g., a few sacks weighed together).
 */
data class SaleWeighingBatch(
    val id: String = UUID.randomUUID().toString(),
    val grossWeightKg: BigDecimal,
    val tareCount: Int // Number of sacks/boxes in this batch
)

/**
 * Represents a complete position (product line) in a sale with all its weighings.
 */
data class SalePosition(
    val id: String = UUID.randomUUID().toString(),
    val product: Product,
    val batches: List<SaleWeighingBatch>,
    val tareWeightPerUnit: BigDecimal, // Weight of one sack/box
    val pricePerKg: BigDecimal
) {
    val grossWeight: BigDecimal
        get() = batches.fold(BigDecimal.ZERO) { acc, batch -> acc.add(batch.grossWeightKg) }
    
    val totalTareCount: Int
        get() = batches.sumOf { it.tareCount }
    
    val totalTareWeight: BigDecimal
        get() = tareWeightPerUnit.multiply(BigDecimal(totalTareCount))
    
    val netWeight: BigDecimal
        get() = (grossWeight - totalTareWeight).max(BigDecimal.ZERO)
    
    val totalAmount: BigDecimal
        get() = netWeight.multiply(pricePerKg).setScale(2, RoundingMode.HALF_UP)
}

/**
 * Input field enum for tablet numpad focus tracking.
 */
enum class SaleInputField {
    WEIGHT,           // Batch weight input (kg)
    TARE_COUNT,       // Batch tare count input (number of sacks/bags)
    TARE_WEIGHT_UNIT, // Tare weight per unit (kg per sack/bag)
    PRICE             // Price per kg
}

/**
 * Screen state for the sale entry flow.
 */
enum class SaleEntryScreenState {
    PRODUCT_GRID,    // Selecting product from grid
    WEIGHT_ENTRY,    // Regular mode mobile: simple weight + price entry (mirrors purchase flow)
    WEIGHING,        // Wholesale mode: adding weight batches with tare count for current product
    POSITION_REVIEW, // Wholesale mode: review current position before adding to list
    POSITIONS_LIST,  // Viewing all positions, can add more products
    SUMMARY,         // Final confirmation before saving
    UNIFIED_ENTRY    // Tablet: unified three-column layout
}

/**
 * UI state for the sale entry flow.
 */
data class SaleEntryUiState(
    val saleMode: SaleMode = SaleMode.WHOLESALE, // Mode is immutable once set
    val products: List<Product> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),

    // Current product being weighed
    val selectedProduct: Product? = null,
    // Tablet mode: product shown in data entry panel (separate from phone's selectedProduct)
    val dataEntryProduct: Product? = null,
    val availableWeight: BigDecimal = BigDecimal.ZERO,
    // True when using tablet kiosk mode (set by screen based on isTablet())
    val isTabletMode: Boolean = false,
    
    // Current weighing input
    val currentWeight: String = "",
    val currentTareCount: String = "",
    
    // Batches for current product
    val currentBatches: List<SaleWeighingBatch> = emptyList(),
    
    // Tare and price for current product
    val tareWeightPerUnit: String = "0.1", // Default 100g per sack
    val pricePerKg: String = "",
    
    // Completed positions (products already configured)
    val positions: List<SalePosition> = emptyList(),
    
    // Sale-level notes
    val notes: String = "",
    
    val screenState: SaleEntryScreenState = SaleEntryScreenState.PRODUCT_GRID,
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val navigateBack: Boolean = false,
    val editingPosition: SalePosition? = null, // Position being edited
    val showExitConfirmation: Boolean = false, // Show confirmation dialog before exit
    // Editing mode: if set, we're correcting an existing batch
    val editingBatchId: UUID? = null,
    val correctionReason: String = "",
    // Location selection for edit mode
    val availableLocations: List<Location> = emptyList(),
    val selectedLocationId: UUID? = null, // null = use device preference
    val originalLocationId: UUID? = null,  // the original batch location (for display)

    // Tablet-specific numpad state
    val activeInputField: SaleInputField = SaleInputField.WEIGHT,
    val isInFinalizationMode: Boolean = false, // false = batch entry mode, true = finalization mode
    val showWeightingsInMiddlePanel: Boolean = false, // true = show weightings for current product, false = show positions list
    val tabletEditingBatchId: String? = null, // ID of batch being edited inline on tablet (null = adding new)
    val tabletReviewingPositionId: String? = null // ID of position whose weightings are being reviewed (tap on position)
) {
    // Computed property: are we editing an existing batch?
    val isTabletBatchEditMode: Boolean
        get() = tabletEditingBatchId != null
    
    // Computed property: are we reviewing a position's weightings?
    val isReviewingPosition: Boolean
        get() = tabletReviewingPositionId != null
    
    // Get the position being reviewed
    val reviewingPosition: SalePosition?
        get() = tabletReviewingPositionId?.let { id -> positions.find { it.id == id } }
    
    val hasUnsavedData: Boolean
        get() = positions.isNotEmpty() || 
                currentBatches.isNotEmpty() || 
                currentWeight.isNotBlank() ||
                notes.isNotBlank()
    // Current product calculations
    val currentGrossWeight: BigDecimal
        get() = currentBatches.fold(BigDecimal.ZERO) { acc, batch -> acc.add(batch.grossWeightKg) }
    
    val currentTotalTareCount: Int
        get() = currentBatches.sumOf { it.tareCount }
    
    val currentTotalTareWeight: BigDecimal
        get() {
            val unitWeight = tareWeightPerUnit.toBigDecimalOrNull() ?: BigDecimal.ZERO
            return unitWeight.multiply(BigDecimal(currentTotalTareCount))
        }
    
    val currentNetWeight: BigDecimal
        get() = (currentGrossWeight - currentTotalTareWeight).max(BigDecimal.ZERO)
    
    val currentTotalAmount: BigDecimal?
        get() {
            val price = pricePerKg.toBigDecimalOrNull() ?: return null
            return if (price > BigDecimal.ZERO) {
                currentNetWeight.multiply(price).setScale(2, RoundingMode.HALF_UP)
            } else null
        }
    
    val canAddBatch: Boolean
        get() = currentWeight.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
                (currentTareCount.isBlank() || currentTareCount.toIntOrNull()?.let { it >= 0 } == true)
    
    val canProceedToReview: Boolean
        get() = currentBatches.isNotEmpty()
    
    val canAddPosition: Boolean
        get() = currentBatches.isNotEmpty() &&
                pricePerKg.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

    // On tablet: uses dataEntryProduct, on phone: uses selectedProduct
    val activeProduct: Product?
        get() = if (isTabletMode) dataEntryProduct else selectedProduct

    // Regular mode computed properties
    val canAddRegularPosition: Boolean
        get() = saleMode == SaleMode.REGULAR &&
                activeProduct != null &&
                currentWeight.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
                pricePerKg.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

    // Regular mode: single weight * price calculation
    val regularModeTotal: BigDecimal?
        get() {
            if (saleMode != SaleMode.REGULAR) return null
            val weight = currentWeight.toBigDecimalOrNull() ?: return null
            val price = pricePerKg.toBigDecimalOrNull() ?: return null
            return if (weight > BigDecimal.ZERO && price > BigDecimal.ZERO) {
                weight.multiply(price).setScale(2, RoundingMode.HALF_UP)
            } else null
        }
    
    // All positions calculations
    val totalWeight: BigDecimal
        get() = positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.netWeight) }
    
    val totalAmount: BigDecimal
        get() = positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.totalAmount) }
    
    val canFinalize: Boolean
        get() = positions.isNotEmpty()
    
    // Inventory warning for current product
    val showInventoryWarning: Boolean
        get() = currentNetWeight > availableWeight && availableWeight >= BigDecimal.ZERO
}

@HiltViewModel
class SaleEntryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val transactionRepository: TransactionRepository,
    private val saleBatchRepository: SaleBatchRepository,
    private val locationRepository: LocationRepository,
    private val devicePreferences: DevicePreferences,
    private val productOrderPreferences: ProductOrderPreferences,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        // Pre-compiled regex patterns for input validation (avoid recompilation on each keystroke)
        private val DECIMAL_PATTERN = Regex("^\\d*\\.?\\d*$")
        private val INTEGER_PATTERN = Regex("^\\d+$")
    }

    // Navigation arguments
    private val editingBatchIdArg: String? = savedStateHandle[Destination.SaleEntry.ARG_BATCH_ID]
    private val saleModeArg: String? = savedStateHandle[Destination.SaleEntry.ARG_MODE]

    // Start with loading=true to prevent flash when editing
    private val _uiState = MutableStateFlow(
        SaleEntryUiState(
            isLoading = true,
            saleMode = SaleMode.fromString(saleModeArg)
        )
    )
    val uiState: StateFlow<SaleEntryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    /**
     * Set tablet mode - switches to unified layout for tablets.
     */
    fun setTabletMode(isTablet: Boolean) {
        _uiState.update {
            it.copy(
                isTabletMode = isTablet,
                screenState = if (isTablet && it.screenState == SaleEntryScreenState.PRODUCT_GRID)
                    SaleEntryScreenState.UNIFIED_ENTRY
                else
                    it.screenState
            )
        }
    }

    private fun loadData() {
        viewModelScope.launch {
            try {
                // Load locations for edit mode dropdown
                val locations = locationRepository.getAllLocations().first()

                val products = productRepository.getActiveProducts().first()
                val orderedProducts = productOrderPreferences.applyOrder(products) { it.id }
                val locationId = devicePreferences.getSelectedLocationId()
                val inventory = if (locationId != null) {
                    transactionRepository.getInventoryByLocation(locationId).first()
                } else {
                    emptyList()
                }

                _uiState.update {
                    it.copy(
                        products = orderedProducts,
                        inventory = inventory,
                        availableLocations = locations,
                        isLoading = editingBatchIdArg != null // Keep loading if we need to load a batch
                    )
                }

                // Load batch for editing if batchId was provided
                if (editingBatchIdArg != null && _uiState.value.editingBatchId == null) {
                    loadBatchForEditingInternal(editingBatchIdArg)
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
    
    /**
     * Internal function to load batch for editing.
     * Called from init when batchId is provided via SavedStateHandle.
     */
    private fun loadBatchForEditingInternal(batchIdString: String) {
        loadBatchForEditing(batchIdString)
    }

    fun onProductOrderChanged(newOrder: List<UUID>) {
        productOrderPreferences.setProductOrder(newOrder)
        // Reorder the current products list
        val currentProducts = _uiState.value.products
        val orderedProducts = productOrderPreferences.applyOrder(currentProducts) { it.id }
        _uiState.update { it.copy(products = orderedProducts) }
    }

    fun selectProduct(product: Product) {
        val state = _uiState.value

        // REGULAR MODE: Simple product selection for data entry (both phone and tablet)
        if (state.saleMode == SaleMode.REGULAR) {
            selectProductForEntry(product)
            return
        }

        // WHOLESALE MODE: Different flow for phone vs tablet
        if (state.isTabletMode) {
            // TABLET: Check for existing position or create new empty position
            val existingPosition = state.positions.find { it.product.id == product.id }

            if (existingPosition != null) {
                reviewPositionWeightings(existingPosition)
            } else {
                // Create new empty position for wholesale tablet mode
                val defaultPrice = product.defaultSellPrice ?: BigDecimal.ZERO

                val newPosition = SalePosition(
                    product = product,
                    batches = emptyList(),
                    tareWeightPerUnit = BigDecimal("0.1"), // Default tare
                    pricePerKg = defaultPrice
                )

                _uiState.update {
                    it.copy(
                        positions = it.positions + newPosition,
                        tabletReviewingPositionId = newPosition.id,
                        currentWeight = "",
                        currentTareCount = "",
                        tareWeightPerUnit = "0.1",
                        pricePerKg = if (defaultPrice > BigDecimal.ZERO) defaultPrice.toPlainString() else "",
                        selectedProduct = null,
                        activeInputField = SaleInputField.WEIGHT
                    )
                }
            }
            resetTabletState()
        } else {
            // PHONE: Navigate to weighing screen with selected product
            val defaultPrice = product.defaultSellPrice ?: BigDecimal.ZERO
            _uiState.update {
                it.copy(
                    selectedProduct = product,
                    currentWeight = "",
                    currentTareCount = "",
                    currentBatches = emptyList(),
                    tareWeightPerUnit = "0.1",
                    pricePerKg = if (defaultPrice > BigDecimal.ZERO) defaultPrice.toPlainString() else "",
                    screenState = SaleEntryScreenState.WEIGHING,
                    activeInputField = SaleInputField.WEIGHT
                )
            }
        }
    }

    /**
     * Tablet/Regular mode: select product for data entry panel without creating position.
     * Populates input fields with default values.
     * For regular mode on phone, navigates to WEIGHT_ENTRY (simple weight+price screen like purchase).
     */
    fun selectProductForEntry(product: Product) {
        val state = _uiState.value
        val defaultPrice = product.defaultSellPrice ?: BigDecimal.ZERO

        // Get available inventory for this product
        val availableWeight = state.inventory
            .find { it.productId == product.id }
            ?.totalWeightKg ?: BigDecimal.ZERO

        _uiState.update {
            it.copy(
                selectedProduct = product,
                dataEntryProduct = product,
                availableWeight = availableWeight,
                currentWeight = "",
                pricePerKg = if (defaultPrice > BigDecimal.ZERO) defaultPrice.toPlainString() else "",
                activeInputField = SaleInputField.WEIGHT,
                // For regular mode on phone, navigate to WEIGHT_ENTRY (mirrors purchase flow)
                screenState = if (!state.isTabletMode && state.saleMode == SaleMode.REGULAR)
                    SaleEntryScreenState.WEIGHT_ENTRY
                else
                    it.screenState
            )
        }
    }

    fun onWeightChange(weight: String) {
        if (weight.isEmpty() || DECIMAL_PATTERN.matches(weight)) {
            _uiState.update { it.copy(currentWeight = weight) }
        }
    }

    fun onTareCountChange(count: String) {
        if (count.isEmpty() || INTEGER_PATTERN.matches(count)) {
            _uiState.update { it.copy(currentTareCount = count) }
        }
    }

    fun addBatch() {
        val state = _uiState.value
        val weight = state.currentWeight.toBigDecimalOrNull() ?: return
        val tareCount = if (state.currentTareCount.isBlank()) 0 else state.currentTareCount.toIntOrNull() ?: return

        if (weight <= BigDecimal.ZERO || tareCount < 0) return

        // TABLET MODE: Add batch to position in the list
        if (state.isTabletMode) {
            val selectedId = state.tabletReviewingPositionId ?: return
            val currentPosition = state.positions.find { it.id == selectedId } ?: return

            val editingBatchId = state.tabletEditingBatchId

            // Create the new list of batches for this position
            val newBatches = if (editingBatchId != null) {
                // Edit existing batch inside position
                currentPosition.batches.map { batch ->
                    if (batch.id == editingBatchId) {
                        batch.copy(grossWeightKg = weight, tareCount = tareCount)
                    } else batch
                }
            } else {
                // Add new batch to position
                val newBatch = SaleWeighingBatch(
                    grossWeightKg = weight,
                    tareCount = tareCount
                )
                currentPosition.batches + newBatch
            }

            // Update the position with new batches
            val updatedPosition = currentPosition.copy(batches = newBatches)

            _uiState.update {
                it.copy(
                    // Replace the old position with the updated one in the main list
                    positions = it.positions.map { pos ->
                        if (pos.id == selectedId) updatedPosition else pos
                    },
                    // Reset inputs
                    currentWeight = "",
                    currentTareCount = "",
                    tabletEditingBatchId = null,
                    activeInputField = SaleInputField.WEIGHT
                )
            }
        } else {
            // MOBILE MODE: Add batch to currentBatches (original master branch logic)
            val batch = SaleWeighingBatch(
                grossWeightKg = weight,
                tareCount = tareCount
            )

            _uiState.update {
                it.copy(
                    currentBatches = it.currentBatches + batch,
                    currentWeight = "",
                    currentTareCount = ""
                )
            }
        }
    }

    fun removeBatch(batchId: String) {
        val state = _uiState.value

        // TABLET MODE: Remove batch from position in list
        if (state.isTabletMode) {
            val selectedId = state.tabletReviewingPositionId ?: return

            _uiState.update { s ->
                val newPositions = s.positions.map { pos ->
                    if (pos.id == selectedId) {
                        pos.copy(batches = pos.batches.filter { it.id != batchId })
                    } else pos
                }

                s.copy(
                    positions = newPositions,
                    // Clear edit state if needed
                    tabletEditingBatchId = if (s.tabletEditingBatchId == batchId) null else s.tabletEditingBatchId,
                    currentWeight = if (s.tabletEditingBatchId == batchId) "" else s.currentWeight,
                    currentTareCount = if (s.tabletEditingBatchId == batchId) "" else s.currentTareCount
                )
            }
        } else {
            // MOBILE MODE: Remove batch from currentBatches (original master branch logic)
            _uiState.update { s ->
                s.copy(
                    currentBatches = s.currentBatches.filter { it.id != batchId }
                )
            }
        }
    }

    /**
     * Tablet only: select a batch for inline editing.
     * Tapping the same batch again deselects it (toggle behavior).
     */
    fun selectTabletBatch(batch: SaleWeighingBatch) {
        _uiState.update { state ->
            if (state.tabletEditingBatchId == batch.id) {
                // Deselect - clear inputs and edit state
                state.copy(
                    tabletEditingBatchId = null,
                    currentWeight = "",
                    currentTareCount = "",
                    activeInputField = SaleInputField.WEIGHT
                )
            } else {
                // Select - fill inputs with batch data
                state.copy(
                    tabletEditingBatchId = batch.id,
                    currentWeight = batch.grossWeightKg.toPlainString(),
                    currentTareCount = batch.tareCount.toString(),
                    activeInputField = SaleInputField.WEIGHT
                )
            }
        }
    }

    /**
     * Tablet only: tap on a position to review its weightings.
     * Tapping the same position again clears the review (toggle behavior).
     */
    fun reviewPositionWeightings(position: SalePosition) {
        _uiState.update { state ->
            if (state.tabletReviewingPositionId == position.id) {
                // TOGGLE OFF: If clicking the already selected one, close the panel
                state.copy(
                    tabletReviewingPositionId = null,
                    currentWeight = "",
                    currentTareCount = "",
                    tabletEditingBatchId = null
                )
            } else {
                // TOGGLE ON: Select this position
                state.copy(
                    tabletReviewingPositionId = position.id,
                    // Clear inputs so we don't accidentally add data to the wrong product
                    currentWeight = "",
                    currentTareCount = "",
                    tabletEditingBatchId = null,
                    activeInputField = SaleInputField.WEIGHT
                )
            }
        }
    }

    /**
     * Update an existing batch's weight and tare count.
     * Used for editing weighings in the review step.
     */
    fun updateBatch(batchId: String, newWeight: BigDecimal, newTareCount: Int) {
        _uiState.update { state ->
            state.copy(
                currentBatches = state.currentBatches.map { batch ->
                    if (batch.id == batchId) {
                        batch.copy(
                            grossWeightKg = newWeight,
                            tareCount = newTareCount
                        )
                    } else batch
                }
            )
        }
    }

    fun proceedToReview() {
        if (_uiState.value.currentBatches.isNotEmpty()) {
            _uiState.update { it.copy(screenState = SaleEntryScreenState.POSITION_REVIEW) }
        }
    }

    fun onTareWeightPerUnitChange(weight: String) {
        if (weight.isEmpty() || DECIMAL_PATTERN.matches(weight)) {
            _uiState.update { it.copy(tareWeightPerUnit = weight) }
        }
    }

    fun onPriceChange(price: String) {
        if (price.isEmpty() || DECIMAL_PATTERN.matches(price)) {
            _uiState.update { it.copy(pricePerKg = price) }
        }
    }

    /**
     * Called when price field receives focus for the first time.
     * Clears the default price so user can type without deleting it manually.
     */
    fun onPriceFocused() {
        _uiState.update { it.copy(pricePerKg = "") }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun addPositionAndContinue() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val price = state.pricePerKg.toBigDecimalOrNull() ?: return
        val tareWeight = state.tareWeightPerUnit.toBigDecimalOrNull() ?: BigDecimal("0.1")

        if (state.currentBatches.isEmpty() || price <= BigDecimal.ZERO) return

        val position = SalePosition(
            product = product,
            batches = state.currentBatches,
            tareWeightPerUnit = tareWeight,
            pricePerKg = price
        )

        _uiState.update {
            it.copy(
                positions = it.positions + position,
                selectedProduct = null,
                currentBatches = emptyList(),
                currentWeight = "",
                currentTareCount = "",
                tareWeightPerUnit = "0.1",
                pricePerKg = "",
                showWeightingsInMiddlePanel = false, // Stay on positions view after adding
                screenState = if (it.screenState == SaleEntryScreenState.UNIFIED_ENTRY)
                    SaleEntryScreenState.UNIFIED_ENTRY
                else
                    SaleEntryScreenState.POSITIONS_LIST
            )
        }
        resetTabletState()
    }

    /**
     * Regular mode: Add position with single weight (no batches, no tare).
     */
    fun addRegularPositionAndContinue() {
        val state = _uiState.value
        if (state.saleMode != SaleMode.REGULAR) return

        val product = state.activeProduct ?: return
        val weight = state.currentWeight.toBigDecimalOrNull() ?: return
        val price = state.pricePerKg.toBigDecimalOrNull() ?: return

        if (weight <= BigDecimal.ZERO || price <= BigDecimal.ZERO) return

        // Create a single batch with the weight (tare count = 0, tare weight = 0)
        val batch = SaleWeighingBatch(
            grossWeightKg = weight,
            tareCount = 0
        )

        val position = SalePosition(
            product = product,
            batches = listOf(batch),
            tareWeightPerUnit = BigDecimal.ZERO,
            pricePerKg = price
        )

        _uiState.update {
            it.copy(
                positions = it.positions + position,
                selectedProduct = null,
                dataEntryProduct = null,
                currentWeight = "",
                pricePerKg = "",
                showWeightingsInMiddlePanel = false,
                screenState = if (it.screenState == SaleEntryScreenState.UNIFIED_ENTRY)
                    SaleEntryScreenState.UNIFIED_ENTRY
                else
                    SaleEntryScreenState.POSITIONS_LIST
            )
        }
        resetTabletState()
    }

    fun removePosition(positionId: String) {
        _uiState.update { state ->
            val newPositions = state.positions.filter { it.id != positionId }

            val nextScreen = when {
                // If on tablet/unified mode, stay there regardless of empty list
                state.screenState == SaleEntryScreenState.UNIFIED_ENTRY -> SaleEntryScreenState.UNIFIED_ENTRY
                // If on mobile and list is empty, go back to grid
                newPositions.isEmpty() -> SaleEntryScreenState.PRODUCT_GRID
                // Otherwise stay on the list
                else -> state.screenState
            }

            state.copy(
                positions = newPositions,
                screenState = nextScreen,
                // Also ensure we clear the review ID if the currently selected position was deleted
                tabletReviewingPositionId = if (state.tabletReviewingPositionId == positionId) null else state.tabletReviewingPositionId,
                // If we deleted the active position, clear inputs too
                currentWeight = if (state.tabletReviewingPositionId == positionId) "" else state.currentWeight,
                currentTareCount = if (state.tabletReviewingPositionId == positionId) "" else state.currentTareCount
            )
        }
    }

    fun startEditPosition(position: SalePosition) {
        _uiState.update { it.copy(editingPosition = position) }
    }

    fun cancelEditPosition() {
        _uiState.update { it.copy(editingPosition = null) }
    }

    fun updatePosition(positionId: String, newTareWeight: BigDecimal, newPrice: BigDecimal) {
        _uiState.update { state ->
            val newPositions = state.positions.map { pos ->
                if (pos.id == positionId) {
                    pos.copy(
                        tareWeightPerUnit = newTareWeight,
                        pricePerKg = newPrice
                    )
                } else pos
            }
            state.copy(
                positions = newPositions,
                editingPosition = null
            )
        }
    }

    /**
     * Update a batch within a saved position.
     */
    fun updatePositionBatch(positionId: String, batchId: String, newWeight: BigDecimal, newTareCount: Int) {
        _uiState.update { state ->
            val newPositions = state.positions.map { pos ->
                if (pos.id == positionId) {
                    pos.copy(
                        batches = pos.batches.map { batch ->
                            if (batch.id == batchId) {
                                batch.copy(grossWeightKg = newWeight, tareCount = newTareCount)
                            } else batch
                        }
                    )
                } else pos
            }
            // Update editingPosition to reflect changes
            val updatedEditingPosition = newPositions.find { it.id == positionId }
            state.copy(
                positions = newPositions,
                editingPosition = updatedEditingPosition
            )
        }
    }

    /**
     * Delete a batch from a saved position.
     */
    fun deletePositionBatch(positionId: String, batchId: String) {
        _uiState.update { state ->
            val newPositions = state.positions.map { pos ->
                if (pos.id == positionId) {
                    pos.copy(batches = pos.batches.filter { it.id != batchId })
                } else pos
            }
            // Update editingPosition to reflect changes
            val updatedEditingPosition = newPositions.find { it.id == positionId }
            state.copy(
                positions = newPositions,
                editingPosition = updatedEditingPosition
            )
        }
    }

    fun addAnotherProduct() {
        _uiState.update {
            it.copy(screenState = SaleEntryScreenState.PRODUCT_GRID)
        }
    }

    fun backToWeighing() {
        _uiState.update {
            it.copy(screenState = SaleEntryScreenState.WEIGHING)
        }
    }

    fun backToGrid() {
        _uiState.update {
            it.copy(
                selectedProduct = null,
                currentBatches = emptyList(),
                currentWeight = "",
                currentTareCount = "",
                tareWeightPerUnit = "0.1",
                pricePerKg = "",
                screenState = if (it.positions.isNotEmpty())
                    SaleEntryScreenState.POSITIONS_LIST
                else
                    SaleEntryScreenState.PRODUCT_GRID
            )
        }
    }

    fun finalize() {
        val state = _uiState.value
        if (!state.canFinalize) return

        // Save immediately and show summary
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                // Use selected location if available (edit mode), otherwise use device preference
                val locationId = _uiState.value.selectedLocationId 
                    ?: devicePreferences.getSelectedLocationId()
                    ?: throw IllegalStateException("Локація не обрана")

                val now = Instant.now()
                val deviceId = devicePreferences.getDeviceId()
                val batchId = UUID.randomUUID()
                val batchLocalId = UUID.randomUUID().toString()

                // Calculate totals
                val totalWeight = state.positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.netWeight) }
                val totalAmount = state.positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.totalAmount) }

                val batch = SaleBatch(
                    id = batchId,
                    localId = batchLocalId,
                    locationId = locationId,
                    notes = state.notes.ifBlank { null },
                    totalWeightKg = totalWeight,
                    totalAmount = totalAmount,
                    itemCount = state.positions.size,
                    deviceId = deviceId,
                    createdAt = now,
                    syncedAt = null
                )

                val transactions = state.positions.map { position ->
                    Transaction(
                        id = UUID.randomUUID(),
                        localId = UUID.randomUUID().toString(),
                        locationId = locationId,
                        type = TransactionType.SALE,
                        transferLocationId = null,
                        productId = position.product.id,
                        weightKg = -position.netWeight, // Sales are negative
                        pricePerKg = position.pricePerKg,
                        totalAmount = position.totalAmount,
                        notes = buildPositionNotes(position, state.notes),
                        deviceId = deviceId,
                        createdAt = now,
                        syncedAt = null,
                        batchId = null,
                        saleBatchId = batchId
                    )
                }

                // Check if we're in correction mode
                val editingBatchId = state.editingBatchId
                if (editingBatchId != null) {
                    val reason = state.correctionReason.ifBlank { "Виправлення помилки" }
                    saleBatchRepository.correctBatch(
                        originalBatchId = editingBatchId,
                        correctedBatch = batch,
                        correctedTransactions = transactions,
                        reason = reason
                    )
                } else {
                    saleBatchRepository.createBatchWithTransactions(batch, transactions)
                }

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        screenState = SaleEntryScreenState.SUMMARY
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        error = e.message ?: "Помилка збереження"
                    )
                }
            }
        }
    }

    fun onCorrectionReasonChange(reason: String) {
        _uiState.update { it.copy(correctionReason = reason) }
    }

    /**
     * Select a location for the batch (used in edit mode).
     */
    fun selectLocation(locationId: UUID?) {
        _uiState.update { it.copy(selectedLocationId = locationId) }
    }

    /**
     * Exit from summary screen - navigates back to the list.
     */
    fun exitFromSummary() {
        _uiState.update { it.copy(navigateBack = true) }
    }

    /**
     * Load an existing batch for editing/correction.
     * Pre-fills the UI with the batch's transactions.
     */
    fun loadBatchForEditing(batchIdString: String) {
        val batchId = try {
            UUID.fromString(batchIdString)
        } catch (e: Exception) {
            _uiState.update { it.copy(error = "Невірний ID партії") }
            return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val batch = saleBatchRepository.getById(batchId)
                if (batch == null) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Партію не знайдено"
                        )
                    }
                    return@launch
                }

                val transactions = saleBatchRepository.getTransactionsForBatch(batchId)
                
                // Fetch products directly from repository to ensure they're available
                // (init loading may not have completed yet)
                val allProducts = productRepository.getActiveProducts().first()
                val products = allProducts.associateBy { it.id }

                // Convert transactions back to SalePositions
                // Note: This is a simplified conversion - tare info from notes may be lost
                val positions = transactions.mapNotNull { tx ->
                    val product = tx.productId?.let { products[it] }
                    if (product != null) {
                        SalePosition(
                            product = product,
                            batches = listOf(
                                SaleWeighingBatch(
                                    grossWeightKg = tx.weightKg.abs(),
                                    tareCount = 0
                                )
                            ),
                            tareWeightPerUnit = BigDecimal("0.1"),
                            pricePerKg = tx.pricePerKg ?: BigDecimal.ZERO
                        )
                    } else null
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        editingBatchId = batchId,
                        positions = positions,
                        notes = batch.notes ?: "",
                        products = allProducts.ifEmpty { it.products },
                        // Preserve original location for edit mode
                        selectedLocationId = batch.locationId,
                        originalLocationId = batch.locationId,
                        screenState = if (positions.isNotEmpty()) 
                            SaleEntryScreenState.POSITIONS_LIST 
                        else 
                            SaleEntryScreenState.PRODUCT_GRID
                    )
                }
            } catch (e: Exception) {
                _uiState.update {
                    it.copy(
                        isLoading = false,
                        error = e.message ?: "Помилка завантаження"
                    )
                }
            }
        }
    }

    private fun buildPositionNotes(position: SalePosition, saleNotes: String): String? {
        val parts = mutableListOf<String>()
        
        // Add tare info
        if (position.totalTareCount > 0) {
            val tareInfo = "${position.totalTareCount} шт × ${position.tareWeightPerUnit.toPlainString()} кг = ${position.totalTareWeight.toPlainString()} кг тара"
            parts.add(tareInfo)
        }
        
        // Add gross weight for reference
        parts.add("Брутто: ${position.grossWeight.toPlainString()} кг")
        
        // Add user notes if present
        if (saleNotes.isNotBlank()) {
            parts.add(saleNotes)
        }
        
        return parts.joinToString("; ").ifBlank { null }
    }

    fun dismissSummary() {
        _uiState.update { it.copy(screenState = SaleEntryScreenState.POSITIONS_LIST) }
    }

    fun cancel() {
        val state = _uiState.value
        if (state.hasUnsavedData) {
            _uiState.update { it.copy(showExitConfirmation = true) }
        } else {
            _uiState.update { it.copy(navigateBack = true) }
        }
    }

    fun confirmExit() {
        _uiState.update { it.copy(showExitConfirmation = false, navigateBack = true) }
    }

    fun dismissExitConfirmation() {
        _uiState.update { it.copy(showExitConfirmation = false) }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigateBack = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // ==================== TABLET NUMPAD METHODS ====================

    /**
     * Handle numpad character input (digits).
     */
    fun onKeypadInput(char: Char) {
        val state = _uiState.value

        // 1. Update the UI Text State
        var newWeight = state.currentWeight
        var newTareCount = state.currentTareCount
        var newTareWeight = state.tareWeightPerUnit
        var newPrice = state.pricePerKg

        when (state.activeInputField) {
            SaleInputField.WEIGHT -> {
                if (DECIMAL_PATTERN.matches(state.currentWeight + char)) {
                    newWeight += char
                }
            }
            SaleInputField.TARE_COUNT -> {
                if (INTEGER_PATTERN.matches(state.currentTareCount + char)) {
                    newTareCount += char
                }
            }
            SaleInputField.TARE_WEIGHT_UNIT -> {
                if (DECIMAL_PATTERN.matches(state.tareWeightPerUnit + char)) {
                    newTareWeight += char
                }
            }
            SaleInputField.PRICE -> {
                if (DECIMAL_PATTERN.matches(state.pricePerKg + char)) {
                    newPrice += char
                }
            }
        }

        // 2. Commit changes to State AND Active Position
        updateStateAndPosition(newWeight, newTareCount, newTareWeight, newPrice)
    }

    /**
     * Handle decimal point input.
     */
    fun onKeypadDecimal() {
        val state = _uiState.value
        var newWeight = state.currentWeight
        var newTareWeight = state.tareWeightPerUnit
        var newPrice = state.pricePerKg

        when (state.activeInputField) {
            SaleInputField.WEIGHT -> {
                if (!newWeight.contains('.')) newWeight += if (newWeight.isEmpty()) "0." else "."
            }
            SaleInputField.TARE_WEIGHT_UNIT -> {
                if (!newTareWeight.contains('.')) newTareWeight += if (newTareWeight.isEmpty()) "0." else "."
            }
            SaleInputField.PRICE -> {
                if (!newPrice.contains('.')) newPrice += if (newPrice.isEmpty()) "0." else "."
            }
            else -> {} // Tare count is int
        }

        updateStateAndPosition(newWeight, state.currentTareCount, newTareWeight, newPrice)
    }

    /**
     * Handle backspace (delete last character).
     */
    fun onKeypadBackspace() {
        val state = _uiState.value
        val newWeight = if (state.activeInputField == SaleInputField.WEIGHT) state.currentWeight.dropLast(1) else state.currentWeight
        val newTareCount = if (state.activeInputField == SaleInputField.TARE_COUNT) state.currentTareCount.dropLast(1) else state.currentTareCount
        val newTareWeight = if (state.activeInputField == SaleInputField.TARE_WEIGHT_UNIT) state.tareWeightPerUnit.dropLast(1) else state.tareWeightPerUnit
        val newPrice = if (state.activeInputField == SaleInputField.PRICE) state.pricePerKg.dropLast(1) else state.pricePerKg

        updateStateAndPosition(newWeight, newTareCount, newTareWeight, newPrice)
    }

    // Helper to update both UI state strings AND the actual Position object
    private fun updateStateAndPosition(
        weight: String,
        tareCount: String,
        tareWeight: String,
        price: String
    ) {
        _uiState.update { s ->
            // Update Position if we are editing Price or Tare Weight
            var updatedPositions = s.positions
            if (s.tabletReviewingPositionId != null) {
                updatedPositions = s.positions.map { pos ->
                    if (pos.id == s.tabletReviewingPositionId) {
                        pos.copy(
                            tareWeightPerUnit = tareWeight.toBigDecimalOrNull() ?: BigDecimal.ZERO,
                            pricePerKg = price.toBigDecimalOrNull() ?: BigDecimal.ZERO
                        )
                    } else pos
                }
            }

            s.copy(
                currentWeight = weight,
                currentTareCount = tareCount,
                tareWeightPerUnit = tareWeight,
                pricePerKg = price,
                positions = updatedPositions
            )
        }
    }

    /**
     * Cycle to next input field (TAB behavior).
     */
    fun onNextInputField() {
        val state = _uiState.value
        val nextField = when {
            // Regular mode: cycle between WEIGHT and PRICE only
            state.saleMode == SaleMode.REGULAR -> {
                when (state.activeInputField) {
                    SaleInputField.WEIGHT -> SaleInputField.PRICE
                    SaleInputField.PRICE -> SaleInputField.WEIGHT
                    else -> SaleInputField.WEIGHT
                }
            }
            // Wholesale mode: existing logic
            else -> {
                when (state.activeInputField) {
                    SaleInputField.WEIGHT -> SaleInputField.TARE_COUNT
                    SaleInputField.TARE_COUNT -> if (state.isInFinalizationMode)
                        SaleInputField.TARE_WEIGHT_UNIT
                    else
                        SaleInputField.WEIGHT
                    SaleInputField.TARE_WEIGHT_UNIT -> SaleInputField.PRICE
                    SaleInputField.PRICE -> SaleInputField.TARE_WEIGHT_UNIT
                }
            }
        }
        _uiState.update { it.copy(activeInputField = nextField) }
    }

    /**
     * Select an input field and clear its current value (tap-to-clear UX).
     */
    fun selectInputFieldAndClear(field: SaleInputField) {
        val state = _uiState.value
        val newState = when (field) {
            SaleInputField.WEIGHT -> state.copy(activeInputField = field, currentWeight = "")
            SaleInputField.TARE_COUNT -> state.copy(activeInputField = field, currentTareCount = "")
            SaleInputField.TARE_WEIGHT_UNIT -> state.copy(activeInputField = field, tareWeightPerUnit = "")
            SaleInputField.PRICE -> state.copy(activeInputField = field, pricePerKg = "")
        }
        _uiState.update { newState }
    }

    /**
     * Toggle between batch entry mode and finalization mode.
     */
    fun toggleFinalizationMode() {
        val state = _uiState.value
        if (state.currentBatches.isEmpty() && !state.isInFinalizationMode) {
            // Can't proceed to finalization without batches
            return
        }

        val newMode = !state.isInFinalizationMode
        val newActiveField = if (newMode) SaleInputField.TARE_WEIGHT_UNIT else SaleInputField.WEIGHT

        _uiState.update {
            it.copy(
                isInFinalizationMode = newMode,
                activeInputField = newActiveField
                // Keep weightings visible during finalization - they switch to positions only after adding position
            )
        }
    }

    /**
     * Reset tablet state when product changes or position is added.
     */
    fun resetTabletState() {
        _uiState.update {
            it.copy(
                isInFinalizationMode = false,
                activeInputField = SaleInputField.WEIGHT
                // Note: showWeightingsInMiddlePanel is managed by selectProduct and addPosition
            )
        }
    }
}
