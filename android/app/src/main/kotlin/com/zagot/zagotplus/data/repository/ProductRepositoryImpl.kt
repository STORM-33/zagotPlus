package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of ProductRepository using Room as data source.
 */
@Singleton
class ProductRepositoryImpl @Inject constructor(
    private val productDao: ProductDao,
    private val syncManager: SyncManager
) : ProductRepository {

    override fun getAllProducts(): Flow<List<Product>> =
        productDao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getActiveProducts(): Flow<List<Product>> =
        productDao.getActiveFlow().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getProductById(id: UUID): Product? =
        productDao.getById(id)?.toDomain()

    override suspend fun createProduct(
        name: String,
        defaultBuyPrice: BigDecimal?,
        defaultSellPrice: BigDecimal?,
        imageUri: String?
    ): Product {
        val id = UUID.randomUUID()
        val entity = ProductEntity(
            id = id,
            localId = id.toString(),
            name = name,
            defaultBuyPrice = defaultBuyPrice,
            defaultSellPrice = defaultSellPrice,
            isActive = true,
            createdAt = Instant.now(),
            syncedAt = null,
            imageUri = imageUri
        )
        productDao.insert(entity)
        syncManager.triggerManualSync()
        return entity.toDomain()
    }

    override suspend fun updateProduct(product: Product) {
        val existing = productDao.getById(product.id) ?: return
        val entity = existing.copy(
            name = product.name,
            defaultBuyPrice = product.defaultBuyPrice,
            defaultSellPrice = product.defaultSellPrice,
            isActive = product.isActive,
            imageUri = product.imageUri,
            syncedAt = null // Mark as unsynced after update
        )
        productDao.update(entity)
        syncManager.triggerManualSync()
    }

    override suspend fun toggleProductActive(productId: UUID) {
        val existing = productDao.getById(productId) ?: return
        val updated = existing.copy(
            isActive = !existing.isActive,
            syncedAt = null // Mark as unsynced after toggle
        )
        productDao.update(updated)
        syncManager.triggerManualSync()
    }

    override suspend fun deleteProduct(productId: UUID) {
        productDao.deleteById(productId)
    }

    private fun ProductEntity.toDomain() = Product(
        id = id,
        name = name,
        defaultBuyPrice = defaultBuyPrice,
        defaultSellPrice = defaultSellPrice,
        isActive = isActive,
        createdAt = createdAt,
        imageUri = imageUri
    )
}
