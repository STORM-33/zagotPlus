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
    DEPOSIT,      // Manual deposit (внесення)
    WITHDRAWAL,   // Manual withdrawal (виведення)
    PAYMENT,      // Manual payment/expense (оплата)
    PURCHASE,     // Purchase batch (закупка)
    SALE,         // Sale batch (cash inflow)
    TRANSFER;     // Transfer between locations (переказ)

    val isInflow: Boolean
        get() = this == DEPOSIT || this == SALE

    val isOutflow: Boolean
        get() = this == WITHDRAWAL || this == PAYMENT || this == PURCHASE
    
    /** Display name in Ukrainian */
    fun displayName(): String = when (this) {
        DEPOSIT -> "Внесення"
        WITHDRAWAL -> "Виведення"
        PAYMENT -> "Оплата"
        PURCHASE -> "Закупка"
        SALE -> "Продаж"
        TRANSFER -> "Переказ"
    }
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
    val batchCount: Int? = null, // Number of batches in daily aggregate (for purchases/sales)
    val locationId: UUID? = null, // Location where this item occurred
    val locationName: String? = null, // Location name for display in totals view
    val isTransferIn: Boolean = false // For transfers: true = incoming (deposit), false = outgoing (withdrawal)
) {
    /**
     * Returns the signed amount: positive for inflows, negative for outflows.
     * For transfers: positive if incoming, negative if outgoing.
     */
    val signedAmount: BigDecimal
        get() = when {
            type == CashHistoryItemType.TRANSFER -> if (isTransferIn) amount else amount.negate()
            type.isInflow -> amount
            else -> amount.negate()
        }
    
    /** Check if this is a transfer (detected via notes pattern or type) */
    val isTransfer: Boolean
        get() = type == CashHistoryItemType.TRANSFER || 
                notes?.startsWith("Переказ") == true
}

/**
 * Represents a group of cash operations for a single day.
 * Used for collapsible day panels in the cash history view.
 */
data class DayCashGroup(
    val date: java.time.LocalDate,
    val items: List<CashHistoryItem>,
    val totalAmount: BigDecimal
) {
    /** Items grouped by operation type for display in expanded panel */
    val itemsByType: Map<CashHistoryItemType, List<CashHistoryItem>>
        get() = items.groupBy { 
            // Detect transfers from notes
            if (it.isTransfer && it.type != CashHistoryItemType.TRANSFER) {
                CashHistoryItemType.TRANSFER
            } else {
                it.type
            }
        }
    
    /** Types that have operations on this day (for showing only non-empty sections) */
    val activeTypes: List<CashHistoryItemType>
        get() = itemsByType.keys.toList().sortedBy { type ->
            // Sort order: внесення, закупка, оплата, виведення, переказ
            when (type) {
                CashHistoryItemType.DEPOSIT -> 0
                CashHistoryItemType.PURCHASE -> 1
                CashHistoryItemType.PAYMENT -> 2
                CashHistoryItemType.WITHDRAWAL -> 3
                CashHistoryItemType.TRANSFER -> 4
                CashHistoryItemType.SALE -> 5
            }
        }
    
    /** Total for this day (positive = net income, negative = net expense) */
    val dayTotal: BigDecimal
        get() = items.sumOf { it.signedAmount }
}

/**
 * Cash totals broken down by source (for global totals display).
 * Transfers are excluded from totals as they don't affect overall balance.
 */
data class CashTotalsBySource(
    val depositsByLocation: Map<String, BigDecimal> = emptyMap(),
    val withdrawalsByLocation: Map<String, BigDecimal> = emptyMap(),
    val paymentsByLocation: Map<String, BigDecimal> = emptyMap(),
    val purchasesByLocation: Map<String, BigDecimal> = emptyMap(), // kiosk/storage purchases
    val salesByLocation: Map<String, BigDecimal> = emptyMap()
) {
    val totalDeposits: BigDecimal
        get() = depositsByLocation.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
    
    val totalWithdrawals: BigDecimal
        get() = withdrawalsByLocation.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
    
    val totalPayments: BigDecimal
        get() = paymentsByLocation.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
    
    val totalPurchases: BigDecimal
        get() = purchasesByLocation.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
    
    val totalSales: BigDecimal
        get() = salesByLocation.values.fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
    
    /** Net balance change (excluding transfers) */
    val netChange: BigDecimal
        get() = totalDeposits.add(totalSales)
            .subtract(totalWithdrawals)
            .subtract(totalPayments)
            .subtract(totalPurchases)
}
