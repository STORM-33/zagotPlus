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

    @Query("SELECT * FROM cash_operations WHERE transaction_id = :transactionId")
    suspend fun getByTransactionId(transactionId: UUID): CashOperationEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(operation: CashOperationEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(operations: List<CashOperationEntity>)

    @Query("""
        SELECT COALESCE(
            SUM(CASE 
                WHEN type = 'deposit' THEN amount
                WHEN type IN ('withdrawal', 'payment', 'purchase') THEN -amount
                ELSE 0
            END), 0
        ) FROM cash_operations
        WHERE location_id = :locationId
    """)
    fun getBalanceByLocation(locationId: UUID): Flow<java.math.BigDecimal>

    @Query("""
        SELECT COALESCE(
            SUM(CASE 
                WHEN type = 'deposit' THEN amount
                WHEN type IN ('withdrawal', 'payment', 'purchase') THEN -amount
                ELSE 0
            END), 0
        ) FROM cash_operations
        WHERE location_id = :locationId
          AND created_at >= :startOfDay
          AND created_at < :endOfDay
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

    @Query("""
        SELECT COALESCE(
            SUM(CASE 
                WHEN type = 'deposit' THEN amount
                WHEN type IN ('withdrawal', 'payment', 'purchase') THEN -amount
                ELSE 0
            END), 0
        ) FROM cash_operations
    """)
    fun getTotalBalance(): Flow<java.math.BigDecimal>

    @Query("""
        SELECT COALESCE(
            SUM(CASE 
                WHEN type = 'deposit' THEN amount
                WHEN type IN ('withdrawal', 'payment', 'purchase') THEN -amount
                ELSE 0
            END), 0
        ) FROM cash_operations
        WHERE created_at >= :startOfDay AND created_at < :endOfDay
    """)
    fun getDailyBalanceChange(startOfDay: Instant, endOfDay: Instant): Flow<java.math.BigDecimal>

    @Query("SELECT * FROM cash_operations WHERE local_id = :localId")
    suspend fun getByLocalId(localId: String): CashOperationEntity?
}
