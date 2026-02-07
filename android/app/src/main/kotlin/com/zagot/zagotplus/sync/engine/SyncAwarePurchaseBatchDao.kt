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
    private val syncEngine: SyncEngine,
) : PurchaseBatchDao by dao {

    override suspend fun insert(batch: PurchaseBatchEntity) {
        dao.insert(batch)
        outboxDao.insert(outboxEntry(batch, "INSERT"))
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun insertAll(batches: List<PurchaseBatchEntity>) {
        dao.insertAll(batches)
        batches.forEach { outboxDao.insert(outboxEntry(it, "INSERT")) }
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun upsertAll(batches: List<PurchaseBatchEntity>) {
        // Passthrough only — upsertAll is used by applyToRoom (raw DAO path).
        // If called via SyncAware, we must NOT create outbox entries to avoid
        // pushing pulled records back to server in a loop.
        dao.upsertAll(batches)
    }

    override suspend fun update(batch: PurchaseBatchEntity) {
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

    private fun outboxEntry(entity: PurchaseBatchEntity, operation: String) = SyncOutboxEntity(
        tableName = "purchase_batches",
        recordId = entity.id.toString(),
        operation = operation,
        payload = Json.encodeToString(PurchaseBatchDto.fromEntity(entity)),
        createdAt = System.currentTimeMillis(),
    )
}
