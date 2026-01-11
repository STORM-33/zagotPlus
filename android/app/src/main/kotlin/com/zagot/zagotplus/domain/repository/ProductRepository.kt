package com.zagot.zagotplus.domain.repository

import com.zagot.zagotplus.domain.model.Product
import kotlinx.coroutines.flow.Flow
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
}
