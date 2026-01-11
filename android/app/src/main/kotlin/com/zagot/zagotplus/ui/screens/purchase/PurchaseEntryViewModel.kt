package com.zagot.zagotplus.ui.screens.purchase

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
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
    POSITIONS_LIST   // Viewing/editing positions before finalizing
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
    val scaleWeight: BigDecimal? = null, // null = not connected (placeholder)
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val navigateToSummary: Boolean = false,
    val navigateBack: Boolean = false
) {
    val currentTotal: BigDecimal?
        get() {
            val weight = currentWeight.toBigDecimalOrNull()
            val price = currentPrice.toBigDecimalOrNull()
            return if (weight != null && price != null && weight > BigDecimal.ZERO) {
                weight.multiply(price).setScale(2, java.math.RoundingMode.HALF_UP)
            } else null
        }

    val canAddPosition: Boolean
        get() = selectedProduct != null &&
                currentWeight.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
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
    private val devicePreferences: DevicePreferences
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
                _uiState.update { 
                    it.copy(
                        products = products,
                        isLoading = false
                    )
                }
            }
        }
    }

    fun selectProduct(product: Product) {
        _uiState.update {
            it.copy(
                selectedProduct = product,
                currentWeight = "",
                currentPrice = product.defaultBuyPrice?.toPlainString() ?: "",
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
        val weight = state.currentWeight.toBigDecimalOrNull() ?: return
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

                _uiState.update {
                    it.copy(
                        isSaving = false,
                        navigateToSummary = true
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

    fun cancel() {
        _uiState.update { it.copy(navigateBack = true) }
    }

    fun onNavigationHandled() {
        _uiState.update {
            it.copy(
                navigateToSummary = false,
                navigateBack = false
            )
        }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
