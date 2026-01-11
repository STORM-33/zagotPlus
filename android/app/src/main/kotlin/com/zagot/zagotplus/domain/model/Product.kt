package com.zagot.zagotplus.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Domain model for a product.
 */
data class Product(
    val id: UUID,
    val name: String,
    val defaultBuyPrice: BigDecimal?,
    val defaultSellPrice: BigDecimal?,
    val isActive: Boolean,
    val createdAt: Instant
)
