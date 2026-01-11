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
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.Instant
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
    private val transactionDao: TransactionDao
) : PurchaseBatchRepository {

    override fun observeAll(): Flow<List<PurchaseBatch>> =
        purchaseBatchDao.observeAll().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun observeTodaysBatches(): Flow<List<PurchaseBatch>> =
        purchaseBatchDao.observeTodaysBatches().map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun getTodaysBatches(): List<PurchaseBatch> =
        purchaseBatchDao.getTodaysBatches().map { it.toDomain() }

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
