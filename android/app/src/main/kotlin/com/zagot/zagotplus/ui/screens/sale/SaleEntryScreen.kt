package com.zagot.zagotplus.ui.screens.sale

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zagot.zagotplus.ui.navigation.SaleMode
import com.zagot.zagotplus.ui.screens.shared.TransactionEntryCallbacks
import com.zagot.zagotplus.ui.screens.shared.TransactionEntryConfig
import com.zagot.zagotplus.ui.screens.shared.TransactionEntryScreen

/**
 * Sale entry screen - thin wrapper around unified TransactionEntryScreen.
 * Delegates all logic to shared components and SaleEntryViewModel.
 */
@Composable
fun SaleEntryScreen(
    onNavigateBack: () -> Unit,
    editingBatchId: String? = null,
    mode: SaleMode = SaleMode.WHOLESALE,
    viewModel: SaleEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    // Handle navigation
    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) {
            viewModel.onNavigationHandled()
            onNavigateBack()
        }
    }

    val config = remember { TransactionEntryConfig.forSale() }

    // Create inventory map for product grid display
    val inventoryMap = remember(uiState.inventory) {
        uiState.inventory.associate { it.productId to it.totalWeightKg }
    }

    val callbacks = remember(viewModel) {
        TransactionEntryCallbacks(
            // Navigation
            onCancel = viewModel::cancel,
            onBackToGrid = viewModel::backToGrid,
            onBackToWeighing = viewModel::backToWeighing,
            onDismissSummary = viewModel::dismissSummary,
            onExitFromSummary = viewModel::exitFromSummary,
            onNavigationHandled = viewModel::onNavigationHandled,
            onAddAnotherProduct = viewModel::addAnotherProduct,

            // Product selection
            onSelectProduct = viewModel::selectProduct,
            onSelectProductForEntry = viewModel::selectProductForEntry,
            onProductOrderChanged = viewModel::onProductOrderChanged,

            // Input handling
            onWeightChange = viewModel::onWeightChange,
            onPriceChange = viewModel::onPriceChange,
            onPriceFocused = viewModel::onPriceFocused,
            onTareCountChange = viewModel::onTareCountChange,
            onTareWeightPerUnitChange = viewModel::onTareWeightPerUnitChange,
            onNotesChange = viewModel::onNotesChange,
            onToggleManualWeightMode = viewModel::toggleManualWeightMode,

            // Tablet numpad
            onKeypadInput = viewModel::onKeypadInput,
            onKeypadDecimal = viewModel::onKeypadDecimal,
            onKeypadBackspace = viewModel::onKeypadBackspace,
            onNextInputField = viewModel::onNextInputField,
            onSelectInputFieldAndClear = viewModel::selectInputFieldAndClear,

            // Position operations
            onAddPosition = viewModel::addRegularPositionAndContinue,
            onRemovePosition = viewModel::removePosition,
            onSelectTabletPosition = viewModel::selectTabletPosition,
            onStartEditPosition = viewModel::startEditPosition,
            onCancelEditPosition = viewModel::cancelEditPosition,
            onUpdatePosition = viewModel::updatePosition,
            onUpdatePositionBatch = viewModel::updatePositionBatch,
            onDeletePositionBatch = viewModel::deletePositionBatch,

            // Batch operations
            onAddBatch = viewModel::addBatch,
            onRemoveBatch = viewModel::removeBatch,
            onUpdateBatch = viewModel::updateBatch,
            onSelectTabletBatch = viewModel::selectTabletBatch,
            onProceedToReview = viewModel::proceedToReview,
            onAddBatchPositionAndContinue = viewModel::addPositionAndContinue,
            onReviewPositionWeightings = viewModel::reviewPositionWeightings,

            // Location
            onSelectLocation = viewModel::selectLocation,

            // Finalization
            onFinalize = viewModel::finalize,
            onSetTabletMode = viewModel::setTabletMode,

            // Weight restore
            onConfirmWeightRestore = viewModel::confirmWeightRestore,
            onDismissWeightRestore = viewModel::dismissWeightRestore,

            // Exit dialog
            onConfirmExit = viewModel::confirmExit,
            onDismissExitConfirmation = viewModel::dismissExitConfirmation,
            onDismissError = viewModel::dismissError
        )
    }

    TransactionEntryScreen(
        uiState = uiState,
        config = config,
        callbacks = callbacks,
        inventoryMap = inventoryMap,
        modifier = Modifier
    )
}
