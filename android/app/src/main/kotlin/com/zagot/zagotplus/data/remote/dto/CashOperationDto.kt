package com.zagot.zagotplus.data.remote.dto

import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Data Transfer Object for cash operation records sent to/from Supabase.
 * Uses kotlinx.serialization for JSON encoding/decoding.
 */
@Serializable
data class CashOperationDto(
    @SerialName("id")
    val id: String,

    @SerialName("local_id")
    val localId: String,

    @SerialName("location_id")
    val locationId: String?,

    @SerialName("type")
    val type: String,

    @SerialName("amount")
    val amount: Double,

    @SerialName("category_id")
    val categoryId: String?,

    @SerialName("batch_id")
    val batchId: String?,

    @SerialName("notes")
    val notes: String?,

    @SerialName("device_id")
    val deviceId: String?,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("synced_at")
    val syncedAt: String?,

    @SerialName("is_transfer")
    val isTransfer: Boolean = false,

    @SerialName("server_updated_at")
    val serverUpdatedAt: String? = null
) {
    /**
     * Convert DTO to Room entity.
     */
    fun toEntity(): CashOperationEntity = CashOperationEntity(
        id = UUID.fromString(id),
        localId = localId,
        locationId = locationId?.let { UUID.fromString(it) },
        type = type,
        amount = BigDecimal.valueOf(amount),
        categoryId = categoryId?.let { UUID.fromString(it) },
        batchId = batchId?.let { UUID.fromString(it) },
        notes = notes,
        deviceId = deviceId,
        createdAt = Instant.parse(createdAt),
        syncedAt = syncedAt?.let { Instant.parse(it) },
        isTransfer = isTransfer
    )

    companion object {
        /**
         * Create DTO from Room entity for push to Supabase.
         */
        fun fromEntity(entity: CashOperationEntity): CashOperationDto = CashOperationDto(
            id = entity.id.toString(),
            localId = entity.localId,
            locationId = entity.locationId?.toString(),
            type = entity.type,
            amount = entity.amount.toDouble(),
            categoryId = entity.categoryId?.toString(),
            batchId = entity.batchId?.toString(),
            notes = entity.notes,
            deviceId = entity.deviceId,
            createdAt = entity.createdAt.toString(),
            syncedAt = entity.syncedAt?.toString(),
            isTransfer = entity.isTransfer
        )
    }
}
