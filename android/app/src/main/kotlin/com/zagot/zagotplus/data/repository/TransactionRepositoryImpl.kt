package com.zagot.zagotplus.data.repository

import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.TransactionRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TransactionRepository using Room as data source.
 */
@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val transactionDao: TransactionDao,
    private val devicePreferences: DevicePreferences
) : TransactionRepository {

    override fun getAllTransactions(): Flow<List<Transaction>> =
        transactionDao.getAllFlow().map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getPaginatedTransactions(limit: Int, offset: Int): Flow<List<Transaction>> =
        transactionDao.getAllPaginatedFlow(limit, offset).map { entities ->
            entities.map { it.toDomain() }
        }

    override fun getTotalTransactionCount(): Flow<Int> =
        transactionDao.getTotalCountFlow()

    override fun getTransactionsByLocation(locationId: UUID): Flow<List<Transaction>> =
        transactionDao.getByLocationFlow(locationId).map { entities ->
            entities.map { it.toDomain() }
        }

    override suspend fun createPurchase(
        locationId: UUID,
        productId: UUID,
        weightKg: BigDecimal,
        pricePerKg: BigDecimal,
        notes: String?
    ): Transaction {
        val totalAmount = weightKg * pricePerKg
        val entity = TransactionEntity(
            id = UUID.randomUUID(),
            localId = UUID.randomUUID().toString(),
            locationId = locationId,
            type = TransactionType.PURCHASE.toDbValue(),
            transferLocationId = null,
            productId = productId,
            weightKg = weightKg,
            pricePerKg = pricePerKg,
            totalAmount = totalAmount,
            notes = notes?.ifBlank { null },
            deviceId = devicePreferences.getDeviceId(),
            createdAt = Instant.now(),
            syncedAt = null
        )
        transactionDao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun createSale(
        locationId: UUID,
        productId: UUID,
        weightKg: BigDecimal,
        pricePerKg: BigDecimal,
        notes: String?
    ): Transaction {
        val totalAmount = weightKg * pricePerKg
        val entity = TransactionEntity(
            id = UUID.randomUUID(),
            localId = UUID.randomUUID().toString(),
            locationId = locationId,
            type = TransactionType.SALE.toDbValue(),
            transferLocationId = null,
            productId = productId,
            weightKg = -weightKg, // Sales are negative
            pricePerKg = pricePerKg,
            totalAmount = totalAmount,
            notes = notes?.ifBlank { null },
            deviceId = devicePreferences.getDeviceId(),
            createdAt = Instant.now(),
            syncedAt = null
        )
        transactionDao.insert(entity)
        return entity.toDomain()
    }

    override suspend fun createTransfer(
        fromLocationId: UUID,
        toLocationId: UUID,
        productId: UUID,
        weightKg: BigDecimal
    ): Pair<Transaction, Transaction> {
        val transferId = UUID.randomUUID().toString()
        val now = Instant.now()
        val deviceId = devicePreferences.getDeviceId()

        val outEntity = TransactionEntity(
            id = UUID.randomUUID(),
            localId = "$transferId-out",
            locationId = fromLocationId,
            type = TransactionType.TRANSFER_OUT.toDbValue(),
            transferLocationId = toLocationId,
            productId = productId,
            weightKg = -weightKg,
            pricePerKg = null,
            totalAmount = null,
            notes = null,
            deviceId = deviceId,
            createdAt = now,
            syncedAt = null
        )

        val inEntity = TransactionEntity(
            id = UUID.randomUUID(),
            localId = "$transferId-in",
            locationId = toLocationId,
            type = TransactionType.TRANSFER_IN.toDbValue(),
            transferLocationId = fromLocationId,
            productId = productId,
            weightKg = weightKg,
            pricePerKg = null,
            totalAmount = null,
            notes = null,
            deviceId = deviceId,
            createdAt = now,
            syncedAt = null
        )

        transactionDao.insertAll(listOf(outEntity, inEntity))
        return outEntity.toDomain() to inEntity.toDomain()
    }

    override fun getInventory(): Flow<List<InventoryItem>> =
        transactionDao.getAllFlow().map { transactions ->
            computeInventory(transactions)
        }

    override fun getInventoryByLocation(locationId: UUID): Flow<List<InventoryItem>> =
        transactionDao.getByLocationFlow(locationId).map { transactions ->
            computeInventory(transactions)
        }

    private fun computeInventory(transactions: List<TransactionEntity>): List<InventoryItem> {
        return transactions
            .filter { it.locationId != null && it.productId != null }
            .groupBy { it.locationId!! to it.productId!! }
            .map { (key, txns) ->
                val (locationId, productId) = key
                val totalWeight = txns.sumOf { it.weightKg }
                InventoryItem(
                    locationId = locationId,
                    productId = productId,
                    totalWeightKg = totalWeight
                )
            }
    }

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
        syncedAt = syncedAt
    )
}
