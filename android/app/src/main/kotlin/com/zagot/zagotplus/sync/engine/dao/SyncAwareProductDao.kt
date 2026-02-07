package com.zagot.zagotplus.sync.engine.dao

import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.sync.engine.api.SyncEngine
import com.zagot.zagotplus.sync.engine.db.SyncOutboxDao
import com.zagot.zagotplus.sync.engine.db.SyncOutboxEntity
import com.zagot.zagotplus.sync.engine.util.RawDao
import com.zagot.zagotplus.sync.engine.util.SyncJson
import kotlinx.serialization.encodeToString
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Outbox-instrumented wrapper for [ProductDao].
 * Intercepts writes and creates outbox entries for sync.
 */
@Singleton
class SyncAwareProductDao @Inject constructor(
    @RawDao private val dao: ProductDao,
    private val outboxDao: SyncOutboxDao,
    private val syncEngine: SyncEngine,
) : ProductDao by dao {

    override suspend fun insert(product: ProductEntity) {
        dao.insert(product)
        outboxDao.insert(outboxEntry(product, "INSERT"))
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun insertAll(products: List<ProductEntity>) {
        dao.insertAll(products)
        products.forEach { outboxDao.insert(outboxEntry(it, "INSERT")) }
        syncEngine.notifyOutboxChanged()
    }

    override suspend fun update(product: ProductEntity) {
        dao.update(product)
        outboxDao.insert(outboxEntry(product, "UPDATE"))
        syncEngine.notifyOutboxChanged()
    }

    private fun outboxEntry(entity: ProductEntity, operation: String) = SyncOutboxEntity(
        tableName = "products",
        recordId = entity.id.toString(),
        operation = operation,
        payload = SyncJson.encodeToString(ProductDto.fromEntity(entity)),
        createdAt = System.currentTimeMillis(),
    )
}
