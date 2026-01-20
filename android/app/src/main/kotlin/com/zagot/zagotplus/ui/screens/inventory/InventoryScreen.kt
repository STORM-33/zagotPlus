package com.zagot.zagotplus.ui.screens.inventory

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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.ui.components.EmptyState
import com.zagot.zagotplus.ui.components.EmptyStateIcons
import com.zagot.zagotplus.ui.components.InventoryItemSkeleton
import com.zagot.zagotplus.ui.components.SkeletonList
import com.zagot.zagotplus.ui.components.adaptiveHorizontalPadding
import com.zagot.zagotplus.ui.components.adaptiveItemSpacing
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
    isRestrictedMode: Boolean = false,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayItems by viewModel.displayItems.collectAsStateWithLifecycle()
    val currencyFormat = remember { DecimalFormat("#,##0") }
    val weightFormat = remember { DecimalFormat("#,##0.0") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    
    // Force current location when restricted mode is enabled
    LaunchedEffect(isRestrictedMode) {
        viewModel.setRestrictedMode(isRestrictedMode)
    }
    
    // State for product selection (for summary calculation)
    var selectedProductIds by remember { mutableStateOf<Set<UUID>>(emptySet()) }
    
    // Calculate summary based on selection
    val summary = remember(displayItems, selectedProductIds) {
        val itemsForSummary = if (selectedProductIds.isEmpty()) {
            displayItems
        } else {
            displayItems.filter { it.productId in selectedProductIds }
        }
        
        val totalWeight = itemsForSummary.sumOf { it.weightKg }
        val totalProfit = itemsForSummary.mapNotNull { it.projectedProfit }.sumOf { it }
        val totalInvested = itemsForSummary.sumOf { item ->
            val price = item.salePrice ?: java.math.BigDecimal.ZERO
            item.weightKg.multiply(price)
        }.setScale(2, java.math.RoundingMode.HALF_UP)
        
        InventorySummary(
            totalWeight = totalWeight,
            totalExpectedProfit = totalProfit,
            totalInvested = totalInvested
        )
    }
    
    // State for dialogs
    var selectedItem by remember { mutableStateOf<InventoryDisplayItem?>(null) }
    var showMoveDialog by remember { mutableStateOf(false) }
    var showAdjustDialog by remember { mutableStateOf(false) }
    
    val swipeRefreshState = rememberSwipeRefreshState(uiState.isLoading || uiState.isRefreshing)

    Box(modifier = modifier.fillMaxSize()) {
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier.fillMaxSize()
            ) {
            // Location tabs + Total tab (restricted mode: only current location, no Total)
            if (uiState.locations.isNotEmpty()) {
                val isTotalView = uiState.viewMode == InventoryViewMode.TOTAL && !isRestrictedMode
                
                // In restricted mode, filter to only show current location
                val displayLocations = if (isRestrictedMode) {
                    uiState.selectedLocation?.let { listOf(it) } ?: emptyList()
                } else {
                    uiState.locations
                }
                
                val selectedIndex = if (isTotalView) {
                    displayLocations.size // Total tab is last
                } else {
                    displayLocations.indexOfFirst { 
                        it.id == uiState.selectedLocation?.id 
                    }.coerceAtLeast(0)
                }
                
                if (displayLocations.isNotEmpty()) {
                    TabRow(selectedTabIndex = selectedIndex) {
                        displayLocations.forEachIndexed { index, location ->
                            Tab(
                                selected = index == selectedIndex,
                                onClick = { viewModel.selectLocation(location) },
                                text = { Text(location.name) }
                            )
                        }
                        // Total tab - hide in restricted mode
                        if (!isRestrictedMode) {
                            Tab(
                                selected = isTotalView,
                                onClick = { viewModel.selectTotalView() },
                                text = { Text("Всього") }
                            )
                        }
                    }
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
                        SkeletonList(itemCount = 6) { InventoryItemSkeleton() }
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
                            val horizontalPadding = adaptiveHorizontalPadding()
                            val itemSpacing = adaptiveItemSpacing()
                            
                            LazyColumn(
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
                                verticalArrangement = Arrangement.spacedBy(itemSpacing)
                            ) {
                                // Inventory items as cards
                                items(displayItems, key = { it.productId }) { item ->
                                    val isSelected = item.productId in selectedProductIds
                                    InventoryItemCard(
                                        item = item,
                                        currencyFormat = currencyFormat,
                                        weightFormat = weightFormat,
                                        isSelected = isSelected,
                                        showContextMenuOption = !isTotalView,
                                        isRestrictedMode = isRestrictedMode,
                                        onClick = {
                                            selectedProductIds = if (isSelected) {
                                                selectedProductIds - item.productId
                                            } else {
                                                selectedProductIds + item.productId
                                            }
                                        },
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
                                
                                // Summary panel at the end of the list
                                item(key = "summary") {
                                    InventorySummaryPanel(
                                        summary = summary,
                                        currencyFormat = currencyFormat,
                                        weightFormat = weightFormat,
                                        hasSelection = selectedProductIds.isNotEmpty(),
                                        isRestrictedMode = isRestrictedMode
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
                    weightFormat = weightFormat,
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
    currencyFormat: DecimalFormat,
    weightFormat: DecimalFormat,
    isSelected: Boolean = false,
    showContextMenuOption: Boolean = false,
    isRestrictedMode: Boolean = false,
    onClick: () -> Unit = {},
    onTransferClick: () -> Unit = {},
    onAdjustClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }
    
    val weightText = "${weightFormat.format(item.weightKg)} кг"
    val isNegative = item.isNegative
    
    val containerColor = when {
        isSelected -> MaterialTheme.colorScheme.primaryContainer
        isNegative -> MaterialTheme.colorScheme.errorContainer
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = when {
        isSelected -> MaterialTheme.colorScheme.onPrimaryContainer
        isNegative -> MaterialTheme.colorScheme.onErrorContainer
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Box {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = if (showContextMenuOption) {
                        { showContextMenu = true }
                    } else null
                ),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = if (isSelected) {
                BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
            } else null
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Product image thumbnail
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(MaterialTheme.colorScheme.surface),
                        contentAlignment = Alignment.Center
                    ) {
                        if (item.productImageUri != null) {
                            AsyncImage(
                                model = item.productImageUri,
                                contentDescription = item.productName,
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
                
                // Projected profit display - hide in restricted mode
                if (!isRestrictedMode) {
                    item.projectedProfit?.let { profit ->
                        val profitColor = if (profit >= BigDecimal.ZERO) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.error
                        }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = "Очікуваний прибуток:",
                                style = MaterialTheme.typography.bodySmall,
                                color = contentColor.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "₴${currencyFormat.format(profit)}",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = profitColor
                            )
                        }
                    }
                }
            }
        }
        
        // Context menu for long press - hide in restricted mode
        if (!isRestrictedMode) {
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
    weightFormat: DecimalFormat,
    onDismiss: () -> Unit,
    onConfirm: (actualWeight: BigDecimal, reason: String?) -> Unit
) {
    var actualWeightText by remember { mutableStateOf(weightFormat.format(currentWeightKg)) }
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
                        text = "${weightFormat.format(currentWeightKg)} кг",
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
                        "+${weightFormat.format(difference)}"
                    } else {
                        weightFormat.format(difference)
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

/**
 * Summary panel showing inventory totals.
 */
@Composable
private fun InventorySummaryPanel(
    summary: InventorySummary,
    currencyFormat: DecimalFormat,
    weightFormat: DecimalFormat,
    hasSelection: Boolean = false,
    isRestrictedMode: Boolean = false,
    modifier: Modifier = Modifier
) {
    val profitColor = if (summary.totalExpectedProfit >= java.math.BigDecimal.ZERO) {
        MaterialTheme.colorScheme.tertiary
    } else {
        MaterialTheme.colorScheme.error
    }
    
    val title = if (hasSelection) "Підсумок (вибрані)" else "Підсумок"
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
            
            HorizontalDivider(
                color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
            )
            
            // Total weight - always show
            Column(
                verticalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Text(
                    text = "Загальна вага",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                )
                Text(
                    text = "${weightFormat.format(summary.totalWeight)} кг",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
            
            // Product value - hide in restricted mode
            if (!isRestrictedMode) {
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Вартість товару",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "₴${currencyFormat.format(summary.totalInvested)}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                
                HorizontalDivider(
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.2f)
                )
                
                // Expected profit - hide in restricted mode
                Column(
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(
                        text = "Очікуваний прибуток",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.7f)
                    )
                    Text(
                        text = "₴${currencyFormat.format(summary.totalExpectedProfit)}",
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = profitColor
                    )
                }
            }
        }
    }
}
