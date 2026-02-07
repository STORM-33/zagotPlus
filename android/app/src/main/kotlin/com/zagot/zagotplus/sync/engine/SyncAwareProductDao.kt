package com.zagot.zagotplus.sync.engine

import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.data.remote.dto.ProductDto
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
) : ProductDao by dao {

    override suspend fun insert(product: ProductEntity) {
        dao.insert(product)
        outboxDao.insert(outboxEntry(product, "INSERT"))
    }

    override suspend fun insertAll(products: List<ProductEntity>) {
        dao.insertAll(products)
        products.forEach { outboxDao.insert(outboxEntry(it, "INSERT")) }
    }

    override suspend fun update(product: ProductEntity) {
        dao.update(product)
        outboxDao.insert(outboxEntry(product, "UPDATE"))
    }

    private fun outboxEntry(entity: ProductEntity, operation: String) = SyncOutboxEntity(
        tableName = "products",
        recordId = entity.id.toString(),
        operation = operation,
        payload = Json.encodeToString(ProductDto.fromEntity(entity)),
        createdAt = System.currentTimeMillis(),
    )
}
