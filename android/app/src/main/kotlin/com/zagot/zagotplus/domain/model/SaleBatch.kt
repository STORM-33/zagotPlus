package com.zagot.zagotplus.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Domain model for a sale batch.
 * Groups multiple sale transactions per client session.
 * 
 * Supports correction workflow: when a batch is corrected, the original
 * is marked as voided (isVoided=true) and a new correction batch is created
 * with correctsBatchId pointing to the original. This maintains immutability
 * while providing full audit trail.
 */
data class SaleBatch(
    val id: UUID,
    val localId: String,
    val locationId: UUID?,
    val notes: String?,
    val totalWeightKg: BigDecimal?,
    val totalAmount: BigDecimal?,
    val itemCount: Int?,
    val deviceId: String?,
    val createdAt: Instant,
    val syncedAt: Instant?,
    /** If true, this batch has been voided by a correction. Excluded from inventory. */
    val isVoided: Boolean = false,
    /** ID of the batch this one corrects (null if not a correction). */
    val correctsBatchId: UUID? = null,
    /** Reason for correction (only set on correction batches). */
    val correctionReason: String? = null
)
