package com.zagot.zagotplus.sync.engine

import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.entity.PurchaseBatchEntity
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outbox-instrumented wrapper for [PurchaseBatchDao].
 * Intercepts writes and creates outbox entries for sync.
 */
@Singleton
class SyncAwarePurchaseBatchDao @Inject constructor(
    @RawDao private val dao: PurchaseBatchDao,
    private val outboxDao: SyncOutboxDao,
) : PurchaseBatchDao by dao {

    override suspend fun insert(batch: PurchaseBatchEntity) {
        dao.insert(batch)
        outboxDao.insert(outboxEntry(batch, "INSERT"))
    }

    override suspend fun insertAll(batches: List<PurchaseBatchEntity>) {
        dao.insertAll(batches)
        batches.forEach { outboxDao.insert(outboxEntry(it, "INSERT")) }
    }

    override suspend fun upsertAll(batches: List<PurchaseBatchEntity>) {
        dao.upsertAll(batches)
        batches.forEach { outboxDao.insert(outboxEntry(it, "INSERT")) }
    }

    override suspend fun update(batch: PurchaseBatchEntity) {
        dao.update(batch)
        outboxDao.insert(outboxEntry(batch, "UPDATE"))
    }

    override suspend fun markVoided(id: UUID, voidedAt: Long, deviceId: String): Int {
        val result = dao.markVoided(id, voidedAt, deviceId)
        if (result > 0) {
            val entity = dao.getById(id) ?: return result
            outboxDao.insert(outboxEntry(entity, "UPDATE"))
        }
        return result
    }

    private fun outboxEntry(entity: PurchaseBatchEntity, operation: String) = SyncOutboxEntity(
        tableName = "purchase_batches",
        recordId = entity.id.toString(),
        operation = operation,
        payload = Json.encodeToString(PurchaseBatchDto.fromEntity(entity)),
        createdAt = System.currentTimeMillis(),
    )
}
