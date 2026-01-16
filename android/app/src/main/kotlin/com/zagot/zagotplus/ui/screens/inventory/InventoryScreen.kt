package com.zagot.zagotplus.ui.screens.inventory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.ui.components.EmptyState
import com.zagot.zagotplus.ui.components.EmptyStateIcons
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

/**
 * Action type for inventory item context menu.
 */
private enum class InventoryItemAction {
    TRANSFER,
    ADJUST
}

@Composable
fun InventoryScreen(
    modifier: Modifier = Modifier,
    onNavigateToTransfer: (productId: String, sourceLocationId: String) -> Unit = { _, _ -> },
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayItems by viewModel.displayItems.collectAsStateWithLifecycle()
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    
    // State for dialogs
    var selectedItem by remember { mutableStateOf<InventoryDisplayItem?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier.fillMaxSize()
        ) {
            // Location tabs + Total tab
            if (uiState.locations.isNotEmpty()) {
                val isTotalView = uiState.viewMode == InventoryViewMode.TOTAL
                val selectedIndex = if (isTotalView) {
                    uiState.locations.size // Total tab is last
                } else {
                    uiState.locations.indexOfFirst { 
                        it.id == uiState.selectedLocation?.id 
                    }.coerceAtLeast(0)
                }
                
                TabRow(selectedTabIndex = selectedIndex) {
                    uiState.locations.forEachIndexed { index, location ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { viewModel.selectLocation(location) },
                            text = { Text(location.name) }
                        )
                    }
                    // Total tab
                    Tab(
                        selected = isTotalView,
                        onClick = { viewModel.selectTotalView() },
                        text = { Text("Всього") }
                    )
                }
            }

            // Content
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
            ) {
                when {
                    uiState.isLoading || uiState.isRefreshing -> {
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        if (displayItems.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                EmptyState(
                                    icon = EmptyStateIcons.Inventory,
                                    title = "Немає товарів",
                                    description = "Залишки з'являться після закупівель"
                                )
                            }
                        } else {
                            val isTotalView = uiState.viewMode == InventoryViewMode.TOTAL
                            
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Inventory items as cards
                                items(displayItems, key = { it.productId }) { item ->
                                    InventoryItemCard(
                                        item = item,
                                        decimalFormat = decimalFormat,
                                        showContextMenuOption = !isTotalView,
                                        onTransferClick = {
                                            selectedItem = item
                                            showMoveDialog = true
                                        },
                                        onAdjustClick = {
                                            selectedItem = item
                                            showAdjustDialog = true
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            // Last sync timestamp footer
            uiState.lastSyncTime?.let { timestamp ->
                val localTime = timestamp.atZone(ZoneId.systemDefault())
                val formattedTime = timeFormatter.format(localTime)
                
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                        .padding(horizontal = 16.dp, vertical = 8.dp)
                ) {
                    Text(
                        text = "Останнє оновлення: $formattedTime",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        
        // Move product dialog
        if (showMoveDialog && selectedItem != null) {
            val item = selectedItem!!
            val currentLocationId = item.locationId

            if (currentLocationId != null) {
                MoveProductDialog(
                    productName = item.productName,
                    onDismiss = {
                        showMoveDialog = false
                        selectedItem = null
                    },
                    onConfirm = {
                        onNavigateToTransfer(
                            item.productId.toString(),
                            currentLocationId.toString()
                        )
                        showMoveDialog = false
                        selectedItem = null
                    }
                )
            }
        }
        
        // Adjustment dialog
        if (showAdjustDialog && selectedItem != null) {
            val item = selectedItem!!
            val locationId = item.locationId
            
            if (locationId != null) {
                AdjustmentDialog(
                    productName = item.productName,
                    currentWeightKg = item.weightKg,
                    decimalFormat = decimalFormat,
                    onDismiss = {
                        showAdjustDialog = false
                        selectedItem = null
                    },
                    onConfirm = { actualWeight, reason ->
                        viewModel.createAdjustment(
                            locationId = locationId,
                            productId = item.productId,
                            actualWeightKg = actualWeight,
                            currentWeightKg = item.weightKg,
                            reason = reason
                        )
                        showAdjustDialog = false
                        selectedItem = null
                    }
                )
            }
        }
    }
}

@Composable
private fun MoveProductDialog(
    productName: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Перемістити",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Product name card
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.primaryContainer
                    )
                ) {
                    Text(
                        text = productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                        modifier = Modifier.padding(16.dp)
                    )
                }

                Text(
                    text = "Ви хочете перемістити цей товар на іншу точку?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text("Перемістити")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun InventoryItemCard(
    item: InventoryDisplayItem,
    decimalFormat: DecimalFormat,
    showContextMenuOption: Boolean = false,
    onTransferClick: () -> Unit = {},
    onAdjustClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }
    
    val weightText = "${decimalFormat.format(item.weightKg)} кг"
    val isNegative = item.isNegative
    
    val containerColor = when {
        isNegative -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = when {
        isNegative -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .then(
                    if (showContextMenuOption) {
                        Modifier.combinedClickable(
                            onClick = { },
                            onLongClick = { showContextMenu = true }
                        )
                    } else {
                        Modifier
                    }
                ),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor,
                    modifier = Modifier.weight(1f)
                )
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (isNegative) {
                        Icon(
                            imageVector = Icons.Filled.Warning,
                            contentDescription = "Від'ємний залишок",
                            tint = MaterialTheme.colorScheme.error
                        )
                    }
                    Text(
                        text = weightText,
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = if (isNegative) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }
        
        // Context menu for long press
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            // Transfer option - only show if item has positive weight
            if (item.weightKg.compareTo(BigDecimal.ZERO) == 1) {
                DropdownMenuItem(
                    text = { Text("Перемістити") },
                    onClick = {
                        showContextMenu = false
                        onTransferClick()
                    },
                    leadingIcon = {
                        Icon(Icons.Filled.SwapHoriz, contentDescription = "Перемістити")
                    }
                )
            }
            // Adjust option - always available
            DropdownMenuItem(
                text = { Text("Коригувати") },
                onClick = {
                    showContextMenu = false
                    onAdjustClick()
                },
                leadingIcon = {
                    Icon(Icons.Filled.Edit, contentDescription = "Коригувати")
                }
            )
        }
    }
}

/**
 * Adjustment reasons for inventory corrections.
 */
private enum class AdjustmentReason(val displayName: String) {
    DAMAGE("Пошкодження"),
    LOSS("Втрата"),
    COUNTING_ERROR("Помилка підрахунку"),
    THEFT("Крадіжка"),
    OTHER("Інше")
}

@Composable
private fun AdjustmentDialog(
    productName: String,
    currentWeightKg: BigDecimal,
    decimalFormat: DecimalFormat,
    onDismiss: () -> Unit,
    onConfirm: (actualWeight: BigDecimal, reason: String?) -> Unit
) {
    var actualWeightText by remember { mutableStateOf(decimalFormat.format(currentWeightKg)) }
    var selectedReason by remember { mutableStateOf<AdjustmentReason?>(null) }
    var showReasonDropdown by remember { mutableStateOf(false) }
    
    val actualWeight = remember(actualWeightText) {
        try {
            BigDecimal(actualWeightText.replace(",", ".").replace(" ", ""))
        } catch (e: NumberFormatException) {
            null
        }
    }
    
    val difference = actualWeight?.let { it - currentWeightKg }
    val isValid = actualWeight != null && difference != null && difference.compareTo(BigDecimal.ZERO) != 0
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Коригування залишків") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // Product name
                Text(
                    text = productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                
                // Current weight display
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Поточний залишок:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "${decimalFormat.format(currentWeightKg)} кг",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Medium
                    )
                }
                
                // Actual weight input
                OutlinedTextField(
                    value = actualWeightText,
                    onValueChange = { actualWeightText = it },
                    label = { Text("Фактична вага (кг)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = actualWeight == null && actualWeightText.isNotEmpty()
                )
                
                // Difference display
                if (difference != null && difference.compareTo(BigDecimal.ZERO) != 0) {
                    val diffText = if (difference > BigDecimal.ZERO) {
                        "+${decimalFormat.format(difference)}"
                    } else {
                        decimalFormat.format(difference)
                    }
                    val diffColor = if (difference > BigDecimal.ZERO) {
                        MaterialTheme.colorScheme.primary
                    } else {
                        MaterialTheme.colorScheme.error
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Різниця:",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "$diffText кг",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = diffColor
                        )
                    }
                }
                
                // Reason dropdown
                Box {
                    OutlinedTextField(
                        value = selectedReason?.displayName ?: "",
                        onValueChange = {},
                        label = { Text("Причина (необов'язково)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showReasonDropdown = true },
                        readOnly = true,
                        enabled = false,
                        trailingIcon = {
                            Icon(
                                imageVector = Icons.Filled.ArrowDropDown,
                                contentDescription = "Обрати причину"
                            )
                        }
                    )
                    // Clickable overlay for the text field
                    Box(
                        modifier = Modifier
                            .matchParentSize()
                            .clickable { showReasonDropdown = true }
                    )
                    
                    DropdownMenu(
                        expanded = showReasonDropdown,
                        onDismissRequest = { showReasonDropdown = false }
                    ) {
                        AdjustmentReason.entries.forEach { reason ->
                            DropdownMenuItem(
                                text = { Text(reason.displayName) },
                                onClick = {
                                    selectedReason = reason
                                    showReasonDropdown = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    actualWeight?.let { weight ->
                        onConfirm(weight, selectedReason?.displayName)
                    }
                },
                enabled = isValid
            ) {
                Text("Підтвердити")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Скасувати")
            }
        }
    )
}
