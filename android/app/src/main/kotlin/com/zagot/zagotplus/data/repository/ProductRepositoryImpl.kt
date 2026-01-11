package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.repository.ProductRepository
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
    private val productDao: ProductDao
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
        val entity = ProductEntity(
            id = UUID.randomUUID(),
            name = name,
            defaultBuyPrice = defaultBuyPrice,
            defaultSellPrice = defaultSellPrice,
            isActive = true,
            createdAt = Instant.now(),
            imageUri = imageUri
        )
        productDao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun updateProduct(product: Product) {
        val entity = ProductEntity(
            id = product.id,
            name = product.name,
            defaultBuyPrice = product.defaultBuyPrice,
            defaultSellPrice = product.defaultSellPrice,
            isActive = product.isActive,
            createdAt = product.createdAt,
            imageUri = product.imageUri
        )
        productDao.update(entity)
    }

    override suspend fun toggleProductActive(productId: UUID) {
        val existing = productDao.getById(productId) ?: return
        val updated = existing.copy(isActive = !existing.isActive)
        productDao.update(updated)
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
