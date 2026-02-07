package com.zagot.zagotplus.sync.engine

import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outbox-instrumented wrapper for [TransactionDao].
 * Intercepts writes and creates outbox entries for sync.
 */
@Singleton
class SyncAwareTransactionDao @Inject constructor(
    @RawDao private val dao: TransactionDao,
    private val outboxDao: SyncOutboxDao,
    private val syncEngine: SyncEngine,
) : TransactionDao by dao {

    override suspend fun insert(transaction: TransactionEntity) {
        dao.insert(transaction)
        outboxDao.insert(outboxEntry(transaction, "INSERT"))
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun insertAll(transactions: List<TransactionEntity>) {
        dao.insertAll(transactions)
        transactions.forEach { outboxDao.insert(outboxEntry(it, "INSERT")) }
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun update(transaction: TransactionEntity) {
        dao.update(transaction)
        outboxDao.insert(outboxEntry(transaction, "UPDATE"))
        syncEngine.notifyOutboxChanged()
    }

    private fun outboxEntry(entity: TransactionEntity, operation: String) = SyncOutboxEntity(
        tableName = "transactions",
        recordId = entity.id.toString(),
        operation = operation,
        payload = Json.encodeToString(TransactionDto.fromEntity(entity)),
        createdAt = System.currentTimeMillis(),
    )
}
