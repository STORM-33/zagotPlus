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
 * Room entity representing a transaction (purchase, sale, transfer).
 * Mirrors Supabase 'transactions' table.
 *
 * Append-only ledger - transactions are never edited, only reversed if needed.
 *
 * @property id Primary key (UUID stored as TEXT, server-generated)
 * @property localId Device-generated UUID, ensures conflict-free sync
 * @property locationId Location where transaction occurred
 * @property type Transaction type: "purchase", "sale", "transfer_out", "transfer_in"
 * @property transferLocationId For transfers: the other location involved
 * @property productId Product being transacted
 * @property weightKg Weight in kilograms
 * @property pricePerKg Price per kilogram
 * @property totalAmount Total transaction amount
 * @property notes Optional notes
 * @property deviceId Identifies which device created the transaction
 * @property createdAt Timestamp when transaction was created
 * @property syncedAt Timestamp when synced to Supabase (null = pending sync)
 * @property batchId Optional reference to parent purchase batch
 */
@Entity(
    tableName = "transactions",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["transfer_location_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ProductEntity::class,
            parentColumns = ["id"],
            childColumns = ["product_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = PurchaseBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.SET_NULL
        )
    ],
    indices = [
        Index(value = ["local_id"], unique = true),
        Index(value = ["location_id"]),
        Index(value = ["transfer_location_id"]),
        Index(value = ["product_id"]),
        Index(value = ["batch_id"]),
        Index(value = ["synced_at"]),
        Index(value = ["created_at"])
    ]
)
data class TransactionEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "local_id")
    val localId: String,

    @ColumnInfo(name = "location_id")
    val locationId: UUID?,

    @ColumnInfo(name = "type")
    val type: String, // "purchase" | "sale" | "transfer_out" | "transfer_in"

    @ColumnInfo(name = "transfer_location_id")
    val transferLocationId: UUID?,

    @ColumnInfo(name = "product_id")
    val productId: UUID?,

    @ColumnInfo(name = "weight_kg")
    val weightKg: BigDecimal,

    @ColumnInfo(name = "price_per_kg")
    val pricePerKg: BigDecimal?,

    @ColumnInfo(name = "total_amount")
    val totalAmount: BigDecimal?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "device_id")
    val deviceId: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "synced_at")
    val syncedAt: Instant?,

    @ColumnInfo(name = "batch_id")
    val batchId: UUID? = null
)
