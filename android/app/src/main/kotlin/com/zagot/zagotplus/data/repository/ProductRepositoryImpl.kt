package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.entity.ProductEntity
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.repository.ProductRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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

    private fun ProductEntity.toDomain() = Product(
        id = id,
        name = name,
        defaultBuyPrice = defaultBuyPrice,
        defaultSellPrice = defaultSellPrice,
        isActive = isActive,
        createdAt = createdAt
    )
}
