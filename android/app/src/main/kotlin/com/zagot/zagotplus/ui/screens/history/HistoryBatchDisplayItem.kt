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
    abstract val isVoided: Boolean
    abstract val isCorrection: Boolean
    abstract val correctionReason: String?
    /** True if this is an edited batch (has original data to show). */
    open val isEdited: Boolean = false
    /** Notes for the batch, if any. */
    abstract val notes: String?

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
        override val notes: String?,
        override val isVoided: Boolean = false,
        override val isCorrection: Boolean = false,
        override val correctionReason: String? = null,
        val correctsBatchId: UUID? = null
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
        override val notes: String?,
        override val isVoided: Boolean = false,
        override val isCorrection: Boolean = false,
        override val correctionReason: String? = null,
        val correctsBatchId: UUID? = null
    ) : HistoryBatchDisplayItem() {
        override val id: String = "sale_batch_$batchId"
        override val batchType: BatchType = BatchType.SALE
    }

    /**
     * Merged view of an edited batch - combines original (voided) + correction into one display item.
     * Shows current values with ability to view previous/original values.
     */
    data class EditedBatch(
        val currentBatchId: UUID,
        val originalBatchId: UUID,
        override val batchType: BatchType,
        override val createdAt: Instant,
        override val totalWeightKg: BigDecimal,
        override val totalAmount: BigDecimal?,
        override val itemCount: Int,
        override val locationName: String,
        override val isSynced: Boolean,
        override val notes: String?,
        override val correctionReason: String?,
        /** Original batch data before editing. */
        val originalTotalWeightKg: BigDecimal,
        val originalTotalAmount: BigDecimal?,
        val originalItemCount: Int,
        val originalNotes: String?,
        val originalCreatedAt: Instant
    ) : HistoryBatchDisplayItem() {
        override val id: String = "edited_batch_$currentBatchId"
        override val isVoided: Boolean = false
        override val isCorrection: Boolean = false // Not showing as "correction", showing as "edited"
        override val isEdited: Boolean = true
    }

    /**
     * Virtual batch grouping transactions by time window.
     * Used for adjustments and old transactions without batch_id.
     */
    data class VirtualBatch(
        val transactionIds: List<UUID>,
        val timeWindowStart: Instant,
        override val batchType: BatchType,
        override val createdAt: Instant,
        override val totalWeightKg: BigDecimal,
        override val totalAmount: BigDecimal?,
        override val itemCount: Int,
        val locationId: UUID?,
        override val locationName: String,
        override val isSynced: Boolean
    ) : HistoryBatchDisplayItem() {
        override val id: String = "virtual_${batchType.name}_${timeWindowStart.toEpochMilli()}_${locationId ?: "unknown"}_${transactionIds.firstOrNull() ?: "empty"}"
        override val isVoided: Boolean = false
        override val isCorrection: Boolean = false
        override val correctionReason: String? = null
        override val notes: String? = null
    }

    /**
     * Transfer operation shown as a single item with source and destination.
     * Combines TRANSFER_OUT and TRANSFER_IN transactions into one display item.
     */
    data class TransferBatch(
        val transactionIds: List<UUID>,
        val fromLocationName: String,
        val toLocationName: String,
        override val createdAt: Instant,
        override val totalWeightKg: BigDecimal,
        override val itemCount: Int,
        override val isSynced: Boolean
    ) : HistoryBatchDisplayItem() {
        override val id: String = "transfer_${transactionIds.firstOrNull() ?: createdAt.toEpochMilli()}"
        override val batchType: BatchType = BatchType.TRANSFER
        override val totalAmount: BigDecimal? = null
        override val locationName: String = "$fromLocationName → $toLocationName"
        override val isVoided: Boolean = false
        override val isCorrection: Boolean = false
        override val correctionReason: String? = null
        override val notes: String? = null
    }
}

/**
 * Type of batch for display purposes.
 */
enum class BatchType {
    PURCHASE,
    SALE,
    TRANSFER,
    ADJUSTMENT;

    fun toDisplayString(): String = when (this) {
        PURCHASE -> "Закупка"
        SALE -> "Продаж"
        TRANSFER -> "Переміщення"
        ADJUSTMENT -> "Коригування"
    }
}
