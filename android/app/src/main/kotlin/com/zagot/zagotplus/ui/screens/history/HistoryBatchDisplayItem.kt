package com.zagot.zagotplus.ui.screens.history

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Represents a batch or virtual batch in the history list.
 * Can be either a real PurchaseBatch, real SaleBatch, or a virtual grouping of transactions.
 */
sealed class HistoryBatchDisplayItem {
    abstract val id: String
    abstract val batchType: BatchType
    abstract val createdAt: Instant
    abstract val totalWeightKg: BigDecimal
    abstract val totalAmount: BigDecimal?
    abstract val itemCount: Int
    abstract val locationName: String
    abstract val isSynced: Boolean

    /**
     * Real purchase batch from database.
     */
    data class RealBatch(
        val batchId: UUID,
        override val createdAt: Instant,
        override val totalWeightKg: BigDecimal,
        override val totalAmount: BigDecimal?,
        override val itemCount: Int,
        override val locationName: String,
        override val isSynced: Boolean,
        val notes: String?
    ) : HistoryBatchDisplayItem() {
        override val id: String = "batch_$batchId"
        override val batchType: BatchType = BatchType.PURCHASE
    }

    /**
     * Real sale batch from database.
     */
    data class RealSaleBatch(
        val batchId: UUID,
        override val createdAt: Instant,
        override val totalWeightKg: BigDecimal,
        override val totalAmount: BigDecimal?,
        override val itemCount: Int,
        override val locationName: String,
        override val isSynced: Boolean,
        val notes: String?
    ) : HistoryBatchDisplayItem() {
        override val id: String = "sale_batch_$batchId"
        override val batchType: BatchType = BatchType.SALE
    }

    /**
     * Virtual batch grouping transactions by time window.
     * Used for transfers and old transactions without batch_id.
     */
    data class VirtualBatch(
        val transactionIds: List<UUID>,
        val timeWindowStart: Instant,
        override val batchType: BatchType,
        override val createdAt: Instant,
        override val totalWeightKg: BigDecimal,
        override val totalAmount: BigDecimal?,
        override val itemCount: Int,
        override val locationName: String,
        override val isSynced: Boolean
    ) : HistoryBatchDisplayItem() {
        override val id: String = "virtual_${batchType.name}_${timeWindowStart.toEpochMilli()}_${locationName.hashCode()}"
    }
}

/**
 * Type of batch for display purposes.
 */
enum class BatchType {
    PURCHASE,
    SALE,
    TRANSFER;

    fun toDisplayString(): String = when (this) {
        PURCHASE -> "Закупка"
        SALE -> "Продаж"
        TRANSFER -> "Переміщення"
    }
}
