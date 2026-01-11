package com.zagot.zagotplus.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Filter criteria for transactions.
 */
data class TransactionFilter(
    val types: Set<TransactionType> = emptySet(),
    val locationId: UUID? = null,
    val startDate: Instant? = null,
    val endDate: Instant? = null,
    val productNameSearch: String? = null
) {
    val hasActiveFilters: Boolean
        get() = types.isNotEmpty() ||
                locationId != null ||
                startDate != null ||
                endDate != null ||
                !productNameSearch.isNullOrBlank()
}

/**
 * Preset date ranges for filtering.
 */
enum class DateRangePreset {
    TODAY,
    THIS_WEEK,
    THIS_MONTH,
    CUSTOM,
    ALL
}
