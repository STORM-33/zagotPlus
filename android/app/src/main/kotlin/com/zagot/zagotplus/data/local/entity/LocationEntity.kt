package com.zagot.zagotplus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Room entity representing a location where transactions occur.
 * Mirrors Supabase 'locations' table.
 *
 * @property id Primary key (UUID stored as TEXT)
 * @property name Location name (e.g., "Кіоск", "Мобільний")
 * @property type Location type: "kiosk" or "mobile"
 * @property createdAt Timestamp when location was created
 */
@Entity(tableName = "locations")
data class LocationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: String, // "kiosk" | "mobile"

    @ColumnInfo(name = "created_at")
    val createdAt: Instant
)
