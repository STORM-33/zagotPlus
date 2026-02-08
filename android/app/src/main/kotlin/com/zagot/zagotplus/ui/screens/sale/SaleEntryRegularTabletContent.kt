package com.zagot.zagotplus.ui.screens.sale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.ui.screens.shared.SaleEntryUiState
import com.zagot.zagotplus.ui.screens.shared.SaleInputField
import com.zagot.zagotplus.ui.screens.shared.TransactionEntryRegularTabletContent
import java.util.UUID

/**
 * Tablet-specific three-column layout for sale entry in REGULAR mode.
 * Delegates to shared TransactionEntryRegularTabletContent.
 */
@Composable
fun SaleEntryRegularTabletContent(
    uiState: SaleEntryUiState,
    onProductSelect: (Product) -> Unit,
    onProductOrderChanged: (List<UUID>) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onSelectInputFieldAndClear: (SaleInputField) -> Unit,
    onAddPosition: () -> Unit,
    onRemovePosition: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Create inventory map for product grid
    val inventoryMap = remember(uiState.inventory) {
        uiState.inventory.associate { it.productId to it.totalWeightKg }
    }

    TransactionEntryRegularTabletContent(
        uiState = uiState,
        transactionType = TransactionType.SALE,
        onProductSelect = onProductSelect,
        onProductOrderChanged = onProductOrderChanged,
        onKeypadInput = onKeypadInput,
        onKeypadDecimal = onKeypadDecimal,
        onKeypadBackspace = onKeypadBackspace,
        onNextInputField = onNextInputField,
        onSelectInputFieldAndClear = onSelectInputFieldAndClear,
        onAddPosition = onAddPosition,
        onSelectPosition = null, // Sale regular mode doesn't support position editing
        onRemovePosition = onRemovePosition,
        onNotesChange = onNotesChange,
        inventoryMap = inventoryMap,
        modifier = modifier
    )
}
