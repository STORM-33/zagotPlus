package com.zagot.zagotplus.domain.repository

import com.zagot.zagotplus.domain.model.Product
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.UUID

/**
 * Repository interface for Product domain model.
 */
interface ProductRepository {

    /**
     * Get all products as reactive Flow.
     */
    fun getAllProducts(): Flow<List<Product>>

    /**
     * Get active products only.
     */
    fun getActiveProducts(): Flow<List<Product>>

    /**
     * Get product by ID (one-time read).
     */
    suspend fun getProductById(id: UUID): Product?

    /**
     * Create a new product.
     */
    suspend fun createProduct(
        name: String,
        defaultBuyPrice: BigDecimal?,
        defaultSellPrice: BigDecimal?
    ): Product

    /**
     * Update an existing product.
     */
    suspend fun updateProduct(product: Product)

    /**
     * Toggle product active status.
     */
    suspend fun toggleProductActive(productId: UUID)
}
