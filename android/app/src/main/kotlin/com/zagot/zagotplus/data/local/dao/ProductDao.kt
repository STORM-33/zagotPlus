package com.zagot.zagotplus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import com.zagot.zagotplus.data.local.entity.ProductEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
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
     * Upsert products (INSERT or UPDATE, no DELETE).
     * Safe for sync pull — avoids FK cascade issues from REPLACE strategy.
     */
    @Upsert
    suspend fun upsertAll(products: List<ProductEntity>)

    /**
     * Update existing product.
     */
    @Update
    suspend fun update(product: ProductEntity)

    /**
     * Delete a product by ID.
     */
    @Query("DELETE FROM products WHERE id = :id")
    suspend fun deleteById(id: UUID)

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
     * Get product by local_id (for sync deduplication).
     */
    @Query("SELECT * FROM products WHERE local_id = :localId")
    suspend fun getByLocalId(localId: String): ProductEntity?

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
     * Get all unsynced products (for push).
     */
    @Query("SELECT * FROM products WHERE synced_at IS NULL")
    suspend fun getUnsynced(): List<ProductEntity>

    /**
     * Mark product as synced.
     */
    @Query("UPDATE products SET synced_at = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: UUID, syncedAt: Instant)

    /**
     * Delete all products (for testing/reset).
     */
    @Query("DELETE FROM products")
    suspend fun deleteAll()
}
 
