package com.zagot.zagotplus.domain.repository

import com.zagot.zagotplus.domain.model.InventoryItem
import com.zagot.zagotplus.domain.model.Transaction
import kotlinx.coroutines.flow.Flow
import java.math.BigDecimal
import java.util.UUID

/**
 * Repository interface for Transaction domain model.
 */
interface TransactionRepository {

    /**
     * Get all transactions as reactive Flow.
     */
    fun getAllTransactions(): Flow<List<Transaction>>

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
}
