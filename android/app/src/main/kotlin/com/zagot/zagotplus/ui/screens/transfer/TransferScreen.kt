package com.zagot.zagotplus.ui.screens.transfer

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.DropdownMenuItem
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.LocationType
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.ui.components.AnimatedListItem
import com.zagot.zagotplus.ui.components.EmptyState
import com.zagot.zagotplus.ui.components.EmptyStateIcons
import com.zagot.zagotplus.ui.components.ReorderableProductGrid
import java.math.BigDecimal
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransferScreen(
    onNavigateBack: () -> Unit,
    prefilledProductId: String? = null,
    prefilledSourceLocationId: String? = null,
    prefilledDestinationLocationId: String? = null,
    viewModel: TransferViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // Prefilled data is now handled by ViewModel via SavedStateHandle - no LaunchedEffect needed

    // Handle navigation
    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) {
            viewModel.onNavigationHandled()
            onNavigateBack()
        }
    }

    // Show error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    val topBarTitle = when (uiState.screenState) {
        TransferScreenState.LOCATIONS -> "Переміщення"
        TransferScreenState.PRODUCT_GRID -> "Оберіть товар"
        TransferScreenState.WEIGHT_ENTRY -> uiState.selectedProduct?.name ?: "Введіть вагу"
        TransferScreenState.POSITIONS_LIST -> "Позиції (${uiState.positions.size})"
        TransferScreenState.SUMMARY -> "Підсумок"
    }

    val showBackButton = uiState.screenState != TransferScreenState.SUMMARY
    val showTopBar = uiState.screenState != TransferScreenState.SUMMARY

    Scaffold(
        topBar = {
            if (showTopBar) {
                TopAppBar(
                    title = { Text(topBarTitle) },
                    navigationIcon = {
                        IconButton(
                            onClick = {
                                when (uiState.screenState) {
                                    TransferScreenState.LOCATIONS -> viewModel.cancel()
                                    TransferScreenState.PRODUCT_GRID -> viewModel.backToLocations()
                                    TransferScreenState.WEIGHT_ENTRY -> viewModel.backToGrid()
                                    TransferScreenState.POSITIONS_LIST -> viewModel.backToGrid()
                                    TransferScreenState.SUMMARY -> viewModel.backToPositions()
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
            if (uiState.screenState == TransferScreenState.SUMMARY) {
                TransferSummaryOverlay(
                    positions = uiState.positions,
                    sourceLocation = uiState.sourceLocation,
                    destinationLocation = uiState.destinationLocation,
                    totalWeight = uiState.totalWeight,
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
                            TransferScreenState.LOCATIONS -> {
                                LocationsSelector(
                                    sourceLocation = uiState.sourceLocation,
                                    destinationLocation = uiState.destinationLocation,
                                    allLocations = uiState.allLocations,
                                    availableDestinations = uiState.availableDestinations,
                                    canProceed = uiState.canProceedToProducts,
                                    onSourceLocationChange = viewModel::selectSourceLocation,
                                    onDestinationLocationChange = viewModel::selectDestinationLocation,
                                    onProceed = viewModel::proceedToProducts,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            TransferScreenState.PRODUCT_GRID -> {
                                InventoryGrid(
                                    inventoryItems = uiState.inventoryItems,
                                    sourceLocation = uiState.sourceLocation,
                                    destinationLocation = uiState.destinationLocation,
                                    onProductClick = viewModel::selectProductById,
                                    onTransferAll = viewModel::transferAll,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            TransferScreenState.WEIGHT_ENTRY -> {
                                TransferWeightEntry(
                                    product = uiState.selectedProduct,
                                    grossWeight = uiState.currentWeight,
                                    tareCount = uiState.currentTareCount,
                                    tareWeightPerUnit = uiState.tareWeightPerUnit,
                                    netWeight = uiState.currentNetWeight,
                                    totalTareWeight = uiState.currentTotalTareWeight,
                                    availableStock = uiState.selectedAvailableStock,
                                    scaleWeight = uiState.scaleWeight,
                                    canAdd = uiState.canAddPosition,
                                    exceedsStock = uiState.exceedsAvailableStock,
                                    onGrossWeightChange = viewModel::onWeightChange,
                                    onTareCountChange = viewModel::onTareCountChange,
                                    onTareWeightPerUnitChange = viewModel::onTareWeightPerUnitChange,
                                    onAddPosition = viewModel::addPosition,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            TransferScreenState.POSITIONS_LIST -> {
                                TransferPositionsList(
                                    positions = uiState.positions,
                                    notes = uiState.notes,
                                    totalWeight = uiState.totalWeight,
                                    canFinalize = uiState.canFinalize,
                                    onNotesChange = viewModel::onNotesChange,
                                    onRemovePosition = viewModel::removePosition,
                                    onAddAnother = viewModel::addAnotherProduct,
                                    onProceed = viewModel::proceedToSummary,
                                    onCancel = viewModel::cancel,
                                    modifier = Modifier.fillMaxSize()
                                )
                            }
                            TransferScreenState.SUMMARY -> {
                                // Handled above as overlay
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun InventoryGrid(
    inventoryItems: List<InventoryWithProduct>,
    sourceLocation: Location?,
    destinationLocation: Location?,
    onProductClick: (UUID) -> Unit,
    onTransferAll: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Extract products and inventory map for ReorderableProductGrid
    val products = remember(inventoryItems) {
        inventoryItems.map { it.product }
    }
    val inventoryMap = remember(inventoryItems) {
        inventoryItems.associate { it.product.id to it.inventory.totalWeightKg }
    }

    Column(modifier = modifier) {
        // Transfer direction header (source → destination)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Звідки",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = sourceLocation?.name ?: "-",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = "Напрямок",
                    modifier = Modifier.padding(horizontal = 16.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "Куди",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = destinationLocation?.name ?: "-",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            }
        }

        if (inventoryItems.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                EmptyState(
                    icon = EmptyStateIcons.Inventory,
                    title = "Немає товарів для переміщення",
                    description = "На цій точці немає залишків"
                )
            }
        } else {
            ReorderableProductGrid(
                products = products,
                onProductClick = { product -> onProductClick(product.id) },
                onOrderChanged = { /* Reordering not persisted for transfer screen */ },
                showPrice = false,
                inventoryMap = inventoryMap,
                modifier = Modifier.weight(1f)
            )

            // Transfer All button
            Button(
                onClick = onTransferAll,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(56.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                )
            ) {
                Icon(
                    imageVector = Icons.Filled.ArrowForward,
                    contentDescription = null
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "Перемістити все",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

@Composable
private fun TransferWeightEntry(
    product: Product?,
    grossWeight: String,
    tareCount: String,
    tareWeightPerUnit: String,
    netWeight: BigDecimal,
    totalTareWeight: BigDecimal,
    availableStock: BigDecimal,
    scaleWeight: BigDecimal?,
    canAdd: Boolean,
    exceedsStock: Boolean,
    onGrossWeightChange: (String) -> Unit,
    onTareCountChange: (String) -> Unit,
    onTareWeightPerUnitChange: (String) -> Unit,
    onAddPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Auto-focus weight field
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    LaunchedEffect(Unit) {
        // Prevent crash if composition completes before window is focused
        if (view.isAttachedToWindow) {
            focusRequester.requestFocus()
        }
    }

    val hasTare = tareCount.toIntOrNull()?.let { it > 0 } == true
    
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Available stock display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Доступно:",
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
                Text(
                    text = "${availableStock.toPlainString()} кг",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Gross weight input
        OutlinedTextField(
            value = grossWeight,
            onValueChange = onGrossWeightChange,
            label = { Text("Брутто вага (кг)") },
            isError = exceedsStock,
            supportingText = if (exceedsStock) {
                { Text("Перевищує доступний залишок", color = MaterialTheme.colorScheme.error) }
            } else null,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester)
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Tare section
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Tare count (number of sacks/boxes)
            OutlinedTextField(
                value = tareCount,
                onValueChange = onTareCountChange,
                label = { Text("Тара (шт)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            
            // Tare weight per unit
            OutlinedTextField(
                value = tareWeightPerUnit,
                onValueChange = onTareWeightPerUnitChange,
                label = { Text("Вага тари (кг)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Done
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        // Show tare calculation and net weight if tare is specified
        if (hasTare && totalTareWeight > BigDecimal.ZERO) {
            Spacer(modifier = Modifier.height(16.dp))
            
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.tertiaryContainer
                )
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(16.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Тара (${tareCount} шт):",
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "-${totalTareWeight.toPlainString()} кг",
                            color = MaterialTheme.colorScheme.error,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    HorizontalDivider()
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Нетто:",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onTertiaryContainer
                        )
                        Text(
                            text = "${netWeight.toPlainString()} кг",
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Add position button - directly under weight field
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransferPositionsList(
    positions: List<TransferPosition>,
    notes: String,
    totalWeight: BigDecimal,
    canFinalize: Boolean,
    onNotesChange: (String) -> Unit,
    onRemovePosition: (String) -> Unit,
    onAddAnother: () -> Unit,
    onProceed: () -> Unit,
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
                AnimatedListItem {
                    TransferPositionItem(
                        position = position,
                        onRemove = { onRemovePosition(position.id) },
                    )
                }
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Add another product button
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

        // Footer with totals and actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
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
                Text(
                    text = "${totalWeight.toPlainString()} кг",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Action buttons
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
                    onClick = onProceed,
                    enabled = canFinalize,
                    modifier = Modifier
                        .weight(1f)
                        .height(56.dp),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text(
                        text = "Далі",
                        style = MaterialTheme.typography.labelLarge
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Icon(Icons.Filled.ArrowForward, contentDescription = "Перейти далі")
                }
            }
        }
    }
}

@Composable
private fun TransferPositionItem(
    position: TransferPosition,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val hasTare = position.tareCount > 0 && position.totalTareWeight > BigDecimal.ZERO
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
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
                if (hasTare) {
                    Text(
                        text = "Брутто: ${position.grossWeightKg.toPlainString()} кг, тара: ${position.tareCount} шт × ${position.tareWeightPerUnit.toPlainString()} кг",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Text(
                text = "${position.netWeightKg.toPlainString()} кг",
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
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationsSelector(
    sourceLocation: Location?,
    destinationLocation: Location?,
    allLocations: List<Location>,
    availableDestinations: List<Location>,
    canProceed: Boolean,
    onSourceLocationChange: (Location) -> Unit,
    onDestinationLocationChange: (Location) -> Unit,
    onProceed: () -> Unit,
    modifier: Modifier = Modifier
) {
    var sourceExpanded by remember { mutableStateOf(false) }
    var destinationExpanded by remember { mutableStateOf(false) }

    Column(
        modifier = modifier.padding(16.dp)
    ) {
        Text(
            text = "Оберіть точки",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 24.dp)
        )

        // Source location selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            ExposedDropdownMenuBox(
                expanded = sourceExpanded,
                onExpandedChange = { sourceExpanded = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = "Локація",
                        tint = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Звідки:",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                        )
                        Text(
                            text = sourceLocation?.name ?: "Оберіть точку",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    ExposedDropdownMenuDefaults.TrailingIcon(expanded = sourceExpanded)
                }

                ExposedDropdownMenu(
                    expanded = sourceExpanded,
                    onDismissRequest = { sourceExpanded = false }
                ) {
                    allLocations.forEach { location ->
                        DropdownMenuItem(
                            text = { Text(location.name) },
                            onClick = {
                                onSourceLocationChange(location)
                                sourceExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Place,
                                    contentDescription = "Локація"
                                )
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Arrow indicator
        Icon(
            imageVector = Icons.Filled.ArrowForward,
            contentDescription = "Напрямок",
            modifier = Modifier
                .align(Alignment.CenterHorizontally)
                .size(32.dp),
            tint = MaterialTheme.colorScheme.primary
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Destination location selector
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = if (destinationLocation != null) 
                    MaterialTheme.colorScheme.secondaryContainer 
                else 
                    MaterialTheme.colorScheme.surfaceVariant
            )
        ) {
            ExposedDropdownMenuBox(
                expanded = destinationExpanded,
                onExpandedChange = { 
                    if (availableDestinations.isNotEmpty()) {
                        destinationExpanded = it 
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Filled.Place,
                        contentDescription = "Локація",
                        tint = if (destinationLocation != null) 
                            MaterialTheme.colorScheme.onSecondaryContainer 
                        else 
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.width(12.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Куди:",
                            style = MaterialTheme.typography.labelMedium,
                            color = if (destinationLocation != null)
                                MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                            else
                                MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                        )
                        Text(
                            text = destinationLocation?.name ?: if (sourceLocation == null) "Спочатку оберіть звідки" else "Оберіть точку",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (destinationLocation != null) 
                                MaterialTheme.colorScheme.onSecondaryContainer 
                            else 
                                MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (availableDestinations.isNotEmpty()) {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = destinationExpanded)
                    }
                }

                ExposedDropdownMenu(
                    expanded = destinationExpanded,
                    onDismissRequest = { destinationExpanded = false }
                ) {
                    availableDestinations.forEach { location ->
                        DropdownMenuItem(
                            text = { Text(location.name) },
                            onClick = {
                                onDestinationLocationChange(location)
                                destinationExpanded = false
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Filled.Place,
                                    contentDescription = "Локація"
                                )
                            }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Proceed button
        Button(
            onClick = onProceed,
            enabled = canProceed,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = MaterialTheme.colorScheme.primary
            )
        ) {
            Text(
                text = "Далі",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.width(8.dp))
            Icon(Icons.Filled.ArrowForward, contentDescription = "Далі")
        }
    }
}

@Composable
private fun LocationCard(
    location: Location,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val typeLabel = when (location.type) {
        LocationType.KIOSK -> "Кіоск"
        LocationType.MOBILE -> "Мобільна точка"
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected) 
                MaterialTheme.colorScheme.primaryContainer 
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Filled.Place,
                contentDescription = "Локація",
                modifier = Modifier.size(32.dp),
                tint = if (isSelected) 
                    MaterialTheme.colorScheme.onPrimaryContainer 
                else 
                    MaterialTheme.colorScheme.primary
            )
            Spacer(modifier = Modifier.width(16.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = location.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = typeLabel,
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (isSelected)
                        MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isSelected) {
                Icon(
                    imageVector = Icons.Filled.Place,
                    contentDescription = "Обрано",
                    tint = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TransferSummaryOverlay(
    positions: List<TransferPosition>,
    sourceLocation: Location?,
    destinationLocation: Location?,
    totalWeight: BigDecimal,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val haptic = LocalHapticFeedback.current
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f)),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.9f),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surface
            ),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Title
                Text(
                    text = "ПЕРЕМІЩЕННЯ",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )

                Spacer(modifier = Modifier.height(16.dp))

                // From → To
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Звідки",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = sourceLocation?.name ?: "-",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Icon(
                        imageVector = Icons.Filled.ArrowForward,
                        contentDescription = "Напрямок переміщення",
                        modifier = Modifier.padding(horizontal = 16.dp),
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Куди",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = destinationLocation?.name ?: "-",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Positions list
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(positions, key = { it.id }) { position ->
                        AnimatedListItem {
                            SummaryTransferItem(
                                position = position,
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))

                // Total
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Всього:",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${totalWeight.toPlainString()} кг",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))

                // Confirm button with haptic feedback
                if (isSaving) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                            onConfirm()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(56.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = MaterialTheme.colorScheme.primary
                        )
                    ) {
                        Text(
                            text = "ПЕРЕМІСТИТИ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryTransferItem(
    position: TransferPosition,
    modifier: Modifier = Modifier
) {
    val hasTare = position.tareCount > 0 && position.totalTareWeight > BigDecimal.ZERO
    
    Column(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = position.product.name,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = "${position.netWeightKg.toPlainString()} кг",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
        if (hasTare) {
            Text(
                text = "(брутто: ${position.grossWeightKg.toPlainString()} кг − тара: ${position.totalTareWeight.toPlainString()} кг)",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
