package com.zagot.zagotplus.ui.screens.shared

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.preferences.ProductOrderPreferences
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.hardware.scales.ScalesConnectionState
import com.zagot.zagotplus.hardware.scales.ScalesService
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID
/**
 * Data class for loading existing batch for editing.
 */
data class EditingBatchData(
    val batchId: UUID,
    val positions: List<TransactionPosition>,
    val notes: String,
    val locationId: UUID?
)

/**
 * Abstract base ViewModel for transaction entry flows (purchase and sale).
 * Contains all shared logic for product selection, weight entry, batch operations,
 * scales integration, and tablet numpad handling.
 */
abstract class TransactionEntryBaseViewModel(
    protected val productRepository: ProductRepository,
    protected val locationRepository: LocationRepository,
    protected val devicePreferences: DevicePreferences,
    protected val productOrderPreferences: ProductOrderPreferences,
    protected val scalesService: ScalesService,
    protected val savedStateHandle: SavedStateHandle,
    val transactionType: TransactionType,
    private val batchIdKey: String,
    private val modeKey: String
) : ViewModel() {

    companion object {
        private const val TAG = "TransactionEntryVM"
        internal val DECIMAL_PATTERN = Regex("^\\d*\\.?\\d*$")
        internal val INTEGER_PATTERN = Regex("^\\d+$")
    }

    // ==================== ABSTRACT MEMBERS ====================

    /** Get default price for a product (buyPrice for purchase, sellPrice for sale) */
    abstract fun getDefaultPrice(product: Product): BigDecimal?

    /** Save the batch with all positions to the database */
    abstract suspend fun saveBatch(
        positions: List<TransactionPosition>,
        notes: String,
        locationId: UUID,
        correctionReason: String?,
        editingBatchId: UUID?
    )

    /** Load existing batch for editing/correction */
    abstract suspend fun loadExistingBatch(batchId: UUID): EditingBatchData?

    /** Called after successful save (e.g., for printing receipt) */
    protected open fun onSaveSuccess(batchLocalId: String, positions: List<TransactionPosition>, notes: String?) {}

    // FIX: Initialize these properties directly from the keys passed in constructor
    protected val argBatchId: String? = savedStateHandle[batchIdKey]
    protected val argMode: String? = savedStateHandle[modeKey]

    // ==================== STATE ====================

    // FIX: _uiState now initializes safely because transactionType and argMode are available
    protected val _uiState = MutableStateFlow(
        TransactionEntryUiState(
            transactionType = transactionType,
            isLoading = true,
            entryMode = TransactionEntryMode.fromString(argMode)
        )
    )
    val uiState: StateFlow<TransactionEntryUiState> = _uiState.asStateFlow()

    // ==================== INITIALIZATION ====================

    protected fun initialize() {
        loadProductsAndLocations()
        observeScales()
    }

    private fun loadProductsAndLocations() {
        viewModelScope.launch {
            // Load locations for edit mode dropdown
            val locations = locationRepository.getAllLocations().first()
            _uiState.update { it.copy(availableLocations = locations) }

            productRepository.getActiveProducts().collect { products ->
                val orderedProducts = productOrderPreferences.applyOrder(products) { it.id }
                _uiState.update {
                    it.copy(
                        products = orderedProducts,
                        isLoading = argBatchId != null // Keep loading if we need to load a batch
                    )
                }

                // Load batch for editing if batchId was provided
                if (argBatchId != null && _uiState.value.editingBatchId == null) {
                    loadBatchForEditing(argBatchId!!)
                }
            }
        }
    }

    // ==================== SCALES INTEGRATION ====================

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

    // ==================== PRODUCT SELECTION ====================

    fun onProductOrderChanged(newOrder: List<UUID>) {
        productOrderPreferences.setProductOrder(newOrder)
        val currentProducts = _uiState.value.products
        val orderedProducts = productOrderPreferences.applyOrder(currentProducts) { it.id }
        _uiState.update { it.copy(products = orderedProducts) }
    }

    open fun selectProduct(product: Product) {
        val state = _uiState.value

        // REGULAR MODE
        if (state.entryMode == TransactionEntryMode.REGULAR) {
            if (state.isTabletMode) {
                selectProductForEntry(product)
            } else {
                // Phone: navigate to weight entry screen
                _uiState.update {
                    it.copy(
                        selectedProduct = product,
                        currentWeight = "",
                        currentPrice = getDefaultPrice(product)?.toPlainString() ?: "",
                        isManualWeightMode = false,
                        screenState = TransactionEntryScreenState.WEIGHT_ENTRY
                    )
                }
            }
            return
        }

        // BATCH MODE
        if (state.isTabletMode) {
            // TABLET BATCH: Check for existing position or create new empty position
            val existingPosition = state.positions.find { it.product.id == product.id }

            if (existingPosition != null) {
                reviewPositionWeightings(existingPosition)
            } else {
                // Create new empty position for batch tablet mode
                val defaultPrice = getDefaultPrice(product) ?: BigDecimal.ZERO

                val newPosition = TransactionPosition(
                    product = product,
                    batches = emptyList(),
                    tareWeightPerUnit = BigDecimal("0.1"),
                    pricePerKg = defaultPrice
                )

                _uiState.update {
                    it.copy(
                        positions = it.positions + newPosition,
                        tabletReviewingPositionId = newPosition.id,
                        currentWeight = "",
                        currentTareCount = "",
                        tareWeightPerUnit = "0.1",
                        currentPrice = if (defaultPrice > BigDecimal.ZERO) defaultPrice.toPlainString() else "",
                        selectedProduct = null,
                        activeInputField = TransactionInputField.WEIGHT
                    )
                }
            }
            resetTabletState()
        } else {
            // PHONE BATCH: Navigate to weighing screen with selected product
            val defaultPrice = getDefaultPrice(product) ?: BigDecimal.ZERO
            _uiState.update {
                it.copy(
                    selectedProduct = product,
                    currentWeight = "",
                    currentTareCount = "",
                    currentBatches = emptyList(),
                    tareWeightPerUnit = "0.1",
                    currentPrice = if (defaultPrice > BigDecimal.ZERO) defaultPrice.toPlainString() else "",
                    screenState = TransactionEntryScreenState.WEIGHING,
                    activeInputField = TransactionInputField.WEIGHT
                )
            }
        }
    }

    open fun selectProductForEntry(product: Product) {
        val currentState = _uiState.value
        
        // Preserve existing weight if valid (> 0), otherwise clear
        val preservedWeight = if (currentState.currentWeight.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true) {
            currentState.currentWeight
        } else ""
        
        // Preserve existing price if valid (> 0), otherwise use default
        val preservedPrice = if (currentState.currentPrice.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true) {
            currentState.currentPrice
        } else {
            getDefaultPrice(product)?.toPlainString() ?: ""
        }
        
        _uiState.update {
            it.copy(
                dataEntryProduct = product,
                currentWeight = preservedWeight,
                currentPrice = preservedPrice,
                isManualWeightMode = preservedWeight.isNotEmpty(), // Keep manual mode if weight was preserved
                tabletEditingPositionId = null,
                activeInputField = TransactionInputField.WEIGHT
            )
        }
    }

    // ==================== INPUT HANDLING ====================

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

    fun onTareWeightPerUnitChange(weight: String) {
        if (weight.isEmpty() || DECIMAL_PATTERN.matches(weight)) {
            _uiState.update { it.copy(tareWeightPerUnit = weight) }
        }
    }

    fun onPriceChange(price: String) {
        if (price.isEmpty() || DECIMAL_PATTERN.matches(price)) {
            _uiState.update { it.copy(currentPrice = price) }
        }
    }

    fun onPriceFocused() {
        _uiState.update { it.copy(currentPrice = "") }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    // ==================== TABLET NUMPAD ====================

    fun selectInputField(field: TransactionInputField) {
        _uiState.update { it.copy(activeInputField = field) }
    }

    fun selectInputFieldAndClear(field: TransactionInputField) {
        _uiState.update { state ->
            when (field) {
                TransactionInputField.WEIGHT -> state.copy(
                    activeInputField = field,
                    currentWeight = "",
                    isManualWeightMode = true  // Auto-activate manual mode for numpad input
                )
                TransactionInputField.TARE_COUNT -> state.copy(activeInputField = field, currentTareCount = "")
                TransactionInputField.TARE_WEIGHT_UNIT -> state.copy(activeInputField = field, tareWeightPerUnit = "")
                TransactionInputField.PRICE -> state.copy(activeInputField = field, currentPrice = "")
            }
        }
    }

    fun onNextInputField() {
        val state = _uiState.value
        val nextField = when {
            // Regular mode: cycle between WEIGHT and PRICE only
            state.entryMode == TransactionEntryMode.REGULAR -> {
                when (state.activeInputField) {
                    TransactionInputField.WEIGHT -> TransactionInputField.PRICE
                    TransactionInputField.PRICE -> TransactionInputField.WEIGHT
                    else -> TransactionInputField.WEIGHT
                }
            }
            // Batch mode
            else -> {
                when (state.activeInputField) {
                    TransactionInputField.WEIGHT -> TransactionInputField.TARE_COUNT
                    TransactionInputField.TARE_COUNT -> if (state.isInFinalizationMode)
                        TransactionInputField.TARE_WEIGHT_UNIT
                    else
                        TransactionInputField.WEIGHT
                    TransactionInputField.TARE_WEIGHT_UNIT -> TransactionInputField.PRICE
                    TransactionInputField.PRICE -> TransactionInputField.TARE_WEIGHT_UNIT
                }
            }
        }
        _uiState.update { it.copy(activeInputField = nextField) }
    }

    fun onKeypadInput(char: Char) {
        if (!char.isDigit()) return

        val state = _uiState.value
        var newWeight = state.currentWeight
        var newTareCount = state.currentTareCount
        var newTareWeight = state.tareWeightPerUnit
        var newPrice = state.currentPrice

        when (state.activeInputField) {
            TransactionInputField.WEIGHT -> {
                if (DECIMAL_PATTERN.matches(state.currentWeight + char)) {
                    newWeight = appendDigit(state.currentWeight, char)
                }
            }
            TransactionInputField.TARE_COUNT -> {
                if (INTEGER_PATTERN.matches(state.currentTareCount + char)) {
                    newTareCount += char
                }
            }
            TransactionInputField.TARE_WEIGHT_UNIT -> {
                if (DECIMAL_PATTERN.matches(state.tareWeightPerUnit + char)) {
                    newTareWeight = appendDigit(state.tareWeightPerUnit, char)
                }
            }
            TransactionInputField.PRICE -> {
                if (DECIMAL_PATTERN.matches(state.currentPrice + char)) {
                    newPrice = appendDigit(state.currentPrice, char)
                }
            }
        }

        updateStateAndPosition(newWeight, newTareCount, newTareWeight, newPrice)
    }

    fun onKeypadDecimal() {
        val state = _uiState.value
        var newWeight = state.currentWeight
        var newTareWeight = state.tareWeightPerUnit
        var newPrice = state.currentPrice

        when (state.activeInputField) {
            TransactionInputField.WEIGHT -> {
                if (!newWeight.contains('.')) newWeight += if (newWeight.isEmpty()) "0." else "."
            }
            TransactionInputField.TARE_WEIGHT_UNIT -> {
                if (!newTareWeight.contains('.')) newTareWeight += if (newTareWeight.isEmpty()) "0." else "."
            }
            TransactionInputField.PRICE -> {
                if (!newPrice.contains('.')) newPrice += if (newPrice.isEmpty()) "0." else "."
            }
            else -> {} // Tare count is int
        }

        updateStateAndPosition(newWeight, state.currentTareCount, newTareWeight, newPrice)
    }

    fun onKeypadBackspace() {
        val state = _uiState.value
        val newWeight = if (state.activeInputField == TransactionInputField.WEIGHT) state.currentWeight.dropLast(1) else state.currentWeight
        val newTareCount = if (state.activeInputField == TransactionInputField.TARE_COUNT) state.currentTareCount.dropLast(1) else state.currentTareCount
        val newTareWeight = if (state.activeInputField == TransactionInputField.TARE_WEIGHT_UNIT) state.tareWeightPerUnit.dropLast(1) else state.tareWeightPerUnit
        val newPrice = if (state.activeInputField == TransactionInputField.PRICE) state.currentPrice.dropLast(1) else state.currentPrice

        updateStateAndPosition(newWeight, newTareCount, newTareWeight, newPrice)
    }

    private fun updateStateAndPosition(
        weight: String,
        tareCount: String,
        tareWeight: String,
        price: String
    ) {
        _uiState.update { s ->
            var updatedPositions = s.positions

            // Update the position if we are reviewing one
            if (s.tabletReviewingPositionId != null) {
                val tareWeightDecimal = tareWeight.toBigDecimalOrNull() ?: BigDecimal.ZERO
                val priceDecimal = price.toBigDecimalOrNull() ?: BigDecimal.ZERO

                updatedPositions = s.positions.map { pos ->
                    if (pos.id == s.tabletReviewingPositionId) {
                        recalculatePosition(pos, tareWeightDecimal, priceDecimal)
                    } else pos
                }
            }

            s.copy(
                currentWeight = weight,
                currentTareCount = tareCount,
                tareWeightPerUnit = tareWeight,
                currentPrice = price,
                positions = updatedPositions
            )
        }
    }

    private fun recalculatePosition(
        position: TransactionPosition,
        newTareWeightPerUnit: BigDecimal,
        newPricePerKg: BigDecimal
    ): TransactionPosition {
        return position.copy(
            tareWeightPerUnit = newTareWeightPerUnit,
            pricePerKg = newPricePerKg
        )
        // Note: netWeight and totalAmount are computed properties
    }

    private fun appendDigit(current: String, digit: Char): String {
        if (current.length >= 10) return current

        val decimalIndex = current.indexOf('.')
        if (decimalIndex != -1) {
            val decimalPlaces = current.length - decimalIndex - 1
            if (decimalPlaces >= 2) return current
        }

        if (current == "0" && digit != '.') {
            return digit.toString()
        }

        return current + digit
    }

    // ==================== BATCH OPERATIONS ====================

    fun addBatch() {
        val state = _uiState.value
        val weight = state.currentWeight.toBigDecimalOrNull() ?: return
        val tareCount = if (state.currentTareCount.isBlank()) 0 else state.currentTareCount.toIntOrNull() ?: return

        if (weight <= BigDecimal.ZERO || tareCount < 0) return

        if (state.isTabletMode) {
            // TABLET MODE: Add batch to position in the list
            val selectedId = state.tabletReviewingPositionId ?: return
            val currentPosition = state.positions.find { it.id == selectedId } ?: return

            val editingBatchId = state.tabletEditingBatchId

            val newBatches = if (editingBatchId != null) {
                // Edit existing batch
                currentPosition.batches.map { batch ->
                    if (batch.id == editingBatchId) {
                        batch.copy(grossWeightKg = weight, tareCount = tareCount)
                    } else batch
                }
            } else {
                // Add new batch
                val newBatch = WeighingBatch(
                    grossWeightKg = weight,
                    tareCount = tareCount
                )
                currentPosition.batches + newBatch
            }

            val updatedPosition = currentPosition.copy(batches = newBatches)

            _uiState.update {
                it.copy(
                    positions = it.positions.map { pos ->
                        if (pos.id == selectedId) updatedPosition else pos
                    },
                    currentWeight = "",
                    currentTareCount = "",
                    tabletEditingBatchId = null,
                    activeInputField = TransactionInputField.WEIGHT
                )
            }
        } else {
            // MOBILE MODE: Add batch to currentBatches
            val batch = WeighingBatch(
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
                    tabletEditingBatchId = if (s.tabletEditingBatchId == batchId) null else s.tabletEditingBatchId,
                    currentWeight = if (s.tabletEditingBatchId == batchId) "" else s.currentWeight,
                    currentTareCount = if (s.tabletEditingBatchId == batchId) "" else s.currentTareCount
                )
            }
        } else {
            _uiState.update { s ->
                s.copy(
                    currentBatches = s.currentBatches.filter { it.id != batchId }
                )
            }
        }
    }

    fun updateBatch(batchId: String, newWeight: BigDecimal, newTareCount: Int) {
        _uiState.update { state ->
            state.copy(
                currentBatches = state.currentBatches.map { batch ->
                    if (batch.id == batchId) {
                        batch.copy(grossWeightKg = newWeight, tareCount = newTareCount)
                    } else batch
                }
            )
        }
    }

    fun selectTabletBatch(batch: WeighingBatch) {
        _uiState.update { state ->
            if (state.tabletEditingBatchId == batch.id) {
                // Deselect
                state.copy(
                    tabletEditingBatchId = null,
                    currentWeight = "",
                    currentTareCount = "",
                    activeInputField = TransactionInputField.WEIGHT
                )
            } else {
                // Select
                state.copy(
                    tabletEditingBatchId = batch.id,
                    currentWeight = batch.grossWeightKg.toPlainString(),
                    currentTareCount = batch.tareCount.toString(),
                    activeInputField = TransactionInputField.WEIGHT
                )
            }
        }
    }

    // ==================== POSITION OPERATIONS ====================

    fun addPosition() {
        val state = _uiState.value
        val product = state.activeProduct ?: return
        val weight = state.effectiveWeight.toBigDecimalOrNull() ?: return
        val price = state.currentPrice.toBigDecimalOrNull() ?: return

        if (weight <= BigDecimal.ZERO || price <= BigDecimal.ZERO) return

        // Tablet edit mode: update existing position
        if (state.isTabletEditMode && state.tabletEditingPositionId != null) {
            _uiState.update {
                val updatedPositions = it.positions.map { pos ->
                    if (pos.id == state.tabletEditingPositionId) {
                        // Create single batch with weight
                        val batch = WeighingBatch(grossWeightKg = weight, tareCount = 0)
                        pos.copy(
                            product = product,
                            batches = listOf(batch),
                            tareWeightPerUnit = BigDecimal.ZERO,
                            pricePerKg = price
                        )
                    } else pos
                }
                it.copy(
                    positions = updatedPositions,
                    dataEntryProduct = null,
                    currentWeight = "",
                    currentPrice = "",
                    tabletEditingPositionId = null,
                    activeInputField = TransactionInputField.WEIGHT
                )
            }
            return
        }

        // Normal add mode - create position with single batch
        val batch = WeighingBatch(grossWeightKg = weight, tareCount = 0)
        val position = TransactionPosition(
            product = product,
            batches = listOf(batch),
            tareWeightPerUnit = BigDecimal.ZERO,
            pricePerKg = price
        )

        // Validate minimum totalAmount to prevent zero-sum transactions
        val minAmount = BigDecimal("0.01")
        if (position.totalAmount < minAmount) return

        _uiState.update {
            it.copy(
                positions = it.positions + position,
                selectedProduct = null,
                dataEntryProduct = null,
                currentWeight = "",
                currentPrice = "",
                tabletEditingPositionId = null,
                screenState = if (it.isTabletMode)
                    TransactionEntryScreenState.UNIFIED_ENTRY
                else
                    TransactionEntryScreenState.POSITIONS_LIST
            )
        }
    }

    fun addBatchPositionAndContinue() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val price = state.currentPrice.toBigDecimalOrNull() ?: return
        val tareWeight = state.tareWeightPerUnit.toBigDecimalOrNull() ?: BigDecimal("0.1")

        if (state.currentBatches.isEmpty() || price <= BigDecimal.ZERO) return

        val position = TransactionPosition(
            product = product,
            batches = state.currentBatches,
            tareWeightPerUnit = tareWeight,
            pricePerKg = price
        )

        // Validate minimum totalAmount to prevent zero-sum transactions
        val minAmount = BigDecimal("0.01")
        if (position.totalAmount < minAmount) return

        _uiState.update {
            it.copy(
                positions = it.positions + position,
                selectedProduct = null,
                currentBatches = emptyList(),
                currentWeight = "",
                currentTareCount = "",
                tareWeightPerUnit = "0.1",
                currentPrice = "",
                screenState = if (it.screenState == TransactionEntryScreenState.UNIFIED_BATCH)
                    TransactionEntryScreenState.UNIFIED_BATCH
                else
                    TransactionEntryScreenState.POSITIONS_LIST
            )
        }
        resetTabletState()
    }

    /** Alias for addPosition - used by sale regular mode screens */
    fun addRegularPositionAndContinue() = addPosition()

    /** Alias for addBatchPositionAndContinue - used by sale wholesale mode screens */
    fun addPositionAndContinue() = addBatchPositionAndContinue()

    fun removePosition(positionId: String) {
        _uiState.update { state ->
            val newPositions = state.positions.filter { it.id != positionId }

            val nextScreen = when {
                state.screenState == TransactionEntryScreenState.UNIFIED_ENTRY -> TransactionEntryScreenState.UNIFIED_ENTRY
                state.screenState == TransactionEntryScreenState.UNIFIED_BATCH -> TransactionEntryScreenState.UNIFIED_BATCH
                newPositions.isEmpty() -> TransactionEntryScreenState.PRODUCT_GRID
                else -> state.screenState
            }

            state.copy(
                positions = newPositions,
                screenState = nextScreen,
                tabletReviewingPositionId = if (state.tabletReviewingPositionId == positionId) null else state.tabletReviewingPositionId,
                currentWeight = if (state.tabletReviewingPositionId == positionId) "" else state.currentWeight,
                currentTareCount = if (state.tabletReviewingPositionId == positionId) "" else state.currentTareCount
            )
        }
    }

    fun startEditPosition(position: TransactionPosition) {
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
            val updatedEditingPosition = newPositions.find { it.id == positionId }
            state.copy(
                positions = newPositions,
                editingPosition = updatedEditingPosition
            )
        }
    }

    fun deletePositionBatch(positionId: String, batchId: String) {
        _uiState.update { state ->
            val newPositions = state.positions.map { pos ->
                if (pos.id == positionId) {
                    pos.copy(batches = pos.batches.filter { it.id != batchId })
                } else pos
            }
            val updatedEditingPosition = newPositions.find { it.id == positionId }
            state.copy(
                positions = newPositions,
                editingPosition = updatedEditingPosition
            )
        }
    }

    fun reviewPositionWeightings(position: TransactionPosition) {
        _uiState.update { state ->
            if (state.tabletReviewingPositionId == position.id) {
                // Toggle off
                state.copy(
                    tabletReviewingPositionId = null,
                    currentWeight = "",
                    currentTareCount = "",
                    tabletEditingBatchId = null
                )
            } else {
                // Toggle on
                state.copy(
                    tabletReviewingPositionId = position.id,
                    currentWeight = "",
                    currentTareCount = "",
                    tabletEditingBatchId = null,
                    activeInputField = TransactionInputField.WEIGHT
                )
            }
        }
    }

    fun selectTabletPosition(position: TransactionPosition) {
        _uiState.update { state ->
            if (state.tabletEditingPositionId == position.id) {
                // Deselect
                state.copy(
                    tabletEditingPositionId = null,
                    dataEntryProduct = null,
                    currentWeight = "",
                    currentPrice = "",
                    activeInputField = TransactionInputField.WEIGHT
                )
            } else {
                // Select - get weight from first batch if exists
                val weight = position.batches.firstOrNull()?.grossWeightKg ?: position.netWeight
                state.copy(
                    tabletEditingPositionId = position.id,
                    dataEntryProduct = position.product,
                    currentWeight = weight.toPlainString(),
                    currentPrice = position.pricePerKg.toPlainString(),
                    activeInputField = TransactionInputField.WEIGHT
                )
            }
        }
    }

    fun cancelTabletPositionEdit() {
        _uiState.update { state ->
            state.copy(
                tabletEditingPositionId = null,
                dataEntryProduct = null,
                currentWeight = "",
                currentPrice = "",
                activeInputField = TransactionInputField.WEIGHT
            )
        }
    }

    // ==================== NAVIGATION ====================

    fun setTabletMode(isTablet: Boolean) {
        _uiState.update { state ->
            val newScreenState = if (isTablet && state.screenState != TransactionEntryScreenState.SUMMARY) {
                when (state.entryMode) {
                    TransactionEntryMode.REGULAR -> TransactionEntryScreenState.UNIFIED_ENTRY
                    TransactionEntryMode.BATCH -> TransactionEntryScreenState.UNIFIED_BATCH
                }
            } else {
                state.screenState
            }
            state.copy(
                isTabletMode = isTablet,
                screenState = newScreenState
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
                    TransactionEntryScreenState.POSITIONS_LIST
                else
                    TransactionEntryScreenState.PRODUCT_GRID
            )
        }
    }

    fun addAnotherProduct() {
        _uiState.update {
            it.copy(screenState = TransactionEntryScreenState.PRODUCT_GRID)
        }
    }

    fun proceedToReview() {
        if (_uiState.value.currentBatches.isNotEmpty()) {
            _uiState.update { it.copy(screenState = TransactionEntryScreenState.POSITION_REVIEW) }
        }
    }

    fun backToWeighing() {
        _uiState.update {
            it.copy(screenState = TransactionEntryScreenState.WEIGHING)
        }
    }

    fun toggleFinalizationMode() {
        val state = _uiState.value
        if (state.currentBatches.isEmpty() && !state.isInFinalizationMode) {
            return
        }

        val newMode = !state.isInFinalizationMode
        val newActiveField = if (newMode) TransactionInputField.TARE_WEIGHT_UNIT else TransactionInputField.WEIGHT

        _uiState.update {
            it.copy(
                isInFinalizationMode = newMode,
                activeInputField = newActiveField
            )
        }
    }

    fun resetTabletState() {
        _uiState.update {
            it.copy(
                isInFinalizationMode = false,
                activeInputField = TransactionInputField.WEIGHT
            )
        }
    }

    // ==================== FINALIZATION ====================

    fun finalize() {
        val state = _uiState.value
        if (state.positions.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val locationId = state.selectedLocationId ?: devicePreferences.getSelectedLocationId()
                    ?: throw IllegalStateException("Локація не обрана")

                val batchLocalId = UUID.randomUUID().toString()

                saveBatch(
                    positions = state.positions,
                    notes = state.notes,
                    locationId = locationId,
                    correctionReason = state.correctionReason.ifBlank { null },
                    editingBatchId = state.editingBatchId
                )

                onSaveSuccess(batchLocalId, state.positions, state.notes.ifBlank { null })

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        screenState = TransactionEntryScreenState.SUMMARY
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

    // ==================== EDIT MODE ====================

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
                val data = loadExistingBatch(batchId)
                if (data == null) {
                    _uiState.update {
                        it.copy(
                            isLoading = false,
                            error = "Партію не знайдено"
                        )
                    }
                    return@launch
                }

                _uiState.update {
                    it.copy(
                        isLoading = false,
                        editingBatchId = data.batchId,
                        positions = data.positions,
                        notes = data.notes,
                        selectedLocationId = data.locationId,
                        originalLocationId = data.locationId,
                        screenState = if (data.positions.isNotEmpty())
                            TransactionEntryScreenState.POSITIONS_LIST
                        else
                            TransactionEntryScreenState.PRODUCT_GRID
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

    fun onCorrectionReasonChange(reason: String) {
        _uiState.update { it.copy(correctionReason = reason) }
    }

    fun selectLocation(locationId: UUID?) {
        _uiState.update { it.copy(selectedLocationId = locationId) }
    }

    // ==================== EXIT HANDLING ====================

    fun exitFromSummary() {
        _uiState.update { it.copy(navigateBack = true) }
    }

    fun dismissSummary() {
        _uiState.update { it.copy(screenState = TransactionEntryScreenState.POSITIONS_LIST) }
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

    // ==================== HELPERS ====================

    protected fun buildPositionNotes(position: TransactionPosition, batchNotes: String): String? {
        val parts = mutableListOf<String>()

        if (position.totalTareCount > 0) {
            val tareInfo = "${position.totalTareCount} шт × ${position.tareWeightPerUnit.toPlainString()} кг = ${position.totalTareWeight.toPlainString()} кг тара"
            parts.add(tareInfo)
        }

        if (position.batches.isNotEmpty()) {
            parts.add("Брутто: ${position.grossWeight.toPlainString()} кг")
        }

        if (batchNotes.isNotBlank()) {
            parts.add(batchNotes)
        }

        return parts.joinToString("; ").ifBlank { null }
    }
}
