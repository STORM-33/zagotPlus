package com.zagot.zagotplus.ui.screens.sale

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.ui.draw.alpha
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
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import kotlin.math.roundToInt
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zagot.zagotplus.domain.model.Product
import com.zagot.zagotplus.ui.components.CustomNumpad
import com.zagot.zagotplus.ui.components.InputDisplayBox
import com.zagot.zagotplus.ui.components.PriceType
import com.zagot.zagotplus.ui.components.ReorderableProductGrid
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.UUID

/**
 * Tablet-specific three-column layout for sale entry with custom numpad.
 *
 * Layout:
 * - Left (40%): Product grid for selection
 * - Center (30%): Positions list with notes and grand total
 * - Right (30%): Data entry panel with two modes:
 *   1. Batch Entry Mode: Add multiple batches (weight + tare count)
 *   2. Finalization Mode: Enter tare weight per unit + price, then add position
 */
@Composable
fun SaleEntryTabletContent(
    uiState: SaleEntryUiState,
    onProductSelect: (Product) -> Unit,
    onProductOrderChanged: (List<UUID>) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onSelectInputFieldAndClear: (SaleInputField) -> Unit,
    onAddBatch: () -> Unit,
    onRemoveBatch: (String) -> Unit,
    onSelectBatch: (SaleWeighingBatch) -> Unit,
    onAddPosition: () -> Unit,
    onSelectPosition: (SalePosition) -> Unit,
    onRemovePosition: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val inventoryMap = remember(uiState.inventory) {
        uiState.inventory.associate { it.productId to it.totalWeightKg }
    }

    // Determine Active Position
    val selectedPositionId = uiState.tabletReviewingPositionId
    val selectedPosition = uiState.positions.find { it.id == selectedPositionId }
    val isPanelOpen = selectedPosition != null

    // Animation
    val slideProgress = animateFloatAsState(
        targetValue = if (isPanelOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 250, easing = FastOutSlowInEasing),
        label = "positionsPanelSlide"
    )

    // LAYOUT CONSTANTS
    val spacingDp = 16.dp
    val density = LocalDensity.current

    // Use BoxWithConstraints to calculate exact pixel positions
    BoxWithConstraints(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = spacingDp, vertical = 8.dp)
    ) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val spacingPx = with(density) { spacingDp.toPx() }

        // CALCULATE GRID GEOMETRY
        // The Row below uses weights 0.4 / 0.3 / 0.3 with 16dp spacing.
        // Formula: AvailableWidth = TotalWidth - (2 * Spacing)
        val availableWidthPx = containerWidthPx - (2 * spacingPx)

        // Exact width of columns based on weights
        val leftColWidthPx = availableWidthPx * 0.4f
        val middleColWidthPx = availableWidthPx * 0.3f

        // The X coordinate where the Middle Column starts
        val middleColStartX = leftColWidthPx + spacingPx

        // OFFSETS FOR ANIMATION
        // Closed State: Panel sits exactly in the Middle Column slot
        val closedOffset = middleColStartX

        // Open State: Panel sits to the LEFT of the Middle Column, separated by 'spacingPx'
        // Position = (MiddleStart) - (Gap) - (PanelWidth)
        val openOffset = middleColStartX - spacingPx - middleColWidthPx

        // Current interpolated offset
        val currentOffset = closedOffset + ((openOffset - closedOffset) * slideProgress.value)

        // --- LAYER 1: BACKGROUND GRID ---
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.spacedBy(spacingDp)
        ) {
            // LEFT PANEL (Product Grid)
            Box(
                modifier = Modifier
                    .weight(0.4f)
                    .fillMaxHeight()
                    .alpha(if (isPanelOpen) 0.3f else 1f)
            ) {
                ProductGridPanel(
                    products = uiState.products,
                    selectedProductId = selectedPosition?.product?.id,
                    inventoryMap = inventoryMap,
                    onProductClick = if (isPanelOpen) { {} } else onProductSelect,
                    onOrderChanged = if (isPanelOpen) { {} } else onProductOrderChanged,
                    modifier = Modifier.fillMaxSize()
                )
            }

            // MIDDLE PANEL (Weightings)
            if (selectedPosition != null) {
                WeightingsPanel(
                    productName = selectedPosition.product.name,
                    batches = selectedPosition.batches,
                    grossWeight = selectedPosition.grossWeight,
                    tareWeight = uiState.tareWeightPerUnit,
                    price = uiState.pricePerKg,
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

            // RIGHT PANEL (Numpad)
            BatchEntryPanelWithNumpad(
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

        // --- LAYER 2: SLIDING PANEL (Positions) ---
        // We set the width explicitly to 'middleColWidthPx' so it matches the grid exactly
        Box(
            modifier = Modifier
                .width(with(density) { middleColWidthPx.toDp() })
                .fillMaxHeight()
                .offset { IntOffset(currentOffset.roundToInt(), 0) }
        ) {
            PositionsPanel(
                positions = uiState.positions,
                notes = uiState.notes,
                selectedPositionId = selectedPositionId,
                totalWeight = uiState.totalWeight,
                totalAmount = uiState.totalAmount,
                onNotesChange = onNotesChange,
                onPositionClick = onSelectPosition,
                onRemovePosition = onRemovePosition,
                modifier = Modifier.fillMaxSize()
            )
        }
    }
}

/**
 * Updated Batch Entry Panel
 * Now accepts productName string instead of whole Product object,
 * and takes an explicit `inputsEnabled` boolean.
 */
@Composable
private fun BatchEntryPanelWithNumpad(
    weight: String,
    tareCount: String,
    activeInputField: SaleInputField,
    canAddBatch: Boolean,
    isEditMode: Boolean,
    inputsEnabled: Boolean,
    onFieldSelect: (SaleInputField) -> Unit,
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
        // TOP SECTION: Inputs
        Column(
            modifier = Modifier.fillMaxWidth()
        ) {
            // 1. Header (Single line, Bold Title Medium - Exact match to WeightingsPanel)
            Text(
                text = "Введення ваги",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )

            // 2. Exact spacer match (12.dp)
            Spacer(modifier = Modifier.height(12.dp))

            // 3. Inputs
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InputDisplayBox(
                    label = "Вага (кг)",
                    value = weight,
                    isActive = activeInputField == SaleInputField.WEIGHT,
                    onClick = { onFieldSelect(SaleInputField.WEIGHT) },
                    enabled = inputsEnabled,
                    modifier = Modifier.weight(1f)
                )

                InputDisplayBox(
                    label = "Тара (шт)",
                    value = tareCount,
                    isActive = activeInputField == SaleInputField.TARE_COUNT,
                    onClick = { onFieldSelect(SaleInputField.TARE_COUNT) },
                    enabled = inputsEnabled,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // "Done" Button
            Button(
                onClick = onDone,
                enabled = inputsEnabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.secondary
                ),
                modifier = Modifier.fillMaxWidth().height(40.dp)
            ) {
                Text("ГОТОВО (ЗБЕРЕГТИ)", style = MaterialTheme.typography.labelMedium)
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // NUMPAD
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
 * Left panel: Product grid with selection highlight and inventory display.
 */
@Composable
private fun ProductGridPanel(
    products: List<Product>,
    selectedProductId: UUID?,
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
        // Header
        Text(
            text = "Товари",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )

        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

        // Product grid with inventory
        ReorderableProductGrid(
            products = products,
            onProductClick = onProductClick,
            onOrderChanged = onOrderChanged,
            modifier = Modifier.fillMaxSize(),
            showPrice = false,
            inventoryMap = inventoryMap,
            selectedProductId = selectedProductId
        )
    }
}

/**
 * Center panel (Mode 1): Weightings list for current product.
 * Shown when product is selected and in batch entry mode.
 */
@Composable
private fun WeightingsPanel(
    productName: String,
    batches: List<SaleWeighingBatch>,
    grossWeight: BigDecimal,
    tareWeight: String,
    price: String,
    activeInputField: SaleInputField,
    onFieldSelect: (SaleInputField) -> Unit,
    selectedBatchId: String?,
    onSelectBatch: (SaleWeighingBatch) -> Unit,
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
        // 1. Header
        Text(
            text = productName,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )

        Spacer(modifier = Modifier.height(12.dp))

        // 2. Global Settings (Mirrors Right Panel Layout)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            InputDisplayBox(
                label = "Тара (кг/шт)",
                value = tareWeight,
                isActive = activeInputField == SaleInputField.TARE_WEIGHT_UNIT,
                onClick = { onFieldSelect(SaleInputField.TARE_WEIGHT_UNIT) },
                enabled = true,
                modifier = Modifier.weight(1f)
            )

            InputDisplayBox(
                label = "Ціна (₴/кг)",
                value = price,
                isActive = activeInputField == SaleInputField.PRICE,
                onClick = { onFieldSelect(SaleInputField.PRICE) },
                enabled = true,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(12.dp))

        // 3. Running Total
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            ),
            border = BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
        ) {
            Row(
                modifier = Modifier.padding(12.dp).fillMaxWidth(),
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

        // 4. Batches List
        if (batches.isEmpty()) {
            Box(
                modifier = Modifier.weight(1f).fillMaxWidth(),
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
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(batches.asReversed()) { batch ->
                    WeightingBatchItem(
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
 * Weighting batch item in the middle panel.
 * Larger and more detailed than the compact version.
 * Supports tap-to-select for inline editing.
 */
@Composable
private fun WeightingBatchItem(
    batchNumber: Int,
    batch: SaleWeighingBatch,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val selectionBorderColor = Color(0xFFFFC107) // Yellow for selection
    val cardShape = RoundedCornerShape(12.dp)
    val contentAlpha = 1f

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(Modifier.clickable(onClick = onClick))
            .alpha(contentAlpha),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = if (isSelected)
                MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            else
                MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected)
            BorderStroke(3.dp, selectionBorderColor)
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
                // Batch number badge
                Box(
                    modifier = Modifier
                        .size(32.dp)
                        .clip(RoundedCornerShape(6.dp))
                        .background(
                            if (isSelected)
                                selectionBorderColor
                            else
                                MaterialTheme.colorScheme.primaryContainer
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = "#$batchNumber",
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (isSelected)
                            Color.Black
                        else
                            MaterialTheme.colorScheme.onPrimaryContainer
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
 * Center panel (Mode 2): Positions list with notes field and grand total.
 * Shown when in finalization mode or when no product is selected.
 */
@Composable
private fun PositionsPanel(
    positions: List<SalePosition>,
    notes: String,
    selectedPositionId: String?,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    onNotesChange: (String) -> Unit,
    onPositionClick: (SalePosition) -> Unit,
    onRemovePosition: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val currencyFormat = remember { DecimalFormat("#,##0") }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(16.dp)
    ) {
        // Header with count
        Text(
            text = "Позиції (${positions.size})",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold
        )

        Spacer(modifier = Modifier.height(12.dp))
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        Spacer(modifier = Modifier.height(12.dp))

        // Positions list
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
                modifier = Modifier.weight(1f).fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(positions, key = { it.id }) { position ->
                    TabletPositionItem(
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

        // Notes field
        OutlinedTextField(
            value = notes,
            onValueChange = onNotesChange,
            label = { Text("Покупець / примітки") },
            modifier = Modifier.fillMaxWidth(),
            minLines = 2,
            maxLines = 3,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done)
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Grand Total Card (Receipt Total)
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
 * Compact position item for tablet center panel.
 */
@Composable
private fun TabletPositionItem(
    position: SalePosition,
    isSelected: Boolean = false,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val cardShape = RoundedCornerShape(12.dp)

    // Highlight colors (Matching the batch items)
    val selectionColor = Color(0xFFFFC107) // Amber/Yellow
    val backgroundColor = if (isSelected) selectionColor.copy(alpha = 0.15f) else MaterialTheme.colorScheme.surface
    val borderColor = if (isSelected) selectionColor else MaterialTheme.colorScheme.outlineVariant
    val borderWidth = if (isSelected) 2.dp else 1.dp

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(containerColor = backgroundColor),
        border = BorderStroke(borderWidth, borderColor) // <--- Yellow border if selected
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

                // Optional: Hide remove button on the active item to prevent accidental deletion while editing?
                // For now, we keep it visible.
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