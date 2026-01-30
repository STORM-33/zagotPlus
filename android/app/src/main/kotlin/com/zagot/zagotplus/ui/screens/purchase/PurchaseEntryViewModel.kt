package com.zagot.zagotplus.ui.screens.purchase

import android.util.Log
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.preferences.ProductOrderPreferences
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.PurchaseBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.PurchaseBatchRepository
import com.zagot.zagotplus.hardware.printer.PrinterService
import com.zagot.zagotplus.hardware.printer.receipt.PurchaseReceiptBuilder
import com.zagot.zagotplus.hardware.scales.ScalesService
import com.zagot.zagotplus.ui.navigation.Destination
import com.zagot.zagotplus.ui.screens.shared.EditingBatchData
import com.zagot.zagotplus.ui.screens.shared.TransactionEntryBaseViewModel
import com.zagot.zagotplus.ui.screens.shared.TransactionPosition
import com.zagot.zagotplus.ui.screens.shared.WeighingBatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.time.LocalDateTime
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class PurchaseEntryViewModel @Inject constructor(
    productRepository: ProductRepository,
    locationRepository: LocationRepository,
    devicePreferences: DevicePreferences,
    productOrderPreferences: ProductOrderPreferences,
    scalesService: ScalesService,
    private val purchaseBatchRepository: PurchaseBatchRepository,
    private val printerService: PrinterService,
    savedStateHandle: SavedStateHandle
) : TransactionEntryBaseViewModel(
    productRepository,
    locationRepository,
    devicePreferences,
    productOrderPreferences,
    scalesService,
    savedStateHandle,
    TransactionType.PURCHASE,
    Destination.PurchaseEntry.ARG_BATCH_ID,
    Destination.PurchaseEntry.ARG_MODE
) {
    companion object {
        private const val TAG = "PurchaseEntryVM"
    }

    // Removed abstract overrides since they are passed in constructor now

    override fun getDefaultPrice(product: Product): BigDecimal? = product.defaultBuyPrice

    init {
        initialize()
    }

    override suspend fun saveBatch(
        positions: List<TransactionPosition>,
        notes: String,
        locationId: UUID,
        correctionReason: String?,
        editingBatchId: UUID?
    ) {
        val now = Instant.now()
        val deviceId = devicePreferences.getDeviceId()
        val batchId = UUID.randomUUID()
        val batchLocalId = UUID.randomUUID().toString()

        val totalWeight = positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.netWeight) }
        val totalAmount = positions.fold(BigDecimal.ZERO) { acc, pos -> acc.add(pos.totalAmount) }

        val batch = PurchaseBatch(
            id = batchId,
            localId = batchLocalId,
            locationId = locationId,
            notes = notes.ifBlank { null },
            totalWeightKg = totalWeight,
            totalAmount = totalAmount,
            itemCount = positions.size,
            deviceId = deviceId,
            createdAt = now,
            syncedAt = null
        )

        val transactions = positions.map { position ->
            Transaction(
                id = UUID.randomUUID(),
                localId = UUID.randomUUID().toString(),
                locationId = locationId,
                type = TransactionType.PURCHASE,
                transferLocationId = null,
                productId = position.product.id,
                weightKg = position.netWeight, // Positive for purchase
                pricePerKg = position.pricePerKg,
                totalAmount = position.totalAmount,
                notes = buildPositionNotes(position, notes),
                deviceId = deviceId,
                createdAt = now,
                syncedAt = null,
                batchId = batchId
            )
        }

        if (editingBatchId != null) {
            val reason = correctionReason ?: "Виправлення помилки"
            purchaseBatchRepository.correctBatch(
                originalBatchId = editingBatchId,
                correctedBatch = batch,
                correctedTransactions = transactions,
                reason = reason
            )
        } else {
            purchaseBatchRepository.createBatchWithTransactions(batch, transactions)
        }

        // Try to print receipt
        tryPrintReceipt(
            receiptNumber = batchLocalId.take(8).uppercase(),
            positions = positions,
            notes = notes.ifBlank { null }
        )
    }

    override suspend fun loadExistingBatch(batchId: UUID): EditingBatchData? {
        val batch = purchaseBatchRepository.getById(batchId) ?: return null
        val transactions = purchaseBatchRepository.getTransactionsForBatch(batchId)
        val allProducts = productRepository.getActiveProducts().first()
        val products = allProducts.associateBy { it.id }

        val positions = transactions.mapNotNull { tx ->
            val product = tx.productId?.let { products[it] }
            if (product != null) {
                // Create position with single batch for regular mode
                val batch = WeighingBatch(
                    grossWeightKg = tx.weightKg,
                    tareCount = 0
                )
                TransactionPosition(
                    product = product,
                    batches = listOf(batch),
                    tareWeightPerUnit = BigDecimal.ZERO,
                    pricePerKg = tx.pricePerKg ?: BigDecimal.ZERO
                )
            } else null
        }

        return EditingBatchData(
            batchId = batchId,
            positions = positions,
            notes = batch.notes ?: "",
            locationId = batch.locationId
        )
    }

    private fun tryPrintReceipt(
        receiptNumber: String,
        positions: List<TransactionPosition>,
        notes: String?
    ) {
        if (!printerService.isReady()) {
            Log.d(TAG, "Printer not ready, skipping receipt print")
            return
        }

        viewModelScope.launch {
            try {
                val receipt = PurchaseReceiptBuilder()
                    .receiptNumber(receiptNumber)
                    .date(LocalDateTime.now())
                    .also { builder ->
                        positions.forEach { pos ->
                            builder.addItem(pos.product.name, pos.netWeight, pos.pricePerKg)
                        }
                    }
                    .notes(notes)
                    .build()

                printerService.print(receipt).onFailure { e ->
                    Log.e(TAG, "Receipt print failed", e)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Receipt build/print error", e)
            }
        }
    }
}