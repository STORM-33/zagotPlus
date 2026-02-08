package com.zagot.zagotplus.data.local.converter

import androidx.room.TypeConverter
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Room TypeConverters for non-primitive types used in entities.
 * Handles conversion between database representations (TEXT, INTEGER, TEXT)
 * and Kotlin types (UUID, Instant, BigDecimal).
 */
class Converters {

    // UUID <-> String
    @TypeConverter
    fun fromUUID(uuid: UUID?): String? {
        return uuid?.toString()
    }

    @TypeConverter
    fun toUUID(string: String?): UUID? = try {
        string?.let { UUID.fromString(it) }
    } catch (e: IllegalArgumentException) {
        null
    }

    // Instant <-> Long (epoch millis)
    @TypeConverter
    fun fromInstant(instant: Instant?): Long? {
        return instant?.toEpochMilli()
    }

    @TypeConverter
    fun toInstant(epochMilli: Long?): Instant? {
        return epochMilli?.let { Instant.ofEpochMilli(it) }
    }

    // BigDecimal <-> String (preserves precision)
    @TypeConverter
    fun fromBigDecimal(decimal: BigDecimal?): String? {
        return decimal?.toPlainString()
    }

    @TypeConverter
    fun toBigDecimal(string: String?): BigDecimal? = try {
        string?.let { BigDecimal(it) }
    } catch (e: NumberFormatException) {
        null
    }
}
