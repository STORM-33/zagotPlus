package com.zagot.zagotplus.ui.screens.purchase

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.preferences.ProductOrderPreferences
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.PurchaseBatchRepository
import com.zagot.zagotplus.hardware.printer.PrinterConnectionState
import com.zagot.zagotplus.hardware.printer.PrinterService
import com.zagot.zagotplus.hardware.printer.receipt.PurchaseReceiptBuilder
import com.zagot.zagotplus.hardware.scales.ScalesConnectionState
import com.zagot.zagotplus.hardware.scales.ScalesService
import com.zagot.zagotplus.ui.navigation.Destination
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

/**
 * Represents a single purchase position (line item).
 */
data class PurchasePosition(
    val id: String = UUID.randomUUID().toString(),
    val product: Product,
    val weightKg: BigDecimal,
    val pricePerKg: BigDecimal,
    val totalAmount: BigDecimal
)

/**
 * Tracks which input field is active for numpad input in tablet mode.
 */
enum class InputField { WEIGHT, PRICE }

/**
 * Screen state for the purchase entry flow.
 */
enum class PurchaseEntryScreenState {
    PRODUCT_GRID,    // Phone: Selecting product from grid
    WEIGHT_ENTRY,    // Phone: Entering weight/price for selected product
    POSITIONS_LIST,  // Phone: Viewing/editing positions before finalizing
    UNIFIED_ENTRY,   // Tablet: All-in-one kiosk layout
    SUMMARY          // Both: Showing summary overlay before saving
}

/**
 * UI state for the purchase entry flow.
 */
data class PurchaseEntryUiState(
    val products: List<Product> = emptyList(),
    val selectedProduct: Product? = null,
    // Tablet mode: product shown in data entry panel (separate from phone's selectedProduct)
    val dataEntryProduct: Product? = null,
    // True when using tablet kiosk mode (set by screen based on isTablet())
    val isTabletMode: Boolean = false,
    val currentWeight: String = "",
    val currentPrice: String = "",
    val positions: List<PurchasePosition> = emptyList(),
    val notes: String = "",
    val screenState: PurchaseEntryScreenState = PurchaseEntryScreenState.PRODUCT_GRID,
    // Scale weight from TCP connection (via ScalesService)
    // null when scales not connected - falls back to manual entry
    val scaleWeight: BigDecimal? = null,
    val isScaleStable: Boolean = false, // true when scale reading is stable
    val isManualWeightMode: Boolean = false, // true = user overriding scale weight
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val navigateBack: Boolean = false,
    val editingPosition: PurchasePosition? = null, // Position being edited
    val showExitConfirmation: Boolean = false, // Show confirmation dialog before exit
    // Editing mode: if set, we're correcting an existing batch
    val editingBatchId: UUID? = null,
    val correctionReason: String = "",
    // Location selection for edit mode
    val availableLocations: List<Location> = emptyList(),
    val selectedLocationId: UUID? = null, // null = use device preference
    val originalLocationId: UUID? = null,  // the original batch location (for display)
    // Tablet numpad: which field is receiving input
    val activeInputField: InputField = InputField.WEIGHT,
    // Tablet inline edit: position being edited (null = add mode)
    val tabletEditingPositionId: String? = null
) {
    val hasUnsavedData: Boolean
        get() = positions.isNotEmpty() || 
                currentWeight.isNotBlank() ||
                (currentPrice.isNotBlank() && selectedProduct != null) ||
                notes.isNotBlank()
    val isScaleConnected: Boolean
        get() = scaleWeight != null

    val effectiveWeight: String
        get() = if (isScaleConnected && !isManualWeightMode) {
            scaleWeight?.toPlainString() ?: ""
        } else {
            currentWeight
        }

    val currentTotal: BigDecimal?
        get() {
            val weight = effectiveWeight.toBigDecimalOrNull()
            val price = currentPrice.toBigDecimalOrNull()
            return if (weight != null && price != null && weight > BigDecimal.ZERO) {
                weight.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP)
            } else null
        }

    // On tablet: uses dataEntryProduct, on phone: uses selectedProduct
    val activeProduct: Product?
        get() = if (isTabletMode) dataEntryProduct else selectedProduct

    val canAddPosition: Boolean
        get() = activeProduct != null &&
                effectiveWeight.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
                currentPrice.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

    val canFinalize: Boolean
        get() = positions.isNotEmpty()

    val totalWeight: BigDecimal
        get() = positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.weightKg) }

    val totalAmount: BigDecimal
        get() = positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.totalAmount) }

    // Tablet: true when editing an existing position inline (vs adding new)
    val isTabletEditMode: Boolean
        get() = isTabletMode && tabletEditingPositionId != null
}

