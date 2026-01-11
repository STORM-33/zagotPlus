package com.zagot.zagotplus.domain.model

import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Domain model for a purchase batch.
 * Groups multiple transactions per client session.
 */
data class PurchaseBatch(
    val id: UUID,
    val localId: String,
    val locationId: UUID?,
    val notes: String?,
    val totalWeightKg: BigDecimal?,
    val totalAmount: BigDecimal?,
    val itemCount: Int?,
    val deviceId: String?,
    val createdAt: Instant,
    val syncedAt: Instant?
)
