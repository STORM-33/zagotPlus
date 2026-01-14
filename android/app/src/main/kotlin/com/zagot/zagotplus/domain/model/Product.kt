package com.zagot.zagotplus.domain.model

import androidx.compose.runtime.Stable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Domain model for a product.
 * 
 * Marked @Stable for Compose recomposition optimization since this class is frequently
 * used in UI composables and its equality is based on data class equals().
 */
@Stable
data class Product(
    val id: UUID,
    val name: String,
    val defaultBuyPrice: BigDecimal?,
    val defaultSellPrice: BigDecimal?,
    val isActive: Boolean,
    val createdAt: Instant,
    val imageUri: String? = null
)
