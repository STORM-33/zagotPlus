package com.zagot.zagotplus.sync

import com.zagot.zagotplus.data.remote.dto.CashOperationDto
import com.zagot.zagotplus.data.remote.dto.ExpenseCategoryDto
import com.zagot.zagotplus.data.remote.dto.LocationDto
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import com.zagot.zagotplus.data.remote.dto.SaleBatchDto
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import java.time.Instant

/**
 * Interface for remote sync operations with Supabase backend.
 * 
 * This abstraction enables:
 * - Unit testing without mocking Supabase internals
 * - Easy switching between different backend implementations
 * - Clear separation between sync logic and network layer
 * 
 * ## Sync Strategy
 * 
 * The sync process follows a push-then-pull strategy:
 * 1. Push local unsynced entities to Supabase (using local_id for deduplication)
 * 2. Pull entities from Supabase that were updated after last sync timestamp
 * 
 * ## Conflict Resolution
 * 
 * - Uses server_updated_at (set by Postgres trigger) for pull filtering
 * - UNIQUE constraint on local_id in Supabase handles duplicate pushes
 * - Local wins for concurrent edits (rare in this append-only domain)
 * 
 * ## Error Handling
 * 
 * All methods throw exceptions on network or server errors.
 * Callers should handle exceptions and implement retry logic.
 * 
 * @see SyncService for the sync orchestration logic
 * @see SyncWorker for background sync execution
 */
interface SyncDataSource {

    /**
     * Push transactions to the remote server in batch.
     * Uses upsert with local_id as conflict key.
     * 
     * @param dtos List of transactions to push
     * @throws Exception on network or server error
     */
    suspend fun pushTransactions(dtos: List<TransactionDto>)

    /**
     * Push purchase batches to the remote server in batch.
     * Uses upsert with local_id as conflict key.
     * 
     * @param dtos List of purchase batches to push
     * @throws Exception on network or server error
     */
    suspend fun pushBatches(dtos: List<PurchaseBatchDto>)

    /**
     * Push sale batches to the remote server in batch.
     * Uses upsert with local_id as conflict key.
     * 
     * @param dtos List of sale batches to push
     * @throws Exception on network or server error
     */
    suspend fun pushSaleBatches(dtos: List<SaleBatchDto>)

    /**
     * Push products to the remote server in batch.
     * Uses upsert with local_id as conflict key.
     * 
     * @param dtos List of products to push
     * @throws Exception on network or server error
     */
    suspend fun pushProducts(dtos: List<ProductDto>)

    /**
     * Delete a product from the remote server.
     * 
     * @param id The server ID of the product to delete
     * @throws Exception on network or server error
     */
    suspend fun deleteProduct(id: String)

    /**
     * Push expense categories to the remote server in batch.
     * Uses upsert with local_id as conflict key.
     * 
     * @param dtos List of expense categories to push
     * @throws Exception on network or server error
     */
    suspend fun pushExpenseCategories(dtos: List<ExpenseCategoryDto>)

    /**
     * Push cash operations to the remote server in batch.
     * Uses upsert with local_id as conflict key.
     * 
     * @param dtos List of cash operations to push
     * @throws Exception on network or server error
     */
    suspend fun pushCashOperations(dtos: List<CashOperationDto>)

    /**
     * Pull transactions created/updated after the given timestamp.
     * Filters by server_updated_at which is set by a Postgres trigger.
     * 
     * @param since Timestamp to filter transactions (exclusive)
     * @return List of transactions updated after the timestamp
     * @throws Exception on network error
     */
    suspend fun pullTransactions(since: Instant): List<TransactionDto>

    /**
     * Pull purchase batches created/updated after the given timestamp.
     * 
     * @param since Timestamp to filter batches (exclusive)
     * @return List of purchase batches updated after the timestamp
     * @throws Exception on network error
     */
    suspend fun pullBatches(since: Instant): List<PurchaseBatchDto>

    /**
     * Pull sale batches created/updated after the given timestamp.
     * 
     * @param since Timestamp to filter batches (exclusive)
     * @return List of sale batches updated after the timestamp
     * @throws Exception on network error
     */
    suspend fun pullSaleBatches(since: Instant): List<SaleBatchDto>

    /**
     * Pull expense categories created/updated after the given timestamp.
     * 
     * @param since Timestamp to filter categories (exclusive)
     * @return List of expense categories updated after the timestamp
     * @throws Exception on network error
     */
    suspend fun pullExpenseCategories(since: Instant): List<ExpenseCategoryDto>

    /**
     * Pull cash operations created/updated after the given timestamp.
     * 
     * @param since Timestamp to filter operations (exclusive)
     * @return List of cash operations updated after the timestamp
     * @throws Exception on network error
     */
    suspend fun pullCashOperations(since: Instant): List<CashOperationDto>

    /**
     * Pull all locations (reference data).
     * Locations are server-managed master data, always fully synced.
     * 
     * @return List of all locations
     * @throws Exception on network error
     */
    suspend fun pullLocations(): List<LocationDto>

    /**
     * Pull all products (reference data).
     * Products can be created on devices but are synced fully.
     * 
     * @return List of all products
     * @throws Exception on network error
     */
    suspend fun pullProducts(): List<ProductDto>

    // ============= Server Timestamp Fetch Methods =============
    // Used for server-wins conflict resolution during push.
    // Each method fetches server_updated_at timestamps for a list of local_ids.
    // Returns a map of local_id -> server_updated_at (ISO 8601 string).
    // Records not found on server are not included in the result.

    /**
     * Fetch server timestamps for sale batches by their local_ids.
     * Used to detect conflicts before pushing.
     * 
     * @param localIds List of local_ids to check
     * @return Map of local_id to server_updated_at timestamp string
     * @throws Exception on network error
     */
    suspend fun getSaleBatchTimestamps(localIds: List<String>): Map<String, String>

    /**
     * Fetch server timestamps for purchase batches by their local_ids.
     * Used to detect conflicts before pushing.
     * 
     * @param localIds List of local_ids to check
     * @return Map of local_id to server_updated_at timestamp string
     * @throws Exception on network error
     */
    suspend fun getPurchaseBatchTimestamps(localIds: List<String>): Map<String, String>

    /**
     * Fetch server timestamps for transactions by their local_ids.
     * Used to detect conflicts before pushing.
     * 
     * @param localIds List of local_ids to check
     * @return Map of local_id to server_updated_at timestamp string
     * @throws Exception on network error
     */
    suspend fun getTransactionTimestamps(localIds: List<String>): Map<String, String>

    /**
     * Fetch server timestamps for cash operations by their local_ids.
     * Used to detect conflicts before pushing.
     * 
     * @param localIds List of local_ids to check
     * @return Map of local_id to server_updated_at timestamp string
     * @throws Exception on network error
     */
    suspend fun getCashOperationTimestamps(localIds: List<String>): Map<String, String>

    /**
     * Fetch server timestamps for expense categories by their local_ids.
     * Used to detect conflicts before pushing.
     * 
     * @param localIds List of local_ids to check
     * @return Map of local_id to server_updated_at timestamp string
     * @throws Exception on network error
     */
    suspend fun getExpenseCategoryTimestamps(localIds: List<String>): Map<String, String>

    /**
     * Fetch server timestamps for products by their local_ids.
     * Used to detect conflicts before pushing.
     * 
     * @param localIds List of local_ids to check
     * @return Map of local_id to server_updated_at timestamp string
     * @throws Exception on network error
     */
    suspend fun getProductTimestamps(localIds: List<String>): Map<String, String>
}
