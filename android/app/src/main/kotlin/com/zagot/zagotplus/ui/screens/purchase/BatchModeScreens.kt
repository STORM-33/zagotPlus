package com.zagot.zagotplus.ui.screens.purchase

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import com.zagot.zagotplus.ui.screens.shared.PurchaseEntryUiState
import com.zagot.zagotplus.ui.screens.shared.PurchaseWeighingBatch
import com.zagot.zagotplus.ui.components.AnimatedListItem
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.History
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import java.math.BigDecimal
import java.text.DecimalFormat
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.platform.LocalView
import kotlinx.coroutines.delay

/**
 * Phone batch mode: Weighing screen where user adds multiple weight batches with tare count.
 * After adding batches, user proceeds to review screen for tare weight and price entry.
 */
@Composable
fun BatchWeighingScreen(
    uiState: PurchaseEntryUiState,
    onWeightChange: (String) -> Unit,
    onTareCountChange: (String) -> Unit,
    onAddBatch: () -> Unit,
    onRemoveBatch: (String) -> Unit,
    onProceedToReview: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val productName = uiState.selectedProduct?.name ?: "Невідомий товар"
    
    // Focus management for auto-focus on weight field
    val weightFocusRequester = remember { FocusRequester() }
    val keyboardController = LocalSoftwareKeyboardController.current
    val view = LocalView.current
    val batchCount = uiState.currentBatches.size
    
    // Initial entry: request focus with small delay for layout stability
    LaunchedEffect(Unit) {
        delay(150)  // Just enough for layout to settle
        if (view.isAttachedToWindow && uiState.currentWeight.isEmpty()) {
            weightFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }
    
    // After adding batch: immediate focus if weight is cleared
    LaunchedEffect(batchCount) {
        if (batchCount > 0 && uiState.currentWeight.isEmpty()) {
            weightFocusRequester.requestFocus()
            keyboardController?.show()
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with product name
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = productName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Running total
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${uiState.currentBatches.size} зважувань",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = "${decimalFormat.format(uiState.currentGrossWeight)} кг",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weight and tare count input
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedTextField(
                value = uiState.currentWeight,
                onValueChange = onWeightChange,
                label = { Text("Вага (кг)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Decimal,
                    imeAction = ImeAction.Next
                ),
                singleLine = true,
                modifier = Modifier
                    .weight(1f)
                    .focusRequester(weightFocusRequester)
            )

            OutlinedTextField(
                value = uiState.currentTareCount,
                onValueChange = onTareCountChange,
                label = { Text("Тара (шт)") },
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Number,
                    imeAction = ImeAction.Done
                ),
                keyboardActions = KeyboardActions(
                    onDone = { if (uiState.canAddBatch) onAddBatch() }
                ),
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Add batch button
        OutlinedButton(
            onClick = onAddBatch,
            enabled = uiState.canAddBatch,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Text("  Додати зважування")
        }

        Spacer(modifier = Modifier.height(16.dp))
        HorizontalDivider()
        Spacer(modifier = Modifier.height(16.dp))

        // Batches list
        if (uiState.currentBatches.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Додайте зважування",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            // Pre-compute batch numbers to avoid O(n²) indexOf calls inside LazyColumn
            val indexedBatches = remember(uiState.currentBatches) {
                uiState.currentBatches.mapIndexed { index, batch -> (index + 1) to batch }.asReversed()
            }
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(indexedBatches, key = { it.second.id }) { (batchNumber, batch) ->
                    AnimatedListItem {
                        BatchItem(
                            batchNumber = batchNumber,
                            batch = batch,
                            onRemove = { onRemoveBatch(batch.id) }
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Proceed to review button
        Button(
            onClick = onProceedToReview,
            enabled = uiState.canProceedToReview,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Text(
                "ДАЛІ →",
                style = MaterialTheme.typography.titleMedium
            )
        }
    }
}

/**
 * Phone batch mode: Review screen for entering tare weight per unit and price.
 * Shows net weight calculation and allows viewing/editing batches and adding the position.
 */
@Composable
fun BatchPositionReviewScreen(
    uiState: PurchaseEntryUiState,
    onTareWeightChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onAddPositionAndContinue: () -> Unit,
    onBackToWeighing: () -> Unit,
    onEditBatch: (String, BigDecimal, Int) -> Unit,
    onRemoveBatch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val productName = uiState.selectedProduct?.name ?: "Невідомий товар"
    
    // State for batch history dialog
    var showBatchHistoryDialog by remember { mutableStateOf(false) }
    var editingBatch by remember { mutableStateOf<PurchaseWeighingBatch?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        // Header with product name
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = productName,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer,
                modifier = Modifier.padding(16.dp)
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Weight breakdown with clickable batch count
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
            )
        ) {
            Column(
                modifier = Modifier.padding(16.dp)
            ) {
                // Clickable row to view batch history
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { showBatchHistoryDialog = true }
                        .padding(vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = "Історія зважувань",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                        Text(
                            "${uiState.currentBatches.size} зв. • ${decimalFormat.format(uiState.currentGrossWeight)} кг",
                            style = MaterialTheme.typography.bodyMedium,
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
                Spacer(modifier = Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Тара (${uiState.currentTotalTareCount} шт × ${uiState.tareWeightPerUnit} кг):")
                    Text(
                        "- ${decimalFormat.format(uiState.currentTotalTareWeight)} кг",
                        color = MaterialTheme.colorScheme.error
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
                        "Нетто:",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "${decimalFormat.format(uiState.currentNetWeight)} кг",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Tare weight per unit input
        OutlinedTextField(
            value = uiState.tareWeightPerUnit,
            onValueChange = onTareWeightChange,
            label = { Text("Вага тари (кг/шт)") },
            supportingText = { Text("Наприклад, 0.1 кг для мішка") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Next
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Price input
        OutlinedTextField(
            value = uiState.currentPrice,
            onValueChange = onPriceChange,
            label = { Text("Ціна (₴/кг)") },
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Decimal,
                imeAction = ImeAction.Done
            ),
            keyboardActions = KeyboardActions(
                onDone = { if (uiState.canAddBatchPosition) onAddPositionAndContinue() }
            ),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Total amount
        uiState.currentBatchTotalAmount?.let { total ->
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Сума:",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "₴${decimalFormat.format(total)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            OutlinedButton(
                onClick = onBackToWeighing,
                modifier = Modifier.weight(1f).height(56.dp)
            ) {
                Text("Назад")
            }

            Button(
                onClick = onAddPositionAndContinue,
                enabled = uiState.canAddBatchPosition,
                modifier = Modifier.weight(2f).height(56.dp)
            ) {
                Text(
                    "ДОДАТИ",
                    style = MaterialTheme.typography.titleMedium
                )
            }
        }
    }
    
    // Batch history dialog
    if (showBatchHistoryDialog) {
        BatchHistoryDialog(
            batches = uiState.currentBatches,
            onDismiss = { showBatchHistoryDialog = false },
            onEditBatch = { batch -> 
                editingBatch = batch
                showBatchHistoryDialog = false
            },
            onRemoveBatch = { batchId ->
                onRemoveBatch(batchId)
            }
        )
    }
    
    // Edit batch dialog  
    editingBatch?.let { batch ->
        EditBatchDialog(
            batch = batch,
            onDismiss = { editingBatch = null },
            onConfirm = { newWeight, newTareCount ->
                onEditBatch(batch.id, newWeight, newTareCount)
                editingBatch = null
            }
        )
    }
}

/**
 * Simple batch item for the weighing list.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BatchItem(
    batchNumber: Int,
    batch: PurchaseWeighingBatch,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }

    Card(
        modifier = modifier
            .fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // Batch number
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(MaterialTheme.colorScheme.primaryContainer),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$batchNumber",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column {
                    Text(
                        text = "${decimalFormat.format(batch.grossWeightKg)} кг",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${batch.tareCount} шт тари",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(onClick = onRemove) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Видалити",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}

/**
 * Dialog showing list of all batches with edit and remove options.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun BatchHistoryDialog(
    batches: List<PurchaseWeighingBatch>,
    onDismiss: () -> Unit,
    onEditBatch: (PurchaseWeighingBatch) -> Unit,
    onRemoveBatch: (String) -> Unit
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Зважування (${batches.size})") },
        text = {
            // Pre-compute batch numbers to avoid O(n²) indexOf calls inside LazyColumn
            val indexedBatches = remember(batches) {
                batches.mapIndexed { index, batch -> (index + 1) to batch }.asReversed()
            }
            LazyColumn(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(indexedBatches, key = { it.second.id }) { (batchNumber, batch) ->
                    AnimatedListItem {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth(),
                            colors = CardDefaults.cardColors(
                                containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                            )
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(12.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(28.dp)
                                            .clip(RoundedCornerShape(4.dp))
                                            .background(MaterialTheme.colorScheme.primaryContainer),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "#$batchNumber",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Bold,
                                            color = MaterialTheme.colorScheme.onPrimaryContainer
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "${decimalFormat.format(batch.grossWeightKg)} кг",
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.Medium
                                        )
                                        Text(
                                            text = "${batch.tareCount} шт",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                                Row {
                                    IconButton(onClick = { onEditBatch(batch) }) {
                                        Icon(
                                            Icons.Filled.Edit,
                                            contentDescription = "Редагувати",
                                            tint = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                    IconButton(onClick = { onRemoveBatch(batch.id) }) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Видалити",
                                            tint = MaterialTheme.colorScheme.error
                                        )
                                    }
                                }
                            }
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

/**
 * Dialog for editing a single batch's weight and tare count.
 */
@Composable
private fun EditBatchDialog(
    batch: PurchaseWeighingBatch,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal, Int) -> Unit
) {
    var weightText by remember { mutableStateOf(batch.grossWeightKg.toPlainString()) }
    var tareCountText by remember { mutableStateOf(batch.tareCount.toString()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Редагувати зважування") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedTextField(
                    value = weightText,
                    onValueChange = { weightText = it },
                    label = { Text("Вага (кг)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tareCountText,
                    onValueChange = { tareCountText = it },
                    label = { Text("Кількість тари (шт)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    val weight = weightText.toBigDecimalOrNull()
                    val tareCount = tareCountText.toIntOrNull()
                    if (weight != null && weight > BigDecimal.ZERO && tareCount != null && tareCount >= 0) {
                        onConfirm(weight, tareCount)
                    }
                }
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
