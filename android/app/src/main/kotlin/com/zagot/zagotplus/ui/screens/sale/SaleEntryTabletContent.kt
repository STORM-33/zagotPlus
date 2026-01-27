package com.zagot.zagotplus.ui.screens.sale

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.runtime.remember
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
    onToggleFinalizationMode: () -> Unit,
    onAddPosition: () -> Unit,
    onSelectPosition: (SalePosition) -> Unit,
    onRemovePosition: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    // Create inventory map for product grid
    val inventoryMap = remember(uiState.inventory) {
        uiState.inventory.associate { it.productId to it.totalWeightKg }
    }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // LEFT COLUMN: Product Grid (40%)
        ProductGridPanel(
            products = uiState.products,
            selectedProductId = uiState.selectedProduct?.id,
            inventoryMap = inventoryMap,
            onProductClick = onProductSelect,
            onOrderChanged = onProductOrderChanged,
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
        )

        // CENTER COLUMN: Positions List + Notes + Grand Total (30%)
        PositionsPanel(
            positions = uiState.positions,
            notes = uiState.notes,
            totalWeight = uiState.totalWeight,
            totalAmount = uiState.totalAmount,
            onNotesChange = onNotesChange,
            onPositionClick = onSelectPosition,
            onRemovePosition = onRemovePosition,
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
        )

        // RIGHT COLUMN: Data Entry Panel with Numpad (30%)
        if (uiState.isInFinalizationMode) {
            FinalizationPanelWithNumpad(
                selectedProduct = uiState.selectedProduct,
                batches = uiState.currentBatches,
                grossWeight = uiState.currentGrossWeight,
                tareWeightPerUnit = uiState.tareWeightPerUnit,
                totalTareCount = uiState.currentTotalTareCount,
                totalTareWeight = uiState.currentTotalTareWeight,
                netWeight = uiState.currentNetWeight,
                price = uiState.pricePerKg,
                totalAmount = uiState.currentTotalAmount,
                activeInputField = uiState.activeInputField,
                canAdd = uiState.canAddPosition,
                onFieldSelect = onSelectInputFieldAndClear,
                onKeypadInput = onKeypadInput,
                onKeypadDecimal = onKeypadDecimal,
                onKeypadBackspace = onKeypadBackspace,
                onNextInputField = onNextInputField,
                onBackToBatches = onToggleFinalizationMode,
                onAddPosition = onAddPosition,
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
            )
        } else {
            BatchEntryPanelWithNumpad(
                selectedProduct = uiState.selectedProduct,
                weight = uiState.currentWeight,
                tareCount = uiState.currentTareCount,
                batches = uiState.currentBatches,
                grossWeight = uiState.currentGrossWeight,
                activeInputField = uiState.activeInputField,
                canAddBatch = uiState.canAddBatch,
                canProceed = uiState.canProceedToReview,
                onFieldSelect = onSelectInputFieldAndClear,
                onKeypadInput = onKeypadInput,
                onKeypadDecimal = onKeypadDecimal,
                onKeypadBackspace = onKeypadBackspace,
                onNextInputField = onNextInputField,
                onAddBatch = onAddBatch,
                onRemoveBatch = onRemoveBatch,
                onProceedToFinalize = onToggleFinalizationMode,
                modifier = Modifier
                    .weight(0.3f)
                    .fillMaxHeight()
            )
        }
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
            inventoryMap = inventoryMap
        )
    }
}

/**
 * Center panel: Positions list with notes field and grand total.
 */
