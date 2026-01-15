package com.zagot.zagotplus.ui.screens.purchase

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.ui.components.EmptyState
import com.zagot.zagotplus.ui.components.EmptyStateIcons
import com.zagot.zagotplus.ui.components.PriceType
import com.zagot.zagotplus.ui.components.ReorderableProductGrid
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseEntryScreen(
    onNavigateBack: () -> Unit,
    editingBatchId: String? = null,
    viewModel: PurchaseEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Load batch for editing if provided
    LaunchedEffect(editingBatchId) {
        if (editingBatchId != null) {
            viewModel.loadBatchForEditing(editingBatchId)
        }
    }

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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Show summary overlay if in SUMMARY state
            if (uiState.screenState == PurchaseEntryScreenState.SUMMARY) {
                PurchaseSummaryOverlay(
                    positions = uiState.positions,
                    notes = uiState.notes,
                    totalWeight = uiState.totalWeight,
                    totalAmount = uiState.totalAmount,
                    isSaving = uiState.isSaving,
                    onConfirm = viewModel::confirmSave
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
                    when (uiState.screenState) {
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
                                onToggleManualMode = viewModel::toggleManualWeightMode,
                                onAddPosition = viewModel::addPosition,
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
                                onNotesChange = viewModel::onNotesChange,
                                onRemovePosition = viewModel::removePosition,
                                onEditPosition = viewModel::startEditPosition,
                                onAddAnother = viewModel::addAnotherProduct,
                                onFinalize = viewModel::finalize,
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
                title = { Text("Скасувати закупку?") },
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
    onToggleManualMode: () -> Unit,
    onAddPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
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

    Column(
        modifier = modifier
            .padding(16.dp)
            .imePadding(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Weight input - long press to toggle manual mode when scales connected
        Box(
            modifier = Modifier
                .fillMaxWidth()
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
                label = { Text(weightLabel) },
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
                modifier = Modifier.fillMaxWidth()
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Price input with color coding
        OutlinedTextField(
            value = price,
            onValueChange = onPriceChange,
            label = { Text("Ціна за кг (₴)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            singleLine = true,
            colors = priceFieldColors,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Calculated total
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
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
                    style = MaterialTheme.typography.titleLarge
                )
                Text(
                    text = total?.let { "₴${it.toPlainString()}" } ?: "₴0.00",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Add position button - LARGER touch target
        Button(
            onClick = onAddPosition,
            enabled = canAdd,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Додати")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = "Додати позицію",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

@Composable
private fun PositionsList(
    positions: List<PurchasePosition>,
    notes: String,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    canFinalize: Boolean,
    onNotesChange: (String) -> Unit,
    onRemovePosition: (String) -> Unit,
    onEditPosition: (PurchasePosition) -> Unit,
    onAddAnother: () -> Unit,
    onFinalize: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Positions list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
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
                
                // Notes field
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text("Примітки") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        }

        // Footer with add button, totals and actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            // Add another product button - moved above totals
            OutlinedButton(
                onClick = onAddAnother,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Додати")
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Додати ще товар",
                    style = MaterialTheme.typography.labelLarge
                )
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

            // Action buttons - LARGER touch targets
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text(
                        text = "Скасувати",
                        style = MaterialTheme.typography.labelLarge
                    )
                }
                Button(
                    onClick = onFinalize,
                    enabled = canFinalize,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Розрахувати",
                        style = MaterialTheme.typography.labelLarge
                    )
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
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
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
