package com.zagot.zagotplus.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Domain model for expense category.
 */
data class ExpenseCategory(
    val id: UUID,
    val localId: String,
    val name: String,
    val isActive: Boolean = true,
    val createdAt: Instant,
    val syncedAt: Instant? = null
)

/**
 * Cash operation type.
 */
enum class CashOperationType {
    DEPOSIT,     // Cash added to register
    WITHDRAWAL,  // Cash removed from register
    PAYMENT,     // Cash paid for expenses
    PURCHASE;    // Cash paid for product purchase (auto-linked)

    companion object {
        fun fromString(value: String): CashOperationType = when (value.lowercase()) {
            "deposit" -> DEPOSIT
            "withdrawal" -> WITHDRAWAL
            "payment" -> PAYMENT
            "purchase" -> PURCHASE
            else -> throw IllegalArgumentException("Unknown cash operation type: $value")
        }
    }

    fun toDbValue(): String = name.lowercase()
}

/**
 * Domain model for cash operation.
 */
data class CashOperation(
    val id: UUID,
    val localId: String,
    val locationId: UUID?,
    val type: CashOperationType,
    val amount: BigDecimal,
    val categoryId: UUID? = null,
    val categoryName: String? = null, // Denormalized for display
    val transactionId: UUID? = null,
    val notes: String? = null,
    val deviceId: String? = null,
    val createdAt: Instant,
    val syncedAt: Instant? = null
) {
    /**
     * Returns the signed amount: positive for deposits, negative for outflows.
     */
    val signedAmount: BigDecimal
        get() = when (type) {
            CashOperationType.DEPOSIT -> amount
            CashOperationType.WITHDRAWAL, CashOperationType.PAYMENT, CashOperationType.PURCHASE -> amount.negate()
        }
}
