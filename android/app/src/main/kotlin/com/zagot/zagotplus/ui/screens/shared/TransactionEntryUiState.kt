package com.zagot.zagotplus.ui.screens.shared

import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.TransactionType
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Unified UI state for transaction entry flows (purchase and sale).
 */
data class TransactionEntryUiState(
    // Transaction type
    val transactionType: TransactionType,

    // Mode
    val entryMode: TransactionEntryMode = TransactionEntryMode.REGULAR,

    // Products
    val products: List<Product> = emptyList(),
    val selectedProduct: Product? = null,
    val dataEntryProduct: Product? = null, // Tablet: product in data entry panel

    // Device mode
    val isTabletMode: Boolean = false,

    // Current input fields
    val currentWeight: String = "",
    val currentTareCount: String = "",
    val tareWeightPerUnit: String = "0.1", // Default 100g per sack
    val currentPrice: String = "",

    // Batches for current product (before adding to position)
    val currentBatches: List<WeighingBatch> = emptyList(),

    // Completed positions
    val positions: List<TransactionPosition> = emptyList(),

    // Notes
    val notes: String = "",

    // Scales integration
    val scaleWeight: BigDecimal? = null,
    val isScaleStable: Boolean = false,
    val isManualWeightMode: Boolean = false,

    // Screen state
    val screenState: TransactionEntryScreenState = TransactionEntryScreenState.PRODUCT_GRID,

    // Loading/saving
    val isLoading: Boolean = false,
    val isSaving: Boolean = false,
    val error: String? = null,
    val navigateBack: Boolean = false,

    // Exit confirmation
    val showExitConfirmation: Boolean = false,

    // Edit mode (correcting existing batch)
    val editingBatchId: UUID? = null,
    val correctionReason: String = "",
    val availableLocations: List<Location> = emptyList(),
    val selectedLocationId: UUID? = null,
    val originalLocationId: UUID? = null,

    // Position editing (phone)
    val editingPosition: TransactionPosition? = null,

    // Tablet numpad state
    val activeInputField: TransactionInputField = TransactionInputField.WEIGHT,

    // Tablet batch mode state
    val isInFinalizationMode: Boolean = false,
    val tabletEditingBatchId: String? = null,
    val tabletReviewingPositionId: String? = null,
    val tabletEditingPositionId: String? = null,

    // Sale-specific: inventory tracking (empty for purchase)
    val inventory: List<InventoryItem> = emptyList(),
    val availableWeight: BigDecimal = BigDecimal.ZERO,

    // Weight restore popup (REGULAR mode only)
    val weightRestoreData: WeightRestoreData? = null,

    // Event tracking
    val lastWeighingAddedId: Long = 0L // Incrementing counter to trigger UI events
) {
    // ==================== COMPUTED PROPERTIES ====================

    val hasUnsavedData: Boolean
        get() = positions.isNotEmpty() ||
                currentBatches.isNotEmpty() ||
                currentWeight.isNotBlank() ||
                (currentPrice.isNotBlank() && activeProduct != null) ||
                notes.isNotBlank()

    val isScaleConnected: Boolean
        get() = scaleWeight != null

    val effectiveWeight: String
        get() = if (isScaleConnected && !isManualWeightMode) {
            scaleWeight?.toPlainString() ?: ""
        } else {
            currentWeight
        }

    // On tablet: uses dataEntryProduct, on phone: uses selectedProduct
    val activeProduct: Product?
        get() = if (isTabletMode) dataEntryProduct else selectedProduct

    // ==================== REGULAR MODE ====================

    val currentTotal: BigDecimal?
        get() {
            val weight = effectiveWeight.toBigDecimalOrNull()
            val price = currentPrice.toBigDecimalOrNull()
            return if (weight != null && price != null && weight > BigDecimal.ZERO) {
                weight.multiply(price).setScale(2, RoundingMode.HALF_UP)
            } else null
        }

    val canAddPosition: Boolean
        get() = activeProduct != null &&
                effectiveWeight.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
                currentPrice.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

    // ==================== BATCH MODE ====================

    val canAddBatch: Boolean
        get() = currentWeight.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
                (currentTareCount.isBlank() || currentTareCount.toIntOrNull()?.let { it >= 0 } == true)

    val canProceedToReview: Boolean
        get() = currentBatches.isNotEmpty()

    val canAddBatchPosition: Boolean
        get() = currentBatches.isNotEmpty() &&
                currentPrice.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true

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

    val currentBatchTotalAmount: BigDecimal?
        get() {
            val price = currentPrice.toBigDecimalOrNull() ?: return null
            return if (price > BigDecimal.ZERO) {
                currentNetWeight.multiply(price).setScale(2, RoundingMode.HALF_UP)
            } else null
        }

    // ==================== TABLET STATE ====================

    val isTabletEditMode: Boolean
        get() = isTabletMode && tabletEditingPositionId != null

    val isTabletBatchEditMode: Boolean
        get() = tabletEditingBatchId != null

    val isReviewingPosition: Boolean
        get() = tabletReviewingPositionId != null

    val reviewingPosition: TransactionPosition?
        get() = tabletReviewingPositionId?.let { id -> positions.find { it.id == id } }

    // ==================== TOTALS ====================

    val canFinalize: Boolean
        get() = positions.isNotEmpty()

    val totalWeight: BigDecimal
        get() = positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.netWeight) }

    val totalAmount: BigDecimal
        get() = positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.totalAmount) }

    // ==================== SALE-SPECIFIC ====================

    /** Inventory warning for sale mode - true when selling more than available */
    val showInventoryWarning: Boolean
        get() = currentNetWeight > availableWeight && availableWeight >= BigDecimal.ZERO

    // ==================== COMPATIBILITY ALIASES ====================
    // These properties provide backward compatibility with old screen code
    // during migration. Can be removed once all screens are updated.

    /** Alias for entryMode for purchase screens */
    val purchaseMode: TransactionEntryMode
        get() = entryMode

    /** Alias for entryMode for sale screens */
    val saleMode: TransactionEntryMode
        get() = entryMode

    /** Alias for pricePerKg (sale screens use this name) */
    val pricePerKg: String
        get() = currentPrice

    /** Alias for canAddPosition (sale regular mode) */
    val canAddRegularPosition: Boolean
        get() = canAddPosition

    /** Alias for currentTotal (sale regular mode) */
    val regularModeTotal: BigDecimal?
        get() = currentTotal

    /** Alias for currentBatchTotalAmount (sale wholesale mode) */
    val currentTotalAmount: BigDecimal?
        get() = currentBatchTotalAmount
}

// ==================== UI STATE TYPEALIASES ====================
// These typealiases provide backward compatibility for screens using old type names.

typealias PurchaseEntryUiState = TransactionEntryUiState
typealias SaleEntryUiState = TransactionEntryUiState

/**
 * Data for the weight restore popup shown when user switches product
 * after removing weighed product from scales without adding a position.
 */
data class WeightRestoreData(
    val product: Product,
    val weight: BigDecimal,
    val price: BigDecimal
)
