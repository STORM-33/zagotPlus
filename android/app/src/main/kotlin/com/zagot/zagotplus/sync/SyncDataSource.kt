package com.zagot.zagotplus.sync

import com.zagot.zagotplus.data.remote.dto.LocationDto
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import java.time.Instant

/**
 * Interface for remote sync operations.
 * Abstracts Supabase calls to enable unit testing without mocking Supabase internals.
 */
interface SyncDataSource {

    /**
     * Push a transaction to the remote server.
     * @throws Exception on network or server error
     */
    suspend fun pushTransaction(dto: TransactionDto)

    /**
     * Push a purchase batch to the remote server.
     * @throws Exception on network or server error
     */
    suspend fun pushBatch(dto: PurchaseBatchDto)

    /**
     * Push a product to the remote server.
     * @throws Exception on network or server error
     */
    suspend fun pushProduct(dto: ProductDto)

    /**
     * Delete a product from the remote server.
     * @throws Exception on network or server error
     */
    suspend fun deleteProduct(id: String)

    /**
     * Pull transactions created after the given timestamp.
     */
    suspend fun pullTransactions(since: Instant): List<TransactionDto>

    /**
     * Pull purchase batches created after the given timestamp.
     */
    suspend fun pullBatches(since: Instant): List<PurchaseBatchDto>

    /**
     * Pull all locations (reference data).
     */
    suspend fun pullLocations(): List<LocationDto>

    /**
     * Pull all products (reference data).
     */
    suspend fun pullProducts(): List<ProductDto>
}
