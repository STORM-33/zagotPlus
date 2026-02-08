package com.zagot.zagotplus.data.local

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import com.zagot.zagotplus.data.local.dao.CashOperationDao
import com.zagot.zagotplus.data.local.dao.ExpenseCategoryDao
import com.zagot.zagotplus.data.local.dao.LocationDao
import com.zagot.zagotplus.data.local.dao.ProductDao
import com.zagot.zagotplus.data.local.dao.PurchaseBatchDao
import com.zagot.zagotplus.data.local.dao.SaleBatchDao
import com.zagot.zagotplus.data.local.dao.TransactionDao
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Exports local database to a human-readable text file.
 * Used for debugging and data backup purposes.
 */
@Singleton
class DatabaseExporter @Inject constructor(
    @ApplicationContext private val context: Context,
    private val locationDao: LocationDao,
    private val productDao: ProductDao,
    private val transactionDao: TransactionDao,
    private val purchaseBatchDao: PurchaseBatchDao,
    private val saleBatchDao: SaleBatchDao,
    private val expenseCategoryDao: ExpenseCategoryDao,
    private val cashOperationDao: CashOperationDao
) {
    private val dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault())
    private val fileDateFormat = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

    /**
     * Export database to a text file.
     * @return File path of the exported file
     */
    suspend fun exportToFile(): File = withContext(Dispatchers.IO) {
        val timestamp = fileDateFormat.format(Date())
        val fileName = "zagot_db_export_$timestamp.txt"
        val exportFile = File(context.getExternalFilesDir(null), fileName)

        val content = buildString {
            appendLine("=" .repeat(60))
            appendLine("ZAGOT+ DATABASE EXPORT")
            appendLine("Exported: ${dateTimeFormatter.format(java.time.Instant.now())}")
            appendLine("=".repeat(60))
            appendLine()

            // Locations
            appendLine("=" .repeat(60))
            appendLine("LOCATIONS")
            appendLine("=".repeat(60))
            val locations = locationDao.getAll()
            if (locations.isEmpty()) {
                appendLine("(no data)")
            } else {
                locations.forEach { loc ->
                    appendLine("ID: ${loc.id}")
                    appendLine("  Name: ${loc.name}")
                    appendLine("  Type: ${loc.type}")
                    appendLine("  Created: ${dateTimeFormatter.format(loc.createdAt)}")
                    appendLine("  Synced: ${loc.syncedAt?.let { dateTimeFormatter.format(it) } ?: "NOT SYNCED"}")
                    appendLine()
                }
            }
            appendLine()

            // Products
            appendLine("=".repeat(60))
            appendLine("PRODUCTS")
            appendLine("=".repeat(60))
            val products = productDao.getAll()
            if (products.isEmpty()) {
                appendLine("(no data)")
            } else {
                products.forEach { prod ->
                    appendLine("ID: ${prod.id}")
                    appendLine("  Name: ${prod.name}")
                    appendLine("  Buy Price: ${prod.defaultBuyPrice ?: "-"}")
                    appendLine("  Sell Price: ${prod.defaultSellPrice ?: "-"}")
                    appendLine("  Active: ${prod.isActive}")
                    appendLine("  Created: ${dateTimeFormatter.format(prod.createdAt)}")
                    appendLine("  Synced: ${prod.syncedAt?.let { dateTimeFormatter.format(it) } ?: "NOT SYNCED"}")
                    appendLine()
                }
            }
            appendLine()

            // Purchase Batches
            appendLine("=".repeat(60))
            appendLine("PURCHASE BATCHES")
            appendLine("=".repeat(60))
            val purchaseBatches = purchaseBatchDao.getAllPaginated(1000, 0)
            if (purchaseBatches.isEmpty()) {
                appendLine("(no data)")
            } else {
                purchaseBatches.forEach { batch ->
                    appendLine("ID: ${batch.id}")
                    appendLine("  Location: ${batch.locationId}")
                    appendLine("  Items: ${batch.itemCount ?: 0}")
                    appendLine("  Weight: ${batch.totalWeightKg ?: 0} kg")
                    appendLine("  Amount: ${batch.totalAmount ?: 0} UAH")
                    appendLine("  Notes: ${batch.notes ?: "-"}")
                    appendLine("  Voided: ${batch.isVoided}")
                    if (batch.correctsBatchId != null) {
                        appendLine("  Corrects: ${batch.correctsBatchId}")
                        appendLine("  Reason: ${batch.correctionReason ?: "-"}")
                    }
                    appendLine("  Created: ${dateTimeFormatter.format(batch.createdAt)}")
                    appendLine("  Synced: ${batch.syncedAt?.let { dateTimeFormatter.format(it) } ?: "NOT SYNCED"}")
                    appendLine()
                }
            }
            appendLine()

            // Sale Batches
            appendLine("=".repeat(60))
            appendLine("SALE BATCHES")
            appendLine("=".repeat(60))
            val saleBatches = saleBatchDao.getAllPaginated(1000, 0)
            if (saleBatches.isEmpty()) {
                appendLine("(no data)")
            } else {
                saleBatches.forEach { batch ->
                    appendLine("ID: ${batch.id}")
                    appendLine("  Location: ${batch.locationId}")
                    appendLine("  Items: ${batch.itemCount ?: 0}")
                    appendLine("  Weight: ${batch.totalWeightKg ?: 0} kg")
                    appendLine("  Amount: ${batch.totalAmount ?: 0} UAH")
                    appendLine("  Notes: ${batch.notes ?: "-"}")
                    appendLine("  Voided: ${batch.isVoided}")
                    if (batch.correctsBatchId != null) {
                        appendLine("  Corrects: ${batch.correctsBatchId}")
                        appendLine("  Reason: ${batch.correctionReason ?: "-"}")
                    }
                    appendLine("  Created: ${dateTimeFormatter.format(batch.createdAt)}")
                    appendLine("  Synced: ${batch.syncedAt?.let { dateTimeFormatter.format(it) } ?: "NOT SYNCED"}")
                    appendLine()
                }
            }
            appendLine()

            // Transactions
            appendLine("=".repeat(60))
            appendLine("TRANSACTIONS")
            appendLine("=".repeat(60))
            val transactions = transactionDao.getAllPaginated(5000, 0)
            if (transactions.isEmpty()) {
                appendLine("(no data)")
            } else {
                transactions.forEach { tx ->
                    appendLine("ID: ${tx.id}")
                    appendLine("  Type: ${tx.type}")
                    appendLine("  Location: ${tx.locationId}")
                    appendLine("  Product: ${tx.productId}")
                    appendLine("  Weight: ${tx.weightKg} kg")
                    appendLine("  Price/kg: ${tx.pricePerKg ?: "-"}")
                    appendLine("  Total: ${tx.totalAmount ?: "-"} UAH")
                    if (tx.transferLocationId != null) {
                        appendLine("  Transfer To: ${tx.transferLocationId}")
                    }
                    if (tx.batchId != null) {
                        appendLine("  Purchase Batch: ${tx.batchId}")
                    }
                    if (tx.saleBatchId != null) {
                        appendLine("  Sale Batch: ${tx.saleBatchId}")
                    }
                    appendLine("  Notes: ${tx.notes ?: "-"}")
                    appendLine("  Created: ${dateTimeFormatter.format(tx.createdAt)}")
                    appendLine("  Synced: ${tx.syncedAt?.let { dateTimeFormatter.format(it) } ?: "NOT SYNCED"}")
                    appendLine()
                }
            }
            appendLine()

            // Expense Categories
            appendLine("=".repeat(60))
            appendLine("EXPENSE CATEGORIES")
            appendLine("=".repeat(60))
            val categories = expenseCategoryDao.getUnsynced() + 
                expenseCategoryDao.getUnsynced().let { unsynced ->
                    // Get all by querying for categories not in unsynced
                    emptyList<com.zagot.zagotplus.data.local.entity.ExpenseCategoryEntity>()
                }
            // Use a workaround - get categories via the DAO
            appendLine("(expense categories export pending - use sync data)")
            appendLine()

            // Cash Operations
            appendLine("=".repeat(60))
            appendLine("CASH OPERATIONS")
            appendLine("=".repeat(60))
            val cashOps = cashOperationDao.getOperationsPaged(5000, 0)
            if (cashOps.isEmpty()) {
                appendLine("(no data)")
            } else {
                cashOps.forEach { op ->
                    appendLine("ID: ${op.id}")
                    appendLine("  Type: ${op.type}")
                    appendLine("  Amount: ${op.amount} UAH")
                    appendLine("  Location: ${op.locationId}")
                    if (op.categoryId != null) {
                        appendLine("  Category: ${op.categoryId}")
                    }
                    if (op.batchId != null) {
                        appendLine("  Batch: ${op.batchId}")
                    }
                    if (op.isTransfer) {
                        appendLine("  Transfer Pair: ${op.transferPairId}")
                    }
                    appendLine("  Notes: ${op.notes ?: "-"}")
                    appendLine("  Created: ${dateTimeFormatter.format(op.createdAt)}")
                    appendLine("  Synced: ${op.syncedAt?.let { dateTimeFormatter.format(it) } ?: "NOT SYNCED"}")
                    appendLine()
                }
            }
            appendLine()

            appendLine("=".repeat(60))
            appendLine("END OF EXPORT")
            appendLine("=".repeat(60))
        }

        exportFile.writeText(content)
        exportFile
    }

    /**
     * Get a content URI for sharing the export file.
     */
    fun getShareUri(file: File): Uri {
        return FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
    }
}
