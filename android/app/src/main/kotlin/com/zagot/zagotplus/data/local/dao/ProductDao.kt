package com.zagot.zagotplus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zagot.zagotplus.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow
import java.util.UUID

/**
 * Data Access Object for products table.
 * Provides CRUD operations and reactive queries via Flow.
 */
@Dao
interface ProductDao {

    /**
     * Insert a new product. Replaces on conflict (for sync upsert).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(product: ProductEntity)

    /**
     * Insert multiple products (for sync).
     */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(products: List<ProductEntity>)

    /**
     * Update existing product.
     */
    @Update
    suspend fun update(product: ProductEntity)

    /**
     * Get all products as Flow (reactive for UI).
     */
    @Query("SELECT * FROM products ORDER BY name ASC")
    fun getAllFlow(): Flow<List<ProductEntity>>

    /**
     * Get all products (one-time read).
     */
    @Query("SELECT * FROM products ORDER BY name ASC")
    suspend fun getAll(): List<ProductEntity>

    /**
     * Get product by ID.
     */
    @Query("SELECT * FROM products WHERE id = :id")
    suspend fun getById(id: UUID): ProductEntity?

    /**
     * Get only active products as Flow (for dropdowns).
     */
    @Query("SELECT * FROM products WHERE is_active = 1 ORDER BY name ASC")
    fun getActiveFlow(): Flow<List<ProductEntity>>

    /**
     * Get only active products (one-time read).
     */
    @Query("SELECT * FROM products WHERE is_active = 1 ORDER BY name ASC")
    suspend fun getActive(): List<ProductEntity>

    /**
     * Delete all products (for testing/reset).
     */
    @Query("DELETE FROM products")
    suspend fun deleteAll()
}
 