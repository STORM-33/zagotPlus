package com.zagot.zagotplus.sync

import com.zagot.zagotplus.data.remote.dto.CashOperationDto
import com.zagot.zagotplus.data.remote.dto.ExpenseCategoryDto
import com.zagot.zagotplus.data.remote.dto.LocationDto
import com.zagot.zagotplus.data.remote.dto.ProductDto
import com.zagot.zagotplus.data.remote.dto.PurchaseBatchDto
import com.zagot.zagotplus.data.remote.dto.SaleBatchDto
import com.zagot.zagotplus.data.remote.dto.TransactionDto
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.postgrest
import io.github.jan.supabase.postgrest.query.Columns
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Supabase implementation of SyncDataSource.
 * Handles all remote API calls for sync operations.
 */
@Singleton
class SupabaseSyncDataSource @Inject constructor(
    private val supabaseClient: SupabaseClient
) : SyncDataSource {

    companion object {
        private const val TABLE_TRANSACTIONS = "transactions"
        private const val TABLE_PURCHASE_BATCHES = "purchase_batches"
        private const val TABLE_SALE_BATCHES = "sale_batches"
        private const val TABLE_LOCATIONS = "locations"
        private const val TABLE_PRODUCTS = "products"
        private const val TABLE_EXPENSE_CATEGORIES = "expense_categories"
        private const val TABLE_CASH_OPERATIONS = "cash_operations"
    }

    override suspend fun pushTransactions(dtos: List<TransactionDto>) {
        if (dtos.isEmpty()) return
        supabaseClient.postgrest[TABLE_TRANSACTIONS].upsert(dtos, onConflict = "local_id")
    }

    override suspend fun pushBatches(dtos: List<PurchaseBatchDto>) {
        if (dtos.isEmpty()) return
        supabaseClient.postgrest[TABLE_PURCHASE_BATCHES].upsert(dtos, onConflict = "local_id")
    }

    override suspend fun pushSaleBatches(dtos: List<SaleBatchDto>) {
        if (dtos.isEmpty()) return
        supabaseClient.postgrest[TABLE_SALE_BATCHES].upsert(dtos, onConflict = "local_id")
    }

    override suspend fun pushProducts(dtos: List<ProductDto>) {
        if (dtos.isEmpty()) return
        supabaseClient.postgrest[TABLE_PRODUCTS].upsert(dtos, onConflict = "local_id")
    }

    override suspend fun deleteProduct(id: String) {
        supabaseClient.postgrest[TABLE_PRODUCTS].delete {
            filter {
                eq("id", id)
            }
        }
    }

    override suspend fun pushExpenseCategories(dtos: List<ExpenseCategoryDto>) {
        if (dtos.isEmpty()) return
        supabaseClient.postgrest[TABLE_EXPENSE_CATEGORIES].upsert(dtos, onConflict = "local_id")
    }

    override suspend fun pushCashOperations(dtos: List<CashOperationDto>) {
        if (dtos.isEmpty()) return
        supabaseClient.postgrest[TABLE_CASH_OPERATIONS].upsert(dtos, onConflict = "local_id")
    }

    override suspend fun pullTransactions(since: Instant): List<TransactionDto> {
        return supabaseClient.postgrest[TABLE_TRANSACTIONS]
            .select(Columns.ALL) {
                filter {
                    gt("server_updated_at", since.toString())
                }
            }
            .decodeList()
    }

    override suspend fun pullBatches(since: Instant): List<PurchaseBatchDto> {
        return supabaseClient.postgrest[TABLE_PURCHASE_BATCHES]
            .select(Columns.ALL) {
                filter {
                    gt("server_updated_at", since.toString())
                }
            }
            .decodeList()
    }

    override suspend fun pullSaleBatches(since: Instant): List<SaleBatchDto> {
        return supabaseClient.postgrest[TABLE_SALE_BATCHES]
            .select(Columns.ALL) {
                filter {
                    gt("server_updated_at", since.toString())
                }
            }
            .decodeList()
    }

    override suspend fun pullExpenseCategories(since: Instant): List<ExpenseCategoryDto> {
        return supabaseClient.postgrest[TABLE_EXPENSE_CATEGORIES]
            .select(Columns.ALL) {
                filter {
                    gt("server_updated_at", since.toString())
                }
            }
            .decodeList()
    }

    override suspend fun pullCashOperations(since: Instant): List<CashOperationDto> {
        return supabaseClient.postgrest[TABLE_CASH_OPERATIONS]
            .select(Columns.ALL) {
                filter {
                    gt("server_updated_at", since.toString())
                }
            }
            .decodeList()
    }

    override suspend fun pullLocations(): List<LocationDto> {
        return supabaseClient.postgrest[TABLE_LOCATIONS]
            .select(Columns.ALL)
            .decodeList()
    }

    override suspend fun pullProducts(): List<ProductDto> {
        return supabaseClient.postgrest[TABLE_PRODUCTS]
            .select(Columns.ALL)
            .decodeList()
    }
}
