package com.zagot.zagotplus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

// Note: PurchaseBatchEntity import not needed as it's in the same package

/**
 * Room entity representing an expense category.
 * User-defined categories for organizing cash payments.
 */
@Entity(
    tableName = "expense_categories",
    indices = [
        Index(value = ["local_id"], unique = true),
        Index(value = ["synced_at"]),
        Index(value = ["server_updated_at"])
    ]
)
data class ExpenseCategoryEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "local_id")
    val localId: String,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean = true,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "synced_at")
    val syncedAt: Instant? = null,

    @ColumnInfo(name = "server_updated_at")
    val serverUpdatedAt: Instant? = null
)

/**
 * Room entity representing a cash operation.
 * Tracks all cash movements: deposits, withdrawals, and payments.
 *
 * Operation types:
 * - "deposit": Cash added to register from external source
 * - "withdrawal": Cash removed from register to external destination
 * - "payment": Cash paid out for expenses (has category)
 * - "purchase": Cash paid for product purchase (auto-generated, linked to batch)
 *
 * @property id Primary key (UUID)
 * @property localId Device-generated UUID for sync
 * @property locationId Location where operation occurred
 * @property type Operation type: "deposit", "withdrawal", "payment", "purchase"
 * @property amount Amount in UAH (always positive)
 * @property categoryId For payments: optional expense category
 * @property batchId For purchases: linked purchase batch
 * @property notes Optional description
 * @property deviceId Which device created this operation
 * @property createdAt When operation was created
 * @property syncedAt When synced to Supabase (null = pending)
 */
/**
 * Projection class for unified cash history query.
 * Used to receive results from UNION query combining cash_operations and daily aggregates of purchase_batches/sale_batches.
 */
data class CashHistoryProjection(
    val id: String, // UUID for operations, date-based ID for aggregates
    val type: String, // "deposit", "withdrawal", "payment", "purchase", "sale"
    val amount: BigDecimal,
    val notes: String?,
    @ColumnInfo(name = "category_name")
    val categoryName: String?,
    @ColumnInfo(name = "item_count")
    val itemCount: Int?,
    @ColumnInfo(name = "weight_kg")
    val weightKg: BigDecimal?,
    @ColumnInfo(name = "created_at")
    val createdAt: Instant,
    @ColumnInfo(name = "batch_count")
    val batchCount: Int?, // Number of batches in aggregated entry
    @ColumnInfo(name = "location_id")
    val locationId: String?, // UUID of location (null for older records without location)
    @ColumnInfo(name = "location_name")
    val locationName: String?, // Name of location for display in totals view
    @ColumnInfo(name = "is_transfer")
    val isTransfer: Boolean? // True for transfer operations, null for aggregates
)

@Entity(
    tableName = "cash_operations",
    foreignKeys = [
        ForeignKey(
            entity = LocationEntity::class,
            parentColumns = ["id"],
            childColumns = ["location_id"],
            onDelete = ForeignKey.RESTRICT
        ),
        ForeignKey(
            entity = ExpenseCategoryEntity::class,
            parentColumns = ["id"],
            childColumns = ["category_id"],
            onDelete = ForeignKey.SET_NULL
        ),
        ForeignKey(
            entity = PurchaseBatchEntity::class,
            parentColumns = ["id"],
            childColumns = ["batch_id"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [
        Index(value = ["local_id"], unique = true),
        Index(value = ["location_id"]),
        Index(value = ["category_id"]),
        Index(value = ["batch_id"]),
        Index(value = ["synced_at"]),
        Index(value = ["created_at"]),
        Index(value = ["type"]),
        Index(value = ["is_transfer"]),
        Index(value = ["transfer_pair_id"]),
        Index(value = ["server_updated_at"])
    ]
)
data class CashOperationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "local_id")
    val localId: String,

    @ColumnInfo(name = "location_id")
    val locationId: UUID?,

    @ColumnInfo(name = "type")
    val type: String, // "deposit" | "withdrawal" | "payment" | "purchase"

    @ColumnInfo(name = "amount")
    val amount: BigDecimal,

    @ColumnInfo(name = "category_id")
    val categoryId: UUID?,

    @ColumnInfo(name = "batch_id")
    val batchId: UUID?,

    @ColumnInfo(name = "notes")
    val notes: String?,

    @ColumnInfo(name = "device_id")
    val deviceId: String?,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "synced_at")
    val syncedAt: Instant? = null,

    @ColumnInfo(name = "is_transfer", defaultValue = "0")
    val isTransfer: Boolean = false,

    @ColumnInfo(name = "transfer_pair_id")
    val transferPairId: String? = null,

    @ColumnInfo(name = "server_updated_at")
    val serverUpdatedAt: Instant? = null
)
