package com.zagot.zagotplus.ui.screens.shared

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.ui.components.CustomNumpad
import com.zagot.zagotplus.ui.components.InputDisplayBox
import com.zagot.zagotplus.ui.components.PriceType
import com.zagot.zagotplus.ui.components.ReorderableProductGrid
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.UUID
import kotlin.math.roundToInt

/**
 * Unified tablet layout for batch (wholesale) transaction entry.
 * Used by both purchase and sale flows.
 *
 * Layout:
 * - Left (40%): Product grid for selection
 * - Center (30%): Positions list with notes and grand total (slides to reveal weightings)
 * - Right (30%): Data entry panel with weight + tare count inputs and numpad
 *
 * @param transactionType Determines display style (PURCHASE shows prices, SALE shows inventory)
 * @param inventoryMap For sale mode: product inventory levels to display in grid
 */
@Composable
fun TransactionEntryBatchTabletContent(
    uiState: TransactionEntryUiState,
    transactionType: TransactionType,
    onProductSelect: (Product) -> Unit,
    onProductOrderChanged: (List<UUID>) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onSelectInputFieldAndClear: (TransactionInputField) -> Unit,
    onAddBatch: () -> Unit,
    onRemoveBatch: (String) -> Unit,
    onSelectBatch: (WeighingBatch) -> Unit,
    onSelectPosition: (TransactionPosition) -> Unit,
    onRemovePosition: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    inventoryMap: Map<UUID, BigDecimal> = emptyMap(),
    modifier: Modifier = Modifier
) {
    // Determine Active Position
    val selectedPositionId = uiState.tabletReviewingPositionId
    val selectedPosition = uiState.positions.find { it.id == selectedPositionId }
    val isPanelOpen = selectedPosition != null

    // Animation for sliding panel
    val slideProgress = animateFloatAsState(
        targetValue = if (isPanelOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "positionsPanelSlide"
    )

    val spacingDp = 16.dp
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacingDp, vertical = 8.dp)
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val spacingPx = with(density) { spacingDp.toPx() }
        val availableWidthPx = containerWidthPx - (2 * spacingPx)
        val leftColWidthPx = availableWidthPx * 0.4f
        val middleColWidthPx = availableWidthPx * 0.3f
        val middleColStartX = leftColWidthPx + spacingPx
        val closedOffset = middleColStartX
        val openOffset = middleColStartX - spacingPx - middleColWidthPx
        val currentOffset = closedOffset + ((openOffset - closedOffset) * slideProgress.value)

        // LAYER 1: BACKGROUND GRID
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacingDp)
        ) {
            // LEFT: Product Grid
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .alpha(if (isPanelOpen) 0.3f else 1f)
            ) {
                BatchProductGridPanel(
                    products = uiState.products,
                    selectedProductId = selectedPosition?.product?.id,
                    transactionType = transactionType,
                    inventoryMap = inventoryMap,
                    onProductClick = if (isPanelOpen) { {} } else onProductSelect,
                    onOrderChanged = if (isPanelOpen) { {} } else onProductOrderChanged,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // MIDDLE: Weightings Panel (visible when position selected)
            if (selectedPosition != null) {
                BatchWeightingsPanel(
                    productName = selectedPosition.product.name,
                    batches = selectedPosition.batches,
                    grossWeight = selectedPosition.grossWeight,
                    tareWeight = uiState.tareWeightPerUnit,
                    price = uiState.currentPrice,
                    activeInputField = uiState.activeInputField,
                    selectedBatchId = uiState.tabletEditingBatchId,
                    onFieldSelect = onSelectInputFieldAndClear,
                    onSelectBatch = onSelectBatch,
                    onRemoveBatch = onRemoveBatch,
                    modifier = Modifier
                        .weight(0.3f)
                        .fillMaxHeight()
                )
            } else {
                Spacer(modifier = Modifier.weight(0.3f))
            }

            // RIGHT: Numpad Panel
            BatchEntryNumpadPanel(
                weight = uiState.currentWeight,
                tareCount = uiState.currentTareCount,
                activeInputField = uiState.activeInputField,
                canAddBatch = uiState.canAddBatch,
                isEditMode = uiState.isTabletBatchEditMode,
                inputsEnabled = isPanelOpen,
                onFieldSelect = onSelectInputFieldAndClear,
                onKeypadInput = onKeypadInput,
                onKeypadDecimal = onKeypadDecimal,
                onKeypadBackspace = onKeypadBackspace,
                onNextInputField = onNextInputField,
                onAddBatch = onAddBatch,
                onDone = { if (selectedPosition != null) onSelectPosition(selectedPosition) },
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
            )
        }

        // LAYER 2: SLIDING POSITIONS PANEL
        Box(
            modifier = Modifier
                .width(with(density) { middleColWidthPx.toDp() })
                .fillMaxHeight()
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
        ) {
            BatchPositionsPanel(
                positions = uiState.positions,
                notes = uiState.notes,
                selectedPositionId = selectedPositionId,
                totalWeight = uiState.totalWeight,
                totalAmount = uiState.totalAmount,
                transactionType = transactionType,
                onNotesChange = onNotesChange,
                onPositionClick = onSelectPosition,
                onRemovePosition = onRemovePosition,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Product grid panel for batch mode.
 */
@Composable
private fun BatchProductGridPanel(
    products: List<Product>,
    selectedProductId: UUID?,
    transactionType: TransactionType,
    inventoryMap: Map<UUID, BigDecimal>,
    onProductClick: (Product) -> Unit,
    onOrderChanged: (List<UUID>) -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
    ) {
        Text(
            text = "Товари",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        ReorderableProductGrid(
            products = products,
            onProductClick = onProductClick,
            onOrderChanged = onOrderChanged,
            modifier = Modifier.fillMaxSize(),
            showPrice = transactionType == TransactionType.PURCHASE,
            priceType = PriceType.BUY,
            inventoryMap = if (transactionType == TransactionType.SALE) inventoryMap else emptyMap(),
            selectedProductId = selectedProductId
        )
    }
}

/**
 * Weightings panel showing batches for the selected position.
 */
@Composable
private fun BatchWeightingsPanel(
    productName: String,
    batches: List<WeighingBatch>,
    grossWeight: BigDecimal,
    tareWeight: String,
    price: String,
    activeInputField: TransactionInputField,
    selectedBatchId: String?,
    onFieldSelect: (TransactionInputField) -> Unit,
    onSelectBatch: (WeighingBatch) -> Unit,
    onRemoveBatch: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        Text(
            text = productName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Tare weight and price inputs
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InputDisplayBox(
                label = "Тара (кг/шт)",
                value = tareWeight,
                isActive = activeInputField == TransactionInputField.TARE_WEIGHT_UNIT,
                onClick = { onFieldSelect(TransactionInputField.TARE_WEIGHT_UNIT) },
                enabled = true,
                modifier = Modifier.weight(1f)
            )

            InputDisplayBox(
                label = "Ціна (₴/кг)",
                value = price,
                isActive = activeInputField == TransactionInputField.PRICE,
                onClick = { onFieldSelect(TransactionInputField.PRICE) },
                enabled = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Running total card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier
                    .padding(12.dp)
                    .fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "${batches.size} зважувань:",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${decimalFormat.format(grossWeight)} кг",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Batches list
        if (batches.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Введіть вагу справа →",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(batches.asReversed()) { batch ->
                    BatchWeightingItem(
                        batchNumber = batches.indexOf(batch) + 1,
                        batch = batch,
                        isSelected = batch.id == selectedBatchId,
                        onClick = { onSelectBatch(batch) },
                        onRemove = { onRemoveBatch(batch.id) }
                    )
                }
            }
        }
    }
}

/**
 * Individual weighting batch item.
 */
@Composable
private fun BatchWeightingItem(
    batchNumber: Int,
    batch: WeighingBatch,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val selectionColor = Color(0xFFFFC107)
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected)
            BorderStroke(3.dp, selectionColor)
        else
            BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier.weight(1f),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected) selectionColor
                            else MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$batchNumber",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected) Color.Black
                        else MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }

                Column {
                    Text(
                        text = "${decimalFormat.format(batch.grossWeightKg)} кг",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "${batch.tareCount} шт тари",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(36.dp)
            ) {
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
 * Batch entry numpad panel.
 */
@Composable
private fun BatchEntryNumpadPanel(
    weight: String,
    tareCount: String,
    activeInputField: TransactionInputField,
    canAddBatch: Boolean,
    isEditMode: Boolean,
    inputsEnabled: Boolean,
    onFieldSelect: (TransactionInputField) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onAddBatch: () -> Unit,
    onDone: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                text = "Введення ваги",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            Spacer(modifier = Modifier.height(12.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InputDisplayBox(
                    label = "Вага (кг)",
                    value = weight,
                    isActive = activeInputField == TransactionInputField.WEIGHT,
                    onClick = { onFieldSelect(TransactionInputField.WEIGHT) },
                    enabled = inputsEnabled,
                    modifier = Modifier.weight(1f)
                )

                InputDisplayBox(
                    label = "Тара (шт)",
                    value = tareCount,
                    isActive = activeInputField == TransactionInputField.TARE_COUNT,
                    onClick = { onFieldSelect(TransactionInputField.TARE_COUNT) },
                    enabled = inputsEnabled,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Button(
                onClick = {
                    // Auto-add pending weighing if valid before saving
                    if (canAddBatch && inputsEnabled) {
                        onAddBatch()
                    }
                    onDone()
                },
                enabled = inputsEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(40.dp)
            ) {
                Text("ГОТОВО (ЗБЕРЕГТИ)", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        CustomNumpad(
            onNumberClick = onKeypadInput,
            onDecimalClick = onKeypadDecimal,
            onBackspaceClick = onKeypadBackspace,
            onNextFieldClick = onNextInputField,
            onActionClick = onAddBatch,
            actionEnabled = canAddBatch && inputsEnabled,
            isEditMode = isEditMode,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}

/**
 * Sliding positions panel with notes and grand total.
 */
@Composable
private fun BatchPositionsPanel(
    positions: List<TransactionPosition>,
    notes: String,
    selectedPositionId: String?,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    transactionType: TransactionType,
    onNotesChange: (String) -> Unit,
    onPositionClick: (TransactionPosition) -> Unit,
    onRemovePosition: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val currencyFormat = remember { DecimalFormat("#,##0") }
    val notesLabel = when (transactionType) {
        TransactionType.PURCHASE -> "Постачальник / примітки"
        TransactionType.SALE -> "Покупець / примітки"
        else -> "Примітки"
    }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp)
    ) {
        Text(
            text = "Позиції (${positions.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))

        if (positions.isEmpty()) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "Оберіть товар зліва",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(positions, key = { it.id }) { position ->
                    BatchPositionItem(
                        position = position,
                        isSelected = position.id == selectedPositionId,
                        onClick = { onPositionClick(position) },
                        onRemove = { onRemovePosition(position.id) }
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))

        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text(notesLabel) },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        Spacer(modifier = Modifier.height(12.dp))

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f)
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
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
                        text = "Всього",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${decimalFormat.format(totalWeight)} кг",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "₴${currencyFormat.format(totalAmount)}",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

/**
 * Position item for batch mode.
 */
@Composable
private fun BatchPositionItem(
    position: TransactionPosition,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val selectionColor = Color(0xFFFFC107)
    val cardShape = RoundedCornerShape(12.dp)
    val backgroundColor = if (isSelected) selectionColor.copy(alpha = 0.15f)
    else MaterialTheme.colorScheme.surface
    val borderColor = if (isSelected) selectionColor
    else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(borderWidth, borderColor)
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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = "${decimalFormat.format(position.netWeight)} кг × ₴${decimalFormat.format(position.pricePerKg)} (${position.batches.size} зв.)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "₴${decimalFormat.format(position.totalAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Spacer(modifier = Modifier.width(8.dp))
                IconButton(
                    onClick = onRemove,
                    modifier = Modifier.size(32.dp)
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
