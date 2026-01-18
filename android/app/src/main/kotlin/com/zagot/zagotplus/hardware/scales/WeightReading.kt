package com.zagot.zagotplus.hardware.scales

import java.math.BigDecimal
import java.time.Instant

/**
 * Represents a single weight reading from scales.
 */
data class WeightReading(
    val weightKg: BigDecimal,
    val isStable: Boolean,
    val timestamp: Instant,
    val raw: String  // Raw protocol data for debugging
)
