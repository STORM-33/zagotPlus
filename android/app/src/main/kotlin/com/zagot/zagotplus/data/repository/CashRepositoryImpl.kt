package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.entity.CashOperationEntity
import com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity
import com.zagot.zagotplus.data.preferences.DevicePreferences
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
    private val cashOperationDao: CashOperationDao,
    private val expenseCategoryDao: ExpenseCategoryDao,
    private val devicePreferences: DevicePreferences
) : CashRepository {

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

    override fun getAllOperations(): Flow<List<CashOperation>> =
        cashOperationDao.getAllOperations().map { entities ->
            enrichWithCategories(entities)
        }

    override fun getRecentOperationsGlobal(limit: Int): Flow<List<CashOperation>> =
        cashOperationDao.getRecentOperations(limit).map { entities ->
            enrichWithCategories(entities)
        }

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
            transactionId = null,
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
            transactionId = null,
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
            transactionId = null,
            notes = notes,
            deviceId = devicePreferences.getDeviceId(),
            createdAt = now,
            syncedAt = null
        )
        cashOperationDao.insert(entity)
    }

    override suspend fun recordPurchasePayment(locationId: UUID, amount: BigDecimal, transactionId: UUID) {
        val now = Instant.now()
        val entity = CashOperationEntity(
            id = UUID.randomUUID(),
            localId = UUID.randomUUID().toString(),
            locationId = locationId,
            type = CashOperationType.PURCHASE.toDbValue(),
            amount = amount,
            categoryId = null,
            transactionId = transactionId,
            notes = null,
            deviceId = devicePreferences.getDeviceId(),
            createdAt = now,
            syncedAt = null
        )
        cashOperationDao.insert(entity)
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
        transactionId = transactionId,
        notes = notes,
        deviceId = deviceId,
        createdAt = createdAt,
        syncedAt = syncedAt
    )
}
