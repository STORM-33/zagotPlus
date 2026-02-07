package com.zagot.zagotplus.sync.engine

import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.entity.LocationEntity
import com.zagot.zagotplus.data.remote.dto.LocationDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outbox-instrumented wrapper for [LocationDao].
 * Intercepts writes and creates outbox entries for sync.
 * Read methods delegate directly to the raw DAO.
 */
@Singleton
class SyncAwareLocationDao @Inject constructor(
    @RawDao private val dao: LocationDao,
    private val outboxDao: SyncOutboxDao,
) : LocationDao by dao {

    override suspend fun insert(location: LocationEntity) {
        dao.insert(location)
        outboxDao.insert(outboxEntry(location, "INSERT"))
    }

    override suspend fun insertAll(locations: List<LocationEntity>) {
        dao.insertAll(locations)
        locations.forEach { outboxDao.insert(outboxEntry(it, "INSERT")) }
    }

    override suspend fun update(location: LocationEntity) {
        dao.update(location)
        outboxDao.insert(outboxEntry(location, "UPDATE"))
    }

    private fun outboxEntry(entity: LocationEntity, operation: String) = SyncOutboxEntity(
        tableName = "locations",
        recordId = entity.id.toString(),
        operation = operation,
        payload = Json.encodeToString(LocationDto.fromEntity(entity)),
        createdAt = System.currentTimeMillis(),
    )
}
