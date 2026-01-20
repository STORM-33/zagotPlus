package com.zagot.zagotplus.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.zagot.zagotplus.data.local.entity.CashHistoryProjection
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(categories: List<ExpenseCategoryEntity>)

    /**
     * Get all existing local_ids for efficient batch deduplication during sync.
     */
    @Query("SELECT local_id FROM expense_categories")
    suspend fun getAllLocalIds(): List<String>

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
                WHEN type IN ('withdrawal', 'payment', 'purchase') THEN -amount
                ELSE 0
            END), 0) FROM cash_operations WHERE location_id = :locationId)
            +
            (SELECT COALESCE(SUM(-t.total_amount), 0) FROM transactions t
            LEFT JOIN purchase_batches pb ON t.batch_id = pb.id
            WHERE t.location_id = :locationId
              AND t.type = 'purchase'
              AND (t.batch_id IS NULL OR pb.is_voided = 0))
        , 0)
    """)
    fun getBalanceByLocation(locationId: UUID): Flow<java.math.BigDecimal>

    @Query("""
        SELECT COALESCE(
            (SELECT COALESCE(SUM(CASE 
                WHEN type = 'deposit' THEN amount
                WHEN type IN ('withdrawal', 'payment', 'purchase') THEN -amount
                ELSE 0
            END), 0) FROM cash_operations
            WHERE location_id = :locationId
              AND created_at >= :startOfDay
              AND created_at < :endOfDay)
            +
            (SELECT COALESCE(SUM(-t.total_amount), 0) FROM transactions t
            LEFT JOIN purchase_batches pb ON t.batch_id = pb.id
            WHERE t.location_id = :locationId
              AND t.created_at >= :startOfDay
              AND t.created_at < :endOfDay
              AND t.type = 'purchase'
              AND (t.batch_id IS NULL OR pb.is_voided = 0))
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
                WHEN type IN ('withdrawal', 'payment', 'purchase') THEN -amount
                ELSE 0
            END), 0) FROM cash_operations)
            +
            (SELECT COALESCE(SUM(-t.total_amount), 0) FROM transactions t
            LEFT JOIN purchase_batches pb ON t.batch_id = pb.id
            WHERE t.type = 'purchase'
              AND (t.batch_id IS NULL OR pb.is_voided = 0))
        , 0)
    """)
    fun getTotalBalance(): Flow<java.math.BigDecimal>

    @Query("""
        SELECT COALESCE(
            (SELECT COALESCE(SUM(CASE 
                WHEN type = 'deposit' THEN amount
                WHEN type IN ('withdrawal', 'payment', 'purchase') THEN -amount
                ELSE 0
            END), 0) FROM cash_operations
            WHERE created_at >= :startOfDay AND created_at < :endOfDay)
            +
            (SELECT COALESCE(SUM(-t.total_amount), 0) FROM transactions t
            LEFT JOIN purchase_batches pb ON t.batch_id = pb.id
            WHERE t.created_at >= :startOfDay AND t.created_at < :endOfDay
              AND t.type = 'purchase'
              AND (t.batch_id IS NULL OR pb.is_voided = 0))
        , 0)
    """)
    fun getDailyBalanceChange(startOfDay: Instant, endOfDay: Instant): Flow<java.math.BigDecimal>

    @Query("SELECT * FROM cash_operations WHERE local_id = :localId")
    suspend fun getByLocalId(localId: String): CashOperationEntity?

    /**
     * Get all existing local_ids for efficient batch deduplication during sync.
     */
    @Query("SELECT local_id FROM cash_operations")
    suspend fun getAllLocalIds(): List<String>

    @Update
    suspend fun update(operation: CashOperationEntity)

    /**
     * Get unified cash history with pagination.
     * Combines individual cash_operations with daily aggregates of purchase_batches.
     * Sales are excluded from cash history as per user preference.
     * Purchases are grouped by day only (across all locations) to avoid duplicate entries in totals view.
     * Transfers are excluded in totals view (they don't affect global balance).
     * Includes location info for display in totals view.
     *
     * Note: cash_operations with type='purchase' are excluded to avoid double-counting
     * since purchase data is already aggregated from purchase_batches.
     * Note: Transfers are detected by is_transfer flag (or notes pattern for legacy data).
     * 
     * Optimization: Uses integer arithmetic for day grouping instead of date() function
     * to enable index usage on created_at column. Groups by (created_at / 86400000) which
     * represents days since epoch in milliseconds.
     */
    @Query("""
        SELECT
            id,
            type,
            amount,
            notes,
            category_name,
            item_count,
            weight_kg,
            created_at,
            batch_count,
            location_id,
            location_name,
            is_transfer
        FROM (
            SELECT
                CAST(co.id AS TEXT) as id,
                co.type,
                co.amount,
                co.notes,
                ec.name as category_name,
                NULL as item_count,
                NULL as weight_kg,
                co.created_at,
                NULL as batch_count,
                CAST(co.location_id AS TEXT) as location_id,
                l.name as location_name,
                co.is_transfer as is_transfer
            FROM cash_operations co
            LEFT JOIN expense_categories ec ON co.category_id = ec.id
            LEFT JOIN locations l ON co.location_id = l.id
            WHERE co.type != 'purchase' AND co.is_transfer = 0
            UNION ALL
            SELECT 
                'purchase_' || CAST((pb.created_at / 86400000) AS TEXT) as id,
                'purchase' as type,
                SUM(pb.total_amount) as amount,
                NULL as notes,
                NULL as category_name,
                SUM(pb.item_count) as item_count,
                SUM(pb.total_weight_kg) as weight_kg,
                MAX(pb.created_at) as created_at,
                COUNT(*) as batch_count,
                NULL as location_id,
                NULL as location_name,
                NULL as is_transfer
            FROM purchase_batches pb
            WHERE pb.total_amount IS NOT NULL AND pb.is_voided = 0
            GROUP BY (pb.created_at / 86400000)
        )
        ORDER BY created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getCashHistoryPaged(limit: Int, offset: Int): List<CashHistoryProjection>

    /**
     * Get cash history filtered by location with pagination.
     * Sales are excluded from cash history as per user preference.
     *
     * Note: cash_operations with type='purchase' are excluded to avoid double-counting
     * since purchase data is already aggregated from purchase_batches.
     * 
     * Optimization: Uses integer arithmetic for day grouping instead of date() function.
     */
    @Query("""
        SELECT
            id,
            type,
            amount,
            notes,
            category_name,
            item_count,
            weight_kg,
            created_at,
            batch_count,
            location_id,
            location_name,
            is_transfer
        FROM (
            SELECT
                CAST(co.id AS TEXT) as id,
                co.type,
                co.amount,
                co.notes,
                ec.name as category_name,
                NULL as item_count,
                NULL as weight_kg,
                co.created_at,
                NULL as batch_count,
                CAST(co.location_id AS TEXT) as location_id,
                l.name as location_name,
                co.is_transfer as is_transfer
            FROM cash_operations co
            LEFT JOIN expense_categories ec ON co.category_id = ec.id
            LEFT JOIN locations l ON co.location_id = l.id
            WHERE co.location_id = :locationId AND co.type != 'purchase'
            UNION ALL
            SELECT 
                'purchase_' || CAST(pb.location_id AS TEXT) || '_' || CAST((pb.created_at / 86400000) AS TEXT) as id,
                'purchase' as type,
                SUM(pb.total_amount) as amount,
                NULL as notes,
                NULL as category_name,
                SUM(pb.item_count) as item_count,
                SUM(pb.total_weight_kg) as weight_kg,
                MAX(pb.created_at) as created_at,
                COUNT(*) as batch_count,
                CAST(pb.location_id AS TEXT) as location_id,
                l.name as location_name,
                NULL as is_transfer
            FROM purchase_batches pb
            LEFT JOIN locations l ON pb.location_id = l.id
            WHERE pb.total_amount IS NOT NULL AND pb.is_voided = 0 AND pb.location_id = :locationId
            GROUP BY pb.location_id, (pb.created_at / 86400000)
        )
        ORDER by created_at DESC
        LIMIT :limit OFFSET :offset
    """)
    suspend fun getCashHistoryByLocationPaged(locationId: UUID, limit: Int, offset: Int): List<CashHistoryProjection>

    /**
     * Get total count of all cash history items (operations + unique day combinations of purchase batches).
     * Sales are excluded from cash history.
     * Cash_operations with type='purchase' are excluded to avoid double-counting.
     * Purchases are counted by unique days only (not per-location) to match getCashHistoryPaged.
     * Transfers (is_transfer=1) are excluded in totals view.
     * 
     * Optimization: Uses integer division for day counting instead of date() function.
     */
    @Query("""
        SELECT
            (SELECT COUNT(*) FROM cash_operations WHERE type != 'purchase' AND is_transfer = 0) +
            (SELECT COUNT(DISTINCT (created_at / 86400000)) FROM purchase_batches WHERE total_amount IS NOT NULL AND is_voided = 0)
    """)
    suspend fun getTotalHistoryCount(): Int

    /**
     * Get count of cash history items for a specific location.
     * Sales are excluded from cash history.
     * Cash_operations with type='purchase' are excluded to avoid double-counting.
     * 
     * Optimization: Uses integer division for day counting instead of date() function.
     */
    @Query("""
        SELECT
            (SELECT COUNT(*) FROM cash_operations WHERE location_id = :locationId AND type != 'purchase') +
            (SELECT COUNT(DISTINCT (created_at / 86400000)) FROM purchase_batches WHERE total_amount IS NOT NULL AND is_voided = 0 AND location_id = :locationId)
    """)
    suspend fun getTotalHistoryCountByLocation(locationId: UUID): Int
    
    /**
     * Get today's deposits for a specific location.
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) 
        FROM cash_operations
        WHERE location_id = :locationId
          AND type = 'deposit'
          AND created_at >= :startOfDay
          AND created_at < :endOfDay
    """)
    fun getDailyDeposits(locationId: UUID, startOfDay: Instant, endOfDay: Instant): Flow<java.math.BigDecimal>
    
    /**
     * Get today's deposits globally (all locations).
     */
    @Query("""
        SELECT COALESCE(SUM(amount), 0) 
        FROM cash_operations
        WHERE type = 'deposit'
          AND created_at >= :startOfDay
          AND created_at < :endOfDay
    """)
    fun getDailyDepositsGlobal(startOfDay: Instant, endOfDay: Instant): Flow<java.math.BigDecimal>
}
