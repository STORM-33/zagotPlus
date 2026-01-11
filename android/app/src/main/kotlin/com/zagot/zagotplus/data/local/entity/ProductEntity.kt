package com.zagot.zagotplus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID

/**
 * Room entity representing a product (nut or seed type).
 * Mirrors Supabase 'products' table.
 *
 * @property id Primary key (UUID stored as TEXT)
 * @property name Product name (e.g., "Горіх білий", "Насіння чорне")
 * @property defaultBuyPrice Default purchase price per kg
 * @property defaultSellPrice Default wholesale price per kg
 * @property isActive Whether product is currently in use
 * @property createdAt Timestamp when product was created
 */
@Entity(tableName = "products")
data class ProductEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "default_buy_price")
    val defaultBuyPrice: BigDecimal?,

    @ColumnInfo(name = "default_sell_price")
    val defaultSellPrice: BigDecimal?,

    @ColumnInfo(name = "is_active")
    val isActive: Boolean,

    @ColumnInfo(name = "created_at")
    val createdAt: Instant
)
