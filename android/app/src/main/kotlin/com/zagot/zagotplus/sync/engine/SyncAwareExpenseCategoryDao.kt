package com.zagot.zagotplus.sync.engine

import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.remote.dto.ExpenseCategoryDto
import kotlinx.serialization.encodeToString
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outbox-instrumented wrapper for [ExpenseCategoryDao].
 * Intercepts writes and creates outbox entries for sync.
 */
@Singleton
class SyncAwareExpenseCategoryDao @Inject constructor(
    @RawDao private val dao: ExpenseCategoryDao,
    private val outboxDao: SyncOutboxDao,
    private val syncEngine: SyncEngine,
) : ExpenseCategoryDao by dao {

    override suspend fun insert(category: ExpenseCategoryEntity) {
        dao.insert(category)
        outboxDao.insert(outboxEntry(category, "INSERT"))
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun insertAll(categories: List<ExpenseCategoryEntity>) {
        dao.insertAll(categories)
        categories.forEach { outboxDao.insert(outboxEntry(it, "INSERT")) }
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun update(category: ExpenseCategoryEntity) {
        dao.update(category)
        outboxDao.insert(outboxEntry(category, "UPDATE"))
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun deactivate(id: UUID) {
        dao.deactivate(id)
        val entity = dao.getById(id) ?: return
        outboxDao.insert(outboxEntry(entity, "UPDATE"))
        syncEngine.notifyOutboxChanged()
    }

    private fun outboxEntry(entity: ExpenseCategoryEntity, operation: String) = SyncOutboxEntity(
        tableName = "expense_categories",
        recordId = entity.id.toString(),
        operation = operation,
        payload = SyncJson.encodeToString(ExpenseCategoryDto.fromEntity(entity)),
        createdAt = System.currentTimeMillis(),
    )
}