@Composable
private fun PositionsPanel(
    positions: List<SalePosition>,
    notes: String,
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
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                items(positions, key = { it.id }) { position ->
                    TabletPositionItem(
                        position = position,
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
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
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

/**
 * Right panel (Batch Entry Mode): Add multiple batches before finalization.
 * Shows weight + tare count inputs, batches list, and action buttons.
 */
@Composable
private fun BatchEntryPanelWithNumpad(
    selectedProduct: Product?,
    weight: String,
    tareCount: String,
    batches: List<SaleWeighingBatch>,
    grossWeight: BigDecimal,
    activeInputField: SaleInputField,
    canAddBatch: Boolean,
    canProceed: Boolean,
    onFieldSelect: (SaleInputField) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onAddBatch: () -> Unit,
    onRemoveBatch: (String) -> Unit,
    onProceedToFinalize: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val inputsEnabled = selectedProduct != null

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        // ==================== TOP SECTION: Inputs & Batches (35%) ====================
        Column(
            modifier = Modifier.weight(0.35f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header
            Text(
                text = "Зважування",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Product name card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = if (selectedProduct != null)
                        MaterialTheme.colorScheme.secondaryContainer
                    else
                        MaterialTheme.colorScheme.surfaceContainerHigh
                )
            ) {
                Text(
                    text = selectedProduct?.name ?: "Оберіть товар зліва",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = if (selectedProduct != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedProduct != null)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(10.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Weight and Tare Count input boxes
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
                    label = "Кількість тари",
                    value = tareCount,
                    isActive = activeInputField == SaleInputField.TARE_COUNT,
                    onClick = { onFieldSelect(SaleInputField.TARE_COUNT) },
                    enabled = inputsEnabled,
                    modifier = Modifier.weight(1f)
                )
            }

            // Running total of batches
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "${batches.size} зважувань:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${decimalFormat.format(grossWeight)} кг",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            // Batches list (scrollable)
            if (batches.isNotEmpty()) {
                LazyColumn(
                    modifier = Modifier.weight(1f, fill = false),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(batches, key = { it.id }) { batch ->
                        BatchItem(batch = batch, onRemove = { onRemoveBatch(batch.id) })
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Action buttons (Add Batch / Review)
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (canProceed) {
                OutlinedButton(
                    onClick = onProceedToFinalize,
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("ПЕРЕГЛЯНУТИ", style = MaterialTheme.typography.labelLarge)
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(Icons.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ==================== BOTTOM SECTION: Custom Numpad (65%) ====================
        CustomNumpad(
            onNumberClick = onKeypadInput,
            onDecimalClick = onKeypadDecimal,
            onBackspaceClick = onKeypadBackspace,
            onNextFieldClick = onNextInputField,
            onActionClick = onAddBatch,
            actionEnabled = canAddBatch,
            isEditMode = false,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
        )
    }
}

/**
 * Compact batch item in the batches list.
 */
@Composable
private fun BatchItem(
    batch: SaleWeighingBatch,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "${decimalFormat.format(batch.grossWeightKg)} кг (${batch.tareCount} шт)",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Medium
            )
            IconButton(
                onClick = onRemove,
                modifier = Modifier.size(24.dp)
            ) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Видалити",
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(16.dp)
                )
            }
        }
    }
}

/**
 * Right panel (Finalization Mode): Enter tare weight per unit + price, review calculations.
 * Shows tare weight + price inputs, net weight calculation, and "Add Position" button.
 */
@Composable
private fun FinalizationPanelWithNumpad(
    selectedProduct: Product?,
    batches: List<SaleWeighingBatch>,
    grossWeight: BigDecimal,
    tareWeightPerUnit: String,
    totalTareCount: Int,
    totalTareWeight: BigDecimal,
    netWeight: BigDecimal,
    price: String,
    totalAmount: BigDecimal?,
    activeInputField: SaleInputField,
    canAdd: Boolean,
    onFieldSelect: (SaleInputField) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onBackToBatches: () -> Unit,
    onAddPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val currencyFormat = remember { DecimalFormat("#,##0") }

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        // ==================== TOP SECTION: Inputs & Calculation (35%) ====================
        Column(
            modifier = Modifier.weight(0.35f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header
            Text(
                text = "Фіналізація",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            // Product name card
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer
                )
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text(
                        text = selectedProduct?.name ?: "",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = "${batches.size} зважувань • ${decimalFormat.format(grossWeight)} кг",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                }
            }

            // Tare Weight and Price input boxes
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InputDisplayBox(
                    label = "Тара (кг/шт)",
                    value = tareWeightPerUnit,
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

            // Weight calculation breakdown
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surface
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant)
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Брутто:", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "${decimalFormat.format(grossWeight)} кг",
                            style = MaterialTheme.typography.bodySmall,
                            fontWeight = FontWeight.Medium
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Тара ($totalTareCount шт):", style = MaterialTheme.typography.bodySmall)
                        Text(
                            "-${decimalFormat.format(totalTareWeight)} кг",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                    HorizontalDivider(modifier = Modifier.padding(vertical = 4.dp))
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            "Нетто:",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            "${decimalFormat.format(netWeight)} кг",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            // Total amount - LARGE and prominent
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.5f)
                ),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.secondary)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Сума:",
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                    Text(
                        text = totalAmount?.let { "₴${currencyFormat.format(it)}" } ?: "₴0.00",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Back to batches button
        OutlinedButton(
            onClick = onBackToBatches,
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            colors = ButtonDefaults.outlinedButtonColors(
                contentColor = MaterialTheme.colorScheme.onSurfaceVariant
            )
        ) {
            Text("← НАЗАД ДО ЗВАЖУВАНЬ", style = MaterialTheme.typography.labelMedium)
        }

        Spacer(modifier = Modifier.height(8.dp))

        // ==================== BOTTOM SECTION: Custom Numpad (65%) ====================
        CustomNumpad(
            onNumberClick = onKeypadInput,
            onDecimalClick = onKeypadDecimal,
            onBackspaceClick = onKeypadBackspace,
            onNextFieldClick = onNextInputField,
            onActionClick = onAddPosition,
            actionEnabled = canAdd,
            isEditMode = false,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
        )
    }
}
