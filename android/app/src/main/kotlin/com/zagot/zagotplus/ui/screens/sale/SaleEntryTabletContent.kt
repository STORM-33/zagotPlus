package com.zagot.zagotplus.ui.screens.sale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.ui.screens.shared.SaleEntryUiState
import com.zagot.zagotplus.ui.screens.shared.SaleInputField
import com.zagot.zagotplus.ui.screens.shared.SalePosition
import com.zagot.zagotplus.ui.screens.shared.SaleWeighingBatch
import com.zagot.zagotplus.ui.screens.shared.TransactionEntryBatchTabletContent
import java.util.UUID

/**
 * Tablet-specific three-column layout for sale entry in batch (wholesale) mode.
 * Delegates to shared TransactionEntryBatchTabletContent.
 */
@Composable
fun SaleEntryTabletContent(
    uiState: SaleEntryUiState,
    onProductSelect: (Product) -> Unit,
    onProductOrderChanged: (List<UUID>) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onSelectInputFieldAndClear: (SaleInputField) -> Unit,
    onAddBatch: () -> Unit,
    onRemoveBatch: (String) -> Unit,
    onSelectBatch: (SaleWeighingBatch) -> Unit,
    onAddPosition: () -> Unit,
    onSelectPosition: (SalePosition) -> Unit,
    onRemovePosition: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Create inventory map for product grid
    val inventoryMap = remember(uiState.inventory) {
        uiState.inventory.associate { it.productId to it.totalWeightKg }
    }

    TransactionEntryBatchTabletContent(
        uiState = uiState,
        transactionType = TransactionType.SALE,
        onProductSelect = onProductSelect,
        onProductOrderChanged = onProductOrderChanged,
        onKeypadInput = onKeypadInput,
        onKeypadDecimal = onKeypadDecimal,
        onKeypadBackspace = onKeypadBackspace,
        onNextInputField = onNextInputField,
        onSelectInputFieldAndClear = onSelectInputFieldAndClear,
        onAddBatch = onAddBatch,
        onRemoveBatch = onRemoveBatch,
        onSelectBatch = onSelectBatch,
        onSelectPosition = onSelectPosition,
        onRemovePosition = onRemovePosition,
        onNotesChange = onNotesChange,
        inventoryMap = inventoryMap,
        modifier = modifier
    )
}
