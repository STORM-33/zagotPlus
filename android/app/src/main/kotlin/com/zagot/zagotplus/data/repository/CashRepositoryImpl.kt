package com.zagot.zagotplus.data.repository

import android.util.Log
import androidx.room.withTransaction
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.entity.CashHistoryProjection
import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.CashHistoryItem
import com.zagot.zagotplus.domain.model.CashHistoryItemType
import com.zagot.zagotplus.domain.model.CashOperation
import com.zagot.zagotplus.domain.model.CashOperationType
import com.zagot.zagotplus.domain.model.ExpenseCategory
import com.zagot.zagotplus.domain.repository.CashRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CashRepositoryImpl @Inject constructor(
    private val database: ZagotDatabase,
    private val cashOperationDao: CashOperationDao,
    private val expenseCategoryDao: ExpenseCategoryDao,
    private val devicePreferences: DevicePreferences
) : CashRepository {

    companion object {
        private const val TAG = "CashRepositoryImpl"
    }

    // ========== Expense Categories ==========

    override fun getActiveCategories(): Flow<List<ExpenseCategory>> =
        expenseCategoryDao.getActiveCategories().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getAllCategories(): Flow<List<ExpenseCategory>> =
        expenseCategoryDao.getAllCategories().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun createCategory(name: String): ExpenseCategory {
        val now = Instant.now()
        val entity = ExpenseCategoryEntity(
            id = UUID.randomUUID(),
            localId = UUID.randomUUID().toString(),
            name = name,
            isActive = true,
            createdAt = now,
            syncedAt = null
        )
        expenseCategoryDao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun updateCategory(id: UUID, name: String) {
        val existing = expenseCategoryDao.getById(id) ?: return
        expenseCategoryDao.update(existing.copy(name = name))
    }

    override suspend fun deactivateCategory(id: UUID) {
        expenseCategoryDao.deactivate(id)
    }

    // ========== Cash Operations ==========

    override fun getOperationsByLocation(locationId: UUID): Flow<List<CashOperation>> =
        cashOperationDao.getByLocation(locationId).map { entities ->
            enrichWithCategories(entities)
        }

    override fun getOperationsByLocationAndDate(locationId: UUID, date: LocalDate): Flow<List<CashOperation>> {
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        
        return cashOperationDao.getByLocationAndDate(locationId, startOfDay, endOfDay).map { entities ->
            enrichWithCategories(entities)
        }
    }

    override fun getRecentOperations(locationId: UUID, limit: Int): Flow<List<CashOperation>> =
        cashOperationDao.getRecentByLocation(locationId, limit).map { entities ->
            enrichWithCategories(entities)
        }

    // ========== Global Cash Operations ==========

    override fun getRecentOperationsGlobal(limit: Int): Flow<List<CashOperation>> =
        cashOperationDao.getRecentOperations(limit).map { entities ->
            enrichWithCategories(entities)
        }

    override suspend fun getOperationsPaged(limit: Int, offset: Int): List<CashOperation> {
        val entities = cashOperationDao.getOperationsPaged(limit, offset)
        return enrichWithCategories(entities)
    }

    override suspend fun getTotalOperationsCount(): Int =
        cashOperationDao.getTotalOperationsCount()

    // ========== Cash History (Unified) ==========

    override suspend fun getCashHistoryPaged(limit: Int, offset: Int): List<CashHistoryItem> {
        val projections = cashOperationDao.getCashHistoryPaged(limit, offset)
        return projections.map { it.toDomain() }
    }

    override suspend fun getCashHistoryByLocationPaged(locationId: UUID, limit: Int, offset: Int): List<CashHistoryItem> {
        val projections = cashOperationDao.getCashHistoryByLocationPaged(locationId, limit, offset)
        return projections.map { it.toDomain() }
    }

    override suspend fun getTotalHistoryCount(): Int =
        cashOperationDao.getTotalHistoryCount()

    override suspend fun getTotalHistoryCountByLocation(locationId: UUID): Int =
        cashOperationDao.getTotalHistoryCountByLocation(locationId)

    // ========== Balance ==========

    override fun getTotalBalance(): Flow<BigDecimal> =
        cashOperationDao.getTotalBalance()

    override fun getDailyChangeGlobal(date: LocalDate): Flow<BigDecimal> {
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        
        return cashOperationDao.getDailyBalanceChange(startOfDay, endOfDay)
    }

    override fun getBalance(locationId: UUID): Flow<BigDecimal> =
        cashOperationDao.getBalanceByLocation(locationId)

    override fun getDailyChange(locationId: UUID, date: LocalDate): Flow<BigDecimal> {
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        
        return cashOperationDao.getDailyBalanceChange(locationId, startOfDay, endOfDay)
    }

    override suspend fun deposit(locationId: UUID?, amount: BigDecimal, notes: String?) {
        val now = Instant.now()
        val entity = CashOperationEntity(
            id = UUID.randomUUID(),
            localId = UUID.randomUUID().toString(),
            locationId = locationId,
            type = CashOperationType.DEPOSIT.toDbValue(),
            amount = amount,
            categoryId = null,
            batchId = null,
            notes = notes,
            deviceId = devicePreferences.getDeviceId(),
            createdAt = now,
            syncedAt = null
        )
        cashOperationDao.insert(entity)
    }

    override suspend fun withdraw(locationId: UUID?, amount: BigDecimal, notes: String?) {
        val now = Instant.now()
        val entity = CashOperationEntity(
            id = UUID.randomUUID(),
            localId = UUID.randomUUID().toString(),
            locationId = locationId,
            type = CashOperationType.WITHDRAWAL.toDbValue(),
            amount = amount,
            categoryId = null,
            batchId = null,
            notes = notes,
            deviceId = devicePreferences.getDeviceId(),
            createdAt = now,
            syncedAt = null
        )
        cashOperationDao.insert(entity)
    }

    override suspend fun payment(locationId: UUID?, amount: BigDecimal, categoryId: UUID?, notes: String?) {
        val now = Instant.now()
        val entity = CashOperationEntity(
            id = UUID.randomUUID(),
            localId = UUID.randomUUID().toString(),
            locationId = locationId,
            type = CashOperationType.PAYMENT.toDbValue(),
            amount = amount,
            categoryId = categoryId,
            batchId = null,
            notes = notes,
            deviceId = devicePreferences.getDeviceId(),
            createdAt = now,
            syncedAt = null
        )
        cashOperationDao.insert(entity)
    }
    
    override suspend fun transfer(sourceLocationId: UUID, destinationLocationId: UUID, amount: BigDecimal, notes: String?) {
        val now = Instant.now()
        val deviceId = devicePreferences.getDeviceId()
        val transferNote = notes?.let { "Переказ: $it" } ?: "Переказ"
        val transferPairId = UUID.randomUUID().toString()
        
        // Withdrawal from source (marked as transfer)
        val withdrawalEntity = CashOperationEntity(
            id = UUID.randomUUID(),
            localId = UUID.randomUUID().toString(),
            locationId = sourceLocationId,
            type = CashOperationType.WITHDRAWAL.toDbValue(),
            amount = amount,
            categoryId = null,
            batchId = null,
            notes = transferNote,
            deviceId = deviceId,
            createdAt = now,
            syncedAt = null,
            isTransfer = true,
            transferPairId = transferPairId
        )
        
        // Deposit to destination (marked as transfer)
        val depositEntity = CashOperationEntity(
            id = UUID.randomUUID(),
            localId = UUID.randomUUID().toString(),
            locationId = destinationLocationId,
            type = CashOperationType.DEPOSIT.toDbValue(),
            amount = amount,
            categoryId = null,
            batchId = null,
            notes = transferNote,
            deviceId = deviceId,
            createdAt = now,
            syncedAt = null,
            isTransfer = true,
            transferPairId = transferPairId
        )
        
        database.withTransaction {
            cashOperationDao.insert(withdrawalEntity)
            cashOperationDao.insert(depositEntity)
        }
    }
    
    override fun getDailyDeposits(locationId: UUID, date: LocalDate): Flow<BigDecimal> {
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        
        return cashOperationDao.getDailyDeposits(locationId, startOfDay, endOfDay)
    }
    
    override fun getDailyDepositsGlobal(date: LocalDate): Flow<BigDecimal> {
        val zone = ZoneId.systemDefault()
        val startOfDay = date.atStartOfDay(zone).toInstant()
        val endOfDay = date.plusDays(1).atStartOfDay(zone).toInstant()
        
        return cashOperationDao.getDailyDepositsGlobal(startOfDay, endOfDay)
    }

    // ========== Update ==========

    override suspend fun getOperationById(id: UUID): CashOperation? {
        val entity = cashOperationDao.getById(id) ?: return null
        val categoryName = entity.categoryId?.let { catId ->
            expenseCategoryDao.getById(catId)?.name
        }
        return entity.toDomain(categoryName)
    }

    override suspend fun updateOperation(id: UUID, amount: BigDecimal, categoryId: UUID?, notes: String?) {
        val existing = cashOperationDao.getById(id) ?: return
        val updated = existing.copy(
            amount = amount,
            categoryId = categoryId,
            notes = notes,
            syncedAt = null // Mark for re-sync
        )
        cashOperationDao.update(updated)
    }

    // ========== Helpers ==========

    private suspend fun enrichWithCategories(entities: List<CashOperationEntity>): List<CashOperation> {
        val categories = expenseCategoryDao.getAllCategories().first()
        val categoryMap = categories.associateBy { it.id }
        
        return entities.map { entity ->
            entity.toDomain(categoryMap[entity.categoryId]?.name)
        }
    }

    private fun ExpenseCategoryEntity.toDomain() = ExpenseCategory(
        id = id,
        localId = localId,
        name = name,
        isActive = isActive,
        createdAt = createdAt,
        syncedAt = syncedAt
    )

    private fun CashOperationEntity.toDomain(categoryName: String? = null) = CashOperation(
        id = id,
        localId = localId,
        locationId = locationId,
        type = CashOperationType.fromString(type),
        amount = amount,
        categoryId = categoryId,
        categoryName = categoryName,
        notes = notes,
        deviceId = deviceId,
        createdAt = createdAt,
        syncedAt = syncedAt
    )

    private fun CashHistoryProjection.toDomain(): CashHistoryItem {
        // Use is_transfer flag from database, fallback to notes pattern for legacy data
        val isTransferOperation = isTransfer == true || 
            (isTransfer == null && notes?.startsWith("Переказ") == true && (type == "deposit" || type == "withdrawal"))
        val isIncomingTransfer = isTransferOperation && type == "deposit"
        
        return CashHistoryItem(
            id = id,
            type = when {
                // Detect transfers via is_transfer flag or fallback to notes pattern
                isTransferOperation -> CashHistoryItemType.TRANSFER
                type == "deposit" -> CashHistoryItemType.DEPOSIT
                type == "withdrawal" -> CashHistoryItemType.WITHDRAWAL
                type == "payment" -> CashHistoryItemType.PAYMENT
                type == "purchase" -> CashHistoryItemType.PURCHASE
                type == "sale" -> CashHistoryItemType.SALE
                type == "transfer" -> CashHistoryItemType.TRANSFER
                else -> throw IllegalArgumentException("Unknown cash history type: $type")
            },
            amount = amount,
            notes = notes,
            categoryName = categoryName,
            itemCount = itemCount,
            weightKg = weightKg,
            createdAt = createdAt,
            batchCount = batchCount,
            locationId = locationId?.let { UUID.fromString(it) },
            locationName = locationName,
            isTransferIn = isIncomingTransfer
        )
    }
}
