package com.zagot.zagotplus.ui.screens.shared

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Print
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.ui.components.AnimatedListItem
import com.zagot.zagotplus.ui.components.adaptivePadding
import com.zagot.zagotplus.ui.components.isTablet
import java.math.BigDecimal
import java.text.DecimalFormat

// ==================== Edit Position Dialog ====================

/**
 * Dialog for editing a transaction position.
 * Supports both simple (regular mode) and batch mode positions.
 */
@Composable
fun TransactionEditPositionDialog(
    position: TransactionPosition,
    onDismiss: () -> Unit,
    onConfirm: (BigDecimal, BigDecimal) -> Unit,
    onUpdateBatch: (String, BigDecimal, Int) -> Unit,
    onDeleteBatch: (String) -> Unit
) {
    // For regular mode: edit net weight directly
    // For batch mode: edit tare weight per unit
    val isBatchMode = position.batches.size > 1 || (position.batches.isNotEmpty() && position.totalTareCount > 0)

    var weight by remember(position) {
        mutableStateOf(
            if (isBatchMode) position.tareWeightPerUnit.toPlainString()
            else position.netWeight.toPlainString()
        )
    }
    var price by remember(position) { mutableStateOf(position.pricePerKg.toPlainString()) }

    var showHistoryDialog by remember { mutableStateOf(false) }
    var editingBatch by remember { mutableStateOf<WeighingBatch?>(null) }

    val parsedWeight = weight.toBigDecimalOrNull()
    val parsedPrice = price.toBigDecimalOrNull()
    val isValid = parsedWeight != null && parsedWeight >= BigDecimal.ZERO &&
            parsedPrice != null && parsedPrice > BigDecimal.ZERO

    val decimalFormat = remember { DecimalFormat("#,##0.00") }

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
                // Show weighing history if batches exist (batch mode)
                if (position.batches.isNotEmpty() && isBatchMode) {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showHistoryDialog = true },
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
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.History,
                                    contentDescription = "Історія зважувань",
                                    modifier = Modifier.size(20.dp),
                                    tint = MaterialTheme.colorScheme.primary
                                )
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
                }

                OutlinedTextField(
                    value = weight,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d*\\.?\\d*$"))) weight = it },
                    label = { Text(if (isBatchMode) "Вага тари за шт (кг)" else "Вага (кг)") },
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

                // Show calculated total for confirmation
                if (parsedWeight != null && parsedPrice != null) {
                    HorizontalDivider()
                    val calculatedTotal = if (isBatchMode) {
                        // Recalculate net weight with new tare
                        val newTareWeight = parsedWeight.multiply(BigDecimal(position.totalTareCount))
                        val newNetWeight = (position.grossWeight - newTareWeight).max(BigDecimal.ZERO)
                        newNetWeight.multiply(parsedPrice)
                    } else {
                        parsedWeight.multiply(parsedPrice)
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Сума:",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = "₴${decimalFormat.format(calculatedTotal)}",
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

    // Weighing history dialog
    if (showHistoryDialog && position.batches.isNotEmpty()) {
        WeighingHistoryDialog(
            batches = position.batches,
            grossWeight = position.grossWeight,
            onDismiss = { showHistoryDialog = false },
            onEditBatch = { batch ->
                showHistoryDialog = false
                editingBatch = batch
            },
            onRemoveBatch = { batchId ->
                onDeleteBatch(batchId)
            }
        )
    }

    // Edit individual batch dialog
    editingBatch?.let { batch ->
        EditBatchDialog(
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

/**
 * Dialog showing weighing history for a position.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeighingHistoryDialog(
    batches: List<WeighingBatch>,
    grossWeight: BigDecimal,
    onDismiss: () -> Unit,
    onEditBatch: (WeighingBatch) -> Unit,
    onRemoveBatch: (String) -> Unit
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
                items(
                    count = batches.size,
                    key = { index -> batches[index].id }
                ) { index ->
                    val batch = batches[index]
                    AnimatedListItem {
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onEditBatch(batch) },
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
                                Row {
                                    Text(
                                        text = "редагувати",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.padding(end = 8.dp)
                                    )
                                    IconButton(
                                        onClick = { onRemoveBatch(batch.id) },
                                        modifier = Modifier.size(24.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Close,
                                            contentDescription = "Видалити",
                                            tint = MaterialTheme.colorScheme.error,
                                            modifier = Modifier.size(20.dp)
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
 * Dialog for editing a single weighing batch.
 */
@Composable
private fun EditBatchDialog(
    batch: WeighingBatch,
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
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = tareCount,
                    onValueChange = { if (it.isEmpty() || it.matches(Regex("^\\d+$"))) tareCount = it },
                    label = { Text("Кількість тари (шт)") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Number,
                        imeAction = ImeAction.Done
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (canDelete) {
                    TextButton(
                        onClick = onDelete,
                        colors = ButtonDefaults.textButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        ),
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

// ==================== Weight Restore Dialog ====================

/**
 * Dialog shown when user removes product from scales and selects a different product
 * without adding a position. Offers to add the missed position.
 */
@Composable
fun WeightRestoreDialog(
    data: WeightRestoreData,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit
) {
    val formatter = remember { DecimalFormat("#,##0.00") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Додати позицію?") },
        text = {
            Column {
                Text(
                    text = data.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text("Вага: ${formatter.format(data.weight)} кг")
                Text("Ціна: ${formatter.format(data.price)} ₴/кг")
                Text(
                    "Сума: ${formatter.format(data.weight.multiply(data.price))} ₴",
                    fontWeight = FontWeight.Medium
                )
            }
        },
        confirmButton = {
            Button(onClick = onConfirm) {
                Text("Додати")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Пропустити")
            }
        }
    )
}

// ==================== Summary Overlay ====================

/**
 * Full-screen overlay shown after successful transaction save.
 */
@Composable
fun TransactionSummaryOverlay(
    positions: List<TransactionPosition>,
    notes: String,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    transactionType: TransactionType,
    onExit: () -> Unit,
    onPrintReceipt: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val currencyFormat = remember { DecimalFormat("#,##0") }
    val isTabletDevice = isTablet()
    val contentPadding = adaptivePadding()

    val title = when (transactionType) {
        TransactionType.PURCHASE -> "Закупку збережено!"
        TransactionType.SALE -> "Продаж оформлено!"
        else -> "Операцію збережено!"
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
        contentAlignment = Alignment.Center
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(contentPadding),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Success icon
            Icon(
                imageVector = Icons.Filled.CheckCircle,
                contentDescription = "Успіх",
                modifier = Modifier.size(if (isTabletDevice) 120.dp else 80.dp),
                tint = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(24.dp))

            Text(
                text = title,
                style = if (isTabletDevice) MaterialTheme.typography.displaySmall else MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )

            Spacer(modifier = Modifier.height(32.dp))

            // Summary card
            Card(
                modifier = Modifier
                    .fillMaxWidth(if (isTabletDevice) 0.6f else 1f)
                    .clip(RoundedCornerShape(16.dp)),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                ),
                border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = "${positions.size} позицій",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceEvenly
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Вага",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "${decimalFormat.format(totalWeight)} кг",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "Сума",
                                style = MaterialTheme.typography.labelMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "₴${currencyFormat.format(totalAmount)}",
                                style = MaterialTheme.typography.titleLarge,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }

                    if (notes.isNotBlank()) {
                        Spacer(modifier = Modifier.height(16.dp))
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = notes,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Print receipt button (purchase only)
            if (onPrintReceipt != null) {
                OutlinedButton(
                    onClick = onPrintReceipt,
                    modifier = Modifier
                        .fillMaxWidth(if (isTabletDevice) 0.4f else 0.8f)
                        .height(if (isTabletDevice) 56.dp else 48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Filled.Print,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Друкувати чек",
                        style = MaterialTheme.typography.titleSmall
                    )
                }

                Spacer(modifier = Modifier.height(12.dp))
            }

            // Exit button
            Button(
                onClick = onExit,
                modifier = Modifier
                    .fillMaxWidth(if (isTabletDevice) 0.4f else 0.8f)
                    .height(if (isTabletDevice) 64.dp else 56.dp)
            ) {
                Text(
                    text = "ГОТОВО",
                    style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
                )
            }
        }
    }
}
