package com.zagot.zagotplus.ui.screens.shared

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.ui.components.PriceType
import com.zagot.zagotplus.ui.components.ReorderableProductGrid
import com.zagot.zagotplus.ui.components.StepIndicator
import com.zagot.zagotplus.ui.components.isTablet
import java.math.BigDecimal
import java.util.UUID

/**
 * Configuration for transaction-specific labels and behavior.
 */
data class TransactionEntryConfig(
    val transactionType: TransactionType,
    val newEntryTitle: String,
    val editingTitle: String,
    val batchModeTitle: String,
    val finalizeButtonText: String,
    val editButtonText: String,
    val cancelDialogTitle: String,
    val cancelEditDialogTitle: String,
    val notesLabel: String
) {
    companion object {
        fun forPurchase() = TransactionEntryConfig(
            transactionType = TransactionType.PURCHASE,
            newEntryTitle = "Нова закупка",
            editingTitle = "Редагування",
            batchModeTitle = "Оптова закупка",
            finalizeButtonText = "РОЗРАХУВАТИ",
            editButtonText = "РЕДАГУВАТИ",
            cancelDialogTitle = "Скасувати закупку?",
            cancelEditDialogTitle = "Скасувати редагування?",
            notesLabel = "Постачальник / примітки"
        )

        fun forSale() = TransactionEntryConfig(
            transactionType = TransactionType.SALE,
            newEntryTitle = "Новий продаж",
            editingTitle = "Редагування",
            batchModeTitle = "Новий продаж (опт)",
            finalizeButtonText = "ПРОДАТИ",
            editButtonText = "РЕДАГУВАТИ",
            cancelDialogTitle = "Скасувати продаж?",
            cancelEditDialogTitle = "Скасувати редагування?",
            notesLabel = "Покупець / примітки"
        )
    }
}

/**
 * Callbacks for transaction entry screen actions.
 */
data class TransactionEntryCallbacks(
    // Navigation
    val onCancel: () -> Unit,
    val onBackToGrid: () -> Unit,
    val onBackToWeighing: () -> Unit,
    val onDismissSummary: () -> Unit,
    val onExitFromSummary: () -> Unit,
    val onNavigationHandled: () -> Unit,
    val onAddAnotherProduct: () -> Unit,

    // Product selection
    val onSelectProduct: (Product) -> Unit,
    val onSelectProductForEntry: (Product) -> Unit,
    val onProductOrderChanged: (List<UUID>) -> Unit,

    // Input handling
    val onWeightChange: (String) -> Unit,
    val onPriceChange: (String) -> Unit,
    val onPriceFocused: () -> Unit,
    val onTareCountChange: (String) -> Unit,
    val onTareWeightPerUnitChange: (String) -> Unit,
    val onNotesChange: (String) -> Unit,
    val onToggleManualWeightMode: () -> Unit,

    // Tablet numpad
    val onKeypadInput: (Char) -> Unit,
    val onKeypadDecimal: () -> Unit,
    val onKeypadBackspace: () -> Unit,
    val onNextInputField: () -> Unit,
    val onSelectInputFieldAndClear: (TransactionInputField) -> Unit,

    // Position operations
    val onAddPosition: () -> Unit,
    val onRemovePosition: (String) -> Unit,
    val onSelectTabletPosition: (TransactionPosition) -> Unit,
    val onStartEditPosition: (TransactionPosition) -> Unit,
    val onCancelEditPosition: () -> Unit,
    val onUpdatePosition: (String, BigDecimal, BigDecimal) -> Unit,
    val onUpdatePositionBatch: (String, String, BigDecimal, Int) -> Unit,
    val onDeletePositionBatch: (String, String) -> Unit,

    // Batch operations
    val onAddBatch: () -> Unit,
    val onRemoveBatch: (String) -> Unit,
    val onUpdateBatch: (String, BigDecimal, Int) -> Unit,
    val onSelectTabletBatch: (WeighingBatch) -> Unit,
    val onProceedToReview: () -> Unit,
    val onAddBatchPositionAndContinue: () -> Unit,
    val onReviewPositionWeightings: (TransactionPosition) -> Unit,

    // Location
    val onSelectLocation: (UUID?) -> Unit,

    // Finalization
    val onFinalize: () -> Unit,
    val onSetTabletMode: (Boolean) -> Unit,

    // Receipt
    val onPrintReceipt: (() -> Unit)? = null,

    // Exit dialog
    val onConfirmExit: () -> Unit,
    val onDismissExitConfirmation: () -> Unit,
    val onDismissError: () -> Unit
)

