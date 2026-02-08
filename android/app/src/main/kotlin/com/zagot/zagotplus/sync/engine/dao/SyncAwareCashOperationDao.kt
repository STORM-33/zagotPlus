package com.zagot.zagotplus.sync.engine.dao

import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.remote.dto.CashOperationDto
import com.zagot.syncengine.api.SyncEngine
import com.zagot.syncengine.db.SyncOutboxDao
import com.zagot.syncengine.db.SyncOutboxEntity
import com.zagot.syncengine.util.RawDao
import com.zagot.syncengine.util.SyncJson
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outbox-instrumented wrapper for [CashOperationDao].
 * Intercepts writes and creates outbox entries for sync.
 */
@Singleton
class SyncAwareCashOperationDao @Inject constructor(
    @RawDao private val dao: CashOperationDao,
    private val outboxDao: SyncOutboxDao,
    private val syncEngine: SyncEngine,
) : CashOperationDao by dao {

    override suspend fun insert(operation: CashOperationEntity) {
        dao.insert(operation)
        outboxDao.insert(outboxEntry(operation, "INSERT"))
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun insertAll(operations: List<CashOperationEntity>) {
        dao.insertAll(operations)
        operations.forEach { outboxDao.insert(outboxEntry(it, "INSERT")) }
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun update(operation: CashOperationEntity) {
        dao.update(operation)
        outboxDao.insert(outboxEntry(operation, "UPDATE"))
        syncEngine.notifyOutboxChanged()
    }

    private fun outboxEntry(entity: CashOperationEntity, operation: String) = SyncOutboxEntity(
        tableName = "cash_operations",
        recordId = entity.id.toString(),
        operation = operation,
        payload = SyncJson.encodeToString(CashOperationDto.fromEntity(entity)),
        createdAt = System.currentTimeMillis(),
    )
}
