package com.zagot.zagotplus.domain.model

import java.math.BigDecimal
import java.util.UUID

/**
 * Computed inventory item (location + product + total weight).
 */
data class InventoryItem(
    val locationId: UUID,
    val productId: UUID,
    val totalWeightKg: BigDecimal
)
