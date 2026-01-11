package com.zagot.zagotplus.ui.screens.sale

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.math.RoundingMode
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
 * Screen state for the sale entry flow.
 */
enum class SaleEntryScreenState {
    PRODUCT_GRID,    // Selecting product from grid
    WEIGHING,        // Adding weight batches with tare count for current product
    POSITION_REVIEW, // Review current position before adding to list
    POSITIONS_LIST,  // Viewing all positions, can add more products
    SUMMARY          // Final confirmation before saving
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
    val currentTareCount: String = "1",
    
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
    val navigateBack: Boolean = false
) {
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
                currentTareCount.toIntOrNull()?.let { it > 0 } == true
    
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
    private val devicePreferences: DevicePreferences
) : ViewModel() {

    private val _uiState = MutableStateFlow(SaleEntryUiState())
    val uiState: StateFlow<SaleEntryUiState> = _uiState.asStateFlow()

    init {
        loadData()
    }

    private fun loadData() {
        viewModelScope.launch {
            _uiState.update { it.copy(isLoading = true) }
            try {
                val products = productRepository.getActiveProducts().first()
                val locationId = devicePreferences.getSelectedLocationId()
                val inventory = if (locationId != null) {
                    transactionRepository.getInventoryByLocation(locationId).first()
                } else {
                    emptyList()
                }
                
                _uiState.update {
                    it.copy(
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
                currentTareCount = "1",
                tareWeightPerUnit = "0.1",
                screenState = SaleEntryScreenState.WEIGHING
            )
        }
    }

    fun onWeightChange(weight: String) {
        if (weight.isEmpty() || weight.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.update { it.copy(currentWeight = weight) }
        }
    }

    fun onTareCountChange(count: String) {
        if (count.isEmpty() || count.matches(Regex("^\\d+$"))) {
            _uiState.update { it.copy(currentTareCount = count) }
        }
    }

    fun addBatch() {
        val state = _uiState.value
        val weight = state.currentWeight.toBigDecimalOrNull() ?: return
        val tareCount = state.currentTareCount.toIntOrNull() ?: return

        if (weight <= BigDecimal.ZERO || tareCount <= 0) return

        val batch = SaleWeighingBatch(
            grossWeightKg = weight,
            tareCount = tareCount
        )

        _uiState.update {
            it.copy(
                currentBatches = it.currentBatches + batch,
                currentWeight = "",
                currentTareCount = "1"
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

    fun proceedToReview() {
        if (_uiState.value.currentBatches.isNotEmpty()) {
            _uiState.update { it.copy(screenState = SaleEntryScreenState.POSITION_REVIEW) }
        }
    }

    fun onTareWeightPerUnitChange(weight: String) {
        if (weight.isEmpty() || weight.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.update { it.copy(tareWeightPerUnit = weight) }
        }
    }

    fun onPriceChange(price: String) {
        if (price.isEmpty() || price.matches(Regex("^\\d*\\.?\\d*$"))) {
            _uiState.update { it.copy(pricePerKg = price) }
        }
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
                currentTareCount = "1",
                tareWeightPerUnit = "0.1",
                pricePerKg = "",
                screenState = SaleEntryScreenState.POSITIONS_LIST
            )
        }
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
                currentTareCount = "1",
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
        if (_uiState.value.canFinalize) {
            _uiState.update { it.copy(screenState = SaleEntryScreenState.SUMMARY) }
        }
    }

    fun confirmSave() {
        val state = _uiState.value
        if (state.positions.isEmpty()) return

        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            try {
                val locationId = devicePreferences.getSelectedLocationId()
                    ?: throw IllegalStateException("Локація не обрана")

                // Create sale transactions for each position
                for (position in state.positions) {
                    transactionRepository.createSale(
                        locationId = locationId,
                        productId = position.product.id,
                        weightKg = position.netWeight,
                        pricePerKg = position.pricePerKg,
                        notes = buildPositionNotes(position, state.notes)
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
                        screenState = SaleEntryScreenState.POSITIONS_LIST,
                        error = e.message ?: "Помилка збереження"
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
        _uiState.update { it.copy(navigateBack = true) }
    }

    fun onNavigationHandled() {
        _uiState.update { it.copy(navigateBack = false) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }
}
