package com.zagot.zagotplus.data.remote.dto

import com.zagot.zagotplus.data.local.entity.LocationEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Data Transfer Object for location records from Supabase.
 */
@Serializable
data class LocationDto(
    @SerialName("id")
    val id: String,

    @SerialName("name")
    val name: String,

    @SerialName("type")
    val type: String,

    @SerialName("created_at")
    val createdAt: String
) {
    /**
     * Convert DTO to Room entity.
     */
    fun toEntity(): LocationEntity = LocationEntity(
        id = UUID.fromString(id),
        name = name,
        type = type,
        createdAt = Instant.parse(createdAt)
    )

    companion object {
        /**
         * Create DTO from Room entity.
         */
        fun fromEntity(entity: LocationEntity): LocationDto = LocationDto(
            id = entity.id.toString(),
            name = entity.name,
            type = entity.type,
            createdAt = entity.createdAt.toString()
        )
    }
}
