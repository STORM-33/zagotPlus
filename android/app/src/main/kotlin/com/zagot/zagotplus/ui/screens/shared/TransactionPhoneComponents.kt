package com.zagot.zagotplus.ui.screens.shared

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.ui.components.PriceType
import com.zagot.zagotplus.ui.components.ReorderableProductGrid
import com.zagot.zagotplus.ui.components.adaptiveButtonHeight
import com.zagot.zagotplus.ui.components.adaptiveDisplayScale
import com.zagot.zagotplus.ui.components.adaptiveHorizontalPadding
import com.zagot.zagotplus.ui.components.adaptiveItemSpacing
import com.zagot.zagotplus.ui.components.adaptiveMaxButtonWidth
import com.zagot.zagotplus.ui.components.adaptiveMaxInputWidth
import com.zagot.zagotplus.ui.components.adaptivePadding
import com.zagot.zagotplus.ui.components.adaptivePrimaryButtonHeight
import com.zagot.zagotplus.ui.components.isTablet
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.UUID

// ==================== Product Grid ====================

/**
 * Product grid with optional inventory display and proceed button.
 * Used for product selection on phone.
 */
@Composable
fun TransactionProductGrid(
    products: List<Product>,
    positions: List<TransactionPosition>,
    transactionType: TransactionType,
    inventoryMap: Map<UUID, BigDecimal>,
    onProductClick: (Product) -> Unit,
    onOrderChanged: (List<UUID>) -> Unit,
    onProceedToPositions: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        ReorderableProductGrid(
            products = products,
            onProductClick = onProductClick,
            onOrderChanged = onOrderChanged,
            modifier = Modifier.weight(1f),
            showPrice = transactionType == TransactionType.PURCHASE,
            priceType = PriceType.BUY,
            inventoryMap = if (transactionType == TransactionType.SALE) inventoryMap else emptyMap()
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

// ==================== Weight Entry (Regular Mode) ====================

/**
 * Weight and price entry screen for regular (non-batch) mode.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun TransactionWeightEntry(
    weight: String,
    price: String,
    total: BigDecimal?,
    isScaleConnected: Boolean,
    isManualMode: Boolean,
    canAdd: Boolean,
    availableWeight: BigDecimal = BigDecimal.ZERO,
    showInventoryWarning: Boolean = false,
    onWeightChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onPriceFocused: () -> Unit,
    onToggleManualMode: () -> Unit,
    onAddPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    LaunchedEffect(Unit) {
        // Prevent crash if composition completes before window is focused
        if (view.isAttachedToWindow) {
            focusRequester.requestFocus()
        }
    }

    val weightFieldColors = when {
        isScaleConnected && !isManualMode -> OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.tertiary,
            unfocusedBorderColor = MaterialTheme.colorScheme.tertiary,
            focusedLabelColor = MaterialTheme.colorScheme.tertiary,
            unfocusedLabelColor = MaterialTheme.colorScheme.tertiary,
            focusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f),
            unfocusedContainerColor = MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        )
        isScaleConnected && isManualMode -> OutlinedTextFieldDefaults.colors(
            focusedBorderColor = MaterialTheme.colorScheme.error,
            unfocusedBorderColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            focusedLabelColor = MaterialTheme.colorScheme.error,
            unfocusedLabelColor = MaterialTheme.colorScheme.error.copy(alpha = 0.7f),
            focusedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f),
            unfocusedContainerColor = MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
        )
        else -> OutlinedTextFieldDefaults.colors()
    }

    val priceFieldColors = OutlinedTextFieldDefaults.colors(
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

    val contentPadding = adaptivePadding()
    val primaryButtonHeight = adaptivePrimaryButtonHeight()
    val displayScale = adaptiveDisplayScale()
    val isTabletDevice = isTablet()
    val horizontalPadding = adaptiveHorizontalPadding()
    val maxButtonWidth = adaptiveMaxButtonWidth()
    val maxInputWidth = adaptiveMaxInputWidth()

    var priceHasBeenFocused by remember { mutableStateOf(false) }

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
        Row(
            modifier = modifier
                .padding(horizontal = horizontalPadding, vertical = contentPadding)
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(
                modifier = Modifier.weight(0.55f),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                val inputModifier = maxInputWidth?.let { Modifier.widthIn(max = it) } ?: Modifier.fillMaxWidth()
                WeightInputField(inputModifier.fillMaxWidth())
                PriceInputField(inputModifier.fillMaxWidth())
            }

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
        Column(
            modifier = modifier
                .padding(horizontal = horizontalPadding, vertical = contentPadding)
                .imePadding(),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            WeightInputField(Modifier.fillMaxWidth())

            // Inventory warning for sale mode
            if (showInventoryWarning) {
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = "Попередження",
                            tint = MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Доступно лише ${availableWeight.toPlainString()} кг",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))
            PriceInputField(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            SumCard(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(16.dp))
            AddPositionButton(Modifier.fillMaxWidth())
        }
    }
}

// ==================== Batch Weighing Screen ====================

/**
 * Batch weighing screen for collecting multiple weights with tare counts.
 */
@Composable
fun TransactionBatchWeighing(
    productName: String,
    currentWeight: String,
    currentTareCount: String,
    batches: List<WeighingBatch>,
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
    val focusRequester = remember { FocusRequester() }
    val view = LocalView.current
    LaunchedEffect(Unit) {
        // Prevent crash if composition completes before window is focused
        if (view.isAttachedToWindow) {
            focusRequester.requestFocus()
        }
    }

    val contentPadding = adaptivePadding()
    val buttonHeight = adaptiveButtonHeight()
    val primaryButtonHeight = adaptivePrimaryButtonHeight()
    val displayScale = adaptiveDisplayScale()
    val isTabletDevice = isTablet()
    val horizontalPadding = adaptiveHorizontalPadding()
    val maxButtonWidth = adaptiveMaxButtonWidth()
    val maxInputWidth = adaptiveMaxInputWidth()

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

    @Composable
    fun InputFields(fieldModifier: Modifier = Modifier) {
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

            Button(
                onClick = onAddBatch,
                enabled = canAddBatch,
                modifier = btnModifier.fillMaxWidth().height(buttonHeight)
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

    @Composable
    fun ProceedButton(buttonModifier: Modifier = Modifier) {
        if (canProceed) {
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
                    modifier = constrainedModifier.fillMaxWidth().height(primaryButtonHeight),
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
        Row(
            modifier = modifier
                .padding(horizontal = horizontalPadding, vertical = contentPadding)
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(32.dp)
        ) {
            Column(modifier = Modifier.weight(0.55f)) {
                InputFields(Modifier.fillMaxWidth())
            }

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
        Column(
            modifier = modifier
                .padding(horizontal = horizontalPadding, vertical = contentPadding)
                .imePadding()
        ) {
            RunningTotalCard(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))
            InputFields(Modifier.fillMaxWidth())
            Spacer(modifier = Modifier.height(12.dp))

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

            if (canProceed) {
                Spacer(modifier = Modifier.height(16.dp))
                ProceedButton(Modifier.fillMaxWidth())
            }
        }
    }
}

@Composable
private fun WeighingBatchItem(
    batch: WeighingBatch,
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
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
                    text = "${batch.tareCount} шт тари",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onRemove, modifier = Modifier.size(32.dp)) {
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

// ==================== Batch Review Screen ====================

/**
 * Review screen for batch mode - set tare weight and price before adding position.
 */
@Composable
fun TransactionBatchReview(
    productName: String,
    batches: List<WeighingBatch>,
    grossWeight: BigDecimal,
    totalTareCount: Int,
    tareWeightPerUnit: String,
    totalTareWeight: BigDecimal,
    netWeight: BigDecimal,
    pricePerKg: String,
    totalAmount: BigDecimal?,
    availableWeight: BigDecimal = BigDecimal.ZERO,
    showInventoryWarning: Boolean = false,
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
    val horizontalPadding = adaptiveHorizontalPadding()
    val contentPadding = adaptivePadding()
    val primaryButtonHeight = adaptivePrimaryButtonHeight()
    val isTabletDevice = isTablet()

    var priceHasBeenFocused by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .padding(horizontal = horizontalPadding, vertical = contentPadding)
            .imePadding()
    ) {
        // Summary card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f))
        ) {
            Column(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceEvenly
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Брутто", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${decimalFormat.format(grossWeight)} кг", style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Тара", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text("${decimalFormat.format(totalTareWeight)} кг", style = MaterialTheme.typography.titleMedium)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Нетто", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(
                            "${decimalFormat.format(netWeight)} кг",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }

        // Inventory warning
        if (showInventoryWarning) {
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        Icons.Filled.Warning,
                        contentDescription = "Попередження",
                        tint = MaterialTheme.colorScheme.error,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Доступно лише ${availableWeight.toPlainString()} кг",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onErrorContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Tare weight per unit
        OutlinedTextField(
            value = tareWeightPerUnit,
            onValueChange = onTareWeightChange,
            label = { Text("Вага тари за шт (кг)") },
            supportingText = { Text("$totalTareCount шт × $tareWeightPerUnit кг = ${decimalFormat.format(totalTareWeight)} кг") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Next),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Price per kg
        OutlinedTextField(
            value = pricePerKg,
            onValueChange = onPriceChange,
            label = { Text("Ціна за кг (₴)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal, imeAction = ImeAction.Done),
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .onFocusChanged { focusState ->
                    if (focusState.isFocused && !priceHasBeenFocused) {
                        priceHasBeenFocused = true
                        onPriceFocused()
                    }
                }
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Total amount card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Сума:", style = MaterialTheme.typography.titleMedium)
                Text(
                    text = totalAmount?.let { "₴${decimalFormat.format(it)}" } ?: "₴0.00",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Add position button
        Button(
            onClick = onAddPosition,
            enabled = canAdd,
            modifier = Modifier.fillMaxWidth().height(primaryButtonHeight)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Додати")
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                "ДОДАТИ ПОЗИЦІЮ",
                style = if (isTabletDevice) MaterialTheme.typography.titleLarge else MaterialTheme.typography.titleMedium
            )
        }
    }
}

// ==================== Positions List ====================

/**
 * List of added positions with totals and action buttons.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TransactionPositionsList(
    positions: List<TransactionPosition>,
    notes: String,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    canFinalize: Boolean,
    isEditing: Boolean,
    availableLocations: List<Location>,
    selectedLocationId: UUID?,
    transactionType: TransactionType,
    notesLabel: String,
    finalizeButtonText: String,
    onLocationChange: (UUID?) -> Unit,
    onNotesChange: (String) -> Unit,
    onRemovePosition: (String) -> Unit,
    onEditPosition: (TransactionPosition) -> Unit,
    onAddAnother: () -> Unit,
    onFinalize: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    val horizontalPadding = adaptiveHorizontalPadding()
    val itemSpacing = adaptiveItemSpacing()
    val buttonHeight = adaptiveButtonHeight()
    val primaryButtonHeight = adaptivePrimaryButtonHeight()
    val displayScale = adaptiveDisplayScale()
    val isTabletDevice = isTablet()
    val maxButtonWidth = adaptiveMaxButtonWidth()

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

    @Composable
    fun NotesField(fieldModifier: Modifier = Modifier) {
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text(notesLabel) },
            textStyle = if (isTabletDevice) MaterialTheme.typography.bodyLarge else MaterialTheme.typography.bodyMedium,
            modifier = fieldModifier,
            minLines = if (isTabletDevice) 3 else 2,
            maxLines = if (isTabletDevice) 5 else 4,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )
    }

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

    @Composable
    fun ActionButtons(buttonsModifier: Modifier = Modifier) {
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
                    text = finalizeButtonText,
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
        Row(
            modifier = modifier
                .padding(horizontal = horizontalPadding)
                .imePadding(),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            Column(modifier = Modifier.weight(0.6f)) {
                PositionsListContent(
                    listModifier = Modifier.weight(1f).fillMaxWidth(),
                    showAddButton = true
                )
            }

            Column(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(12.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                    .padding(20.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    text = "Чек",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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

                ActionButtons(Modifier.fillMaxWidth())
            }
        }
    } else {
        Column(modifier = modifier.imePadding()) {
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

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(horizontal = horizontalPadding, vertical = 16.dp)
            ) {
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
                        Text(finalizeButtonText, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun PositionItem(
    position: TransactionPosition,
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
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
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
                        text = "${position.netWeight.toPlainString()} кг × ₴${position.pricePerKg.toPlainString()}",
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
