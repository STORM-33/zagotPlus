package com.zagot.zagotplus.domain.repository

import com.zagot.zagotplus.domain.model.CashHistoryItem
import com.zagot.zagotplus.domain.model.CashOperation
import com.zagot.zagotplus.domain.model.CashOperationType
import com.zagot.zagotplus.domain.model.ExpenseCategory
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.time.LocalDate
import java.util.UUID

/**
 * Repository interface for cash register operations.
 */
interface CashRepository {

    // Expense categories
    fun getActiveCategories(): Flow<List<ExpenseCategory>>
    fun getAllCategories(): Flow<List<ExpenseCategory>>
    suspend fun createCategory(name: String): ExpenseCategory
    suspend fun updateCategory(id: UUID, name: String)
    suspend fun deactivateCategory(id: UUID)

    // Cash operations (per location)
    fun getOperationsByLocation(locationId: UUID): Flow<List<CashOperation>>
    fun getOperationsByLocationAndDate(locationId: UUID, date: LocalDate): Flow<List<CashOperation>>
    fun getRecentOperations(locationId: UUID, limit: Int = 50): Flow<List<CashOperation>>
    
    // Global cash operations (all locations)
    /**
     * @deprecated Use getRecentOperationsGlobal(limit) or getOperationsPaged for large datasets.
     * This method loads ALL operations into memory which can cause OOM on large datasets.
     */
    @Deprecated("Use getRecentOperationsGlobal(limit) or getOperationsPaged instead", ReplaceWith("getRecentOperationsGlobal(100)"))
    fun getAllOperations(): Flow<List<CashOperation>>
    fun getRecentOperationsGlobal(limit: Int = 50): Flow<List<CashOperation>>
    
    // Paginated operations (for infinite scroll)
    suspend fun getOperationsPaged(limit: Int, offset: Int): List<CashOperation>
    suspend fun getTotalOperationsCount(): Int
    
    // Cash history (unified: cash_operations + purchases + sales)
    suspend fun getCashHistoryPaged(limit: Int, offset: Int): List<CashHistoryItem>
    suspend fun getCashHistoryByLocationPaged(locationId: UUID, limit: Int, offset: Int): List<CashHistoryItem>
    suspend fun getTotalHistoryCount(): Int
    suspend fun getTotalHistoryCountByLocation(locationId: UUID): Int

    
    // Balance (per location)
    fun getBalance(locationId: UUID): Flow<BigDecimal>
    fun getDailyChange(locationId: UUID, date: LocalDate): Flow<BigDecimal>
    
    // Global balance (all locations)
    fun getTotalBalance(): Flow<BigDecimal>
    fun getDailyChangeGlobal(date: LocalDate): Flow<BigDecimal>

    // Operations
    suspend fun deposit(locationId: UUID?, amount: BigDecimal, notes: String? = null)
    suspend fun withdraw(locationId: UUID?, amount: BigDecimal, notes: String? = null)
    suspend fun payment(locationId: UUID?, amount: BigDecimal, categoryId: UUID?, notes: String? = null)

    // Update operations
    suspend fun getOperationById(id: UUID): CashOperation?
    suspend fun updateOperation(id: UUID, amount: BigDecimal, categoryId: UUID?, notes: String?)
}
