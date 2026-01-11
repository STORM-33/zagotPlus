package com.zagot.zagotplus.domain.repository

import com.zagot.zagotplus.domain.model.Location
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Repository interface for Location domain model.
 */
interface LocationRepository {

    /**
     * Get all locations as reactive Flow.
     */
    fun getAllLocations(): Flow<List<Location>>

    /**
     * Get locations by type.
     */
    fun getLocationsByType(type: String): Flow<List<Location>>

    /**
     * Get location by ID (one-time read).
     */
    suspend fun getLocationById(id: UUID): Location?
}
