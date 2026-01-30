package com.zagot.zagotplus.ui.screens.purchase

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.ui.screens.shared.PurchaseEntryUiState
import com.zagot.zagotplus.ui.screens.shared.PurchaseInputField
import com.zagot.zagotplus.ui.screens.shared.PurchasePosition
import com.zagot.zagotplus.ui.screens.shared.PurchaseWeighingBatch
import com.zagot.zagotplus.ui.screens.shared.TransactionEntryBatchTabletContent
import java.util.UUID

/**
 * Tablet-specific three-column layout for batch (wholesale) purchase entry.
 * Delegates to shared TransactionEntryBatchTabletContent.
 */
@Composable
fun PurchaseEntryBatchTabletContent(
    uiState: PurchaseEntryUiState,
    onProductSelect: (Product) -> Unit,
    onProductOrderChanged: (List<UUID>) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onSelectInputFieldAndClear: (PurchaseInputField) -> Unit,
    onAddBatch: () -> Unit,
    onRemoveBatch: (String) -> Unit,
    onSelectBatch: (PurchaseWeighingBatch) -> Unit,
    onSelectPosition: (PurchasePosition) -> Unit,
    onRemovePosition: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TransactionEntryBatchTabletContent(
        uiState = uiState,
        transactionType = TransactionType.PURCHASE,
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
        modifier = modifier
    )
}
