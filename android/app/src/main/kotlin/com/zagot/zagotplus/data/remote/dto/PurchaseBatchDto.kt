package com.zagot.zagotplus.data.remote.dto

import com.zagot.zagotplus.data.local.entity.PurchaseBatchEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Data Transfer Object for purchase batch records sent to/from Supabase.
 * Uses kotlinx.serialization for JSON encoding/decoding.
 */
@Serializable
data class PurchaseBatchDto(
    @SerialName("id")
    val id: String,

    @SerialName("local_id")
    val localId: String,

    @SerialName("location_id")
    val locationId: String?,

    @SerialName("notes")
    val notes: String?,

    @Serializable(with = FlexibleDecimalSerializerNullable::class)
    @SerialName("total_weight_kg")
    val totalWeightKg: String?,

    @Serializable(with = FlexibleDecimalSerializerNullable::class)
    @SerialName("total_amount")
    val totalAmount: String?,

    @SerialName("item_count")
    val itemCount: Int?,

    @SerialName("device_id")
    val deviceId: String?,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("synced_at")
    val syncedAt: String?,

    @SerialName("server_updated_at")
    val serverUpdatedAt: String? = null,

    @SerialName("is_voided")
    val isVoided: Boolean = false,

    @SerialName("corrects_batch_id")
    val correctsBatchId: String? = null,

    @SerialName("correction_reason")
    val correctionReason: String? = null,

    @SerialName("voided_at")
    val voidedAt: String? = null,

    @SerialName("voided_by_device_id")
    val voidedByDeviceId: String? = null
) {
    /**
     * Convert DTO to Room entity.
     */
    fun toEntity(): PurchaseBatchEntity = PurchaseBatchEntity(
        id = UUID.fromString(id),
        localId = localId,
        locationId = locationId?.let { UUID.fromString(it) },
        notes = notes,
        totalWeightKg = totalWeightKg?.let { BigDecimal(it) },
        totalAmount = totalAmount?.let { BigDecimal(it) },
        itemCount = itemCount,
        deviceId = deviceId,
        createdAt = Instant.parse(createdAt),
        syncedAt = syncedAt?.let { Instant.parse(it) },
        isVoided = isVoided,
        correctsBatchId = correctsBatchId?.let { UUID.fromString(it) },
        correctionReason = correctionReason,
        voidedAt = voidedAt?.let { Instant.parse(it) },
        voidedByDeviceId = voidedByDeviceId,
        serverUpdatedAt = serverUpdatedAt?.let { Instant.parse(it) }
    )

    companion object {
        /**
         * Create DTO from Room entity for push to Supabase.
         */
        fun fromEntity(entity: PurchaseBatchEntity): PurchaseBatchDto = PurchaseBatchDto(
            id = entity.id.toString(),
            localId = entity.localId,
            locationId = entity.locationId?.toString(),
            notes = entity.notes,
            totalWeightKg = entity.totalWeightKg?.toPlainString(),
            totalAmount = entity.totalAmount?.toPlainString(),
            itemCount = entity.itemCount,
            deviceId = entity.deviceId,
            createdAt = entity.createdAt.toString(),
            syncedAt = entity.syncedAt?.toString(),
            isVoided = entity.isVoided,
            correctsBatchId = entity.correctsBatchId?.toString(),
            correctionReason = entity.correctionReason,
            voidedAt = entity.voidedAt?.toString(),
            voidedByDeviceId = entity.voidedByDeviceId
        )
    }
}
