package com.zagot.zagotplus.data.remote.dto

import com.zagot.zagotplus.data.local.entity.TransactionEntity
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

    @Serializable(with = FlexibleDecimalSerializer::class)
    @SerialName("weight_kg")
    val weightKg: String,

    @Serializable(with = FlexibleDecimalSerializerNullable::class)
    @SerialName("price_per_kg")
    val pricePerKg: String?,

    @Serializable(with = FlexibleDecimalSerializerNullable::class)
    @SerialName("total_amount")
    val totalAmount: String?,

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
    
    // server_updated_at is set by server trigger — included as null in push payloads
    // to ensure consistent key sets across batched records (Postgrest requirement).
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
        weightKg = BigDecimal(weightKg),
        pricePerKg = pricePerKg?.let { BigDecimal(it) },
        totalAmount = totalAmount?.let { BigDecimal(it) },
        notes = notes,
        deviceId = deviceId,
        createdAt = Instant.parse(createdAt),
        syncedAt = syncedAt?.let { Instant.parse(it) },
        batchId = batchId?.let { UUID.fromString(it) },
        saleBatchId = saleBatchId?.let { UUID.fromString(it) },
        serverUpdatedAt = serverUpdatedAt?.let { Instant.parse(it) }
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
            weightKg = entity.weightKg.toPlainString(),
            pricePerKg = entity.pricePerKg?.toPlainString(),
            totalAmount = entity.totalAmount?.toPlainString(),
            notes = entity.notes,
            deviceId = entity.deviceId,
            createdAt = entity.createdAt.toString(),
            syncedAt = entity.syncedAt?.toString(),
            batchId = entity.batchId?.toString(),
            saleBatchId = entity.saleBatchId?.toString()
        )

        /**
         * Create DTO from generic Record map (sync engine pull).
         */
        fun fromRecord(record: Map<String, Any?>): TransactionDto = TransactionDto(
            id = record["id"] as String,
            localId = record["local_id"] as String,
            locationId = record["location_id"] as? String,
            type = record["type"] as String,
            transferLocationId = record["transfer_location_id"] as? String,
            productId = record["product_id"] as? String,
            weightKg = record["weight_kg"]?.toString() ?: "0",
            pricePerKg = record["price_per_kg"]?.toString(),
            totalAmount = record["total_amount"]?.toString(),
            notes = record["notes"] as? String,
            deviceId = record["device_id"] as? String,
            createdAt = record["created_at"] as String,
            syncedAt = record["synced_at"] as? String,
            batchId = record["batch_id"] as? String,
            saleBatchId = record["sale_batch_id"] as? String,
            serverUpdatedAt = record["server_updated_at"] as? String,
        )
    }
}
