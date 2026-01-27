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
    WEIGHING,        // Adding weight batches with tare count for current product
    POSITION_REVIEW, // Review current position before adding to list
    POSITIONS_LIST,  // Viewing all positions, can add more products
    SUMMARY,         // Final confirmation before saving
    UNIFIED_ENTRY    // Tablet: unified three-column layout
}

/**
 * UI state for the sale entry flow.
 */
data class SaleEntryUiState(
    val products: List<Product> = emptyList(),
    val inventory: List<InventoryItem> = emptyList(),
    
    // Current product being weighed
    val selectedProduct: Product? = null,
    val availableWeight: BigDecimal = BigDecimal.ZERO,
    
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
    val showWeightingsInMiddlePanel: Boolean = false // true = show weightings for current product, false = show positions list
) {
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

    // Editing batch ID from navigation arguments
    private val editingBatchIdArg: String? = savedStateHandle[Destination.SaleEntry.ARG_BATCH_ID]

    // Start with loading=true to prevent flash when editing
    private val _uiState = MutableStateFlow(SaleEntryUiState(isLoading = true))
    val uiState: StateFlow<SaleEntryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    /**
     * Set tablet mode - switches to unified layout for tablets.
     */
    fun setTabletMode(isTablet: Boolean) {
        if (isTablet && _uiState.value.screenState == SaleEntryScreenState.PRODUCT_GRID) {
            _uiState.update { it.copy(screenState = SaleEntryScreenState.UNIFIED_ENTRY) }
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
        val inventory = _uiState.value.inventory
        val availableWeight = inventory
            .find { it.productId == product.id }
            ?.totalWeightKg ?: BigDecimal.ZERO

        _uiState.update {
            it.copy(
                selectedProduct = product,
                availableWeight = availableWeight,
                pricePerKg = product.defaultSellPrice?.toPlainString() ?: "",
                currentBatches = emptyList(),
                currentWeight = "",
                currentTareCount = "",
                tareWeightPerUnit = "0.1",
                showWeightingsInMiddlePanel = true, // Show weightings when product selected
                screenState = if (it.screenState == SaleEntryScreenState.UNIFIED_ENTRY)
                    SaleEntryScreenState.UNIFIED_ENTRY
                else
                    SaleEntryScreenState.WEIGHING
            )
        }
        resetTabletState()
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

    fun removeBatch(batchId: String) {
        _uiState.update { state ->
            state.copy(
                currentBatches = state.currentBatches.filter { it.id != batchId }
            )
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

    fun removePosition(positionId: String) {
        _uiState.update { state ->
            val newPositions = state.positions.filter { it.id != positionId }
            state.copy(
                positions = newPositions,
                screenState = if (newPositions.isEmpty())
                    SaleEntryScreenState.PRODUCT_GRID
                else
                    state.screenState
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
        when (state.activeInputField) {
            SaleInputField.WEIGHT -> {
                val newValue = state.currentWeight + char
                if (DECIMAL_PATTERN.matches(newValue)) {
                    _uiState.update { it.copy(currentWeight = newValue) }
                }
            }
            SaleInputField.TARE_COUNT -> {
                val newValue = state.currentTareCount + char
                if (INTEGER_PATTERN.matches(newValue)) {
                    _uiState.update { it.copy(currentTareCount = newValue) }
                }
            }
            SaleInputField.TARE_WEIGHT_UNIT -> {
                val newValue = state.tareWeightPerUnit + char
                if (DECIMAL_PATTERN.matches(newValue)) {
                    _uiState.update { it.copy(tareWeightPerUnit = newValue) }
                }
            }
            SaleInputField.PRICE -> {
                val newValue = state.pricePerKg + char
                if (DECIMAL_PATTERN.matches(newValue)) {
                    _uiState.update { it.copy(pricePerKg = newValue) }
                }
            }
        }
    }

    /**
     * Handle decimal point input.
     */
    fun onKeypadDecimal() {
        val state = _uiState.value
        when (state.activeInputField) {
            SaleInputField.WEIGHT -> {
                if (!state.currentWeight.contains('.')) {
                    val newValue = if (state.currentWeight.isEmpty()) "0." else state.currentWeight + "."
                    _uiState.update { it.copy(currentWeight = newValue) }
                }
            }
            SaleInputField.TARE_COUNT -> {
                // Tare count is integer, no decimal
            }
            SaleInputField.TARE_WEIGHT_UNIT -> {
                if (!state.tareWeightPerUnit.contains('.')) {
                    val newValue = if (state.tareWeightPerUnit.isEmpty()) "0." else state.tareWeightPerUnit + "."
                    _uiState.update { it.copy(tareWeightPerUnit = newValue) }
                }
            }
            SaleInputField.PRICE -> {
                if (!state.pricePerKg.contains('.')) {
                    val newValue = if (state.pricePerKg.isEmpty()) "0." else state.pricePerKg + "."
                    _uiState.update { it.copy(pricePerKg = newValue) }
                }
            }
        }
    }

    /**
     * Handle backspace (delete last character).
     */
    fun onKeypadBackspace() {
        val state = _uiState.value
        when (state.activeInputField) {
            SaleInputField.WEIGHT -> {
                _uiState.update { it.copy(currentWeight = state.currentWeight.dropLast(1)) }
            }
            SaleInputField.TARE_COUNT -> {
                _uiState.update { it.copy(currentTareCount = state.currentTareCount.dropLast(1)) }
            }
            SaleInputField.TARE_WEIGHT_UNIT -> {
                _uiState.update { it.copy(tareWeightPerUnit = state.tareWeightPerUnit.dropLast(1)) }
            }
            SaleInputField.PRICE -> {
                _uiState.update { it.copy(pricePerKg = state.pricePerKg.dropLast(1)) }
            }
        }
    }

    /**
     * Cycle to next input field (TAB behavior).
     */
    fun onNextInputField() {
        val state = _uiState.value
        val nextField = when (state.activeInputField) {
            SaleInputField.WEIGHT -> SaleInputField.TARE_COUNT
            SaleInputField.TARE_COUNT -> if (state.isInFinalizationMode)
                SaleInputField.TARE_WEIGHT_UNIT
            else
                SaleInputField.WEIGHT
            SaleInputField.TARE_WEIGHT_UNIT -> SaleInputField.PRICE
            SaleInputField.PRICE -> SaleInputField.TARE_WEIGHT_UNIT
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
