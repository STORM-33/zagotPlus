package com.zagot.zagotplus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zagot.zagotplus.data.local.entity.LocationEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for locations table.
 * Provides CRUD operations and reactive queries via Flow.
 */
@Dao
interface LocationDao {

    /**
     * Insert a new location. Replaces on conflict (for sync upsert).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(location: LocationEntity)

    /**
     * Insert multiple locations (for sync).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(locations: List<LocationEntity>)

    /**
     * Update existing location.
     */
    @Update
    suspend fun update(location: LocationEntity)

    /**
     * Get all locations as Flow (reactive for UI).
     */
    @Query("SELECT * FROM locations ORDER BY created_at ASC")
    fun getAllFlow(): Flow<List<LocationEntity>>

    /**
     * Get all locations (one-time read).
     */
    @Query("SELECT * FROM locations ORDER BY created_at ASC")
    suspend fun getAll(): List<LocationEntity>

    /**
     * Get location by ID.
     */
    @Query("SELECT * FROM locations WHERE id = :id")
    suspend fun getById(id: UUID): LocationEntity?

    /**
     * Get locations by type (kiosk or mobile).
     */
    @Query("SELECT * FROM locations WHERE type = :type ORDER BY created_at ASC")
    fun getByTypeFlow(type: String): Flow<List<LocationEntity>>

    /**
     * Delete all locations (for testing/reset).
     */
    @Query("DELETE FROM locations")
    suspend fun deleteAll()
}