@HiltViewModel
class PurchaseEntryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val purchaseBatchRepository: PurchaseBatchRepository,
    private val locationRepository: LocationRepository,
    private val devicePreferences: DevicePreferences,
    private val productOrderPreferences: ProductOrderPreferences,
    private val scalesService: ScalesService,
    private val printerService: PrinterService,
    savedStateHandle: SavedStateHandle
) : ViewModel() {

    companion object {
        private const val TAG = "PurchaseEntryVM"
        // Pre-compiled regex pattern for decimal input validation (avoid recompilation on each keystroke)
        private val DECIMAL_PATTERN = Regex("^\\d*\\.?\\d*$")
    }

    // Editing batch ID from navigation arguments
    private val editingBatchIdArg: String? = savedStateHandle[Destination.PurchaseEntry.ARG_BATCH_ID]
    
    // Start with loading=true to prevent flash when editing
    private val _uiState = MutableStateFlow(PurchaseEntryUiState(isLoading = true))
    val uiState: StateFlow<PurchaseEntryUiState> = _uiState.asStateFlow()

    init {
        loadProductsAndBatch()
        observeScales()
    }

    /**
     * Observe scales weight readings (optional - no-op if scales not connected).
     * When scales are connected and not in manual mode, weight is auto-populated.
     */
    private fun observeScales() {
        // Observe connection state
        viewModelScope.launch {
            scalesService.connectionState.collect { state ->
                when (state) {
                    is ScalesConnectionState.Connected -> {
                        Log.d(TAG, "Scales connected: ${state.deviceInfo}")
                    }
                    is ScalesConnectionState.Disconnected -> {
                        // Clear scale weight when disconnected - falls back to manual entry
                        _uiState.update { it.copy(scaleWeight = null, isScaleStable = false) }
                    }
                    else -> { /* Connecting, Reconnecting, Error - ignore */ }
                }
            }
        }

        // Observe weight readings
        viewModelScope.launch {
            scalesService.weightReadings.collect { reading ->
                // Only update if not in manual mode
                if (!_uiState.value.isManualWeightMode) {
                    _uiState.update {
                        it.copy(
                            scaleWeight = reading.weightKg,
                            isScaleStable = reading.isStable
                        )
                    }
                }
            }
        }
    }

    private fun loadProductsAndBatch() {
        viewModelScope.launch {
            // Load locations for edit mode dropdown
            val locations = locationRepository.getAllLocations().first()
            _uiState.update { it.copy(availableLocations = locations) }
            
            productRepository.getActiveProducts().collect { products ->
                val orderedProducts = productOrderPreferences.applyOrder(products) { it.id }
                _uiState.update { 
                    it.copy(
                        products = orderedProducts,
                        isLoading = editingBatchIdArg != null // Keep loading if we need to load a batch
                    )
                }
                
                // Load batch for editing if batchId was provided
                if (editingBatchIdArg != null && _uiState.value.editingBatchId == null) {
                    loadBatchForEditingInternal(editingBatchIdArg)
                }
            }
        }
    }

    fun onProductOrderChanged(newOrder: List<UUID>) {
        productOrderPreferences.setProductOrder(newOrder)
        // Reorder the current products list
        val currentProducts = _uiState.value.products
        val orderedProducts = productOrderPreferences.applyOrder(currentProducts) { it.id }
        _uiState.update { it.copy(products = orderedProducts) }
    }

    /**
     * Select a product. On phone, navigates to weight entry. On tablet, only populates data entry panel.
     */
    fun selectProduct(product: Product) {
        val state = _uiState.value
        if (state.isTabletMode) {
            // Tablet: populate data entry panel without navigation
            selectProductForEntry(product)
        } else {
            // Phone: navigate to weight entry screen
            _uiState.update {
                it.copy(
                    selectedProduct = product,
                    currentWeight = "",
                    currentPrice = product.defaultBuyPrice?.toPlainString() ?: "",
                    isManualWeightMode = false, // Reset to auto mode on new product
                    screenState = PurchaseEntryScreenState.WEIGHT_ENTRY
                )
            }
        }
    }

    /**
     * Tablet only: select product for data entry panel without navigating.
     * Also clears any tablet editing state (exits edit mode if editing a position).
     */
    fun selectProductForEntry(product: Product) {
        _uiState.update {
            it.copy(
                dataEntryProduct = product,
                currentWeight = "",
                currentPrice = product.defaultBuyPrice?.toPlainString() ?: "",
                isManualWeightMode = false,
                tabletEditingPositionId = null,
                activeInputField = InputField.WEIGHT
            )
        }
    }

    /**
     * Set tablet mode (called from screen when device is detected as tablet).
     */
    fun setTabletMode(isTablet: Boolean) {
        _uiState.update { 
            it.copy(
                isTabletMode = isTablet,
                screenState = if (isTablet && it.screenState != PurchaseEntryScreenState.SUMMARY) 
                    PurchaseEntryScreenState.UNIFIED_ENTRY 
                else 
                    it.screenState
            )
        }
    }

    fun onWeightChange(weight: String) {
        // Allow only valid decimal input (uses pre-compiled pattern)
        if (weight.isEmpty() || DECIMAL_PATTERN.matches(weight)) {
            _uiState.update { it.copy(currentWeight = weight) }
        }
    }

    fun toggleManualWeightMode() {
        _uiState.update { state ->
            val newManualMode = !state.isManualWeightMode
            // When entering manual mode, copy scale weight to manual field if available
            val newWeight = if (newManualMode && state.scaleWeight != null) {
                state.scaleWeight.toPlainString()
            } else {
                state.currentWeight
            }
            state.copy(
                isManualWeightMode = newManualMode,
                currentWeight = newWeight
            )
        }
    }

    fun onPriceChange(price: String) {
        // Allow only valid decimal input (uses pre-compiled pattern)
        if (price.isEmpty() || DECIMAL_PATTERN.matches(price)) {
            _uiState.update { it.copy(currentPrice = price) }
        }
    }

    /**
     * Called when price field receives focus for the first time.
     * Clears the default price so user can type without deleting it manually.
     */
    fun onPriceFocused() {
        _uiState.update { it.copy(currentPrice = "") }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    // ==================== Tablet Numpad Input Methods ====================

    /**
     * Select which field receives numpad input.
     */
    fun selectInputField(field: InputField) {
        _uiState.update { it.copy(activeInputField = field) }
    }

    /**
     * Select field and clear its current value (auto-clear on tap, like mobile flow).
     */
    fun selectInputFieldAndClear(field: InputField) {
        _uiState.update { state ->
            when (field) {
                InputField.WEIGHT -> state.copy(activeInputField = field, currentWeight = "")
                InputField.PRICE -> state.copy(activeInputField = field, currentPrice = "")
            }
        }
    }

    /**
     * Switch to the next input field (WEIGHT -> PRICE -> WEIGHT).
     */
    fun onNextInputField() {
        _uiState.update { state ->
            val nextField = when (state.activeInputField) {
                InputField.WEIGHT -> InputField.PRICE
                InputField.PRICE -> InputField.WEIGHT
            }
            state.copy(activeInputField = nextField)
        }
    }

    /**
     * Handle numpad digit input - append to the active field.
     */
    fun onKeypadInput(char: Char) {
        if (!char.isDigit()) return
        
        _uiState.update { state ->
            when (state.activeInputField) {
                InputField.WEIGHT -> {
                    val newWeight = appendDigit(state.currentWeight, char)
                    state.copy(currentWeight = newWeight)
                }
                InputField.PRICE -> {
                    val newPrice = appendDigit(state.currentPrice, char)
                    state.copy(currentPrice = newPrice)
                }
            }
        }
    }

    /**
     * Handle decimal point input.
     */
    fun onKeypadDecimal() {
        _uiState.update { state ->
            when (state.activeInputField) {
                InputField.WEIGHT -> {
                    val newWeight = appendDecimal(state.currentWeight)
                    state.copy(currentWeight = newWeight)
                }
                InputField.PRICE -> {
                    val newPrice = appendDecimal(state.currentPrice)
                    state.copy(currentPrice = newPrice)
                }
            }
        }
    }

    /**
     * Handle backspace - remove last character from active field.
     */
    fun onKeypadBackspace() {
        _uiState.update { state ->
            when (state.activeInputField) {
                InputField.WEIGHT -> {
                    val newWeight = state.currentWeight.dropLast(1)
                    state.copy(currentWeight = newWeight)
                }
                InputField.PRICE -> {
                    val newPrice = state.currentPrice.dropLast(1)
                    state.copy(currentPrice = newPrice)
                }
            }
        }
    }

    /**
     * Append a digit to a decimal string, respecting input constraints.
     * - Max 2 decimal places
     * - Max total length of 10 characters
     */
    private fun appendDigit(current: String, digit: Char): String {
        // Limit total length
        if (current.length >= 10) return current
        
        // Check decimal places
        val decimalIndex = current.indexOf('.')
        if (decimalIndex != -1) {
            val decimalPlaces = current.length - decimalIndex - 1
            if (decimalPlaces >= 2) return current // Max 2 decimal places
        }
        
        // Avoid leading zeros (except for "0." pattern)
        if (current == "0" && digit != '.') {
            return digit.toString()
        }
        
        return current + digit
    }

    /**
     * Append a decimal point if valid.
     */
    private fun appendDecimal(current: String): String {
        // Only one decimal allowed
        if (current.contains('.')) return current
        
        // Add leading zero if empty
        if (current.isEmpty()) return "0."
        
        return "$current."
    }

    fun addPosition() {
        val state = _uiState.value
        // Use dataEntryProduct on tablet, selectedProduct on phone
        val product = state.activeProduct ?: return
        val weight = state.effectiveWeight.toBigDecimalOrNull() ?: return
        val price = state.currentPrice.toBigDecimalOrNull() ?: return

        if (weight <= BigDecimal.ZERO || price <= BigDecimal.ZERO) return

        val total = weight.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP)

        // Tablet edit mode: update existing position instead of adding new
        if (state.isTabletEditMode && state.tabletEditingPositionId != null) {
            _uiState.update {
                val updatedPositions = it.positions.map { pos ->
                    if (pos.id == state.tabletEditingPositionId) {
                        pos.copy(
                            product = product,
                            weightKg = weight,
                            pricePerKg = price,
                            totalAmount = total
                        )
                    } else pos
                }
                it.copy(
                    positions = updatedPositions,
                    dataEntryProduct = null,
                    currentWeight = "",
                    currentPrice = "",
                    tabletEditingPositionId = null, // Exit edit mode
                    activeInputField = InputField.WEIGHT
                )
            }
            return
        }

        // Normal add mode
        val position = PurchasePosition(
            product = product,
            weightKg = weight,
            pricePerKg = price,
            totalAmount = total
        )

        _uiState.update {
            it.copy(
                positions = it.positions + position,
                selectedProduct = null,
                dataEntryProduct = null,
                currentWeight = "",
                currentPrice = "",
                tabletEditingPositionId = null,
                // On tablet: stay in unified entry. On phone: go to positions list
                screenState = if (it.isTabletMode) 
                    PurchaseEntryScreenState.UNIFIED_ENTRY 
                else 
                    PurchaseEntryScreenState.POSITIONS_LIST
            )
        }
    }

    /**
     * Tablet mode: Select a position for inline editing.
     * If already selected, deselect it.
     * Populates input fields with position data.
     */
    fun selectTabletPosition(position: PurchasePosition) {
        _uiState.update { state ->
            if (state.tabletEditingPositionId == position.id) {
                // Deselect - clear inputs
                state.copy(
                    tabletEditingPositionId = null,
                    dataEntryProduct = null,
                    currentWeight = "",
                    currentPrice = "",
                    activeInputField = InputField.WEIGHT
                )
            } else {
                // Select - fill inputs with position data
                state.copy(
                    tabletEditingPositionId = position.id,
                    dataEntryProduct = position.product,
                    currentWeight = position.weightKg.toPlainString(),
                    currentPrice = position.pricePerKg.toPlainString(),
                    activeInputField = InputField.WEIGHT
                )
            }
        }
    }

    /**
     * Tablet mode: Cancel editing and deselect position.
     */
    fun cancelTabletPositionEdit() {
        _uiState.update { state ->
            state.copy(
                tabletEditingPositionId = null,
                dataEntryProduct = null,
                currentWeight = "",
                currentPrice = "",
                activeInputField = InputField.WEIGHT
            )
        }
    }

    fun removePosition(positionId: String) {
        _uiState.update { state ->
            val newPositions = state.positions.filter { it.id != positionId }
            state.copy(
                positions = newPositions,
                // On tablet: always stay in unified entry. On phone: go to grid if no positions
                screenState = if (state.isTabletMode) {
                    PurchaseEntryScreenState.UNIFIED_ENTRY
                } else if (newPositions.isEmpty()) {
                    PurchaseEntryScreenState.PRODUCT_GRID
                } else {
                    state.screenState
                }
            )
        }
    }

    fun startEditPosition(position: PurchasePosition) {
        _uiState.update { it.copy(editingPosition = position) }
    }

    fun cancelEditPosition() {
        _uiState.update { it.copy(editingPosition = null) }
    }

    fun updatePosition(positionId: String, newWeight: BigDecimal, newPrice: BigDecimal) {
        _uiState.update { state ->
            val newPositions = state.positions.map { pos ->
                if (pos.id == positionId) {
                    val newTotal = newWeight.multiply(newPrice).setScale(2, java.math.RoundingMode.HALF_UP)
                    pos.copy(
                        weightKg = newWeight,
                        pricePerKg = newPrice,
                        totalAmount = newTotal
                    )
                } else pos
            }
            state.copy(
                positions = newPositions,
                editingPosition = null
            )
        }
    }

    fun backToGrid() {
        _uiState.update {
            it.copy(
                selectedProduct = null,
                currentWeight = "",
                currentPrice = "",
                screenState = if (it.positions.isNotEmpty()) 
                    PurchaseEntryScreenState.POSITIONS_LIST 
                else 
                    PurchaseEntryScreenState.PRODUCT_GRID
            )
        }
    }

    fun addAnotherProduct() {
        _uiState.update {
            it.copy(screenState = PurchaseEntryScreenState.PRODUCT_GRID)
        }
    }

    fun finalize() {
        val state = _uiState.value
        if (state.positions.isEmpty()) return

        // Save immediately and show summary
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val now = Instant.now()
                val deviceId = devicePreferences.getDeviceId()
                // Use selected location if available (edit mode), otherwise use device preference
                val locationId = state.selectedLocationId ?: devicePreferences.getSelectedLocationId()
                val batchId = UUID.randomUUID()
                val batchLocalId = UUID.randomUUID().toString()

                val batch = PurchaseBatch(
                    id = batchId,
                    localId = batchLocalId,
                    locationId = locationId,
                    notes = state.notes.ifBlank { null },
                    totalWeightKg = state.totalWeight,
                    totalAmount = state.totalAmount,
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
                        type = TransactionType.PURCHASE,
                        transferLocationId = null,
                        productId = position.product.id,
                        weightKg = position.weightKg,
                        pricePerKg = position.pricePerKg,
                        totalAmount = position.totalAmount,
                        notes = null,
                        deviceId = deviceId,
                        createdAt = now,
                        syncedAt = null,
                        batchId = batchId
                    )
                }

                // Check if we're in correction mode
                val editingBatchId = state.editingBatchId
                if (editingBatchId != null) {
                    val reason = state.correctionReason.ifBlank { "Виправлення помилки" }
                    purchaseBatchRepository.correctBatch(
                        originalBatchId = editingBatchId,
                        correctedBatch = batch,
                        correctedTransactions = transactions,
                        reason = reason
                    )
                } else {
                    purchaseBatchRepository.createBatchWithTransactions(batch, transactions)
                }

                // Print receipt if printer is connected (optional - silently skip if not ready)
                tryPrintReceipt(
                    receiptNumber = batchLocalId.take(8).uppercase(),
                    positions = state.positions,
                    notes = state.notes.ifBlank { null }
                )

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        screenState = PurchaseEntryScreenState.SUMMARY
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
                val batch = purchaseBatchRepository.getById(batchId)
                if (batch == null) {
                    _uiState.update { 
                        it.copy(
                            isLoading = false,
                            error = "Партію не знайдено"
                        )
                    }
                    return@launch
                }

                val transactions = purchaseBatchRepository.getTransactionsForBatch(batchId)
                
                // Fetch products directly from repository to ensure they're available
                // (init loading may not have completed yet)
                val allProducts = productRepository.getActiveProducts().first()
                val products = allProducts.associateBy { it.id }

                val positions = transactions.mapNotNull { tx ->
                    val product = tx.productId?.let { products[it] }
                    if (product != null) {
                        PurchasePosition(
                            product = product,
                            weightKg = tx.weightKg,
                            pricePerKg = tx.pricePerKg ?: BigDecimal.ZERO,
                            totalAmount = tx.totalAmount ?: BigDecimal.ZERO
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
                            PurchaseEntryScreenState.POSITIONS_LIST 
                        else 
                            PurchaseEntryScreenState.PRODUCT_GRID
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
    
    /**
     * Internal function to load batch for editing.
     * Called from init when batchId is provided via SavedStateHandle.
     */
    private fun loadBatchForEditingInternal(batchIdString: String) {
        loadBatchForEditing(batchIdString)
    }

    fun dismissSummary() {
        _uiState.update {
            it.copy(screenState = PurchaseEntryScreenState.POSITIONS_LIST)
        }
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
        _uiState.update {
            it.copy(navigateBack = false)
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    /**
     * Attempt to print receipt if printer is connected.
     * Silently skips if printer is not ready - purchase is never blocked by print failure.
     */
    private fun tryPrintReceipt(
        receiptNumber: String,
        positions: List<PurchasePosition>,
        notes: String?
    ) {
        // Skip if printer not ready (not connected, not configured, etc.)
        if (!printerService.isReady()) {
            Log.d(TAG, "Printer not ready, skipping receipt print")
            return
        }

        viewModelScope.launch {
            try {
                val receipt = PurchaseReceiptBuilder()
                    .receiptNumber(receiptNumber)
                    .date(LocalDateTime.now())
                    .also { builder ->
                        positions.forEach { pos ->
                            builder.addItem(pos.product.name, pos.weightKg, pos.pricePerKg)
                        }
                    }
                    .notes(notes)
                    .build()

                printerService.print(receipt).onFailure { e ->
                    // Log error but don't show to user - printing is optional
                    Log.e(TAG, "Receipt print failed", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Receipt build/print error", e)
            }
        }
    }
}
