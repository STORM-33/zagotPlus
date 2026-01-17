package com.zagot.zagotplus.data.remote.dto

import com.zagot.zagotplus.data.local.entity.TransactionEntity
import kotlinx.serialization.EncodeDefault
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Data Transfer Object for transaction records sent to/from Supabase.
 * Uses kotlinx.serialization for JSON encoding/decoding.
 */
@Serializable
data class TransactionDto(
    @SerialName("id")
    val id: String,

    @SerialName("local_id")
    val localId: String,

    @SerialName("location_id")
    val locationId: String?,

    @SerialName("type")
    val type: String,

    @SerialName("transfer_location_id")
    val transferLocationId: String?,

    @SerialName("product_id")
    val productId: String?,

    @SerialName("weight_kg")
    val weightKg: Double,

    @SerialName("price_per_kg")
    val pricePerKg: Double?,

    @SerialName("total_amount")
    val totalAmount: Double?,

    @SerialName("notes")
    val notes: String?,

    @SerialName("device_id")
    val deviceId: String?,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("synced_at")
    val syncedAt: String?,

    @SerialName("batch_id")
    val batchId: String?,

    @SerialName("sale_batch_id")
    val saleBatchId: String?,
    
    // server_updated_at is set by server trigger - never sent in push, only received in pull
    @EncodeDefault(EncodeDefault.Mode.NEVER)
    @SerialName("server_updated_at")
    val serverUpdatedAt: String? = null
) {
    /**
     * Convert DTO to Room entity.
     */
    fun toEntity(): TransactionEntity = TransactionEntity(
        id = UUID.fromString(id),
        localId = localId,
        locationId = locationId?.let { UUID.fromString(it) },
        type = type,
        transferLocationId = transferLocationId?.let { UUID.fromString(it) },
        productId = productId?.let { UUID.fromString(it) },
        weightKg = BigDecimal.valueOf(weightKg),
        pricePerKg = pricePerKg?.let { BigDecimal.valueOf(it) },
        totalAmount = totalAmount?.let { BigDecimal.valueOf(it) },
        notes = notes,
        deviceId = deviceId,
        createdAt = Instant.parse(createdAt),
        syncedAt = syncedAt?.let { Instant.parse(it) },
        batchId = batchId?.let { UUID.fromString(it) },
        saleBatchId = saleBatchId?.let { UUID.fromString(it) }
    )

    companion object {
        /**
         * Create DTO from Room entity for push to Supabase.
         */
        fun fromEntity(entity: TransactionEntity): TransactionDto = TransactionDto(
            id = entity.id.toString(),
            localId = entity.localId,
            locationId = entity.locationId?.toString(),
            type = entity.type,
            transferLocationId = entity.transferLocationId?.toString(),
            productId = entity.productId?.toString(),
            weightKg = entity.weightKg.toDouble(),
            pricePerKg = entity.pricePerKg?.toDouble(),
            totalAmount = entity.totalAmount?.toDouble(),
            notes = entity.notes,
            deviceId = entity.deviceId,
            createdAt = entity.createdAt.toString(),
            syncedAt = entity.syncedAt?.toString(),
            batchId = entity.batchId?.toString(),
            saleBatchId = entity.saleBatchId?.toString()
        )
    }
}
