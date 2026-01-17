package com.zagot.zagotplus.data.repository

import androidx.room.withTransaction
import com.zagot.zagotplus.data.local.ZagotDatabase
import com.zagot.zagotplus.data.local.dao.InventoryAggregateResult
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.local.dao.TransactionQueryBuilder
import com.zagot.zagotplus.data.local.entity.TransactionEntity
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionFilter
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.SaleInput
import com.zagot.zagotplus.domain.repository.TransactionRepository
import com.zagot.zagotplus.sync.SyncManager
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Implementation of TransactionRepository using Room as data source.
 *
 * Weight sign convention:
 * - Purchases and transfer_in: POSITIVE weights (add to inventory)
 * - Sales and transfer_out: NEGATIVE weights (subtract from inventory)
 *
 * This allows inventory computation via simple SUM(weight_kg).
 */
@Singleton
class TransactionRepositoryImpl @Inject constructor(
    private val database: ZagotDatabase,
    private val transactionDao: TransactionDao,
    private val devicePreferences: DevicePreferences,
    private val syncManager: SyncManager
) : TransactionRepository {

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
        require(weightKg > BigDecimal.ZERO) { "Weight must be positive" }
        require(pricePerKg >= BigDecimal.ZERO) { "Price must be non-negative" }
        
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
        syncManager.triggerManualSync()
        return entity.toDomain()
    }

    override suspend fun createSale(
        locationId: UUID,
        productId: UUID,
        weightKg: BigDecimal,
        pricePerKg: BigDecimal,
        notes: String?
    ): Transaction {
        require(weightKg > BigDecimal.ZERO) { "Weight must be positive" }
        require(pricePerKg >= BigDecimal.ZERO) { "Price must be non-negative" }
        
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
        syncManager.triggerManualSync()
        return entity.toDomain()
    }

    override suspend fun createTransfer(
        fromLocationId: UUID,
        toLocationId: UUID,
        productId: UUID,
        weightKg: BigDecimal
    ): Pair<Transaction, Transaction> {
        require(weightKg > BigDecimal.ZERO) { "Weight must be positive" }
        require(fromLocationId != toLocationId) { "Cannot transfer to the same location" }
        
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

        // Atomic transaction: both must succeed or both fail
        database.withTransaction {
            transactionDao.insert(outEntity)
            transactionDao.insert(inEntity)
        }
        syncManager.triggerManualSync()
        return outEntity.toDomain() to inEntity.toDomain()
    }

    override fun getInventory():Flow<List<InventoryItem>> =
        transactionDao.getInventoryAggregatedFlow().map { results ->
            results.map { it.toInventoryItem() }
        }

    override fun getInventoryByLocation(locationId: UUID): Flow<List<InventoryItem>> =
        transactionDao.getInventoryByLocationAggregatedFlow(locationId).map { results ->
            results.map { it.toInventoryItem() }
        }

    override fun getProductAvgPurchasePrices(): Flow<Map<UUID, BigDecimal>> =
        transactionDao.observeProductAvgPurchasePrices().map { results ->
            results.mapNotNull { result ->
                val productId = try { UUID.fromString(result.productId) } catch (_: Exception) { return@mapNotNull null }
                val avgPrice = result.avgPricePerKg?.let { 
                    BigDecimal(it).setScale(2, java.math.RoundingMode.HALF_UP) 
                } ?: return@mapNotNull null
                productId to avgPrice
            }.toMap()
        }

    private fun InventoryAggregateResult.toInventoryItem() = InventoryItem(
        locationId = UUID.fromString(locationId),
        productId = UUID.fromString(productId),
        totalWeightKg = BigDecimal(totalWeightKg)
    )

    override suspend fun getFilteredTransactions(
        filter: TransactionFilter,
        limit: Int,
        offset: Int
    ): List<Transaction> {
        val query = TransactionQueryBuilder()
            .withTypes(filter.types.map { it.toDbValue() })
            .withLocation(filter.locationId)
            .withDateRange(filter.startDate, filter.endDate)
            .withProductNameSearch(filter.productNameSearch)
            .withPagination(limit, offset)
            .build()

        return transactionDao.getFiltered(query).map { it.toDomain() }
    }

    override suspend fun getFilteredTransactionCount(filter: TransactionFilter): Int {
        val query = TransactionQueryBuilder()
            .withTypes(filter.types.map { it.toDbValue() })
            .withLocation(filter.locationId)
            .withDateRange(filter.startDate, filter.endDate)
            .withProductNameSearch(filter.productNameSearch)
            .buildCount()

        return transactionDao.getFilteredCount(query)
    }

    override suspend fun createSales(
        sales: List<SaleInput>
    ): List<Transaction> {
        require(sales.isNotEmpty()) { "Sales list cannot be empty" }
        sales.forEach { sale ->
            require(sale.weightKg > BigDecimal.ZERO) { "Weight must be positive" }
            require(sale.pricePerKg >= BigDecimal.ZERO) { "Price must be non-negative" }
        }

        val now = Instant.now()
        val deviceId = devicePreferences.getDeviceId()

        val entities = sales.map { sale ->
            val totalAmount = sale.weightKg * sale.pricePerKg
            TransactionEntity(
                id = UUID.randomUUID(),
                localId = UUID.randomUUID().toString(),
                locationId = sale.locationId,
                type = TransactionType.SALE.toDbValue(),
                transferLocationId = null,
                productId = sale.productId,
                weightKg = -sale.weightKg, // Sales are negative
                pricePerKg = sale.pricePerKg,
                totalAmount = totalAmount,
                notes = sale.notes?.ifBlank { null },
                deviceId = deviceId,
                createdAt = now,
                syncedAt = null
            )
        }

        // Atomic transaction: all sales must succeed or all fail
        database.withTransaction {
            entities.forEach { entity ->
                transactionDao.insert(entity)
            }
        }
        syncManager.triggerManualSync()

        return entities.map { it.toDomain() }
    }

    override suspend fun getTransactionsByIds(ids: List<UUID>): List<Transaction> =
        transactionDao.getByIds(ids).map { it.toDomain() }

    override suspend fun createAdjustment(
        locationId: UUID,
        productId: UUID,
        adjustmentKg: BigDecimal,
        pricePerKg: BigDecimal?,
        reason: String?,
        notes: String?
    ): Transaction {
        require(adjustmentKg != BigDecimal.ZERO) { "Adjustment cannot be zero" }
        
        // Build notes with reason prefix if provided
        val fullNotes = buildString {
            reason?.let { append("[$it] ") }
            notes?.let { append(it) }
        }.ifBlank { null }

        // Calculate total amount for profit tracking
        val totalAmount = pricePerKg?.let { 
            adjustmentKg.abs().multiply(it).setScale(2, java.math.RoundingMode.HALF_UP)
        }

        val entity = TransactionEntity(
            id = UUID.randomUUID(),
            localId = UUID.randomUUID().toString(),
            locationId = locationId,
            type = TransactionType.ADJUSTMENT.toDbValue(),
            transferLocationId = null,
            productId = productId,
            weightKg = adjustmentKg, // Already signed (positive or negative)
            pricePerKg = pricePerKg,
            totalAmount = totalAmount,
            notes = fullNotes,
            deviceId = devicePreferences.getDeviceId(),
            createdAt = Instant.now(),
            syncedAt = null
        )
        transactionDao.insert(entity)
        syncManager.triggerManualSync()
        return entity.toDomain()
    }

    private fun TransactionEntity.toDomain()= Transaction(
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
