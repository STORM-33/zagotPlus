package com.zagot.zagotplus.sync.engine

import com.zagot.zagotplus.data.local.dao.SaleBatchDao
import com.zagot.zagotplus.data.local.entity.SaleBatchEntity
import com.zagot.zagotplus.data.remote.dto.SaleBatchDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outbox-instrumented wrapper for [SaleBatchDao].
 * Intercepts writes and creates outbox entries for sync.
 */
@Singleton
class SyncAwareSaleBatchDao @Inject constructor(
    @RawDao private val dao: SaleBatchDao,
    private val outboxDao: SyncOutboxDao,
    private val syncEngine: SyncEngine,
) : SaleBatchDao by dao {

    override suspend fun insert(batch: SaleBatchEntity) {
        dao.insert(batch)
        outboxDao.insert(outboxEntry(batch, "INSERT"))
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun insertAll(batches: List<SaleBatchEntity>) {
        dao.insertAll(batches)
        batches.forEach { outboxDao.insert(outboxEntry(it, "INSERT")) }
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun upsertAll(batches: List<SaleBatchEntity>) {
        dao.upsertAll(batches)
        batches.forEach { outboxDao.insert(outboxEntry(it, "INSERT")) }
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun update(batch: SaleBatchEntity) {
        dao.update(batch)
        outboxDao.insert(outboxEntry(batch, "UPDATE"))
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun markVoided(id: UUID, voidedAt: Long, deviceId: String): Int {
        val result = dao.markVoided(id, voidedAt, deviceId)
        if (result > 0) {
            val entity = dao.getById(id) ?: return result
            outboxDao.insert(outboxEntry(entity, "UPDATE"))
            syncEngine.notifyOutboxChanged()
        }
        return result
    }

    private fun outboxEntry(entity: SaleBatchEntity, operation: String) = SyncOutboxEntity(
        tableName = "sale_batches",
        recordId = entity.id.toString(),
        operation = operation,
        payload = Json.encodeToString(SaleBatchDto.fromEntity(entity)),
        createdAt = System.currentTimeMillis(),
    )
}
