package com.zagot.zagotplus.data.repository

import androidx.room.withTransaction
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.PurchaseBatchEntity
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.repository.PurchaseBatchRepository
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
 * Implementation of PurchaseBatchRepository using Room as data source.
 */
@Singleton
class PurchaseBatchRepositoryImpl @Inject constructor(
    private val database: ZagotDatabase,
    private val purchaseBatchDao: PurchaseBatchDao,
    private val transactionDao: TransactionDao,
    private val syncManager: SyncManager
) : PurchaseBatchRepository {

    override fun observeAll(): Flow<List<PurchaseBatch>> =
        purchaseBatchDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeTodaysBatches(): Flow<List<PurchaseBatch>> {
        val (startMillis, endMillis) = getTodayRange()
        return purchaseBatchDao.observeBatchesInRange(startMillis, endMillis).map { entities ->
            entities.map { it.toDomain() }
        }
    }

    override suspend fun getTodaysBatches(): List<PurchaseBatch> {
        val (startMillis, endMillis) = getTodayRange()
        return purchaseBatchDao.getBatchesInRange(startMillis, endMillis).map { it.toDomain() }
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

    override suspend fun getById(id: UUID): PurchaseBatch? =
        purchaseBatchDao.getById(id)?.toDomain()

    override suspend fun getByLocalId(localId: String): PurchaseBatch? =
        purchaseBatchDao.getByLocalId(localId)?.toDomain()

    override suspend fun createBatchWithTransactions(
        batch: PurchaseBatch,
        transactions: List<Transaction>
    ) {
        database.withTransaction {
            // Insert batch
            purchaseBatchDao.insert(batch.toEntity())
            
            // Insert transactions with batch_id set
            val transactionEntities = transactions.map { it.toEntity(batch.id) }
            transactionDao.insertAll(transactionEntities)
        }
        syncManager.triggerManualSync()
    }

    override suspend fun getUnsynced(): List<PurchaseBatch> =
        purchaseBatchDao.getUnsynced().map { it.toDomain() }

    override suspend fun markSynced(id: UUID) {
        purchaseBatchDao.markSynced(id, Instant.now())
    }

    override suspend fun delete(id: UUID) {
        purchaseBatchDao.delete(id)
    }

    private fun PurchaseBatchEntity.toDomain() = PurchaseBatch(
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

    private fun PurchaseBatch.toEntity() = PurchaseBatchEntity(
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

    private fun Transaction.toEntity(batchId: UUID) = TransactionEntity(
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
        batchId = batchId
    )
}
