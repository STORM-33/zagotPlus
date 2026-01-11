package com.zagot.zagotplus.sync

import android.util.Log
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import com.zagot.zagotplus.data.remote.dto.LocationDto
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Service responsible for bidirectional sync between Room and Supabase.
 *
 * Push: Local unsynced transactions → Supabase (upsert by local_id)
 * Pull: New remote transactions → Room (since last sync timestamp)
 *
 * Deduplication is handled by UNIQUE constraint on local_id in Supabase.
 */
@Singleton
class SyncService @Inject constructor(
    private val supabaseClient: SupabaseClient,
    private val transactionDao: TransactionDao,
    private val locationDao: LocationDao,
    private val productDao: ProductDao,
    private val syncPreferences: SyncPreferences
) {
    companion object {
        private const val TAG = "SyncService"
        private const val TABLE_TRANSACTIONS = "transactions"
        private const val TABLE_LOCATIONS = "locations"
        private const val TABLE_PRODUCTS = "products"
    }

    /**
     * Perform full sync: push local changes, then pull remote changes.
     * Returns SyncResult with statistics or error.
     */
    suspend fun sync(): SyncResult {
        return try {
            Log.d(TAG, "Starting sync...")

            // Pull reference data first (locations, products)
            pullReferenceData()

            // Push pending transactions
            val pushedCount = pushPendingTransactions()
            Log.d(TAG, "Pushed $pushedCount transactions")

            // Pull new transactions from other devices
            val pulledCount = pullNewTransactions()
            Log.d(TAG, "Pulled $pulledCount transactions")

            SyncResult.success(pushed = pushedCount, pulled = pulledCount)
        } catch (e: Exception) {
            Log.e(TAG, "Sync failed", e)
            SyncResult.failure(e.message ?: "Unknown error")
        }
    }

    /**
     * Push all unsynced local transactions to Supabase.
     * Uses upsert with local_id as conflict key to handle duplicates.
     */
    suspend fun pushPendingTransactions(): Int {
        val pending = transactionDao.getUnsynced()
        if (pending.isEmpty()) {
            Log.d(TAG, "No pending transactions to push")
            return 0
        }

        Log.d(TAG, "Pushing ${pending.size} pending transactions")

        var successCount = 0
        for (entity in pending) {
            try {
                val dto = TransactionDto.fromEntity(entity)
                supabaseClient.postgrest[TABLE_TRANSACTIONS].upsert(dto, onConflict = "local_id")
                // Mark as synced locally
                transactionDao.markAsSynced(entity.localId, Instant.now())
                successCount++
            } catch (e: Exception) {
                Log.e(TAG, "Failed to push transaction ${entity.localId}", e)
                // Continue with next transaction - don't fail entire sync
            }
        }

        return successCount
    }

    /**
     * Pull new transactions from Supabase that were created after last sync.
     * Inserts or updates local Room database.
     */
    suspend fun pullNewTransactions(): Int {
        val lastSync = syncPreferences.getLastSyncTimestamp()
        Log.d(TAG, "Pulling transactions created after $lastSync")

        val remoteDtos = supabaseClient.postgrest[TABLE_TRANSACTIONS]
            .select(Columns.ALL) {
                filter {
                    gt("created_at", lastSync.toString())
                }
            }
            .decodeList<TransactionDto>()

        if (remoteDtos.isEmpty()) {
            Log.d(TAG, "No new remote transactions")
            syncPreferences.setLastSyncTimestamp(Instant.now())
            return 0
        }

        Log.d(TAG, "Found ${remoteDtos.size} new remote transactions")

        var insertCount = 0
        for (dto in remoteDtos) {
            try {
                // Check if we already have this transaction
                val existing = transactionDao.getByLocalId(dto.localId)
                if (existing == null) {
                    // New transaction from another device
                    val entity = dto.toEntity()
                    transactionDao.insert(entity)
                    insertCount++
                }
                // If exists, it's our own transaction - skip
            } catch (e: Exception) {
                Log.e(TAG, "Failed to insert remote transaction ${dto.localId}", e)
            }
        }

        syncPreferences.setLastSyncTimestamp(Instant.now())
        return insertCount
    }

    /**
     * Pull reference data (locations and products) from Supabase.
     * These are master data managed on server, pulled to local DB.
     */
    private suspend fun pullReferenceData() {
        try {
            // Pull locations
            val locations = supabaseClient.postgrest[TABLE_LOCATIONS]
                .select(Columns.ALL)
                .decodeList<LocationDto>()

            locations.forEach { dto ->
                locationDao.insert(dto.toEntity())
            }
            Log.d(TAG, "Pulled ${locations.size} locations")

            // Pull products
            val products = supabaseClient.postgrest[TABLE_PRODUCTS]
                .select(Columns.ALL)
                .decodeList<ProductDto>()

            products.forEach { dto ->
                productDao.insert(dto.toEntity())
            }
            Log.d(TAG, "Pulled ${products.size} products")

        } catch (e: Exception) {
            Log.e(TAG, "Failed to pull reference data", e)
            // Don't fail sync - reference data is less critical
        }
    }
}
