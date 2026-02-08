package com.zagot.zagotplus.ui.screens.sale

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.viewModelScope
import com.zagot.zagotplus.data.preferences.DevicePreferences
import com.zagot.zagotplus.data.preferences.ProductOrderPreferences
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.SaleBatch
import com.zagot.zagotplus.domain.model.Transaction
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.domain.repository.LocationRepository
import com.zagot.zagotplus.domain.repository.ProductRepository
import com.zagot.zagotplus.domain.repository.SaleBatchRepository
import com.zagot.zagotplus.domain.repository.TransactionRepository
import com.zagot.zagotplus.hardware.scales.ScalesService
import com.zagot.zagotplus.ui.navigation.Destination
import com.zagot.zagotplus.ui.screens.shared.EditingBatchData
import com.zagot.zagotplus.ui.screens.shared.TransactionEntryBaseViewModel
import com.zagot.zagotplus.ui.screens.shared.TransactionPosition
import com.zagot.zagotplus.ui.screens.shared.WeighingBatch
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.math.BigDecimal
import java.time.Instant
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class SaleEntryViewModel @Inject constructor(
    productRepository: ProductRepository,
    locationRepository: LocationRepository,
    devicePreferences: DevicePreferences,
    productOrderPreferences: ProductOrderPreferences,
    scalesService: ScalesService,
    private val saleBatchRepository: SaleBatchRepository,
    private val transactionRepository: TransactionRepository,
    savedStateHandle: SavedStateHandle
) : TransactionEntryBaseViewModel(
    productRepository,
    locationRepository,
    devicePreferences,
    productOrderPreferences,
    scalesService,
    savedStateHandle,
    TransactionType.SALE,
    Destination.SaleEntry.ARG_BATCH_ID,
    Destination.SaleEntry.ARG_MODE
) {

    // Removed abstract overrides since they are passed in constructor now

    override fun getDefaultPrice(product: Product): BigDecimal? = product.defaultSellPrice

    init {
        initialize()
        loadInventory()
    }

    private fun loadInventory() {
        viewModelScope.launch {
            try {
                val locationId = devicePreferences.getSelectedLocationId()
                val inventory = if (locationId != null) {
                    transactionRepository.getInventoryByLocation(locationId).first()
                } else {
                    emptyList()
                }
                _uiState.update { it.copy(inventory = inventory) }
            } catch (e: Exception) {
                // Silently fail - inventory is optional
            }
        }
    }

    override fun selectProduct(product: Product) {
        super.selectProduct(product)
        updateAvailableWeight(uiState.value.activeProduct)
    }

    override fun selectProductForEntry(product: Product) {
        super.selectProductForEntry(product)
        updateAvailableWeight(product)
    }

    private fun updateAvailableWeight(product: Product?) {
        if (product == null) return
        val inventory = uiState.value.inventory
        val availableWeight = inventory
            .find { it.productId == product.id }
            ?.totalWeightKg ?: BigDecimal.ZERO
        _uiState.update { it.copy(availableWeight = availableWeight) }
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

        val batch = SaleBatch(
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
                type = TransactionType.SALE,
                transferLocationId = null,
                productId = position.product.id,
                weightKg = -position.netWeight, // NEGATIVE for sale (reduces inventory)
                pricePerKg = position.pricePerKg,
                totalAmount = position.totalAmount,
                notes = buildPositionNotes(position, notes),
                deviceId = deviceId,
                createdAt = now,
                syncedAt = null,
                batchId = null,
                saleBatchId = batchId
            )
        }

        if (editingBatchId != null) {
            val reason = correctionReason ?: "Виправлення помилки"
            saleBatchRepository.correctBatch(
                originalBatchId = editingBatchId,
                correctedBatch = batch,
                correctedTransactions = transactions,
                reason = reason
            )
        } else {
            saleBatchRepository.createBatchWithTransactions(batch, transactions)
        }
    }

    override suspend fun loadExistingBatch(batchId: UUID): EditingBatchData? {
        val batch = saleBatchRepository.getById(batchId) ?: return null
        val transactions = saleBatchRepository.getTransactionsForBatch(batchId)
        val allProducts = productRepository.getActiveProducts().first()
        val products = allProducts.associateBy { it.id }

        val positions = transactions.mapNotNull { tx ->
            val product = tx.productId?.let { products[it] }
            if (product != null) {
                val weighingBatch = WeighingBatch(
                    grossWeightKg = tx.weightKg.abs(),
                    tareCount = 0
                )
                TransactionPosition(
                    product = product,
                    batches = listOf(weighingBatch),
                    tareWeightPerUnit = BigDecimal("0.1"),
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
}
