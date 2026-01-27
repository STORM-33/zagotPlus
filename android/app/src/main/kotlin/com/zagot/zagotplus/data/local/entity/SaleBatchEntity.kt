package com.zagot.zagotplus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Room entity representing a sale batch.
 * Groups multiple sale transaction line items per client session.
 * Mirrors Supabase 'sale_batches' table.
 *
 * Supports correction workflow: when a batch is corrected, the original
 * is marked as voided (isVoided=true) and a new correction batch is created
 * with correctsBatchId pointing to the original.
 *
 * @property id Primary key (UUID stored as TEXT, server-generated)
 * @property localId Device-generated UUID, ensures conflict-free sync
 * @property locationId Location where batch was created
 * @property notes Optional notes (applies to entire batch)
 * @property totalWeightKg Total weight of all items in batch
 * @property totalAmount Total amount of all items in batch
 * @property itemCount Number of items in batch
 * @property deviceId Identifies which device created the batch
 * @property createdAt Timestamp when batch was created
 * @property syncedAt Timestamp when synced to Supabase (null = pending sync)
 * @property isVoided True if this batch has been voided by a correction
 * @property correctsBatchId ID of the batch this one corrects (null if not a correction)
 * @property correctionReason Reason for correction (only set on correction batches)
 */
@Entity(
    tableName = "sale_batches",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.RESTRICT
        )
    ],
    indices = [
        Index(value = ["local_id"], unique = true),
        Index(value = ["location_id"]),
        Index(value = ["synced_at"]),
        Index(value = ["created_at"]),
        Index(value = ["is_voided"]),
        Index(value = ["corrects_batch_id"]),
        Index(value = ["server_updated_at"])
    ]
)
data class SaleBatchEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "local_id")
    val localId: String,

    @ColumnInfo(name = "location_id")
    val locationId: UUID?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "total_weight_kg")
    val totalWeightKg: BigDecimal?,

    @ColumnInfo(name = "total_amount")
    val totalAmount: BigDecimal?,

    @ColumnInfo(name = "item_count")
    val itemCount: Int?,

    @ColumnInfo(name = "device_id")
    val deviceId: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "synced_at")
    val syncedAt: Instant?,

    @ColumnInfo(name = "is_voided", defaultValue = "0")
    val isVoided: Boolean = false,

    @ColumnInfo(name = "corrects_batch_id")
    val correctsBatchId: UUID? = null,

    @ColumnInfo(name = "correction_reason")
    val correctionReason: String? = null,

    @ColumnInfo(name = "voided_at")
    val voidedAt: Instant? = null,

    @ColumnInfo(name = "voided_by_device_id")
    val voidedByDeviceId: String? = null,

    @ColumnInfo(name = "server_updated_at")
    val serverUpdatedAt: Instant? = null
)
