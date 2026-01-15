package com.zagot.zagotplus.domain.model

import androidx.compose.runtime.Stable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Domain model for a transaction.
 * 
 * Marked @Stable for Compose recomposition optimization.
 */
@Stable
data class Transaction(
    val id: UUID,
    val localId: String,
    val locationId: UUID?,
    val type: TransactionType,
    val transferLocationId: UUID?,
    val productId: UUID?,
    val weightKg: BigDecimal,
    val pricePerKg: BigDecimal?,
    val totalAmount: BigDecimal?,
    val notes: String?,
    val deviceId: String?,
    val createdAt: Instant,
    val syncedAt: Instant?,
    val batchId: UUID? = null,
    val saleBatchId: UUID? = null
)

enum class TransactionType {
    PURCHASE,
    SALE,
    TRANSFER_OUT,
    TRANSFER_IN,
    ADJUSTMENT;

    fun toDbValue(): String = when (this) {
        PURCHASE -> "purchase"
        SALE -> "sale"
        TRANSFER_OUT -> "transfer_out"
        TRANSFER_IN -> "transfer_in"
        ADJUSTMENT -> "adjustment"
    }

    companion object {
        fun fromDbValue(value: String): TransactionType = when (value) {
            "purchase" -> PURCHASE
            "sale" -> SALE
            "transfer_out" -> TRANSFER_OUT
            "transfer_in" -> TRANSFER_IN
            "adjustment" -> ADJUSTMENT
            else -> throw IllegalArgumentException("Unknown transaction type: $value")
        }
    }
}
