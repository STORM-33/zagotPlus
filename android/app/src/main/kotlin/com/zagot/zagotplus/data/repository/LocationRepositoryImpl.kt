package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.domain.repository.LocationRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of LocationRepository using Room as data source.
 */
@Singleton
class LocationRepositoryImpl @Inject constructor(
    private val locationDao: LocationDao
) : LocationRepository {

    override fun getAllLocations(): Flow<List<Location>> =
        locationDao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getLocationsByType(type: String): Flow<List<Location>> =
        locationDao.getByTypeFlow(type).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getLocationById(id: UUID): Location? =
        locationDao.getById(id)?.toDomain()

    private fun LocationEntity.toDomain() = Location(
        id = id,
        name = name,
        type = LocationType.fromDbValue(type),
        createdAt = createdAt
    )
}
