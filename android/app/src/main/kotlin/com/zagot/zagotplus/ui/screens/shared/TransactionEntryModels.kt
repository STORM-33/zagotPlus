package com.zagot.zagotplus.ui.screens.shared

import com.zagot.zagotplus.domain.model.Product
import java.math.BigDecimal
import java.math.RoundingMode
import java.util.UUID

/**
 * Represents a single weighing batch (e.g., a few sacks weighed together).
 * Used by both purchase and sale flows.
 */
data class WeighingBatch(
    val id: String = UUID.randomUUID().toString(),
    val grossWeightKg: BigDecimal,
    val tareCount: Int // Number of sacks/boxes in this batch
)

/**
 * Represents a transaction position (line item) with all its weighings.
 * Used by both purchase and sale flows.
 */
data class TransactionPosition(
    val id: String = UUID.randomUUID().toString(),
    val product: Product,
    val batches: List<WeighingBatch>,
    val tareWeightPerUnit: BigDecimal,
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
        get() = if (netWeight > BigDecimal.ZERO && pricePerKg > BigDecimal.ZERO) {
            netWeight.multiply(pricePerKg).setScale(2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

    /** Alias for netWeight - backward compatibility with old PurchasePosition */
    val weightKg: BigDecimal
        get() = netWeight
}

/**
 * Tracks which input field is active for numpad input.
 */
enum class TransactionInputField {
    WEIGHT,           // Weight input (kg)
    TARE_COUNT,       // Batch tare count input (number of sacks/bags)
    TARE_WEIGHT_UNIT, // Tare weight per unit (kg per sack/bag)
    PRICE             // Price per kg
}

/**
 * Screen state for the transaction entry flow.
 */
enum class TransactionEntryScreenState {
    PRODUCT_GRID,    // Selecting product from grid
    WEIGHT_ENTRY,    // Regular mode: simple weight + price entry
    WEIGHING,        // Batch mode: adding weight batches with tare count
    POSITION_REVIEW, // Batch mode: review current position before adding
    POSITIONS_LIST,  // Viewing all positions, can add more products
    UNIFIED_ENTRY,   // Tablet: unified three-column layout (regular mode)
    UNIFIED_BATCH,   // Tablet: batch mode three-column layout
    SUMMARY          // Final confirmation after saving
}

/**
 * Entry mode for transactions.
 */
enum class TransactionEntryMode {
    REGULAR,  // Simple weight entry (no batches, no tare)
    BATCH;    // Multiple weighings with tare tracking

    companion object {
        fun fromString(value: String?): TransactionEntryMode {
            return when (value?.uppercase()) {
                "BATCH", "WHOLESALE" -> BATCH
                else -> REGULAR
            }
        }
    }
}

// ==================== COMPATIBILITY TYPEALIASES ====================
// These typealiases provide backward compatibility during migration.
// Purchase screens can continue using their old type names.
// Sale screens can continue using their old type names.

// Purchase compatibility
typealias PurchaseWeighingBatch = WeighingBatch
typealias PurchasePosition = TransactionPosition
typealias PurchaseInputField = TransactionInputField
typealias PurchaseEntryScreenState = TransactionEntryScreenState

// Sale compatibility
typealias SaleWeighingBatch = WeighingBatch
typealias SalePosition = TransactionPosition
typealias SaleInputField = TransactionInputField
typealias SaleEntryScreenState = TransactionEntryScreenState

// UI State compatibility - import TransactionEntryUiState from shared package
// These are used by tablet content files that reference the old type names
