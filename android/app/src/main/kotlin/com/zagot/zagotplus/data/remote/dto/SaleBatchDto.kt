package com.zagot.zagotplus.data.remote.dto

import com.zagot.zagotplus.data.local.entity.SaleBatchEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Data Transfer Object for sale batch records sent to/from Supabase.
 * Uses kotlinx.serialization for JSON encoding/decoding.
 */
@Serializable
data class SaleBatchDto(
    @SerialName("id")
    val id: String,

    @SerialName("local_id")
    val localId: String,

    @SerialName("location_id")
    val locationId: String?,

    @SerialName("notes")
    val notes: String?,

    @SerialName("total_weight_kg")
    val totalWeightKg: Double?,

    @SerialName("total_amount")
    val totalAmount: Double?,

    @SerialName("item_count")
    val itemCount: Int?,

    @SerialName("device_id")
    val deviceId: String?,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("synced_at")
    val syncedAt: String?,

    @SerialName("server_updated_at")
    val serverUpdatedAt: String? = null
) {
    /**
     * Convert DTO to Room entity.
     */
    fun toEntity(): SaleBatchEntity = SaleBatchEntity(
        id = UUID.fromString(id),
        localId = localId,
        locationId = locationId?.let { UUID.fromString(it) },
        notes = notes,
        totalWeightKg = totalWeightKg?.let { BigDecimal.valueOf(it) },
        totalAmount = totalAmount?.let { BigDecimal.valueOf(it) },
        itemCount = itemCount,
        deviceId = deviceId,
        createdAt = Instant.parse(createdAt),
        syncedAt = syncedAt?.let { Instant.parse(it) }
    )

    companion object {
        /**
         * Create DTO from Room entity for push to Supabase.
         */
        fun fromEntity(entity: SaleBatchEntity): SaleBatchDto = SaleBatchDto(
            id = entity.id.toString(),
            localId = entity.localId,
            locationId = entity.locationId?.toString(),
            notes = entity.notes,
            totalWeightKg = entity.totalWeightKg?.toDouble(),
            totalAmount = entity.totalAmount?.toDouble(),
            itemCount = entity.itemCount,
            deviceId = entity.deviceId,
            createdAt = entity.createdAt.toString(),
            syncedAt = entity.syncedAt?.toString()
        )
    }
}
