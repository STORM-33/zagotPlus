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

    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("amount")
    val amount: String,

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

    @SerialName("transfer_pair_id")
    val transferPairId: String? = null,

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
        amount = BigDecimal(amount),
        categoryId = categoryId?.let { UUID.fromString(it) },
        batchId = batchId?.let { UUID.fromString(it) },
        notes = notes,
        deviceId = deviceId,
        createdAt = Instant.parse(createdAt),
        syncedAt = syncedAt?.let { Instant.parse(it) },
        isTransfer = isTransfer,
        transferPairId = transferPairId,
        serverUpdatedAt = serverUpdatedAt?.let { Instant.parse(it) }
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
            amount = entity.amount.toPlainString(),
            categoryId = entity.categoryId?.toString(),
            batchId = entity.batchId?.toString(),
            notes = entity.notes,
            deviceId = entity.deviceId,
            createdAt = entity.createdAt.toString(),
            syncedAt = entity.syncedAt?.toString(),
            isTransfer = entity.isTransfer,
            transferPairId = entity.transferPairId
        )

        /**
         * Create DTO from generic Record map (sync engine pull).
         */
        fun fromRecord(record: Map<String, Any?>): CashOperationDto = CashOperationDto(
            id = record["id"] as String,
            localId = record["local_id"] as String,
            locationId = record["location_id"] as? String,
            type = record["type"] as String,
            amount = record["amount"]?.toString() ?: "0",
            categoryId = record["category_id"] as? String,
            batchId = record["batch_id"] as? String,
            notes = record["notes"] as? String,
            deviceId = record["device_id"] as? String,
            createdAt = record["created_at"] as String,
            syncedAt = record["synced_at"] as? String,
            isTransfer = record["is_transfer"] as? Boolean ?: false,
            transferPairId = record["transfer_pair_id"] as? String,
            serverUpdatedAt = record["server_updated_at"] as? String,
        )
    }
}
