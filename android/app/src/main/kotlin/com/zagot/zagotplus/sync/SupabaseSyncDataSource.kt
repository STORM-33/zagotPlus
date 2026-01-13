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

    override suspend fun pushTransaction(dto: TransactionDto) {
        supabaseClient.postgrest[TABLE_TRANSACTIONS].upsert(dto, onConflict = "local_id")
    }

    override suspend fun pushBatch(dto: PurchaseBatchDto) {
        supabaseClient.postgrest[TABLE_PURCHASE_BATCHES].upsert(dto, onConflict = "local_id")
    }

    override suspend fun pushSaleBatch(dto: SaleBatchDto) {
        supabaseClient.postgrest[TABLE_SALE_BATCHES].upsert(dto, onConflict = "local_id")
    }

    override suspend fun pushProduct(dto: ProductDto) {
        supabaseClient.postgrest[TABLE_PRODUCTS].upsert(dto, onConflict = "local_id")
    }

    override suspend fun deleteProduct(id: String) {
        supabaseClient.postgrest[TABLE_PRODUCTS].delete {
            filter {
                eq("id", id)
            }
        }
    }

    override suspend fun pushExpenseCategory(dto: ExpenseCategoryDto) {
        supabaseClient.postgrest[TABLE_EXPENSE_CATEGORIES].upsert(dto, onConflict = "local_id")
    }

    override suspend fun pushCashOperation(dto: CashOperationDto) {
        supabaseClient.postgrest[TABLE_CASH_OPERATIONS].upsert(dto, onConflict = "local_id")
    }

    override suspend fun pullTransactions(since: Instant): List<TransactionDto> {
        return supabaseClient.postgrest[TABLE_TRANSACTIONS]
            .select(Columns.ALL) {
                filter {
                    gt("created_at", since.toString())
                }
            }
            .decodeList()
    }

    override suspend fun pullBatches(since: Instant): List<PurchaseBatchDto> {
        return supabaseClient.postgrest[TABLE_PURCHASE_BATCHES]
            .select(Columns.ALL) {
                filter {
                    gt("created_at", since.toString())
                }
            }
            .decodeList()
    }

    override suspend fun pullSaleBatches(since: Instant): List<SaleBatchDto> {
        return supabaseClient.postgrest[TABLE_SALE_BATCHES]
            .select(Columns.ALL) {
                filter {
                    gt("created_at", since.toString())
                }
            }
            .decodeList()
    }

    override suspend fun pullExpenseCategories(since: Instant): List<ExpenseCategoryDto> {
        return supabaseClient.postgrest[TABLE_EXPENSE_CATEGORIES]
            .select(Columns.ALL) {
                filter {
                    gt("created_at", since.toString())
                }
            }
            .decodeList()
    }

    override suspend fun pullCashOperations(since: Instant): List<CashOperationDto> {
        return supabaseClient.postgrest[TABLE_CASH_OPERATIONS]
            .select(Columns.ALL) {
                filter {
                    gt("created_at", since.toString())
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
