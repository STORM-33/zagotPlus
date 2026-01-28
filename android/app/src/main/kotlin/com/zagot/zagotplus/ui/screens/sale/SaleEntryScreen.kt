package com.zagot.zagotplus.ui.screens.sale

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.layout.imePadding
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.ui.components.ReorderableProductGrid
import com.zagot.zagotplus.ui.components.StepIndicator
import com.zagot.zagotplus.ui.components.adaptiveButtonHeight
import com.zagot.zagotplus.ui.components.adaptiveDisplayScale
import com.zagot.zagotplus.ui.components.adaptiveHorizontalPadding
import com.zagot.zagotplus.ui.components.adaptiveItemSpacing
import com.zagot.zagotplus.ui.components.adaptivePadding
import com.zagot.zagotplus.ui.components.adaptivePrimaryButtonHeight
import com.zagot.zagotplus.ui.components.adaptiveMaxButtonWidth
import com.zagot.zagotplus.ui.components.adaptiveMaxInputWidth
import com.zagot.zagotplus.ui.components.isTablet
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleEntryScreen(
    onNavigateBack: () -> Unit,
    editingBatchId: String? = null,
    mode: com.zagot.zagotplus.ui.navigation.SaleMode = com.zagot.zagotplus.ui.navigation.SaleMode.WHOLESALE,
    viewModel: SaleEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current
    val isTabletDevice = isTablet()

    // Set tablet mode in ViewModel once at composition start
    LaunchedEffect(isTabletDevice) {
        viewModel.setTabletMode(isTabletDevice)
    }

    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) {
            viewModel.onNavigationHandled()
            onNavigateBack()
        }
    }

    // Handle Android back button
    BackHandler {
        when (uiState.screenState) {
            SaleEntryScreenState.PRODUCT_GRID -> {
                if (uiState.positions.isEmpty()) {
                    viewModel.cancel()
                } else {
                    viewModel.backToGrid()
                }
            }
            SaleEntryScreenState.WEIGHT_ENTRY -> viewModel.backToGrid()
            SaleEntryScreenState.WEIGHING -> viewModel.backToGrid()
            SaleEntryScreenState.POSITION_REVIEW -> viewModel.backToWeighing()
            SaleEntryScreenState.POSITIONS_LIST -> viewModel.cancel()
            SaleEntryScreenState.UNIFIED_ENTRY -> viewModel.cancel()  // Tablet: same as PRODUCT_GRID
            SaleEntryScreenState.SUMMARY -> viewModel.dismissSummary()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    val isEditing = uiState.editingBatchId != null
    val topBarTitle = when (uiState.screenState) {
        SaleEntryScreenState.PRODUCT_GRID -> if (isEditing) "Редагування" else "Оберіть товар"
        SaleEntryScreenState.WEIGHT_ENTRY -> uiState.selectedProduct?.name ?: "Введіть дані"
        SaleEntryScreenState.WEIGHING -> uiState.selectedProduct?.name ?: "Зважування"
        SaleEntryScreenState.POSITION_REVIEW -> "Перевірка позиції"
        SaleEntryScreenState.POSITIONS_LIST -> "Позиції (${uiState.positions.size})"
        SaleEntryScreenState.UNIFIED_ENTRY -> if (isEditing) "Редагування" else "Новий продаж"  // Tablet
        SaleEntryScreenState.SUMMARY -> if (isEditing) "Виправлення" else "Підсумок"
    }

    // Step indicator configuration
    val stepLabels = listOf("Товар", "Вага", "Позиції")
    val currentStep = when (uiState.screenState) {
        SaleEntryScreenState.PRODUCT_GRID -> 1
        SaleEntryScreenState.WEIGHT_ENTRY -> 2  // Regular mode: weight entry
        SaleEntryScreenState.WEIGHING -> 2
        SaleEntryScreenState.POSITION_REVIEW -> 2  // Part of weighing step
        SaleEntryScreenState.POSITIONS_LIST -> 3
        SaleEntryScreenState.UNIFIED_ENTRY -> 1  // Tablet: no step indicator shown anyway
        SaleEntryScreenState.SUMMARY -> 3  // Summary uses same step as positions (no separate confirmation)
    }

    val showTopBar = uiState.screenState != SaleEntryScreenState.SUMMARY

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                when (uiState.screenState) {
                                    SaleEntryScreenState.PRODUCT_GRID -> {
                                        if (uiState.positions.isEmpty()) {
                                            viewModel.cancel()
                                        } else {
                                            viewModel.backToGrid()
                                        }
                                    }
                                    SaleEntryScreenState.WEIGHT_ENTRY -> viewModel.backToGrid()
                                    SaleEntryScreenState.WEIGHING -> viewModel.backToGrid()
                                    SaleEntryScreenState.POSITION_REVIEW -> viewModel.backToWeighing()
                                    SaleEntryScreenState.POSITIONS_LIST -> viewModel.addAnotherProduct()
                                    SaleEntryScreenState.UNIFIED_ENTRY -> viewModel.cancel()  // Tablet
                                    SaleEntryScreenState.SUMMARY -> viewModel.dismissSummary()
                                }
                            }
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                        }
                    },
                    actions = {
                        // Tablet mode: show Cancel and Finalize buttons in top bar
                        if (uiState.screenState == SaleEntryScreenState.UNIFIED_ENTRY) {
                            OutlinedButton(
                                onClick = { viewModel.cancel() },
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
                                onClick = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.finalize()
                                },
                                enabled = uiState.canFinalize,
                                modifier = Modifier
                                    .padding(end = 16.dp)
                                    .height(52.dp)
                                    .widthIn(min = 180.dp),
                                contentPadding = PaddingValues(horizontal = 32.dp)
                            ) {
                                Text(
                                    if (isEditing) "РЕДАГУВАТИ" else "ПРОДАТИ",
                                    style = MaterialTheme.typography.titleMedium
                                )
                            }
                        }
                    }
                )
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Step indicator (show for phone wizard states only, not on tablet or summary)
            if (uiState.screenState != SaleEntryScreenState.SUMMARY &&
                uiState.screenState != SaleEntryScreenState.UNIFIED_ENTRY) {
                StepIndicator(
                    currentStep = currentStep,
                    totalSteps = stepLabels.size,
                    stepLabels = stepLabels
                )
            }

            Box(
                modifier = Modifier.fillMaxSize()
            ) {
            if (uiState.screenState == SaleEntryScreenState.SUMMARY) {
                SaleSummaryOverlay(
                    positions = uiState.positions,
                    totalWeight = uiState.totalWeight,
                    totalAmount = uiState.totalAmount,
                    notes = uiState.notes,
                    onExit = viewModel::exitFromSummary
                )
            } else {
                when {
                    uiState.isLoading -> {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
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
                        when (state) {
                            SaleEntryScreenState.UNIFIED_ENTRY -> {
                                // Tablet: three-column unified layout - route based on mode
                                when (uiState.saleMode) {
                                    com.zagot.zagotplus.ui.navigation.SaleMode.REGULAR -> {
                                        // Regular mode: simple single-weight flow
                                        SaleEntryRegularTabletContent(
                                            uiState = uiState,
                                            onProductSelect = viewModel::selectProduct,
                                            onProductOrderChanged = viewModel::onProductOrderChanged,
                                            onKeypadInput = viewModel::onKeypadInput,
                                            onKeypadDecimal = viewModel::onKeypadDecimal,
                                            onKeypadBackspace = viewModel::onKeypadBackspace,
                                            onNextInputField = viewModel::onNextInputField,
                                            onSelectInputFieldAndClear = viewModel::selectInputFieldAndClear,
                                            onAddPosition = viewModel::addRegularPositionAndContinue,
                                            onRemovePosition = viewModel::removePosition,
                                            onNotesChange = viewModel::onNotesChange,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                    com.zagot.zagotplus.ui.navigation.SaleMode.WHOLESALE -> {
                                        // Wholesale mode: batch weighing with tare tracking
                                        SaleEntryTabletContent(
                                            uiState = uiState,
                                            onProductSelect = viewModel::selectProduct,
                                            onProductOrderChanged = viewModel::onProductOrderChanged,
                                            onKeypadInput = viewModel::onKeypadInput,
                                            onKeypadDecimal = viewModel::onKeypadDecimal,
                                            onKeypadBackspace = viewModel::onKeypadBackspace,
                                            onNextInputField = viewModel::onNextInputField,
                                            onSelectInputFieldAndClear = viewModel::selectInputFieldAndClear,
                                            onAddBatch = viewModel::addBatch,
                                            onRemoveBatch = viewModel::removeBatch,
                                            onSelectBatch = viewModel::selectTabletBatch,
                                            onAddPosition = viewModel::addPositionAndContinue,
                                            onSelectPosition = viewModel::reviewPositionWeightings,
                                            onRemovePosition = viewModel::removePosition,
                                            onNotesChange = viewModel::onNotesChange,
                                            modifier = Modifier.fillMaxSize()
                                        )
                                    }
                                }
                            }
                            SaleEntryScreenState.PRODUCT_GRID -> {
                                SaleProductGrid(
                                    products = uiState.products,
                                    inventory = uiState.inventory,
                                    positions = uiState.positions,
                                    onProductClick = viewModel::selectProduct,
                                    onOrderChanged = viewModel::onProductOrderChanged,
                                    onProceedToPositions = { viewModel.finalize() },
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            SaleEntryScreenState.WEIGHT_ENTRY -> {
                                RegularWeightEntryScreen(
                                    productName = uiState.selectedProduct?.name ?: "",
                                    currentWeight = uiState.currentWeight,
                                    currentPrice = uiState.pricePerKg,
                                    total = uiState.regularModeTotal,
                                    availableWeight = uiState.availableWeight,
                                    showInventoryWarning = uiState.currentWeight.toBigDecimalOrNull()?.let {
                                        it > uiState.availableWeight && uiState.availableWeight >= BigDecimal.ZERO
                                    } == true,
                                    canAdd = uiState.canAddRegularPosition,
                                    onWeightChange = viewModel::onWeightChange,
                                    onPriceChange = viewModel::onPriceChange,
                                    onPriceFocused = viewModel::onPriceFocused,
                                    onAddPosition = viewModel::addRegularPositionAndContinue,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            SaleEntryScreenState.WEIGHING -> {
                                WeighingScreen(
                                    productName = uiState.selectedProduct?.name ?: "",
                                    currentWeight = uiState.currentWeight,
                                    currentTareCount = uiState.currentTareCount,
                                    batches = uiState.currentBatches,
                                    grossWeight = uiState.currentGrossWeight,
                                    canAddBatch = uiState.canAddBatch,
                                    canProceed = uiState.canProceedToReview,
                                    onWeightChange = viewModel::onWeightChange,
                                    onTareCountChange = viewModel::onTareCountChange,
                                    onAddBatch = viewModel::addBatch,
                                    onRemoveBatch = viewModel::removeBatch,
                                    onProceed = viewModel::proceedToReview,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            SaleEntryScreenState.POSITION_REVIEW -> {
                                PositionReviewScreen(
                                    productName = uiState.selectedProduct?.name ?: "",
                                    batches = uiState.currentBatches,
                                    grossWeight = uiState.currentGrossWeight,
                                    totalTareCount = uiState.currentTotalTareCount,
                                    tareWeightPerUnit = uiState.tareWeightPerUnit,
                                    totalTareWeight = uiState.currentTotalTareWeight,
                                    netWeight = uiState.currentNetWeight,
                                    pricePerKg = uiState.pricePerKg,
                                    totalAmount = uiState.currentTotalAmount,
                                    availableWeight = uiState.availableWeight,
                                    showInventoryWarning = uiState.showInventoryWarning,
                                    canAdd = uiState.canAddPosition,
                                    onTareWeightChange = viewModel::onTareWeightPerUnitChange,
                                    onPriceChange = viewModel::onPriceChange,
                                    onPriceFocused = viewModel::onPriceFocused,
                                    onAddPosition = viewModel::addPositionAndContinue,
                                    onEditBatch = viewModel::updateBatch,
                                    onRemoveBatch = viewModel::removeBatch,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            SaleEntryScreenState.POSITIONS_LIST -> {
                                PositionsListScreen(
                                    positions = uiState.positions,
                                    totalWeight = uiState.totalWeight,
                                    totalAmount = uiState.totalAmount,
                                    notes = uiState.notes,
                                    canFinalize = uiState.canFinalize,
                                    isEditing = isEditing,
                                    availableLocations = uiState.availableLocations,
                                    selectedLocationId = uiState.selectedLocationId,
                                    onLocationChange = viewModel::selectLocation,
                                    onNotesChange = viewModel::onNotesChange,
                                    onRemovePosition = viewModel::removePosition,
                                    onEditPosition = viewModel::startEditPosition,
                                    onAddAnother = viewModel::addAnotherProduct,
                                    onFinalize = viewModel::finalize,
                                    onCancel = viewModel::cancel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            SaleEntryScreenState.SUMMARY -> {
                                // Handled above as overlay
                            }
                        }
                        }
                    }
                }
            }
        }
        }

        // Edit position dialog
        uiState.editingPosition?.let { position ->
            EditSalePositionDialog(
                position = position,
                onDismiss = viewModel::cancelEditPosition,
                onConfirm = { tareWeight, price ->
                    viewModel.updatePosition(position.id, tareWeight, price)
                },
                onUpdateBatch = { batchId, weight, tareCount ->
                    viewModel.updatePositionBatch(position.id, batchId, weight, tareCount)
                },
                onDeleteBatch = { batchId ->
                    viewModel.deletePositionBatch(position.id, batchId)
                }
            )
        }

        // Exit confirmation dialog
        if (uiState.showExitConfirmation) {
            AlertDialog(
                onDismissRequest = viewModel::dismissExitConfirmation,
                title = { Text(if (isEditing) "Скасувати редагування?" else "Скасувати продаж?") },
                text = { Text("Всі введені дані буде втрачено.") },
                confirmButton = {
                    TextButton(onClick = viewModel::confirmExit) {
                        Text("Так, вийти")
                    }
                },
                dismissButton = {
                    TextButton(onClick = viewModel::dismissExitConfirmation) {
                        Text("Продовжити")
                    }
                }
            )
        }
    }
}

@Composable
private fun SaleProductGrid(
    products: List<Product>,
    inventory: List<com.zagot.zagotplus.domain.model.InventoryItem>,
    positions: List<SalePosition>,
    onProductClick: (Product) -> Unit,
    onOrderChanged: (List<UUID>) -> Unit,
    onProceedToPositions: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Create inventory map for the grid
    val inventoryMap = remember(inventory) {
        inventory.associate { it.productId to it.totalWeightKg }
    }

    Column(modifier = modifier) {
        ReorderableProductGrid(
            products = products,
            onProductClick = onProductClick,
            onOrderChanged = onOrderChanged,
            modifier = Modifier.weight(1f),
            showPrice = false,
            inventoryMap = inventoryMap
        )

        // Show proceed button if there are already positions
        if (positions.isNotEmpty()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(16.dp)
            ) {
                Text(
                    text = "${positions.size} позицій додано",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(8.dp))
                Button(
                    onClick = onProceedToPositions,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Text("ПЕРЕЙТИ ДО ОФОРМЛЕННЯ", style = MaterialTheme.typography.labelLarge)
                }
            }
        }
    }
}

@Composable
private fun WeighingScreen(
    productName: String,
    currentWeight: String,
    currentTareCount: String,
    batches: List<SaleWeighingBatch>,
    grossWeight: BigDecimal,
    canAddBatch: Boolean,
    canProceed: Boolean,
    onWeightChange: (String) -> Unit,
    onTareCountChange: (String) -> Unit,
    onAddBatch: () -> Unit,
    onRemoveBatch: (String) -> Unit,
    onProceed: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }

    // Adaptive values for tablet/kiosk display
    val contentPadding = adaptivePadding()
    val buttonHeight = adaptiveButtonHeight()
    val primaryButtonHeight = adaptivePrimaryButtonHeight()
    val displayScale = adaptiveDisplayScale()
    val isTabletDevice = isTablet()
    val maxButtonWidth = adaptiveMaxButtonWidth()
    val maxInputWidth = adaptiveMaxInputWidth()

    // Auto-focus weight field
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    val horizontalPadding = adaptiveHorizontalPadding()

    // Running total card composable - reused in both layouts
    @Composable
    fun RunningTotalCard(cardModifier: Modifier = Modifier) {
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(if (isTabletDevice) 16.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = productName,
                    style = if (isTabletDevice) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(if (isTabletDevice) 6.dp else 4.dp))
                Text(
                    text = "${decimalFormat.format(grossWeight)} кг",
                    style = if (isTabletDevice) MaterialTheme.typography.displayMedium.copy(
                        fontSize = MaterialTheme.typography.displayMedium.fontSize * displayScale.toFloat()
                    ) else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${batches.size} зважувань (брутто)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }

    // Input fields composable - reused in both layouts
    @Composable
    fun InputFields(fieldModifier: Modifier = Modifier) {
        // Constrain input and button widths on tablet
        val inputModifier = if (isTabletDevice && maxInputWidth != null) {
            Modifier.widthIn(max = maxInputWidth)
        } else {
            Modifier.fillMaxWidth()
        }
        val btnModifier = if (isTabletDevice && maxButtonWidth != null) {
            Modifier.widthIn(max = maxButtonWidth)
        } else {
            Modifier.fillMaxWidth()
        }

        Column(
            modifier = fieldModifier,
            horizontalAlignment = if (isTabletDevice) Alignment.CenterHorizontally else Alignment.Start
        ) {
            // Weight input
            OutlinedTextField(
                value = currentWeight,
                onValueChange = onWeightChange,
                label = { Text("Вага (кг)") },
                textStyle = if (isTabletDevice) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = inputModifier.fillMaxWidth().focusRequester(focusRequester)
            )

            Spacer(modifier = Modifier.height(if (isTabletDevice) 20.dp else 16.dp))

            // Tare count input
            OutlinedTextField(
                value = currentTareCount,
                onValueChange = onTareCountChange,
                label = { Text("Кількість мішків/ящиків") },
                textStyle = if (isTabletDevice) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
                singleLine = true,
                modifier = inputModifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(if (isTabletDevice) 20.dp else 16.dp))

            // Add batch button - constrained width
            Button(
                onClick = onAddBatch,
                enabled = canAddBatch,
                modifier = btnModifier.fillMaxWidth().height(buttonHeight as androidx.compose.ui.unit.Dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Додати")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    "Додати зважування",
                    style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    // Batches list composable - reused in both layouts
    @Composable
    fun BatchesList(listModifier: Modifier = Modifier) {
        if (batches.isNotEmpty()) {
            Column(modifier = listModifier) {
                Text(text = "Зважування:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(batches, key = { it.id }) { batch ->
                        WeighingBatchItem(batch = batch, onRemove = { onRemoveBatch(batch.id) })
                    }
                }
            }
        }
    }

    // Proceed button composable - reused in both layouts
    @Composable
    fun ProceedButton(buttonModifier: Modifier = Modifier) {
        if (canProceed) {
            // Constrain button width on tablet
            val constrainedModifier = if (isTabletDevice && maxButtonWidth != null) {
                buttonModifier.widthIn(max = maxButtonWidth)
            } else {
                buttonModifier
            }
            Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Button(
                    onClick = onProceed,
                    modifier = constrainedModifier.fillMaxWidth().height(primaryButtonHeight as androidx.compose.ui.unit.Dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Text(
                        "ДАЛІ →",
                        style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                    )
                }
            }
        }
    }

    if (isTabletDevice) {
        // Tablet: Two-column layout
        Row(
            modifier = modifier
                .padding(horizontal = horizontalPadding, vertical = contentPadding)
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Left column: Input fields (55%)
            Column(
                modifier = Modifier.weight(0.55f)
            ) {
                InputFields(Modifier.fillMaxWidth())
            }

            // Right column: Running total + batches + proceed (45%)
            Column(
                modifier = Modifier.weight(0.45f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                RunningTotalCard(Modifier.fillMaxWidth())
                BatchesList(Modifier.fillMaxWidth().weight(1f, fill = false))
                ProceedButton(Modifier.fillMaxWidth())
            }
        }
    } else {
        // Phone: Single column layout
        Column(
            modifier = modifier
                .padding(horizontal = horizontalPadding, vertical = contentPadding)
                .imePadding()
        ) {
            RunningTotalCard(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            InputFields(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))

            // Batches list
            if (batches.isNotEmpty()) {
                Text(text = "Зважування:", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Medium)
                Spacer(modifier = Modifier.height(8.dp))

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(batches, key = { it.id }) { batch ->
                        WeighingBatchItem(batch = batch, onRemove = { onRemoveBatch(batch.id) })
                    }
                }
            } else {
                Spacer(modifier = Modifier.weight(1f))
            }

            // Proceed button
            if (canProceed) {
                Spacer(modifier = Modifier.height(16.dp))
                ProceedButton(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun WeighingBatchItem(
    batch: SaleWeighingBatch,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${decimalFormat.format(batch.grossWeightKg)} кг",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${batch.tareCount} шт",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Видалити", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun PositionReviewScreen(
    productName: String,
    batches: List<SaleWeighingBatch>,
    grossWeight: BigDecimal,
    totalTareCount: Int,
    tareWeightPerUnit: String,
    totalTareWeight: BigDecimal,
    netWeight: BigDecimal,
    pricePerKg: String,
    totalAmount: BigDecimal?,
    availableWeight: BigDecimal,
    showInventoryWarning: Boolean,
    canAdd: Boolean,
    onTareWeightChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onPriceFocused: () -> Unit,
    onAddPosition: () -> Unit,
    onEditBatch: (String, BigDecimal, Int) -> Unit,
    onRemoveBatch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val currencyFormat = remember { DecimalFormat("#,##0") }
    var showHistoryDialog by remember { mutableStateOf(false) }
    var editingBatch by remember { mutableStateOf<SaleWeighingBatch?>(null) }

    // Adaptive values for tablet/kiosk display
    val contentPadding = adaptivePadding()
    val primaryButtonHeight = adaptivePrimaryButtonHeight()
    val displayScale = adaptiveDisplayScale()
    val isTabletDevice = isTablet()
    val maxButtonWidth = adaptiveMaxButtonWidth()
    val maxInputWidth = adaptiveMaxInputWidth()

    // Track price focus state
    var priceHasBeenFocused by remember { mutableStateOf(false) }

    // Weight summary card composable - reused in both layouts
    @Composable
    fun WeightSummaryCard(cardModifier: Modifier = Modifier) {
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(if (isTabletDevice) 16.dp else 10.dp)) {
                // Weighing summary row with history button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable { showHistoryDialog = true }
                            .padding(end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.History,
                            contentDescription = "Історія зважувань",
                            modifier = Modifier.size(if (isTabletDevice) 24.dp else 18.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            "${batches.size} зв. • ${decimalFormat.format(grossWeight)} кг",
                            style = if (isTabletDevice) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Text(
                        "брутто",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(if (isTabletDevice) 8.dp else 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Тара ($totalTareCount шт):",
                        style = if (isTabletDevice) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodySmall
                    )
                    Text(
                        "-${decimalFormat.format(totalTareWeight)} кг",
                        color = MaterialTheme.colorScheme.error,
                        style = if (isTabletDevice) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
                    )
                }
                Spacer(modifier = Modifier.height(if (isTabletDevice) 8.dp else 4.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(if (isTabletDevice) 8.dp else 4.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Нетто:",
                        fontWeight = FontWeight.Bold,
                        style = if (isTabletDevice) MaterialTheme.typography.titleMedium else MaterialTheme.typography.bodyMedium
                    )
                    Text(
                        "${decimalFormat.format(netWeight)} кг",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall
                    )
                }
            }
        }
    }

    // Input fields composable - reused in both layouts
    @Composable
    fun InputFields(fieldModifier: Modifier = Modifier) {
        // Constrain input widths on tablet
        val inputModifier = if (isTabletDevice && maxInputWidth != null) {
            Modifier.widthIn(max = maxInputWidth)
        } else {
            Modifier.fillMaxWidth()
        }

        Column(
            modifier = fieldModifier,
            horizontalAlignment = if (isTabletDevice) Alignment.CenterHorizontally else Alignment.Start
        ) {
            // Tare weight per unit input
            OutlinedTextField(
                value = tareWeightPerUnit,
                onValueChange = onTareWeightChange,
                label = { Text("Вага тари (кг)") },
                textStyle = if (isTabletDevice) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
                singleLine = true,
                modifier = inputModifier.fillMaxWidth()
            )

            Spacer(modifier = Modifier.height(if (isTabletDevice) 20.dp else 16.dp))

            // Price input
            OutlinedTextField(
                value = pricePerKg,
                onValueChange = onPriceChange,
                label = { Text("Ціна за кг (₴)") },
                textStyle = if (isTabletDevice) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
                singleLine = true,
                modifier = inputModifier
                    .fillMaxWidth()
                    .onFocusChanged { focusState ->
                        if (focusState.isFocused && !priceHasBeenFocused) {
                            priceHasBeenFocused = true
                            onPriceFocused()
                        }
                    }
            )
        }
    }

    // Inventory warning composable - reused in both layouts
    @Composable
    fun InventoryWarningCard() {
        if (showInventoryWarning) {
            Spacer(modifier = Modifier.height(if (isTabletDevice) 12.dp else 8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(if (isTabletDevice) 16.dp else 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(if (isTabletDevice) 12.dp else 8.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = "Попередження", tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        text = "Перевищує залишок (${decimalFormat.format(availableWeight)} кг)!",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = if (isTabletDevice) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
    }

    // Total amount card composable - reused in both layouts
    @Composable
    fun TotalAmountCard(cardModifier: Modifier = Modifier) {
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(if (isTabletDevice) 20.dp else 12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    "Сума:",
                    style = if (isTabletDevice) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.titleMedium
                )
                Text(
                    text = totalAmount?.let { "₴${currencyFormat.format(it)}" } ?: "₴0",
                    style = if (isTabletDevice) MaterialTheme.typography.headlineMedium.copy(
                        fontSize = MaterialTheme.typography.headlineMedium.fontSize * displayScale.toFloat()
                    ) else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Add position button composable - reused in both layouts
    @Composable
    fun AddPositionButton(buttonModifier: Modifier = Modifier) {
        // Constrain button width on tablet
        val constrainedModifier = if (isTabletDevice && maxButtonWidth != null) {
            buttonModifier.widthIn(max = maxButtonWidth)
        } else {
            buttonModifier
        }
        Box(
            modifier = Modifier.fillMaxWidth(),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = onAddPosition,
                enabled = canAdd,
                modifier = constrainedModifier.fillMaxWidth().height(primaryButtonHeight as androidx.compose.ui.unit.Dp)
            ) {
                Text(
                    "ДОДАТИ ПОЗИЦІЮ",
                    style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                )
            }
        }
    }

    if (isTabletDevice) {
        // Tablet: Two-column layout
        Row(
            modifier = modifier
                .padding(contentPadding as androidx.compose.ui.unit.Dp)
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Left column: Weight summary + inputs (55%)
            Column(
                modifier = Modifier.weight(0.55f)
            ) {
                // Product header
                Text(
                    text = productName,
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(20.dp))
                WeightSummaryCard(Modifier.fillMaxWidth())
                Spacer(modifier = Modifier.height(20.dp))
                InputFields(Modifier.fillMaxWidth())
            }

            // Right column: Total amount + warning + button (45%)
            Column(
                modifier = Modifier.weight(0.45f),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                TotalAmountCard(Modifier.fillMaxWidth())
                InventoryWarningCard()
                Spacer(modifier = Modifier.weight(1f))
                AddPositionButton(Modifier.fillMaxWidth())
            }
        }
    } else {
        // Phone: Single column layout
        Column(
            modifier = modifier
                .padding(contentPadding as androidx.compose.ui.unit.Dp)
                .imePadding()
        ) {
            // Product header
            Text(
                text = productName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(12.dp))
            WeightSummaryCard(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            InputFields(Modifier.fillMaxWidth())
            InventoryWarningCard()
            Spacer(modifier = Modifier.height(12.dp))
            TotalAmountCard(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.weight(1f))
            AddPositionButton(Modifier.fillMaxWidth())
        }
    }

    // Weighing history dialog
    if (showHistoryDialog && batches.isNotEmpty()) {
        WeighingHistoryDialog(
            batches = batches,
            grossWeight = grossWeight,
            onDismiss = { showHistoryDialog = false },
            onEditBatch = { batch ->
                showHistoryDialog = false
                editingBatch = batch
            }
        )
    }

    // Edit weighing dialog
    editingBatch?.let { batch ->
        EditWeighingDialog(
            batch = batch,
            canDelete = batches.size > 1,
            onDismiss = { editingBatch = null },
            onConfirm = { newWeight, newTareCount ->
                onEditBatch(batch.id, newWeight, newTareCount)
                editingBatch = null
            },
            onDelete = {
                onRemoveBatch(batch.id)
                editingBatch = null
            }
        )
    }
}

@Composable
private fun WeighingHistoryDialog(
    batches: List<SaleWeighingBatch>,
    grossWeight: BigDecimal,
    onDismiss: () -> Unit,
    onEditBatch: (SaleWeighingBatch) -> Unit
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text(
                    text = "Історія зважувань",
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = "${batches.size} зважувань • ${decimalFormat.format(grossWeight)} кг брутто",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        text = {
            LazyColumn(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(batches.size) { index ->
                    val batch = batches[index]
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onEditBatch(batch) },
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "#${index + 1}",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.width(36.dp)
                                )
                                Column {
                                    Text(
                                        text = "${decimalFormat.format(batch.grossWeightKg)} кг",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Text(
                                        text = "${batch.tareCount} шт тари",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            Text(
                                text = "редагувати",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити")
            }
        }
    )
}

@Composable
private fun EditWeighingDialog(
    batch: SaleWeighingBatch,
    canDelete: Boolean,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal, Int) -> Unit,
    onDelete: () -> Unit
) {
    var weight by remember { mutableStateOf(batch.grossWeightKg.toPlainString()) }
    var tareCount by remember { mutableStateOf(batch.tareCount.toString()) }
    val isValid = weight.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true &&
            (tareCount.isBlank() || tareCount.toIntOrNull()?.let { it >= 0 } == true)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редагувати зважування") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) weight = it },
                    label = { Text("Вага (кг)") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tareCount,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d+$"))) tareCount = it },
                    label = { Text("Кількість мішків/ящиків") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (canDelete) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Видалити зважування")
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val newWeight = weight.toBigDecimalOrNull() ?: return@TextButton
                    val newTareCount = if (tareCount.isBlank()) 0 else tareCount.toIntOrNull() ?: return@TextButton
                    onConfirm(newWeight, newTareCount)
                },
                enabled = isValid
            ) {
                Text("Зберегти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PositionsListScreen(
    positions: List<SalePosition>,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    notes: String,
    canFinalize: Boolean,
    isEditing: Boolean = false,
    availableLocations: List<Location> = emptyList(),
    selectedLocationId: UUID? = null,
    onLocationChange: (UUID?) -> Unit = {},
    onNotesChange: (String) -> Unit,
    onRemovePosition: (String) -> Unit,
    onEditPosition: (SalePosition) -> Unit,
    onAddAnother: () -> Unit,
    onFinalize: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val currencyFormat = remember { DecimalFormat("#,##0") }

    // Adaptive values for tablet/kiosk display
    val horizontalPadding = adaptiveHorizontalPadding()
    val itemSpacing = adaptiveItemSpacing()
    val buttonHeight = adaptiveButtonHeight()
    val primaryButtonHeight = adaptivePrimaryButtonHeight()
    val displayScale = adaptiveDisplayScale()
    val isTabletDevice = isTablet()
    val maxButtonWidth = adaptiveMaxButtonWidth()

    // Positions list composable - reused in both layouts
    @Composable
    fun PositionsListContent(listModifier: Modifier = Modifier, showAddButton: Boolean = false) {
        LazyColumn(
            modifier = listModifier,
            contentPadding = PaddingValues(vertical = if (isTabletDevice) 20.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            items(positions, key = { it.id }) { position ->
                SalePositionItem(
                    position = position,
                    onRemove = { onRemovePosition(position.id) },
                    onClick = { onEditPosition(position) }
                )
            }

            if (showAddButton) {
                item {
                    Spacer(modifier = Modifier.height(12.dp))
                    // Constrained button width on tablet - left-aligned
                    val addButtonModifier = if (isTabletDevice && maxButtonWidth != null) {
                        Modifier.widthIn(max = maxButtonWidth)
                    } else {
                        Modifier.fillMaxWidth()
                    }
                    Box(
                        modifier = Modifier.fillMaxWidth(),
                        contentAlignment = if (isTabletDevice) Alignment.CenterStart else Alignment.Center
                    ) {
                        Button(
                            onClick = onAddAnother,
                            modifier = addButtonModifier.height(buttonHeight as androidx.compose.ui.unit.Dp),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Додати ще")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                "Додати ще товар",
                                style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                            )
                        }
                    }
                }
            }
        }
    }

    // Notes field composable
    @Composable
    fun NotesField(fieldModifier: Modifier = Modifier) {
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("Покупець / примітки") },
            textStyle = if (isTabletDevice) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            modifier = fieldModifier,
            minLines = if (isTabletDevice) 3 else 2,
            maxLines = if (isTabletDevice) 5 else 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
    }

    // Totals display composable
    @Composable
    fun TotalsDisplay(totalsModifier: Modifier = Modifier) {
        Card(
            modifier = totalsModifier,
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(if (isTabletDevice) 24.dp else 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Всього",
                    style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${decimalFormat.format(totalWeight)} кг",
                    style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "₴${currencyFormat.format(totalAmount)}",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = MaterialTheme.typography.headlineLarge.fontSize * displayScale.toFloat()
                    ),
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Action buttons composable
    @Composable
    fun ActionButtons(buttonsModifier: Modifier = Modifier) {
        // Constrain button widths on tablet
        val buttonWidthModifier = if (isTabletDevice && maxButtonWidth != null) {
            Modifier.widthIn(max = maxButtonWidth)
        } else {
            Modifier.fillMaxWidth()
        }

        Column(
            modifier = buttonsModifier,
            verticalArrangement = Arrangement.spacedBy(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Button(
                onClick = onFinalize,
                enabled = canFinalize,
                modifier = buttonWidthModifier.fillMaxWidth().height(primaryButtonHeight as androidx.compose.ui.unit.Dp)
            ) {
                Text(
                    text = if (isEditing) "РЕДАГУВАТИ" else "ПРОДАТИ",
                    style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                )
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = buttonWidthModifier.fillMaxWidth().height(buttonHeight as androidx.compose.ui.unit.Dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
            ) {
                Text("Скасувати", style = if (isTabletDevice) MaterialTheme.typography.titleMedium else MaterialTheme.typography.labelLarge)
            }
        }
    }

    if (isTabletDevice) {
        // Tablet: 60/40 split - List on left, Receipt sidebar on right
        Row(
            modifier = modifier
                .padding(horizontal = horizontalPadding)
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left column: Positions list + Add button (60%)
            Column(modifier = Modifier.weight(0.6f)) {
                PositionsListContent(
                    listModifier = Modifier.weight(1f).fillMaxWidth(),
                    showAddButton = true
                )
            }

            // Right column: Receipt-style checkout sidebar (40%)
            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Receipt header
                Text(
                    text = "Чек",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                // Location selector (only in edit mode)
                if (isEditing && availableLocations.isNotEmpty()) {
                    LocationSelector(
                        selectedLocationId = selectedLocationId,
                        locations = availableLocations,
                        onLocationChange = onLocationChange,
                        modifier = Modifier.fillMaxWidth()
                    )
                }

                NotesField(Modifier.fillMaxWidth())

                Spacer(modifier = Modifier.weight(1f))

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

                TotalsDisplay(Modifier.fillMaxWidth())

                // Action buttons anchored at bottom
                ActionButtons(Modifier.fillMaxWidth())
            }
        }
    } else {
        // Phone: Single column layout
        Column(modifier = modifier.imePadding()) {
            LazyColumn(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                items(positions, key = { it.id }) { position ->
                    SalePositionItem(
                        position = position,
                        onRemove = { onRemovePosition(position.id) },
                        onClick = { onEditPosition(position) }
                    )
                }

                item {
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = onAddAnother,
                        modifier = Modifier.fillMaxWidth().height(buttonHeight as androidx.compose.ui.unit.Dp),
                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = "Додати ще")
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Додати ще товар", style = MaterialTheme.typography.titleMedium)
                    }
                }

                item {
                    Spacer(modifier = Modifier.height(16.dp))

                    // Location selector (only in edit mode)
                    if (isEditing && availableLocations.isNotEmpty()) {
                        LocationSelector(
                            selectedLocationId = selectedLocationId,
                            locations = availableLocations,
                            onLocationChange = onLocationChange,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                    }

                    NotesField(Modifier.fillMaxWidth())
                }
            }

            // Footer with totals and actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = horizontalPadding, vertical = 16.dp)
            ) {
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(
                        "Всього:",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            "${decimalFormat.format(totalWeight)} кг",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            "₴${currencyFormat.format(totalAmount)}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(buttonHeight as androidx.compose.ui.unit.Dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                    ) {
                        Text("Скасувати", style = MaterialTheme.typography.labelLarge)
                    }
                    Button(
                        onClick = onFinalize,
                        enabled = canFinalize,
                        modifier = Modifier.weight(1f).height(buttonHeight)
                    ) {
                        Text(if (isEditing) "РЕДАГУВАТИ" else "ПРОДАТИ", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SalePositionItem(
    position: SalePosition,
    onRemove: () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product image thumbnail
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (position.product.imageUri != null) {
                    AsyncImage(
                        model = position.product.imageUri,
                        contentDescription = position.product.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = "Немає зображення",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    )
                }
            }

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = position.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${decimalFormat.format(position.netWeight)} кг × ₴${decimalFormat.format(position.pricePerKg)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "(${position.batches.size} зважувань)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                )
            }

            Text(
                text = "₴${decimalFormat.format(position.totalAmount)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(onClick = onRemove) {
                Icon(Icons.Filled.Close, contentDescription = "Видалити", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}

@Composable
private fun EditSalePositionDialog(
    position: SalePosition,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal, BigDecimal) -> Unit,
    onUpdateBatch: (String, BigDecimal, Int) -> Unit,
    onDeleteBatch: (String) -> Unit
) {
    var tareWeight by remember(position) { mutableStateOf(position.tareWeightPerUnit.toPlainString()) }
    var price by remember(position) { mutableStateOf(position.pricePerKg.toPlainString()) }
    var editingBatch by remember { mutableStateOf<SaleWeighingBatch?>(null) }
    var showHistoryDialog by remember { mutableStateOf(false) }

    val parsedTareWeight = tareWeight.toBigDecimalOrNull()
    val parsedPrice = price.toBigDecimalOrNull()
    val isValid = parsedTareWeight != null && parsedTareWeight >= BigDecimal.ZERO &&
                  parsedPrice != null && parsedPrice > BigDecimal.ZERO

    val decimalFormat = remember { DecimalFormat("#,##0.00") }

    // Calculate preview values
    val previewTotalTareWeight = if (parsedTareWeight != null) {
        parsedTareWeight.multiply(BigDecimal(position.totalTareCount))
    } else BigDecimal.ZERO
    val previewNetWeight = (position.grossWeight - previewTotalTareWeight).max(BigDecimal.ZERO)
    val previewTotalAmount = if (parsedPrice != null) {
        previewNetWeight.multiply(parsedPrice).setScale(2, java.math.RoundingMode.HALF_UP)
    } else BigDecimal.ZERO

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Редагувати: ${position.product.name}",
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Weighing history summary with button
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showHistoryDialog = true },
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.History,
                                contentDescription = "Історія зважувань",
                                modifier = Modifier.size(20.dp),
                                tint = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = "${position.batches.size} зв. • ${decimalFormat.format(position.grossWeight)} кг",
                                    style = MaterialTheme.typography.titleSmall,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = "брутто",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                        Text(
                            text = "деталі →",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                OutlinedTextField(
                    value = tareWeight,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) tareWeight = it },
                    label = { Text("Вага тари за шт (кг)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = price,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) price = it },
                    label = { Text("Ціна за кг (₴)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                HorizontalDivider()

                // Preview calculations
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Тара (${position.totalTareCount} шт):", style = MaterialTheme.typography.bodyMedium)
                        Text("-${decimalFormat.format(previewTotalTareWeight)} кг", color = MaterialTheme.colorScheme.error)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Нетто:", style = MaterialTheme.typography.titleMedium)
                        Text("${decimalFormat.format(previewNetWeight)} кг", fontWeight = FontWeight.Bold)
                    }
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Сума:", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "₴${decimalFormat.format(previewTotalAmount)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (parsedTareWeight != null && parsedPrice != null) {
                        onConfirm(parsedTareWeight, parsedPrice)
                    }
                },
                enabled = isValid
            ) {
                Text("Зберегти")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )

    // Weighing history dialog
    if (showHistoryDialog) {
        WeighingHistoryDialog(
            batches = position.batches,
            grossWeight = position.grossWeight,
            onDismiss = { showHistoryDialog = false },
            onEditBatch = { batch ->
                showHistoryDialog = false
                editingBatch = batch
            }
        )
    }

    // Nested dialog for editing individual batch
    editingBatch?.let { batch ->
        EditWeighingDialog(
            batch = batch,
            canDelete = position.batches.size > 1,
            onDismiss = { editingBatch = null },
            onConfirm = { newWeight, newTareCount ->
                onUpdateBatch(batch.id, newWeight, newTareCount)
                editingBatch = null
            },
            onDelete = {
                onDeleteBatch(batch.id)
                editingBatch = null
            }
        )
    }
}

@Composable
fun SaleSummaryOverlay(
    positions: List<SalePosition>,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    notes: String,
    onExit: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val currencyFormat = remember { DecimalFormat("#,##0") }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .clickable(onClick = onExit),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .clickable(onClick = onExit),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ЗБЕРЕЖЕНО",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Positions list
                LazyColumn(
                    modifier = Modifier.fillMaxWidth().weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(positions, key = { it.id }) { position ->
                        SummaryPositionItem(position = position)
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Totals
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Всього:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text("${decimalFormat.format(totalWeight)} кг", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
                }

                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Сума:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(
                        "₴${currencyFormat.format(totalAmount)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                if (notes.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        text = "Примітки:",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(text = notes, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.fillMaxWidth())
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Exit instruction
                Text(
                    text = "Натисніть будь-де, щоб вийти",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun SummaryPositionItem(
    position: SalePosition,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }

    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Product image thumbnail
        Box(
            modifier = Modifier
                .size(40.dp)
                .clip(RoundedCornerShape(6.dp))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center
        ) {
            if (position.product.imageUri != null) {
                AsyncImage(
                    model = position.product.imageUri,
                    contentDescription = position.product.name,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop
                )
            } else {
                Icon(
                    imageVector = Icons.Filled.Image,
                    contentDescription = "Немає зображення",
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                )
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(text = position.product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
            Text(
                text = "${decimalFormat.format(position.netWeight)} кг × ₴${decimalFormat.format(position.pricePerKg)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }

        Text(
            text = "₴${decimalFormat.format(position.totalAmount)}",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.primary
        )
    }
}


@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationSelector(
    selectedLocationId: UUID?,
    locations: List<Location>,
    onLocationChange: (UUID?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val selectedLocation = locations.find { it.id == selectedLocationId }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = selectedLocation?.name ?: "Не обрано",
            onValueChange = {},
            readOnly = true,
            label = { Text("Локація") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            locations.forEach { location ->
                DropdownMenuItem(
                    text = { Text(location.name) },
                    onClick = {
                        onLocationChange(location.id)
                        expanded = false
                    }
                )
            }
        }
    }
}

/**
 * Regular mode weight entry screen - simple weight + price entry (mirrors purchase flow).
 * No batches, no tare - just enter weight and price, then add position.
 */
@Composable
private fun RegularWeightEntryScreen(
    productName: String,
    currentWeight: String,
    currentPrice: String,
    total: BigDecimal?,
    availableWeight: BigDecimal,
    showInventoryWarning: Boolean,
    canAdd: Boolean,
    onWeightChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onPriceFocused: () -> Unit,
    onAddPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val currencyFormat = remember { DecimalFormat("#,##0") }
    val contentPadding = adaptivePadding()
    val primaryButtonHeight = adaptivePrimaryButtonHeight()

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(contentPadding as androidx.compose.ui.unit.Dp)
            .imePadding(),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Product name card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = productName,
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(16.dp)
            )
        }

        // Weight input
        OutlinedTextField(
            value = currentWeight,
            onValueChange = onWeightChange,
            label = { Text("Вага (кг)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        // Inventory info
        if (availableWeight > BigDecimal.ZERO) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Залишок:",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "${decimalFormat.format(availableWeight)} кг",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        // Inventory warning
        if (showInventoryWarning) {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onErrorContainer
                    )
                    Text(
                        text = "Увага: недостатньо товару на складі",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        // Price input
        var isPriceFocused by remember { mutableStateOf(false) }
        OutlinedTextField(
            value = currentPrice,
            onValueChange = onPriceChange,
            label = { Text("Ціна (₴/кг)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !isPriceFocused) {
                        onPriceFocused()
                        isPriceFocused = true
                    }
                    if (!focusState.isFocused) {
                        isPriceFocused = false
                    }
                },
            singleLine = true
        )

        // Total amount display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Сума:",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = total?.let { "₴${currencyFormat.format(it)}" } ?: "₴0",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Add button
        Button(
            onClick = onAddPosition,
            enabled = canAdd,
            modifier = Modifier
                .fillMaxWidth()
                .height(primaryButtonHeight as androidx.compose.ui.unit.Dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Додати позицію",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}
