package com.zagot.zagotplus.ui.screens.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.preferences.ProductOrderPreferences
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.CashRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.PurchaseBatchRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
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
 * Screen state for the purchase entry flow.
 */
enum class PurchaseEntryScreenState {
    PRODUCT_GRID,    // Selecting product from grid
    WEIGHT_ENTRY,    // Entering weight/price for selected product
    POSITIONS_LIST,  // Viewing/editing positions before finalizing
    SUMMARY          // Showing summary overlay before saving
}

/**
 * UI state for the purchase entry flow.
 */
data class PurchaseEntryUiState(
    val products: List<Product> = emptyList(),
    val selectedProduct: Product? = null,
    val currentWeight: String = "",
    val currentPrice: String = "",
    val positions: List<PurchasePosition> = emptyList(),
    val notes: String = "",
    val screenState: PurchaseEntryScreenState = PurchaseEntryScreenState.PRODUCT_GRID,
    val scaleWeight: BigDecimal? = null, // null = not connected
    val isManualWeightMode: Boolean = false, // true = user overriding scale weight
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val navigateBack: Boolean = false,
    val editingPosition: PurchasePosition? = null, // Position being edited
    val showExitConfirmation: Boolean = false // Show confirmation dialog before exit
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

    val canAddPosition: Boolean
        get() = selectedProduct != null &&
                effectiveWeight.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
                currentPrice.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

    val canFinalize: Boolean
        get() = positions.isNotEmpty()

    val totalWeight: BigDecimal
        get() = positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.weightKg) }

    val totalAmount: BigDecimal
        get() = positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.totalAmount) }
}

@HiltViewModel
class PurchaseEntryViewModel @Inject constructor(
    private val productRepository: ProductRepository,
    private val purchaseBatchRepository: PurchaseBatchRepository,
    private val cashRepository: CashRepository,
    private val devicePreferences: DevicePreferences,
    private val productOrderPreferences: ProductOrderPreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(PurchaseEntryUiState())
    val uiState: StateFlow<PurchaseEntryUiState> = _uiState.asStateFlow()

    init {
        loadProducts()
    }

    private fun loadProducts() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            productRepository.getActiveProducts().collect { products ->
                val orderedProducts = productOrderPreferences.applyOrder(products) { it.id }
                _uiState.update { 
                    it.copy(
                        products = orderedProducts,
                        isLoading = false
                    )
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

    fun selectProduct(product: Product) {
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

    fun onWeightChange(weight: String) {
        // Allow only valid decimal input
        if (weight.isEmpty() || weight.matches(Regex("^\\d*\\.?\\d*$"))) {
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
        // Allow only valid decimal input
        if (price.isEmpty() || price.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.update { it.copy(currentPrice = price) }
        }
    }

    fun onNotesChange(notes: String) {
        _uiState.update { it.copy(notes = notes) }
    }

    fun addPosition() {
        val state = _uiState.value
        val product = state.selectedProduct ?: return
        val weight = state.effectiveWeight.toBigDecimalOrNull() ?: return
        val price = state.currentPrice.toBigDecimalOrNull() ?: return

        if (weight <= BigDecimal.ZERO || price <= BigDecimal.ZERO) return

        val total = weight.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP)
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
                currentWeight = "",
                currentPrice = "",
                screenState = PurchaseEntryScreenState.POSITIONS_LIST
            )
        }
    }

    fun removePosition(positionId: String) {
        _uiState.update { state ->
            val newPositions = state.positions.filter { it.id != positionId }
            state.copy(
                positions = newPositions,
                screenState = if (newPositions.isEmpty()) 
                    PurchaseEntryScreenState.PRODUCT_GRID 
                else 
                    state.screenState
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

        // Show summary overlay instead of saving immediately
        _uiState.update {
            it.copy(screenState = PurchaseEntryScreenState.SUMMARY)
        }
    }

    fun confirmSave() {
        val state = _uiState.value
        if (state.positions.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val now = Instant.now()
                val deviceId = devicePreferences.getDeviceId()
                val locationId = devicePreferences.getSelectedLocationId()
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

                purchaseBatchRepository.createBatchWithTransactions(batch, transactions)

                // Record cash payment for purchase
                if (locationId != null) {
                    cashRepository.recordPurchasePayment(
                        locationId = locationId,
                        amount = state.totalAmount,
                        batchId = batchId
                    )
                }

                // TODO: Print receipt here (Phase 6 - hardware integration)
                // printReceipt(batch, positions)

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
                        screenState = PurchaseEntryScreenState.POSITIONS_LIST,
                        error = e.message ?: "Помилка збереження"
                    )
                }
            }
        }
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
}
