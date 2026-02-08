package com.zagot.zagotplus.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.Instant
import java.util.UUID

/**
 * Room entity representing a location where transactions occur.
 * Mirrors Supabase 'locations' table.
 *
 * @property id Primary key (UUID stored as TEXT)
 * @property name Location name (e.g., "Кіоск", "Склад")
 * @property type Location type: "kiosk" or "mobile"
 * @property createdAt Timestamp when location was created
 * @property localId Device-generated UUID for sync (same as id for server-created locations)
 * @property syncedAt Timestamp when synced to Supabase (null = pending sync)
 * @property deviceId Which device created this location (null for server-created)
 */
@Entity(
    tableName = "locations",
    indices = [
        Index(value = ["local_id"], unique = true),
        Index(value = ["synced_at"])
    ]
)
data class LocationEntity(
    @PrimaryKey
    @ColumnInfo(name = "id")
    val id: UUID,

    @ColumnInfo(name = "name")
    val name: String,

    @ColumnInfo(name = "type")
    val type: String, // "kiosk" | "mobile"

    @ColumnInfo(name = "created_at")
    val createdAt: Instant,

    @ColumnInfo(name = "local_id")
    val localId: String = "",

    @ColumnInfo(name = "synced_at")
    val syncedAt: Instant? = null,

    @ColumnInfo(name = "device_id")
    val deviceId: String? = null
)
