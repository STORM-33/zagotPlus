package com.zagot.zagotplus.ui.screens.purchase

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
import java.util.UUID

/**
 * Tablet-specific three-column layout for purchase entry with custom numpad.
 * 
 * Layout:
 * - Left (40%): Product grid for selection
 * - Center (30%): Positions list with notes
 * - Right (30%): Data entry panel with custom numpad (no system keyboard)
 */
@Composable
fun PurchaseEntryTabletContent(
    uiState: PurchaseEntryUiState,
    onProductSelect: (Product) -> Unit,
    onProductOrderChanged: (List<UUID>) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onSelectInputFieldAndClear: (InputField) -> Unit,
    onAddPosition: () -> Unit,
    onSelectTabletPosition: (PurchasePosition) -> Unit,
    onRemovePosition: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        horizontalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // LEFT COLUMN: Product Grid (40%)
        ProductGridPanel(
            products = uiState.products,
            selectedProductId = uiState.dataEntryProduct?.id,
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
            selectedPositionId = uiState.tabletEditingPositionId,
            totalWeight = uiState.totalWeight,
            totalAmount = uiState.totalAmount,
            onNotesChange = onNotesChange,
            onPositionClick = onSelectTabletPosition,
            onRemovePosition = onRemovePosition,
            modifier = Modifier
                .weight(0.3f)
                .fillMaxHeight()
        )

        // RIGHT COLUMN: Data Entry Panel with Numpad (30%)
        DataEntryPanelWithNumpad(
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
 * Left panel: Product grid with selection highlight.
 */
@Composable
private fun ProductGridPanel(
    products: List<Product>,
    selectedProductId: UUID?,
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

        // Product grid
        ReorderableProductGrid(
            products = products,
            onProductClick = onProductClick,
            onOrderChanged = onOrderChanged,
            modifier = Modifier.fillMaxSize(),
            showPrice = true,
            priceType = PriceType.BUY
        )
    }
}

/**
 * Center panel: Positions list with notes field.
 * Positions are selectable by tap for inline editing.
 */
@Composable
private fun PositionsPanel(
    positions: List<PurchasePosition>,
    notes: String,
    selectedPositionId: String?,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    onNotesChange: (String) -> Unit,
    onPositionClick: (PurchasePosition) -> Unit,
    onRemovePosition: (String) -> Unit,
    modifier: Modifier = Modifier
) {
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
            label = { Text("Примітки") },
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
                        text = "${totalWeight.toPlainString()} кг",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                Text(
                    text = "₴${totalAmount.toPlainString()}",
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
 * Tap to select for editing, tap again to deselect.
 */
@Composable
private fun TabletPositionItem(
    position: PurchasePosition,
    isSelected: Boolean,
    onClick: () -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) Color(0xFFFFC107) else Color.Transparent
    val borderWidth = if (isSelected) 2.dp else 0.dp
    val cardShape = RoundedCornerShape(12.dp)
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clip(cardShape)
            .clickable(onClick = onClick),
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
                    text = "${position.weightKg.toPlainString()} кг × ₴${position.pricePerKg.toPlainString()}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = "₴${position.totalAmount.toPlainString()}",
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
 * Right panel: Data entry with custom numpad (no system keyboard).
 * 
 * Layout:
 * - Top: Product name, Weight/Price input boxes, current sum, totals, action buttons
 * - Bottom: Custom numpad with Add/Edit button integrated
 */
@Composable
private fun DataEntryPanelWithNumpad(
    selectedProduct: Product?,
    weight: String,
    price: String,
    total: BigDecimal?,
    activeInputField: InputField,
    isEditMode: Boolean,
    canAdd: Boolean,
    onFieldSelect: (InputField) -> Unit,
    onKeypadInput: (Char) -> Unit,
    onKeypadDecimal: () -> Unit,
    onKeypadBackspace: () -> Unit,
    onNextInputField: () -> Unit,
    onAddPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Input fields enabled only when product is selected
    val inputsEnabled = selectedProduct != null
    
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(12.dp))
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(12.dp)
    ) {
        // ==================== TOP SECTION: Inputs & Summary (35%) ====================
        Column(
            modifier = Modifier.weight(0.35f),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header
            Text(
                text = "Введення даних",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )
            
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

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

            // Weight and Price input boxes (side by side)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                InputDisplayBox(
                    label = "Вага (кг)",
                    value = weight,
                    isActive = activeInputField == InputField.WEIGHT,
                    onClick = { onFieldSelect(InputField.WEIGHT) },
                    enabled = inputsEnabled,
                    modifier = Modifier.weight(1f)
                )
                
                InputDisplayBox(
                    label = "Ціна (₴/кг)",
                    value = price,
                    isActive = activeInputField == InputField.PRICE,
                    onClick = { onFieldSelect(InputField.PRICE) },
                    enabled = inputsEnabled,
                    modifier = Modifier.weight(1f)
                )
            }

            // Item Subtotal - LARGE and prominent for instant math confirmation
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
                        text = total?.let { "₴${it.toPlainString()}" } ?: "₴0.00",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
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
            onActionClick = onAddPosition,
            actionEnabled = canAdd,
            isEditMode = isEditMode,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.65f)
        )
    }
}
