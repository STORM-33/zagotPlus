package com.zagot.zagotplus.ui.screens.inventory

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zagot.zagotplus.ui.components.SyncStatusIcon
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun InventoryScreen(
    modifier: Modifier = Modifier,
    viewModel: InventoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Залишки") },
                actions = {
                    // Refresh button
                    IconButton(
                        onClick = { viewModel.refresh() },
                        enabled = !uiState.isRefreshing
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Оновити"
                        )
                    }
                    // Sync status
                    SyncStatusIcon(
                        syncStatusFlow = viewModel.syncStatus,
                        onSyncClick = { viewModel.triggerSync() }
                    )
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
        ) {
            // Location tabs
            if (uiState.locations.isNotEmpty()) {
                val selectedIndex = uiState.locations.indexOfFirst { 
                    it.id == uiState.selectedLocation?.id 
                }.coerceAtLeast(0)
                
                TabRow(selectedTabIndex = selectedIndex) {
                    uiState.locations.forEachIndexed { index, location ->
                        Tab(
                            selected = index == selectedIndex,
                            onClick = { viewModel.selectLocation(location) },
                            text = { Text(location.name) }
                        )
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
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                    else -> {
                        val displayItems = viewModel.getDisplayItems()
                        
                        if (displayItems.isEmpty()) {
                            Box(
                                modifier = Modifier.fillMaxSize(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Немає товарів",
                                    style = MaterialTheme.typography.bodyLarge,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.fillMaxSize()
                            ) {
                                items(displayItems, key = { it.productId }) { item ->
                                    InventoryItemRow(
                                        item = item,
                                        decimalFormat = decimalFormat
                                    )
                                    Divider()
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
}

@Composable
private fun InventoryItemRow(
    item: InventoryDisplayItem,
    decimalFormat: DecimalFormat,
    modifier: Modifier = Modifier
) {
    val weightText = "${decimalFormat.format(item.weightKg)} кг"
    val textColor = when {
        item.isNegative -> MaterialTheme.colorScheme.error
        item.weightKg == BigDecimal.ZERO -> MaterialTheme.colorScheme.onSurfaceVariant
        else -> MaterialTheme.colorScheme.onSurface
    }

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = item.productName,
            style = MaterialTheme.typography.bodyLarge,
            color = textColor,
            modifier = Modifier.weight(1f)
        )
        
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (item.isNegative) {
                Icon(
                    imageVector = Icons.Filled.Warning,
                    contentDescription = "Від'ємний залишок",
                    tint = MaterialTheme.colorScheme.error
                )
            }
            Text(
                text = weightText,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                color = textColor
            )
        }
    }
}
