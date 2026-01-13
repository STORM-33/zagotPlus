package com.zagot.zagotplus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import kotlinx.coroutines.flow.Flow
import java.time.Instant
import java.util.UUID

/**
 * DAO for expense categories.
 */
@Dao
interface ExpenseCategoryDao {

    @Query("SELECT * FROM expense_categories WHERE is_active = 1 ORDER BY name ASC")
    fun getActiveCategories(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<ExpenseCategoryEntity>>

    @Query("SELECT * FROM expense_categories WHERE id = :id")
    suspend fun getById(id: UUID): ExpenseCategoryEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(category: ExpenseCategoryEntity)

    @Update
    suspend fun update(category: ExpenseCategoryEntity)

    @Query("UPDATE expense_categories SET is_active = 0 WHERE id = :id")
    suspend fun deactivate(id: UUID)

    @Query("SELECT * FROM expense_categories WHERE synced_at IS NULL")
    suspend fun getUnsynced(): List<ExpenseCategoryEntity>

    @Query("UPDATE expense_categories SET synced_at = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: UUID, syncedAt: Instant)

    @Query("SELECT * FROM expense_categories WHERE local_id = :localId")
    suspend fun getByLocalId(localId: String): ExpenseCategoryEntity?
}

/**
 * DAO for cash operations.
 */
@Dao
interface CashOperationDao {

    @Query("""
        SELECT * FROM cash_operations 
        WHERE location_id = :locationId 
        ORDER BY created_at DESC
    """)
    fun getByLocation(locationId: UUID): Flow<List<CashOperationEntity>>

    @Query("""
        SELECT * FROM cash_operations 
        WHERE location_id = :locationId 
          AND created_at >= :startOfDay
          AND created_at < :endOfDay
        ORDER BY created_at DESC
    """)
    fun getByLocationAndDate(
        locationId: UUID,
        startOfDay: Instant,
        endOfDay: Instant
    ): Flow<List<CashOperationEntity>>

    @Query("SELECT * FROM cash_operations WHERE id = :id")
    suspend fun getById(id: UUID): CashOperationEntity?

    @Query("SELECT * FROM cash_operations WHERE batch_id = :batchId")
    suspend fun getByBatchId(batchId: UUID): CashOperationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: CashOperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(operations: List<CashOperationEntity>)

    @Query("""
        SELECT COALESCE(
            (SELECT COALESCE(SUM(CASE 
                WHEN type = 'deposit' THEN amount
                WHEN type IN ('withdrawal', 'payment') THEN -amount
                ELSE 0
            END), 0) FROM cash_operations WHERE location_id = :locationId)
            +
            (SELECT COALESCE(SUM(CASE 
                WHEN type = 'sale' THEN total_amount
                WHEN type = 'purchase' THEN -total_amount
                ELSE 0
            END), 0) FROM transactions WHERE location_id = :locationId)
        , 0)
    """)
    fun getBalanceByLocation(locationId: UUID): Flow<java.math.BigDecimal>

    @Query("""
        SELECT COALESCE(
            (SELECT COALESCE(SUM(CASE 
                WHEN type = 'deposit' THEN amount
                WHEN type IN ('withdrawal', 'payment') THEN -amount
                ELSE 0
            END), 0) FROM cash_operations
            WHERE location_id = :locationId
              AND created_at >= :startOfDay
              AND created_at < :endOfDay)
            +
            (SELECT COALESCE(SUM(CASE 
                WHEN type = 'sale' THEN total_amount
                WHEN type = 'purchase' THEN -total_amount
                ELSE 0
            END), 0) FROM transactions
            WHERE location_id = :locationId
              AND created_at >= :startOfDay
              AND created_at < :endOfDay)
        , 0)
    """)
    fun getDailyBalanceChange(
        locationId: UUID,
        startOfDay: Instant,
        endOfDay: Instant
    ): Flow<java.math.BigDecimal>

    @Query("SELECT * FROM cash_operations WHERE synced_at IS NULL")
    suspend fun getUnsynced(): List<CashOperationEntity>

    @Query("UPDATE cash_operations SET synced_at = :syncedAt WHERE id = :id")
    suspend fun markSynced(id: UUID, syncedAt: Instant)

    @Query("""
        SELECT * FROM cash_operations
        WHERE location_id = :locationId
        ORDER BY created_at DESC
        LIMIT :limit
    """)
    fun getRecentByLocation(locationId: UUID, limit: Int): Flow<List<CashOperationEntity>>

    // Global operations (across all locations)
    @Query("SELECT * FROM cash_operations ORDER BY created_at DESC")
    fun getAllOperations(): Flow<List<CashOperationEntity>>

    @Query("SELECT * FROM cash_operations ORDER BY created_at DESC LIMIT :limit")
    fun getRecentOperations(limit: Int): Flow<List<CashOperationEntity>>

    @Query("SELECT * FROM cash_operations ORDER BY created_at DESC LIMIT :limit OFFSET :offset")
    suspend fun getOperationsPaged(limit: Int, offset: Int): List<CashOperationEntity>

    @Query("SELECT COUNT(*) FROM cash_operations")
    suspend fun getTotalOperationsCount(): Int

    @Query("""
        SELECT COALESCE(
            (SELECT COALESCE(SUM(CASE 
                WHEN type = 'deposit' THEN amount
                WHEN type IN ('withdrawal', 'payment') THEN -amount
                ELSE 0
            END), 0) FROM cash_operations)
            +
            (SELECT COALESCE(SUM(CASE 
                WHEN type = 'sale' THEN total_amount
                WHEN type = 'purchase' THEN -total_amount
                ELSE 0
            END), 0) FROM transactions)
        , 0)
    """)
    fun getTotalBalance(): Flow<java.math.BigDecimal>

    @Query("""
        SELECT COALESCE(
            (SELECT COALESCE(SUM(CASE 
                WHEN type = 'deposit' THEN amount
                WHEN type IN ('withdrawal', 'payment') THEN -amount
                ELSE 0
            END), 0) FROM cash_operations
            WHERE created_at >= :startOfDay AND created_at < :endOfDay)
            +
            (SELECT COALESCE(SUM(CASE 
                WHEN type = 'sale' THEN total_amount
                WHEN type = 'purchase' THEN -total_amount
                ELSE 0
            END), 0) FROM transactions
            WHERE created_at >= :startOfDay AND created_at < :endOfDay)
        , 0)
    """)
    fun getDailyBalanceChange(startOfDay: Instant, endOfDay: Instant): Flow<java.math.BigDecimal>

    @Query("SELECT * FROM cash_operations WHERE local_id = :localId")
    suspend fun getByLocalId(localId: String): CashOperationEntity?
}
