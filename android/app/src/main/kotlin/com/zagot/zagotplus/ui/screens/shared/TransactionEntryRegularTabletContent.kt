package com.zagot.zagotplus.ui.screens.shared

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
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

/**
 * Unified tablet layout for regular (single weight) transaction entry.
 * Used by both purchase and sale flows.
 *
 * Layout:
 * - Left (40%): Product grid for selection
 * - Center (30%): Positions list with notes and grand total
 * - Right (30%): Data entry panel with custom numpad
 *
 * @param transactionType Determines display style (PURCHASE shows prices, SALE shows inventory)
 * @param inventoryMap For sale mode: product inventory levels to display in grid
 */
@Composable
fun TransactionEntryRegularTabletContent(
    uiState: TransactionEntryUiState,
    transactionType: TransactionType,
    onProductSelect: (Product) -> Unit,
    onProductOrderChanged: (List<UUID>) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onSelectInputFieldAndClear: (TransactionInputField) -> Unit,
    onAddPosition: () -> Unit,
    onSelectPosition: ((TransactionPosition) -> Unit)? = null,
    onRemovePosition: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    inventoryMap: Map<UUID, BigDecimal> = emptyMap(),
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // LEFT COLUMN: Product Grid (40%)
        RegularProductGridPanel(
            products = uiState.products,
            selectedProductId = uiState.dataEntryProduct?.id,
            transactionType = transactionType,
            inventoryMap = inventoryMap,
            onProductClick = onProductSelect,
            onOrderChanged = onProductOrderChanged,
            modifier = Modifier
                .weight(0.4f)
                .fillMaxHeight()
        )

        // CENTER COLUMN: Positions List + Notes + Grand Total (30%)
        RegularPositionsPanel(
            positions = uiState.positions,
            notes = uiState.notes,
            selectedPositionId = uiState.tabletEditingPositionId,
            totalWeight = uiState.totalWeight,
            totalAmount = uiState.totalAmount,
            transactionType = transactionType,
            onNotesChange = onNotesChange,
            onPositionClick = onSelectPosition,
            onRemovePosition = onRemovePosition,
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
        )

        // RIGHT COLUMN: Data Entry Panel with Numpad (30%)
        RegularDataEntryPanel(
            selectedProduct = uiState.dataEntryProduct,
            weight = uiState.effectiveWeight,
            price = uiState.currentPrice,
            total = uiState.currentTotal,
            activeInputField = uiState.activeInputField,
            isEditMode = uiState.isTabletEditMode,
            canAdd = uiState.canAddPosition,
            onFieldSelect = onSelectInputFieldAndClear,
            onKeypadInput = onKeypadInput,
            onKeypadDecimal = onKeypadDecimal,
            onKeypadBackspace = onKeypadBackspace,
            onNextInputField = onNextInputField,
            onAddPosition = onAddPosition,
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
        )
    }
}

/**
 * Product grid panel with transaction-type specific display.
 * Purchase: shows buy prices
 * Sale: shows inventory levels
 */
@Composable
private fun RegularProductGridPanel(
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
 * Positions panel with notes and grand total.
 * Supports optional position selection for edit mode.
 */
@Composable
private fun RegularPositionsPanel(
    positions: List<TransactionPosition>,
    notes: String,
    selectedPositionId: String?,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    transactionType: TransactionType,
    onNotesChange: (String) -> Unit,
    onPositionClick: ((TransactionPosition) -> Unit)?,
    onRemovePosition: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = DecimalFormat("#,##0.00")
    val currencyFormat = DecimalFormat("#,##0")
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
                    RegularPositionItem(
                        position = position,
                        isSelected = position.id == selectedPositionId,
                        onClick = if (onPositionClick != null) {
                            { onPositionClick(position) }
                        } else null,
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

        // Grand Total Card
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
 * Position item card for regular mode.
 */
@Composable
private fun RegularPositionItem(
    position: TransactionPosition,
    isSelected: Boolean,
    onClick: (() -> Unit)?,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val decimalFormat = DecimalFormat("#,##0.00")
    val borderColor = if (isSelected) Color(0xFF4CAF50) else Color.Transparent
    val borderWidth = if (isSelected) 2.dp else 0.dp
    val cardShape = RoundedCornerShape(12.dp)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        shape = cardShape,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(borderWidth, borderColor) else null
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
                    text = "${decimalFormat.format(position.netWeight)} кг × ₴${decimalFormat.format(position.pricePerKg)}",
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
 * Data entry panel with numpad for regular mode.
 */
@Composable
private fun RegularDataEntryPanel(
    selectedProduct: Product?,
    weight: String,
    price: String,
    total: BigDecimal?,
    activeInputField: TransactionInputField,
    isEditMode: Boolean,
    canAdd: Boolean,
    onFieldSelect: (TransactionInputField) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onAddPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    val currencyFormat = DecimalFormat("#,##0")
    val inputsEnabled = selectedProduct != null

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        // TOP SECTION: Inputs & Summary
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Введення даних",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

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
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (selectedProduct != null) FontWeight.Bold else FontWeight.Normal,
                    color = if (selectedProduct != null)
                        MaterialTheme.colorScheme.onSecondaryContainer
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(8.dp),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            // Weight and Price input boxes
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
                    label = "Ціна (₴/кг)",
                    value = price,
                    isActive = activeInputField == TransactionInputField.PRICE,
                    onClick = { onFieldSelect(TransactionInputField.PRICE) },
                    enabled = inputsEnabled,
                    modifier = Modifier.weight(1f)
                )
            }

            // Total amount display
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
                        text = total?.let { "₴${currencyFormat.format(it)}" } ?: "₴0",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // BOTTOM SECTION: Custom Numpad
        CustomNumpad(
            onNumberClick = onKeypadInput,
            onDecimalClick = onKeypadDecimal,
            onBackspaceClick = onKeypadBackspace,
            onNextFieldClick = onNextInputField,
            onActionClick = onAddPosition,
            actionEnabled = canAdd,
            isEditMode = isEditMode,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        )
    }
}