/**
 * Unified transaction entry screen for both purchase and sale flows.
 * Handles both phone (wizard-style) and tablet (unified three-column) layouts.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionEntryScreen(
    uiState: TransactionEntryUiState,
    config: TransactionEntryConfig,
    callbacks: TransactionEntryCallbacks,
    inventoryMap: Map<UUID, BigDecimal> = emptyMap(),
    modifier: Modifier = Modifier
) {
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val isTabletDevice = isTablet()

    // Set tablet mode in ViewModel once at composition start
    LaunchedEffect(isTabletDevice) {
        callbacks.onSetTabletMode(isTabletDevice)
    }

    // Handle navigation
    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) {
            callbacks.onNavigationHandled()
        }
    }

    // Handle Android back button
    BackHandler {
        handleBackPress(uiState, callbacks)
    }

    // Show error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            callbacks.onDismissError()
        }
    }

    val isEditing = uiState.editingBatchId != null
    val isBatchMode = uiState.entryMode == TransactionEntryMode.BATCH

    val topBarTitle = getTopBarTitle(uiState, config, isEditing)
    val stepLabels = getStepLabels(isBatchMode)
    val currentStep = getCurrentStep(uiState, isBatchMode)
    val showTopBar = uiState.screenState != TransactionEntryScreenState.SUMMARY
    val showBackToGrid = shouldShowBackToGrid(uiState)

    Scaffold(
        topBar = {
            if (showTopBar) {
                TransactionEntryTopBar(
                    title = topBarTitle,
                    uiState = uiState,
                    config = config,
                    isEditing = isEditing,
                    onBackClick = {
                        if (showBackToGrid) {
                            callbacks.onBackToGrid()
                        } else {
                            callbacks.onCancel()
                        }
                    },
                    onCancelClick = callbacks.onCancel,
                    onFinalizeClick = {
                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                        callbacks.onFinalize()
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
        modifier = modifier
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Step indicator (show for phone wizard states only, not on tablet or summary)
            if (shouldShowStepIndicator(uiState)) {
                StepIndicator(
                    currentStep = currentStep,
                    totalSteps = stepLabels.size,
                    stepLabels = stepLabels
                )
            }

            // Redaction mode banner for tablet
            if (isEditing && isTabletDevice) {
                Surface(
                    color = MaterialTheme.colorScheme.tertiaryContainer,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Edit,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onTertiaryContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            "РЕЖИМ РЕДАГУВАННЯ",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                    }
                }
            }

            Box(modifier = Modifier.fillMaxSize()) {
                // Show summary overlay if in SUMMARY state
                if (uiState.screenState == TransactionEntryScreenState.SUMMARY) {
                    TransactionSummaryOverlay(
                        positions = uiState.positions,
                        notes = uiState.notes,
                        totalWeight = uiState.totalWeight,
                        totalAmount = uiState.totalAmount,
                        transactionType = config.transactionType,
                        onExit = callbacks.onExitFromSummary,
                        onPrintReceipt = callbacks.onPrintReceipt
                    )
                } else {
                    when {
                        uiState.isLoading -> {
                            CircularProgressIndicator(
                                modifier = Modifier.align(Alignment.Center)
                            )
                        }
                        uiState.isSaving -> {
                            Column(
                                modifier = Modifier.align(Alignment.Center),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                CircularProgressIndicator()
                                Spacer(modifier = Modifier.height(16.dp))
                                Text("Збереження...")
                            }
                        }
                        else -> {
                            AnimatedContent(
                                targetState = uiState.screenState,
                                transitionSpec = {
                                    if (targetState.ordinal > initialState.ordinal) {
                                        (fadeIn() + slideInHorizontally { it / 3 }) togetherWith
                                            (fadeOut() + slideOutHorizontally { -it / 3 })
                                    } else {
                                        (fadeIn() + slideInHorizontally { -it / 3 }) togetherWith
                                            (fadeOut() + slideOutHorizontally { it / 3 })
                                    }
                                },
                                label = "screenStateTransition"
                            ) { state ->
                                TransactionScreenContent(
                                    screenState = state,
                                    uiState = uiState,
                                    config = config,
                                    callbacks = callbacks,
                                    inventoryMap = inventoryMap,
                                    haptic = haptic
                                )
                            }
                        }
                    }
                }
            }
        }

        // Edit position dialog
        uiState.editingPosition?.let { position ->
            TransactionEditPositionDialog(
                position = position,
                onDismiss = callbacks.onCancelEditPosition,
                onConfirm = { tareWeight, price ->
                    callbacks.onUpdatePosition(position.id, tareWeight, price)
                },
                onUpdateBatch = { batchId, newWeight, newTareCount ->
                    callbacks.onUpdatePositionBatch(position.id, batchId, newWeight, newTareCount)
                },
                onDeleteBatch = { batchId ->
                    callbacks.onDeletePositionBatch(position.id, batchId)
                }
            )
        }

        // Exit confirmation dialog
        if (uiState.showExitConfirmation) {
            AlertDialog(
                onDismissRequest = callbacks.onDismissExitConfirmation,
                title = {
                    Text(
                        if (isEditing) config.cancelEditDialogTitle
                        else config.cancelDialogTitle
                    )
                },
                text = { Text("Всі введені дані буде втрачено.") },
                confirmButton = {
                    TextButton(onClick = callbacks.onConfirmExit) {
                        Text("Так, вийти")
                    }
                },
                dismissButton = {
                    TextButton(onClick = callbacks.onDismissExitConfirmation) {
                        Text("Продовжити")
                    }
                }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransactionEntryTopBar(
    title: String,
    uiState: TransactionEntryUiState,
    config: TransactionEntryConfig,
    isEditing: Boolean,
    onBackClick: () -> Unit,
    onCancelClick: () -> Unit,
    onFinalizeClick: () -> Unit
) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = onBackClick) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Назад")
            }
        },
        actions = {
            // Tablet mode: show Cancel and Finalize buttons in top bar
            if (uiState.screenState == TransactionEntryScreenState.UNIFIED_ENTRY ||
                uiState.screenState == TransactionEntryScreenState.UNIFIED_BATCH) {
                OutlinedButton(
                    onClick = onCancelClick,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.5.dp, MaterialTheme.colorScheme.error),
                    modifier = Modifier
                        .padding(end = 8.dp)
                        .height(52.dp)
                        .widthIn(min = 140.dp),
                    contentPadding = PaddingValues(horizontal = 24.dp)
                ) {
                    Text(
                        "Скасувати",
                        style = MaterialTheme.typography.titleMedium
                    )
                }
                Button(
                    onClick = onFinalizeClick,
                    enabled = uiState.canFinalize,
                    modifier = Modifier
                        .padding(end = 16.dp)
                        .height(52.dp)
                        .widthIn(min = 180.dp),
                    contentPadding = PaddingValues(horizontal = 32.dp)
                ) {
                    Text(
                        if (isEditing) config.editButtonText else config.finalizeButtonText,
                        style = MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    )
}

@Composable
private fun TransactionScreenContent(
    screenState: TransactionEntryScreenState,
    uiState: TransactionEntryUiState,
    config: TransactionEntryConfig,
    callbacks: TransactionEntryCallbacks,
    inventoryMap: Map<UUID, BigDecimal>,
    haptic: androidx.compose.ui.hapticfeedback.HapticFeedback
) {
    val isEditing = uiState.editingBatchId != null

    when (screenState) {
        TransactionEntryScreenState.PRODUCT_GRID -> {
            TransactionProductGrid(
                products = uiState.products,
                positions = uiState.positions,
                transactionType = config.transactionType,
                inventoryMap = inventoryMap,
                onProductClick = callbacks.onSelectProduct,
                onOrderChanged = callbacks.onProductOrderChanged,
                onProceedToPositions = callbacks.onFinalize,
                modifier = Modifier.fillMaxSize()
            )
        }

        TransactionEntryScreenState.WEIGHT_ENTRY -> {
            TransactionWeightEntry(
                weight = uiState.effectiveWeight,
                price = uiState.currentPrice,
                total = uiState.currentTotal,
                isScaleConnected = uiState.isScaleConnected,
                isManualMode = uiState.isManualWeightMode,
                canAdd = uiState.canAddPosition,
                availableWeight = uiState.availableWeight,
                showInventoryWarning = config.transactionType == TransactionType.SALE &&
                    uiState.effectiveWeight.toBigDecimalOrNull()?.let {
                        it > uiState.availableWeight && uiState.availableWeight >= BigDecimal.ZERO
                    } == true,
                onWeightChange = callbacks.onWeightChange,
                onPriceChange = callbacks.onPriceChange,
                onPriceFocused = callbacks.onPriceFocused,
                onToggleManualMode = callbacks.onToggleManualWeightMode,
                onAddPosition = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    callbacks.onAddPosition()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        TransactionEntryScreenState.WEIGHING -> {
            TransactionBatchWeighing(
                productName = uiState.selectedProduct?.name ?: "",
                currentWeight = uiState.currentWeight,
                currentTareCount = uiState.currentTareCount,
                batches = uiState.currentBatches,
                grossWeight = uiState.currentGrossWeight,
                canAddBatch = uiState.canAddBatch,
                canProceed = uiState.canProceedToReview,
                lastWeighingAddedId = uiState.lastWeighingAddedId,
                onWeightChange = callbacks.onWeightChange,
                onTareCountChange = callbacks.onTareCountChange,
                onAddBatch = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    callbacks.onAddBatch()
                },
                onRemoveBatch = callbacks.onRemoveBatch,
                onProceed = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    callbacks.onProceedToReview()
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        TransactionEntryScreenState.POSITION_REVIEW -> {
            TransactionBatchReview(
                productName = uiState.selectedProduct?.name ?: "",
                batches = uiState.currentBatches,
                grossWeight = uiState.currentGrossWeight,
                totalTareCount = uiState.currentTotalTareCount,
                tareWeightPerUnit = uiState.tareWeightPerUnit,
                totalTareWeight = uiState.currentTotalTareWeight,
                netWeight = uiState.currentNetWeight,
                pricePerKg = uiState.currentPrice,
                totalAmount = uiState.currentBatchTotalAmount,
                availableWeight = uiState.availableWeight,
                showInventoryWarning = config.transactionType == TransactionType.SALE && uiState.showInventoryWarning,
                canAdd = uiState.canAddBatchPosition,
                onTareWeightChange = callbacks.onTareWeightPerUnitChange,
                onPriceChange = callbacks.onPriceChange,
                onPriceFocused = callbacks.onPriceFocused,
                onAddPosition = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    callbacks.onAddBatchPositionAndContinue()
                },
                onEditBatch = callbacks.onUpdateBatch,
                onRemoveBatch = callbacks.onRemoveBatch,
                modifier = Modifier.fillMaxSize()
            )
        }

        TransactionEntryScreenState.POSITIONS_LIST -> {
            TransactionPositionsList(
                positions = uiState.positions,
                notes = uiState.notes,
                totalWeight = uiState.totalWeight,
                totalAmount = uiState.totalAmount,
                canFinalize = uiState.canFinalize,
                isEditing = isEditing,
                availableLocations = uiState.availableLocations,
                selectedLocationId = uiState.selectedLocationId,
                transactionType = config.transactionType,
                notesLabel = config.notesLabel,
                finalizeButtonText = if (isEditing) config.editButtonText else config.finalizeButtonText,
                onLocationChange = callbacks.onSelectLocation,
                onNotesChange = callbacks.onNotesChange,
                onRemovePosition = callbacks.onRemovePosition,
                onEditPosition = callbacks.onStartEditPosition,
                onAddAnother = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    callbacks.onAddAnotherProduct()
                },
                onFinalize = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    callbacks.onFinalize()
                },
                onCancel = callbacks.onCancel,
                modifier = Modifier.fillMaxSize()
            )
        }

        TransactionEntryScreenState.UNIFIED_ENTRY -> {
            TransactionEntryRegularTabletContent(
                uiState = uiState,
                transactionType = config.transactionType,
                onProductSelect = callbacks.onSelectProductForEntry,
                onProductOrderChanged = callbacks.onProductOrderChanged,
                onKeypadInput = callbacks.onKeypadInput,
                onKeypadDecimal = callbacks.onKeypadDecimal,
                onKeypadBackspace = callbacks.onKeypadBackspace,
                onNextInputField = callbacks.onNextInputField,
                onSelectInputFieldAndClear = callbacks.onSelectInputFieldAndClear,
                onAddPosition = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    callbacks.onAddPosition()
                },
                onSelectPosition = callbacks.onSelectTabletPosition,
                onRemovePosition = callbacks.onRemovePosition,
                onNotesChange = callbacks.onNotesChange,
                inventoryMap = inventoryMap,
                modifier = Modifier.fillMaxSize()
            )
        }

        TransactionEntryScreenState.UNIFIED_BATCH -> {
            TransactionEntryBatchTabletContent(
                uiState = uiState,
                transactionType = config.transactionType,
                onProductSelect = callbacks.onSelectProduct,
                onProductOrderChanged = callbacks.onProductOrderChanged,
                onKeypadInput = callbacks.onKeypadInput,
                onKeypadDecimal = callbacks.onKeypadDecimal,
                onKeypadBackspace = callbacks.onKeypadBackspace,
                onNextInputField = callbacks.onNextInputField,
                onSelectInputFieldAndClear = callbacks.onSelectInputFieldAndClear,
                onAddBatch = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    callbacks.onAddBatch()
                },
                onRemoveBatch = callbacks.onRemoveBatch,
                onSelectBatch = callbacks.onSelectTabletBatch,
                onSelectPosition = callbacks.onReviewPositionWeightings,
                onRemovePosition = callbacks.onRemovePosition,
                onNotesChange = callbacks.onNotesChange,
                inventoryMap = inventoryMap,
                modifier = Modifier.fillMaxSize()
            )
        }

        TransactionEntryScreenState.SUMMARY -> {
            // Handled above as overlay
        }
    }
}

// ==================== Helper Functions ====================

private fun handleBackPress(
    uiState: TransactionEntryUiState,
    callbacks: TransactionEntryCallbacks
) {
    when (uiState.screenState) {
        TransactionEntryScreenState.PRODUCT_GRID -> {
            if (uiState.positions.isEmpty()) {
                callbacks.onCancel()
            } else {
                callbacks.onBackToGrid()
            }
        }
        TransactionEntryScreenState.WEIGHT_ENTRY -> callbacks.onBackToGrid()
        TransactionEntryScreenState.WEIGHING -> callbacks.onBackToGrid()
        TransactionEntryScreenState.POSITION_REVIEW -> callbacks.onBackToWeighing()
        TransactionEntryScreenState.POSITIONS_LIST -> callbacks.onCancel()
        TransactionEntryScreenState.UNIFIED_ENTRY -> callbacks.onCancel()
        TransactionEntryScreenState.UNIFIED_BATCH -> callbacks.onCancel()
        TransactionEntryScreenState.SUMMARY -> callbacks.onDismissSummary()
    }
}

private fun getTopBarTitle(
    uiState: TransactionEntryUiState,
    config: TransactionEntryConfig,
    isEditing: Boolean
): String {
    return when (uiState.screenState) {
        TransactionEntryScreenState.PRODUCT_GRID ->
            if (isEditing) config.editingTitle else "Оберіть товар"
        TransactionEntryScreenState.WEIGHT_ENTRY ->
            uiState.selectedProduct?.name ?: "Введіть дані"
        TransactionEntryScreenState.WEIGHING ->
            uiState.selectedProduct?.name ?: "Зважування"
        TransactionEntryScreenState.POSITION_REVIEW ->
            "Перевірка позиції"
        TransactionEntryScreenState.POSITIONS_LIST ->
            "Позиції (${uiState.positions.size})"
        TransactionEntryScreenState.UNIFIED_ENTRY ->
            if (isEditing) config.editingTitle else config.newEntryTitle
        TransactionEntryScreenState.UNIFIED_BATCH ->
            if (isEditing) config.editingTitle else config.batchModeTitle
        TransactionEntryScreenState.SUMMARY ->
            if (isEditing) "Виправлення" else "Підсумок"
    }
}

private fun getStepLabels(isBatchMode: Boolean): List<String> {
    return if (isBatchMode) {
        listOf("Товар", "Зважування", "Перевірка", "Позиції")
    } else {
        listOf("Товар", "Вага", "Позиції")
    }
}

private fun getCurrentStep(uiState: TransactionEntryUiState, isBatchMode: Boolean): Int {
    return when (uiState.screenState) {
        TransactionEntryScreenState.PRODUCT_GRID -> 1
        TransactionEntryScreenState.WEIGHT_ENTRY -> 2
        TransactionEntryScreenState.WEIGHING -> 2
        TransactionEntryScreenState.POSITION_REVIEW -> 3
        TransactionEntryScreenState.POSITIONS_LIST -> if (isBatchMode) 4 else 3
        TransactionEntryScreenState.UNIFIED_ENTRY -> 1
        TransactionEntryScreenState.UNIFIED_BATCH -> 1
        TransactionEntryScreenState.SUMMARY -> if (isBatchMode) 4 else 3
    }
}

private fun shouldShowBackToGrid(uiState: TransactionEntryUiState): Boolean {
    return uiState.screenState != TransactionEntryScreenState.PRODUCT_GRID &&
           uiState.screenState != TransactionEntryScreenState.SUMMARY &&
           uiState.screenState != TransactionEntryScreenState.UNIFIED_ENTRY &&
           uiState.screenState != TransactionEntryScreenState.UNIFIED_BATCH
}

private fun shouldShowStepIndicator(uiState: TransactionEntryUiState): Boolean {
    return uiState.screenState != TransactionEntryScreenState.SUMMARY &&
           uiState.screenState != TransactionEntryScreenState.UNIFIED_ENTRY &&
           uiState.screenState != TransactionEntryScreenState.UNIFIED_BATCH
}
