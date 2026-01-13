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
        Index(value = ["created_at"])
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
    val syncedAt: Instant?
)
