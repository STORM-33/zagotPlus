package com.zagot.zagotplus.data.remote.dto

import com.zagot.zagotplus.data.local.entity.ProductEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Data Transfer Object for product records from Supabase.
 */
@Serializable
data class ProductDto(
    @SerialName("id")
    val id: String,

    @SerialName("local_id")
    val localId: String,

    @SerialName("name")
    val name: String,

    @SerialName("default_buy_price")
    val defaultBuyPrice: String?,

    @SerialName("default_sell_price")
    val defaultSellPrice: String?,

    @SerialName("is_active")
    val isActive: Boolean,

    @SerialName("created_at")
    val createdAt: String,

    @SerialName("image_uri")
    val imageUri: String? = null
) {
    /**
     * Convert DTO to Room entity.
     */
    fun toEntity(): ProductEntity = ProductEntity(
        id = UUID.fromString(id),
        localId = localId,
        name = name,
        defaultBuyPrice = defaultBuyPrice?.let { BigDecimal(it) },
        defaultSellPrice = defaultSellPrice?.let { BigDecimal(it) },
        isActive = isActive,
        createdAt = Instant.parse(createdAt),
        syncedAt = Instant.now(),
        imageUri = imageUri
    )

    companion object {
        /**
         * Create DTO from Room entity.
         */
        fun fromEntity(entity: ProductEntity): ProductDto = ProductDto(
            id = entity.id.toString(),
            localId = entity.localId,
            name = entity.name,
            defaultBuyPrice = entity.defaultBuyPrice?.toPlainString(),
            defaultSellPrice = entity.defaultSellPrice?.toPlainString(),
            isActive = entity.isActive,
            createdAt = entity.createdAt.toString(),
            imageUri = entity.imageUri
        )
    }
}
