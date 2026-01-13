package com.zagot.zagotplus.data.repository

import androidx.room.withTransaction
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.dao.SaleBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.SaleBatchEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.domain.model.SaleBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.SaleBatchRepository
import com.zagot.zagotplus.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of SaleBatchRepository using Room as data source.
 */
@Singleton
class SaleBatchRepositoryImpl @Inject constructor(
    private val database: ZagotDatabase,
    private val saleBatchDao: SaleBatchDao,
    private val transactionDao: TransactionDao,
    private val syncManager: SyncManager
) : SaleBatchRepository {

    override fun observeAll(): Flow<List<SaleBatch>> =
        saleBatchDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeTodaysBatches(): Flow<List<SaleBatch>> {
        val (startMillis, endMillis) = getTodayRange()
        return saleBatchDao.observeBatchesInRange(startMillis, endMillis).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTodaysBatches(): List<SaleBatch> {
        val (startMillis, endMillis) = getTodayRange()
        return saleBatchDao.getBatchesInRange(startMillis, endMillis).map { it.toDomain() }
    }
    
    /**
     * Get start and end timestamps for today in device timezone.
     * Returns pair of (startOfDay, startOfTomorrow) in epoch milliseconds.
     */
    private fun getTodayRange(): Pair<Long, Long> {
        val zone = ZoneId.systemDefault()
        val today = LocalDate.now(zone)
        val startOfDay = today.atStartOfDay(zone).toInstant().toEpochMilli()
        val startOfTomorrow = today.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        return Pair(startOfDay, startOfTomorrow)
    }

    override suspend fun getById(id: UUID): SaleBatch? =
        saleBatchDao.getById(id)?.toDomain()

    override suspend fun getByLocalId(localId: String): SaleBatch? =
        saleBatchDao.getByLocalId(localId)?.toDomain()

    override suspend fun createBatchWithTransactions(
        batch: SaleBatch,
        transactions: List<Transaction>
    ) {
        database.withTransaction {
            // Insert batch
            saleBatchDao.insert(batch.toEntity())
            
            // Insert transactions with sale_batch_id set
            val transactionEntities = transactions.map { it.toEntity(batch.id) }
            transactionDao.insertAll(transactionEntities)
        }
        syncManager.triggerManualSync()
    }

    override suspend fun getUnsynced(): List<SaleBatch> =
        saleBatchDao.getUnsynced().map { it.toDomain() }

    override suspend fun markSynced(id: UUID) {
        saleBatchDao.markSynced(id, Instant.now())
    }

    override suspend fun delete(id: UUID) {
        saleBatchDao.delete(id)
    }

    override suspend fun getTransactionsForBatch(batchId: UUID): List<Transaction> =
        transactionDao.getBySaleBatchId(batchId).map { it.toDomain() }

    override suspend fun getAllBatchesPaginated(limit: Int, offset: Int): List<SaleBatch> =
        saleBatchDao.getAllPaginated(limit, offset).map { it.toDomain() }

    override suspend fun getTotalBatchCount(): Int =
        saleBatchDao.getTotalCount()

    override fun observeTotalBatchCount(): Flow<Int> =
        saleBatchDao.observeTotalCount()

    private fun SaleBatchEntity.toDomain() = SaleBatch(
        id = id,
        localId = localId,
        locationId = locationId,
        notes = notes,
        totalWeightKg = totalWeightKg,
        totalAmount = totalAmount,
        itemCount = itemCount,
        deviceId = deviceId,
        createdAt = createdAt,
        syncedAt = syncedAt
    )

    private fun SaleBatch.toEntity() = SaleBatchEntity(
        id = id,
        localId = localId,
        locationId = locationId,
        notes = notes,
        totalWeightKg = totalWeightKg,
        totalAmount = totalAmount,
        itemCount = itemCount,
        deviceId = deviceId,
        createdAt = createdAt,
        syncedAt = syncedAt
    )

    private fun Transaction.toEntity(saleBatchId: UUID) = TransactionEntity(
        id = id,
        localId = localId,
        locationId = locationId,
        type = type.toDbValue(),
        transferLocationId = transferLocationId,
        productId = productId,
        weightKg = weightKg,
        pricePerKg = pricePerKg,
        totalAmount = totalAmount,
        notes = notes,
        deviceId = deviceId,
        createdAt = createdAt,
        syncedAt = syncedAt,
        batchId = null,
        saleBatchId = saleBatchId
    )

    private fun TransactionEntity.toDomain() = Transaction(
        id = id,
        localId = localId,
        locationId = locationId,
        type = TransactionType.fromDbValue(type),
        transferLocationId = transferLocationId,
        productId = productId,
        weightKg = weightKg,
        pricePerKg = pricePerKg,
        totalAmount = totalAmount,
        notes = notes,
        deviceId = deviceId,
        createdAt = createdAt,
        syncedAt = syncedAt,
        batchId = batchId,
        saleBatchId = saleBatchId
    )
}
