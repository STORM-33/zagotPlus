package com.zagot.zagotplus.ui.screens.purchase

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
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.ui.components.EmptyState
import com.zagot.zagotplus.ui.components.EmptyStateIcons
import com.zagot.zagotplus.ui.components.PriceType
import com.zagot.zagotplus.ui.components.ReorderableProductGrid
import com.zagot.zagotplus.ui.components.StepIndicator
import com.zagot.zagotplus.ui.components.AdaptiveMasterDetail
import com.zagot.zagotplus.ui.components.AdaptiveTwoColumn
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
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseEntryScreen(
    onNavigateBack: () -> Unit,
    editingBatchId: String? = null,
    viewModel: PurchaseEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val haptic = LocalHapticFeedback.current

    // Batch for editing is now loaded via SavedStateHandle in ViewModel - no LaunchedEffect needed

    // Handle navigation
    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) {
            viewModel.onNavigationHandled()
            onNavigateBack()
        }
    }

    // Handle Android back button
    BackHandler {
        when (uiState.screenState) {
            PurchaseEntryScreenState.PRODUCT_GRID -> viewModel.cancel()
            PurchaseEntryScreenState.WEIGHT_ENTRY -> viewModel.backToGrid()
            PurchaseEntryScreenState.POSITIONS_LIST -> viewModel.cancel()
            PurchaseEntryScreenState.SUMMARY -> viewModel.dismissSummary()
        }
    }

    // Show error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    val isEditing = uiState.editingBatchId != null
    val topBarTitle = when (uiState.screenState) {
        PurchaseEntryScreenState.PRODUCT_GRID -> if (isEditing) "Редагування" else "Оберіть товар"
        PurchaseEntryScreenState.WEIGHT_ENTRY -> uiState.selectedProduct?.name ?: "Введіть дані"
        PurchaseEntryScreenState.POSITIONS_LIST -> "Позиції (${uiState.positions.size})"
        PurchaseEntryScreenState.SUMMARY -> if (isEditing) "Виправлення" else "Підсумок"
    }

    // Step indicator configuration
    val stepLabels = listOf("Товар", "Вага", "Позиції")
    val currentStep = when (uiState.screenState) {
        PurchaseEntryScreenState.PRODUCT_GRID -> 1
        PurchaseEntryScreenState.WEIGHT_ENTRY -> 2
        PurchaseEntryScreenState.POSITIONS_LIST -> 3
        PurchaseEntryScreenState.SUMMARY -> 3  // Summary uses same step as positions (no separate confirmation)
    }

    val showBackToGrid = uiState.screenState != PurchaseEntryScreenState.PRODUCT_GRID && 
                         uiState.screenState != PurchaseEntryScreenState.SUMMARY
    val showTopBar = uiState.screenState != PurchaseEntryScreenState.SUMMARY

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                if (showBackToGrid) {
                                    viewModel.backToGrid()
                                } else {
                                    viewModel.cancel()
                                }
                            }
                        ) {
                            Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
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
            // Step indicator (show for all states except SUMMARY)
            if (uiState.screenState != PurchaseEntryScreenState.SUMMARY) {
                StepIndicator(
                    currentStep = currentStep,
                    totalSteps = stepLabels.size,
                    stepLabels = stepLabels
                )
            }
            
            Box(
                modifier = Modifier.fillMaxSize()
            ) {
            // Show summary overlay if in SUMMARY state
            if (uiState.screenState == PurchaseEntryScreenState.SUMMARY) {
                PurchaseSummaryOverlay(
                    positions = uiState.positions,
                    notes = uiState.notes,
                    totalWeight = uiState.totalWeight,
                    totalAmount = uiState.totalAmount,
                    onExit = viewModel::exitFromSummary
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
                    when (state) {
                        PurchaseEntryScreenState.PRODUCT_GRID -> {
                            ReorderableProductGrid(
                                products = uiState.products,
                                onProductClick = viewModel::selectProduct,
                                onOrderChanged = viewModel::onProductOrderChanged,
                                modifier = Modifier.fillMaxSize(),
                                showPrice = true,
                                priceType = PriceType.BUY
                            )
                        }
                        PurchaseEntryScreenState.WEIGHT_ENTRY -> {
                            WeightEntry(
                                weight = uiState.effectiveWeight,
                                price = uiState.currentPrice,
                                total = uiState.currentTotal,
                                isScaleConnected = uiState.isScaleConnected,
                                isManualMode = uiState.isManualWeightMode,
                                canAdd = uiState.canAddPosition,
                                onWeightChange = viewModel::onWeightChange,
                                onPriceChange = viewModel::onPriceChange,
                                onPriceFocused = viewModel::onPriceFocused,
                                onToggleManualMode = viewModel::toggleManualWeightMode,
                                onAddPosition = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.addPosition()
                                },
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        PurchaseEntryScreenState.POSITIONS_LIST -> {
                            PositionsList(
                                positions = uiState.positions,
                                notes = uiState.notes,
                                totalWeight = uiState.totalWeight,
                                totalAmount = uiState.totalAmount,
                                canFinalize = uiState.canFinalize,
                                isEditing = isEditing,
                                availableLocations = uiState.availableLocations,
                                selectedLocationId = uiState.selectedLocationId,
                                onLocationChange = viewModel::selectLocation,
                                onNotesChange = viewModel::onNotesChange,
                                onRemovePosition = viewModel::removePosition,
                                onEditPosition = viewModel::startEditPosition,
                                onAddAnother = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.addAnotherProduct()
                                },
                                onFinalize = {
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.finalize()
                                },
                                onCancel = viewModel::cancel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        PurchaseEntryScreenState.SUMMARY -> {
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
            EditPositionDialog(
                position = position,
                onDismiss = viewModel::cancelEditPosition,
                onConfirm = { weight, price ->
                    viewModel.updatePosition(position.id, weight, price)
                }
            )
        }

        // Exit confirmation dialog
        if (uiState.showExitConfirmation) {
            AlertDialog(
                onDismissRequest = viewModel::dismissExitConfirmation,
                title = { Text(if (isEditing) "Скасувати редагування?" else "Скасувати закупку?") },
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeightEntry(
    weight: String,
    price: String,
    total: BigDecimal?,
    isScaleConnected: Boolean,
    isManualMode: Boolean,
    canAdd: Boolean,
    onWeightChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onPriceFocused: () -> Unit,
    onToggleManualMode: () -> Unit,
    onAddPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-focus weight field
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    // Color coding for weight field:
    // - Scales connected + auto mode: green (tertiary)
    // - Scales connected + manual mode: yellow/warning (error container)
    // - Scales not connected: default
    val weightFieldColors = when {
        isScaleConnected && !isManualMode -> androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
            unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            focusedLabelColor = MaterialTheme.colorScheme.tertiary,
            unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
            focusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
            unfocusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
        isScaleConnected && isManualMode -> androidx.compose.material3.OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.error,
            unfocusedBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            focusedLabelColor = MaterialTheme.colorScheme.error,
            unfocusedLabelColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            focusedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            unfocusedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
        else -> androidx.compose.material3.OutlinedTextFieldDefaults.colors()
    }

    // Price field uses secondary color
    val priceFieldColors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
        focusedBorderColor = MaterialTheme.colorScheme.secondary,
        unfocusedBorderColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
        focusedLabelColor = MaterialTheme.colorScheme.secondary,
        unfocusedLabelColor = MaterialTheme.colorScheme.secondary.copy(alpha = 0.7f),
        focusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f),
        unfocusedContainerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
    )

    val weightLabel = when {
        isScaleConnected && !isManualMode -> "Вага з ваг (кг)"
        isScaleConnected && isManualMode -> "Вага вручну (кг)"
        else -> "Вага (кг)"
    }

    // Adaptive values for tablet/kiosk display
    val contentPadding = adaptivePadding()
    val primaryButtonHeight = adaptivePrimaryButtonHeight()
    val displayScale = adaptiveDisplayScale()
    val isTabletDevice = isTablet()
    val horizontalPadding = adaptiveHorizontalPadding()
    val maxButtonWidth = adaptiveMaxButtonWidth()
    val maxInputWidth = adaptiveMaxInputWidth()

    // Track price focus state
    var priceHasBeenFocused by remember { mutableStateOf(false) }

    // Weight input composable - reused in both layouts
    @Composable
    fun WeightInputField(fieldModifier: Modifier = Modifier) {
        Box(
            modifier = fieldModifier
                .then(
                    if (isScaleConnected) {
                        Modifier.combinedClickable(
                            onClick = { },
                            onLongClick = onToggleManualMode
                        )
                    } else {
                        Modifier
                    }
                )
        ) {
            OutlinedTextField(
                value = weight,
                onValueChange = { if (isManualMode || !isScaleConnected) onWeightChange(it) },
                label = { Text(weightLabel, style = if (isTabletDevice) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium) },
                textStyle = if (isTabletDevice) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                readOnly = isScaleConnected && !isManualMode,
                colors = weightFieldColors,
                supportingText = if (isScaleConnected && !isManualMode) {
                    { Text("Утримуйте для ручного вводу", style = MaterialTheme.typography.bodySmall) }
                } else null,
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
            )
        }
    }

    // Price input composable - reused in both layouts
    @Composable
    fun PriceInputField(fieldModifier: Modifier = Modifier) {
        OutlinedTextField(
            value = price,
            onValueChange = onPriceChange,
            label = { Text("Ціна за кг (₴)", style = if (isTabletDevice) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium) },
            textStyle = if (isTabletDevice) MaterialTheme.typography.headlineSmall else MaterialTheme.typography.bodyLarge,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            colors = priceFieldColors,
            modifier = fieldModifier
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !priceHasBeenFocused) {
                        priceHasBeenFocused = true
                        onPriceFocused()
                    }
                }
        )
    }

    // Sum card composable - reused in both layouts
    @Composable
    fun SumCard(cardModifier: Modifier = Modifier) {
        Card(
            modifier = cardModifier,
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(if (isTabletDevice) 20.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Сума",
                    style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(if (isTabletDevice) 8.dp else 4.dp))
                Text(
                    text = total?.let { "₴${it.toPlainString()}" } ?: "₴0.00",
                    style = if (isTabletDevice) MaterialTheme.typography.displayMedium else MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }

    // Add button composable - reused in both layouts
    @Composable
    fun AddPositionButton(buttonModifier: Modifier = Modifier) {
        Button(
            onClick = onAddPosition,
            enabled = canAdd,
            modifier = buttonModifier.height(primaryButtonHeight)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Додати", modifier = if (isTabletDevice) Modifier.size(28.dp) else Modifier)
            Spacer(modifier = Modifier.width(if (isTabletDevice) 12.dp else 8.dp))
            Text(
                text = "ДОДАТИ ПОЗИЦІЮ",
                style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
            )
        }
    }

    if (isTabletDevice) {
        // Tablet: Two-column layout - inputs on left, sum + button on right
        Row(
            modifier = modifier
                .padding(horizontal = horizontalPadding, vertical = contentPadding)
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            // Left column: Input fields (55%) - constrained width for focused input
            Column(
                modifier = Modifier.weight(0.55f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val inputModifier = maxInputWidth?.let { Modifier.widthIn(max = it) } ?: Modifier.fillMaxWidth()
                WeightInputField(inputModifier.fillMaxWidth())
                PriceInputField(inputModifier.fillMaxWidth())
            }

            // Right column: Sum card + Add button (45%) - constrained button width
            Column(
                modifier = Modifier.weight(0.45f),
                verticalArrangement = Arrangement.spacedBy(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                SumCard(Modifier.fillMaxWidth())
                val buttonModifier = maxButtonWidth?.let { Modifier.widthIn(max = it) } ?: Modifier
                AddPositionButton(buttonModifier.fillMaxWidth())
            }
        }
    } else {
        // Phone: Single column layout
        Column(
            modifier = modifier
                .padding(horizontal = horizontalPadding, vertical = contentPadding)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WeightInputField(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            PriceInputField(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            SumCard(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            AddPositionButton(Modifier.fillMaxWidth())
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PositionsList(
    positions: List<PurchasePosition>,
    notes: String,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    canFinalize: Boolean,
    isEditing: Boolean = false,
    availableLocations: List<Location> = emptyList(),
    selectedLocationId: UUID? = null,
    onLocationChange: (UUID?) -> Unit = {},
    onNotesChange: (String) -> Unit,
    onRemovePosition: (String) -> Unit,
    onEditPosition: (PurchasePosition) -> Unit,
    onAddAnother: () -> Unit,
    onFinalize: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Adaptive values for tablet/kiosk display
    val horizontalPadding = adaptiveHorizontalPadding()
    val itemSpacing = adaptiveItemSpacing()
    val buttonHeight = adaptiveButtonHeight()
    val primaryButtonHeight = adaptivePrimaryButtonHeight()
    val displayScale = adaptiveDisplayScale()
    val isTabletDevice = isTablet()
    val maxButtonWidth = adaptiveMaxButtonWidth()

    // Position items composable - reused in both layouts
    @Composable
    fun PositionsListContent(listModifier: Modifier = Modifier, showAddButton: Boolean = false) {
        LazyColumn(
            modifier = listModifier,
            contentPadding = PaddingValues(vertical = if (isTabletDevice) 20.dp else 16.dp),
            verticalArrangement = Arrangement.spacedBy(itemSpacing)
        ) {
            items(positions, key = { it.id }) { position ->
                PositionItem(
                    position = position,
                    onRemove = { onRemovePosition(position.id) },
                    onLongClick = { onEditPosition(position) }
                )
            }

            if (showAddButton) {
                item {
                    Spacer(modifier = Modifier.height(16.dp))
                    // Constrained button width on tablet - centered
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
                            modifier = addButtonModifier.height(buttonHeight),
                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                        ) {
                            Icon(Icons.Filled.Add, contentDescription = "Додати")
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Додати ще товар",
                                style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.labelLarge
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
            label = { Text("Примітки") },
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
                    text = "${totalWeight.toPlainString()} кг",
                    style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                )
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    text = "₴${totalAmount.toPlainString()}",
                    style = MaterialTheme.typography.headlineLarge.copy(
                        fontSize = MaterialTheme.typography.headlineLarge.fontSize * displayScale
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
                modifier = buttonWidthModifier.fillMaxWidth().height(primaryButtonHeight)
            ) {
                Text(
                    text = if (isEditing) "РЕДАГУВАТИ" else "РОЗРАХУВАТИ",
                    style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                )
            }
            OutlinedButton(
                onClick = onCancel,
                modifier = buttonWidthModifier.fillMaxWidth().height(buttonHeight),
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
            // Positions list
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(itemSpacing)
            ) {
                items(positions, key = { it.id }) { position ->
                    PositionItem(
                        position = position,
                        onRemove = { onRemovePosition(position.id) },
                        onLongClick = { onEditPosition(position) }
                    )
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

            // Footer with add button, totals and actions
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = horizontalPadding, vertical = 16.dp)
            ) {
                // Add another product button
                Button(
                    onClick = onAddAnother,
                    modifier = Modifier.fillMaxWidth().height(buttonHeight),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = "Додати")
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(text = "Додати ще товар", style = MaterialTheme.typography.labelLarge)
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Totals
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Всього:",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${totalWeight.toPlainString()} кг",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "₴${totalAmount.toPlainString()}",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onCancel,
                        modifier = Modifier.weight(1f).height(buttonHeight),
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
                        Text(if (isEditing) "РЕДАГУВАТИ" else "Розрахувати", style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PositionItem(
    position: PurchasePosition,
    onRemove: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Product image thumbnail
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerHighest),
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
                        text = "${position.weightKg.toPlainString()} кг × ₴${position.pricePerKg.toPlainString()}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Text(
                    text = "₴${position.totalAmount.toPlainString()}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                IconButton(onClick = onRemove) {
                    Icon(
                        imageVector = Icons.Filled.Close,
                        contentDescription = "Видалити",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Text(
                text = "Утримуйте для редагування",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                modifier = Modifier.padding(start = 12.dp, bottom = 8.dp)
            )
        }
    }
}

@Composable
private fun EditPositionDialog(
    position: PurchasePosition,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal, BigDecimal) -> Unit
) {
    var weight by remember { mutableStateOf(position.weightKg.toPlainString()) }
    var price by remember { mutableStateOf(position.pricePerKg.toPlainString()) }

    val parsedWeight = weight.toBigDecimalOrNull()
    val parsedPrice = price.toBigDecimalOrNull()
    val isValid = parsedWeight != null && parsedWeight > BigDecimal.ZERO &&
                  parsedPrice != null && parsedPrice > BigDecimal.ZERO
    val calculatedTotal = if (parsedWeight != null && parsedPrice != null) {
        parsedWeight.multiply(parsedPrice).setScale(2, java.math.RoundingMode.HALF_UP)
    } else null

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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                OutlinedTextField(
                    value = weight,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) weight = it },
                    label = { Text("Вага (кг)") },
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

                calculatedTotal?.let { total ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Сума:",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "₴${total.toPlainString()}",
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
                    if (parsedWeight != null && parsedPrice != null) {
                        onConfirm(parsedWeight, parsedPrice)
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
