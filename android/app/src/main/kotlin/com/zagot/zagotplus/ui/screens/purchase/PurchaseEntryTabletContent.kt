package com.zagot.zagotplus.ui.screens.purchase

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.ui.screens.shared.PurchaseEntryUiState
import com.zagot.zagotplus.ui.screens.shared.PurchaseInputField
import com.zagot.zagotplus.ui.screens.shared.PurchasePosition
import com.zagot.zagotplus.ui.screens.shared.TransactionEntryRegularTabletContent
import java.util.UUID

// Backward compatibility alias
private typealias InputField = PurchaseInputField

/**
 * Tablet-specific three-column layout for purchase entry with custom numpad.
 * Delegates to shared TransactionEntryRegularTabletContent.
 */
@Composable
fun PurchaseEntryTabletContent(
    uiState: PurchaseEntryUiState,
    onProductSelect: (Product) -> Unit,
    onProductOrderChanged: (List<UUID>) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onSelectInputFieldAndClear: (InputField) -> Unit,
    onAddPosition: () -> Unit,
    onSelectTabletPosition: (PurchasePosition) -> Unit,
    onRemovePosition: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    TransactionEntryRegularTabletContent(
        uiState = uiState,
        transactionType = TransactionType.PURCHASE,
        onProductSelect = onProductSelect,
        onProductOrderChanged = onProductOrderChanged,
        onKeypadInput = onKeypadInput,
        onKeypadDecimal = onKeypadDecimal,
        onKeypadBackspace = onKeypadBackspace,
        onNextInputField = onNextInputField,
        onSelectInputFieldAndClear = onSelectInputFieldAndClear,
        onAddPosition = onAddPosition,
        onSelectPosition = onSelectTabletPosition,
        onRemovePosition = onRemovePosition,
        onNotesChange = onNotesChange,
        modifier = modifier
    )
}
