package com.zagot.zagotplus.ui.screens.sale

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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
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
import java.text.DecimalFormat
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SaleEntryScreen(
    onNavigateBack: () -> Unit,
    viewModel: SaleEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) {
            viewModel.onNavigationHandled()
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    val topBarTitle = when (uiState.screenState) {
        SaleEntryScreenState.PRODUCT_GRID -> "Оберіть товар"
        SaleEntryScreenState.WEIGHING -> uiState.selectedProduct?.name ?: "Зважування"
        SaleEntryScreenState.POSITION_REVIEW -> "Перевірка позиції"
        SaleEntryScreenState.POSITIONS_LIST -> "Позиції (${uiState.positions.size})"
        SaleEntryScreenState.SUMMARY -> "Підсумок"
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
                                    SaleEntryScreenState.WEIGHING -> viewModel.backToGrid()
                                    SaleEntryScreenState.POSITION_REVIEW -> viewModel.backToWeighing()
                                    SaleEntryScreenState.POSITIONS_LIST -> viewModel.addAnotherProduct()
                                    SaleEntryScreenState.SUMMARY -> viewModel.dismissSummary()
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
            if (uiState.screenState == SaleEntryScreenState.SUMMARY) {
                SaleSummaryOverlay(
                    positions = uiState.positions,
                    totalWeight = uiState.totalWeight,
                    totalAmount = uiState.totalAmount,
                    notes = uiState.notes,
                    isSaving = uiState.isSaving,
                    onConfirm = viewModel::confirmSave
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
                        when (uiState.screenState) {
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
                                    onAddPosition = viewModel::addPositionAndContinue,
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

        // Edit position dialog
        uiState.editingPosition?.let { position ->
            EditSalePositionDialog(
                position = position,
                onDismiss = viewModel::cancelEditPosition,
                onConfirm = { tareWeight, price ->
                    viewModel.updatePosition(position.id, tareWeight, price)
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
    
    Column(modifier = modifier.padding(16.dp)) {
        // Running total display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "${decimalFormat.format(grossWeight)} кг",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "${batches.size} зважувань (брутто)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
        
        Spacer(modifier = Modifier.height(24.dp))
        
        // Weight input
        OutlinedTextField(
            value = currentWeight,
            onValueChange = onWeightChange,
            label = { Text("Вага (кг)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Tare count input
        OutlinedTextField(
            value = currentTareCount,
            onValueChange = onTareCountChange,
            label = { Text("Кількість мішків/ящиків") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Add batch button
        Button(
            onClick = onAddBatch,
            enabled = canAddBatch,
            modifier = Modifier.fillMaxWidth().height(56.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Додати зважування", style = MaterialTheme.typography.titleMedium)
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
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
            Button(
                onClick = onProceed,
                modifier = Modifier.fillMaxWidth().height(64.dp),
                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
            ) {
                Text("ДАЛІ →", style = MaterialTheme.typography.titleMedium)
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
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
    onAddPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val currencyFormat = remember { DecimalFormat("#,##0.00") }
    
    Column(
        modifier = modifier.padding(16.dp)
    ) {
        // Product header
        Text(
            text = productName,
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Weight summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
        ) {
            Column(modifier = Modifier.fillMaxWidth().padding(16.dp)) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Брутто (${batches.size} зважувань):")
                    Text("${decimalFormat.format(grossWeight)} кг", fontWeight = FontWeight.Medium)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Тара ($totalTareCount шт):")
                    Text("-${decimalFormat.format(totalTareWeight)} кг", color = MaterialTheme.colorScheme.error)
                }
                Spacer(modifier = Modifier.height(8.dp))
                Divider()
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Нетто:", fontWeight = FontWeight.Bold)
                    Text("${decimalFormat.format(netWeight)} кг", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Tare weight per unit input
        OutlinedTextField(
            value = tareWeightPerUnit,
            onValueChange = onTareWeightChange,
            label = { Text("Вага тари (кг)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Price input
        OutlinedTextField(
            value = pricePerKg,
            onValueChange = onPriceChange,
            label = { Text("Ціна за кг (₴)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        
        // Inventory warning
        if (showInventoryWarning) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Filled.Warning, contentDescription = null, tint = MaterialTheme.colorScheme.onErrorContainer)
                    Text(
                        text = "Перевищує залишок (${decimalFormat.format(availableWeight)} кг)!",
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(16.dp))
        
        // Total amount display
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(20.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Сума:", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
                Text(
                    text = totalAmount?.let { "₴${currencyFormat.format(it)}" } ?: "₴0.00",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        
        Spacer(modifier = Modifier.weight(1f))
        
        // Add position button
        Button(
            onClick = onAddPosition,
            enabled = canAdd,
            modifier = Modifier.fillMaxWidth().height(64.dp)
        ) {
            Text("ДОДАТИ ПОЗИЦІЮ", style = MaterialTheme.typography.titleMedium)
        }
    }
}

@Composable
private fun PositionsListScreen(
    positions: List<SalePosition>,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    notes: String,
    canFinalize: Boolean,
    onNotesChange: (String) -> Unit,
    onRemovePosition: (String) -> Unit,
    onEditPosition: (SalePosition) -> Unit,
    onAddAnother: () -> Unit,
    onFinalize: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val currencyFormat = remember { DecimalFormat("#,##0.00") }
    
    Column(modifier = modifier) {
        LazyColumn(
            modifier = Modifier.weight(1f).fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(positions, key = { it.id }) { position ->
                SalePositionItem(
                    position = position,
                    onRemove = { onRemovePosition(position.id) },
                    onLongClick = { onEditPosition(position) }
                )
            }
            
            item {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedButton(
                    onClick = onAddAnother,
                    modifier = Modifier.fillMaxWidth().height(56.dp)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Додати ще товар")
                }
            }
            
            item {
                Spacer(modifier = Modifier.height(16.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text("Покупець / примітки") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
                )
            }
        }
        
        // Footer with totals and actions
        Column(
            modifier = Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(16.dp)
        ) {
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("Всього:", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                Column(horizontalAlignment = Alignment.End) {
                    Text("${decimalFormat.format(totalWeight)} кг", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "₴${currencyFormat.format(totalAmount)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(20.dp))
            
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f).height(56.dp),
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = MaterialTheme.colorScheme.error),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("Скасувати", style = MaterialTheme.typography.labelLarge)
                }
                Button(
                    onClick = onFinalize,
                    enabled = canFinalize,
                    modifier = Modifier.weight(1f).height(56.dp)
                ) {
                    Text("ПРОДАТИ", style = MaterialTheme.typography.labelLarge)
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
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = { },
                onLongClick = onLongClick
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(12.dp),
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
                    text = "${decimalFormat.format(position.netWeight)} кг × ₴${decimalFormat.format(position.pricePerKg)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Text(
                    text = "(брутто ${decimalFormat.format(position.grossWeight)} - тара ${decimalFormat.format(position.totalTareWeight)})",
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
    onConfirm: (BigDecimal, BigDecimal) -> Unit
) {
    var tareWeight by remember { mutableStateOf(position.tareWeightPerUnit.toPlainString()) }
    var price by remember { mutableStateOf(position.pricePerKg.toPlainString()) }

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
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Show gross weight and tare count (read-only)
                Text(
                    text = "Брутто: ${decimalFormat.format(position.grossWeight)} кг (${position.totalTareCount} шт тари)",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

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

                Divider()

                // Preview calculations
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Тара:", style = MaterialTheme.typography.bodyMedium)
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
}

@Composable
fun SaleSummaryOverlay(
    positions: List<SalePosition>,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    notes: String,
    isSaving: Boolean,
    onConfirm: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val currencyFormat = remember { DecimalFormat("#,##0.00") }
    
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.scrim.copy(alpha = 0.6f))
            .clickable(enabled = !isSaving) { onConfirm() },
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(0.9f).clickable(enabled = !isSaving) { onConfirm() },
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier.padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "ПРОДАЖ",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                Divider()
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
                Divider()
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
                    Divider()
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
                
                if (isSaving) {
                    CircularProgressIndicator()
                } else {
                    Text(
                        text = "(торкніться для підтвердження)",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
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
    
    Column(modifier = modifier.fillMaxWidth()) {
        Text(text = position.product.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(
                text = "${decimalFormat.format(position.netWeight)} кг × ₴${decimalFormat.format(position.pricePerKg)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "₴${decimalFormat.format(position.totalAmount)}",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}