package com.zagot.zagotplus.domain.repository

import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionFilter
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.UUID

/**
 * Input data for creating a sale transaction.
 */
data class SaleInput(
    val locationId: UUID,
    val productId: UUID,
    val weightKg: BigDecimal,
    val pricePerKg: BigDecimal,
    val notes: String? = null
)

/**
 * Repository interface for Transaction domain model.
 */
interface TransactionRepository {

    /**
     * Get paginated transactions as reactive Flow.
     * @param limit Number of transactions per page
     * @param offset Number of transactions to skip
     */
    fun getPaginatedTransactions(limit: Int, offset: Int): Flow<List<Transaction>>

    /**
     * Get total transaction count.
     */
    fun getTotalTransactionCount(): Flow<Int>

    /**
     * Get transactions for a specific location.
     */
    fun getTransactionsByLocation(locationId: UUID): Flow<List<Transaction>>

    /**
     * Create a purchase transaction.
     *
     * @param locationId Location where purchase occurred
     * @param productId Product being purchased
     * @param weightKg Weight in kilograms
     * @param pricePerKg Price per kilogram
     * @param notes Optional notes
     * @return Created transaction
     */
    suspend fun createPurchase(
        locationId: UUID,
        productId: UUID,
        weightKg: BigDecimal,
        pricePerKg: BigDecimal,
        notes: String? = null
    ): Transaction

    /**
     * Create a sale transaction.
     *
     * @param locationId Location where sale occurred
     * @param productId Product being sold
     * @param weightKg Weight in kilograms
     * @param pricePerKg Price per kilogram
     * @param notes Optional notes
     * @return Created transaction
     */
    suspend fun createSale(
        locationId: UUID,
        productId: UUID,
        weightKg: BigDecimal,
        pricePerKg: BigDecimal,
        notes: String? = null
    ): Transaction

    /**
     * Create a transfer between locations.
     * Atomically creates two linked transactions: transfer_out and transfer_in.
     *
     * @param fromLocationId Source location
     * @param toLocationId Destination location
     * @param productId Product being transferred
     * @param weightKg Weight in kilograms
     * @return Pair of created transactions (out, in)
     */
    suspend fun createTransfer(
        fromLocationId: UUID,
        toLocationId: UUID,
        productId: UUID,
        weightKg: BigDecimal
    ): Pair<Transaction, Transaction>

    /**
     * Get computed inventory for all locations and products.
     */
    fun getInventory(): Flow<List<InventoryItem>>

    /**
     * Get computed inventory for a specific location.
     */
    fun getInventoryByLocation(locationId: UUID): Flow<List<InventoryItem>>

    /**
     * Get average purchase price per product (for profit calculation).
     * Returns a map of productId to average purchase price per kg.
     */
    fun getProductAvgPurchasePrices(): Flow<Map<UUID, BigDecimal>>

    /**
     * Observe latest server_updated_at to detect content changes (reactive).
     */
    fun observeLatestUpdate(): Flow<Long?>

    /**
     * Get filtered and paginated transactions.
     * @param filter Filter criteria
     * @param limit Number of transactions per page
     * @param offset Number of transactions to skip
     */
    suspend fun getFilteredTransactions(
        filter: TransactionFilter,
        limit: Int,
        offset: Int
    ): List<Transaction>

    /**
     * Get count of transactions matching filter criteria.
     */
    suspend fun getFilteredTransactionCount(filter: TransactionFilter): Int

    /**
     * Create multiple sale transactions atomically.
     * All sales succeed or all fail together.
     *
     * @param sales List of sale inputs
     * @return List of created transactions
     */
    suspend fun createSales(sales: List<SaleInput>): List<Transaction>

    /**
     * Get transactions by their IDs.
     *
     * @param ids List of transaction IDs
     * @return List of transactions matching the IDs
     */
    suspend fun getTransactionsByIds(ids: List<UUID>): List<Transaction>

    /**
     * Create an inventory adjustment transaction.
     * Used during inventorization to correct discrepancies between recorded and actual inventory.
     *
     * @param locationId Location where adjustment is made
     * @param productId Product being adjusted
     * @param adjustmentKg Weight difference (positive = add to inventory, negative = remove from inventory)
     * @param pricePerKg Current default price per kg (for profit calculation)
     * @param reason Optional reason for adjustment
     * @param notes Optional additional notes
     * @return Created adjustment transaction
     */
    suspend fun createAdjustment(
        locationId: UUID,
        productId: UUID,
        adjustmentKg: BigDecimal,
        pricePerKg: BigDecimal? = null,
        reason: String? = null,
        notes: String? = null
    ): Transaction
}
