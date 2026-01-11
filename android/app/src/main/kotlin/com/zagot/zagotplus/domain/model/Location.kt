package com.zagot.zagotplus.domain.model

import java.time.Instant
import java.util.UUID

/**
 * Domain model for a location.
 */
data class Location(
    val id: UUID,
    val name: String,
    val type: LocationType,
    val createdAt: Instant
)

enum class LocationType {
    KIOSK,
    MOBILE;

    fun toDbValue(): String = when (this) {
        KIOSK -> "kiosk"
        MOBILE -> "mobile"
    }

    companion object {
        fun fromDbValue(value: String): LocationType = when (value) {
            "kiosk" -> KIOSK
            "mobile" -> MOBILE
            else -> throw IllegalArgumentException("Unknown location type: $value")
        }
    }
}
