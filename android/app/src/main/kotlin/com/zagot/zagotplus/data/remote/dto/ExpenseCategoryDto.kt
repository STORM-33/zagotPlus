package com.zagot.zagotplus.data.remote.dto

import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.time.Instant
import java.util.UUID

/**
 * Data Transfer Object for expense category records sent to/from Supabase.
 * Uses kotlinx.serialization for JSON encoding/decoding.
 */
@Serializable
data class ExpenseCategoryDto(
    @SerialName("id")
    val id: String,

    @SerialName("local_id")
    val localId: String,

    @SerialName("name")
    val name: String,

    @SerialName("is_active")
    val isActive: Boolean,

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
    fun toEntity(): ExpenseCategoryEntity = ExpenseCategoryEntity(
        id = UUID.fromString(id),
        localId = localId,
        name = name,
        isActive = isActive,
        createdAt = Instant.parse(createdAt),
        syncedAt = syncedAt?.let { Instant.parse(it) }
    )

    companion object {
        /**
         * Create DTO from Room entity for push to Supabase.
         */
        fun fromEntity(entity: ExpenseCategoryEntity): ExpenseCategoryDto = ExpenseCategoryDto(
            id = entity.id.toString(),
            localId = entity.localId,
            name = entity.name,
            isActive = entity.isActive,
            createdAt = entity.createdAt.toString(),
            syncedAt = entity.syncedAt?.toString()
        )
    }
}
