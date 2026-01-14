package com.zagot.zagotplus.ui.screens.inventory

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
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

@Composable
fun InventoryScreen(
    modifier: Modifier = Modifier,
    onNavigateToTransfer: (productId: String, destinationLocationId: String) -> Unit = { _, _ -> },
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val displayItems by viewModel.displayItems.collectAsStateWithLifecycle()
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    
    // State for move dialog
    var showMoveDialog by remember { mutableStateOf(false) }
    var selectedItemForMove by remember { mutableStateOf<InventoryDisplayItem?>(null) }

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
                                        showMoveOption = !isTotalView && item.weightKg > BigDecimal.ZERO,
                                        onLongPress = {
                                            if (!isTotalView && item.weightKg > BigDecimal.ZERO) {
                                                selectedItemForMove = item
                                                showMoveDialog = true
                                            }
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
        if (showMoveDialog && selectedItemForMove != null) {
            val item = selectedItemForMove!!
            val currentLocationId = item.locationId
            val otherLocations = uiState.locations.filter { it.id != currentLocationId }
            
            MoveProductDialog(
                productName = item.productName,
                availableLocations = otherLocations,
                onDismiss = { 
                    showMoveDialog = false
                    selectedItemForMove = null
                },
                onLocationSelected = { destinationLocation ->
                    onNavigateToTransfer(
                        item.productId.toString(),
                        destinationLocation.id.toString()
                    )
                    showMoveDialog = false
                    selectedItemForMove = null
                }
            )
        }
    }
}

@Composable
private fun MoveProductDialog(
    productName: String,
    availableLocations: List<Location>,
    onDismiss: () -> Unit,
    onLocationSelected: (Location) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Перемістити товар") },
        text = {
            Column {
                Text(
                    text = productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Оберіть куди перемістити:",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 8.dp, bottom = 16.dp)
                )
                availableLocations.forEach { location ->
                    TextButton(
                        onClick = { onLocationSelected(location) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = location.name,
                            style = MaterialTheme.typography.bodyLarge
                        )
                    }
                }
            }
        },
        confirmButton = {},
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
    showMoveOption: Boolean = false,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val weightText = "${decimalFormat.format(item.weightKg)} кг"
    val isNegative = item.isNegative
    val isZero = item.weightKg == BigDecimal.ZERO
    
    val containerColor = when {
        isNegative -> MaterialTheme.colorScheme.errorContainer
        isZero -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        else -> MaterialTheme.colorScheme.surfaceVariant
    }
    
    val contentColor = when {
        isNegative -> MaterialTheme.colorScheme.onErrorContainer
        isZero -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(
                if (showMoveOption) {
                    Modifier.combinedClickable(
                        onClick = { },
                        onLongClick = onLongPress
                    )
                } else {
                    Modifier
                }
            ),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column {
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
            if (showMoveOption) {
                Text(
                    text = "Утримуйте для переміщення",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f),
                    modifier = Modifier.padding(start = 16.dp, bottom = 8.dp)
                )
            }
        }
    }
}
