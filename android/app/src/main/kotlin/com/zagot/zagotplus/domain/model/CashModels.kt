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
 * Note: Purchases and sales affect cash balance via transactions table directly,
 * not through cash_operations. This enum only covers manual cash operations.
 */
enum class CashOperationType {
    DEPOSIT,     // Cash added to register
    WITHDRAWAL,  // Cash removed from register
    PAYMENT;     // Cash paid for expenses

    companion object {
        fun fromString(value: String): CashOperationType = when (value.lowercase()) {
            "deposit" -> DEPOSIT
            "withdrawal" -> WITHDRAWAL
            "payment" -> PAYMENT
            else -> throw IllegalArgumentException("Unknown cash operation type: $value")
        }
    }

    fun toDbValue(): String = name.lowercase()
}

/**
 * Domain model for cash operation.
 * Represents manual cash operations only (deposit, withdrawal, payment).
 * Purchase/sale effects on cash are calculated from transactions table.
 */
data class CashOperation(
    val id: UUID,
    val localId: String,
    val locationId: UUID?,
    val type: CashOperationType,
    val amount: BigDecimal,
    val categoryId: UUID? = null,
    val categoryName: String? = null, // Denormalized for display
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
            CashOperationType.WITHDRAWAL, CashOperationType.PAYMENT -> amount.negate()
        }
}

/**
 * Type of cash history item (unified for all sources).
 */
enum class CashHistoryItemType {
    DEPOSIT,      // Manual deposit
    WITHDRAWAL,   // Manual withdrawal
    PAYMENT,      // Manual payment (expense)
    PURCHASE,     // Purchase batch (cash outflow)
    SALE;         // Sale batch (cash inflow)

    val isInflow: Boolean
        get() = this == DEPOSIT || this == SALE

    val isOutflow: Boolean
        get() = this == WITHDRAWAL || this == PAYMENT || this == PURCHASE
}

/**
 * Unified model for cash history display.
 * Combines cash_operations with daily aggregates of purchase_batches and sale_batches.
 * Purchases and sales are grouped by day to reduce list size.
 */
data class CashHistoryItem(
    val id: String, // UUID for operations, date-based ID for daily aggregates
    val type: CashHistoryItemType,
    val amount: BigDecimal,
    val notes: String?,
    val categoryName: String?,
    val itemCount: Int?,
    val weightKg: BigDecimal?,
    val createdAt: Instant,
    val batchCount: Int? = null // Number of batches in daily aggregate (for purchases/sales)
) {
    /**
     * Returns the signed amount: positive for inflows, negative for outflows.
     */
    val signedAmount: BigDecimal
        get() = if (type.isInflow) amount else amount.negate()
}
