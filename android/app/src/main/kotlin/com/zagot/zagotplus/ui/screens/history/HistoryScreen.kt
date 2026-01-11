package com.zagot.zagotplus.ui.screens.history

import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
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
import com.zagot.zagotplus.domain.model.DateRangePreset
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.ui.components.EmptyState
import com.zagot.zagotplus.ui.components.EmptyStateIcons
import java.text.DecimalFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm") }
    val listState = rememberLazyListState()

    // Load more when reaching the end
    val shouldLoadMore by remember {
        derivedStateOf {
            val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val totalItems = listState.layoutInfo.totalItemsCount
            lastVisibleItem >= totalItems - 5 && !uiState.isLoadingMore && uiState.hasMorePages
        }
    }

    LaunchedEffect(shouldLoadMore) {
        if (shouldLoadMore) {
            viewModel.loadMoreTransactions()
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Filter section
        FilterSection(
            searchQuery = uiState.searchQuery,
            onSearchQueryChange = viewModel::setSearchQuery,
            selectedTypes = uiState.selectedTypes,
            onTypeToggle = viewModel::toggleTypeFilter,
            dateRangePreset = uiState.dateRangePreset,
            onDateRangePresetChange = viewModel::setDateRangePreset,
            selectedLocationId = uiState.selectedLocationId,
            locations = uiState.locations,
            onLocationChange = viewModel::setLocationFilter,
            hasActiveFilters = uiState.hasActiveFilters,
            onClearFilters = viewModel::clearFilters
        )

        Divider()

        // Content
        Box(modifier = Modifier.fillMaxSize()) {
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.transactions.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = EmptyStateIcons.History,
                            title = if (uiState.hasActiveFilters) {
                                "Немає результатів"
                            } else {
                                "Немає транзакцій"
                            },
                            description = if (uiState.hasActiveFilters) {
                                "Спробуйте змінити фільтри"
                            } else {
                                "Транзакції з'являться тут після закупівель або продажів"
                            },
                            actionLabel = if (uiState.hasActiveFilters) "Скинути фільтри" else null,
                            onAction = if (uiState.hasActiveFilters) viewModel::clearFilters else null
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        state = listState,
                        contentPadding = PaddingValues(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(uiState.transactions, key = { it.id }) { item ->
                            HistoryItemCard(
                                item = item,
                                decimalFormat = decimalFormat,
                                dateFormatter = dateFormatter
                            )
                        }

                        if (uiState.isLoadingMore) {
                            item {
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(16.dp),
                                    contentAlignment = Alignment.Center
                                ) {
                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterSection(
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    selectedTypes: Set<TransactionType>,
    onTypeToggle: (TransactionType) -> Unit,
    dateRangePreset: DateRangePreset,
    onDateRangePresetChange: (DateRangePreset) -> Unit,
    selectedLocationId: UUID?,
    locations: List<Location>,
    onLocationChange: (UUID?) -> Unit,
    hasActiveFilters: Boolean,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Search field
        OutlinedTextField(
            value = searchQuery,
            onValueChange = onSearchQueryChange,
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Пошук за назвою товару") },
            leadingIcon = {
                Icon(Icons.Filled.Search, contentDescription = null)
            },
            trailingIcon = {
                if (searchQuery.isNotEmpty()) {
                    IconButton(onClick = { onSearchQueryChange("") }) {
                        Icon(Icons.Filled.Clear, contentDescription = "Очистити")
                    }
                }
            },
            singleLine = true
        )

        // Transaction type filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TransactionType.entries.forEach { type ->
                val label = when (type) {
                    TransactionType.PURCHASE -> "Закупка"
                    TransactionType.SALE -> "Продаж"
                    TransactionType.TRANSFER_OUT -> "Вих."
                    TransactionType.TRANSFER_IN -> "Вх."
                }
                FilterChip(
                    selected = type in selectedTypes,
                    onClick = { onTypeToggle(type) },
                    label = { Text(label) }
                )
            }
        }

        // Date range and location row
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Date range dropdown
            DateRangeDropdown(
                selectedPreset = dateRangePreset,
                onPresetChange = onDateRangePresetChange,
                modifier = Modifier.weight(1f)
            )

            // Location dropdown
            LocationDropdown(
                selectedLocationId = selectedLocationId,
                locations = locations,
                onLocationChange = onLocationChange,
                modifier = Modifier.weight(1f)
            )
        }

        // Clear filters button
        if (hasActiveFilters) {
            TextButton(
                onClick = onClearFilters,
                modifier = Modifier.align(Alignment.End)
            ) {
                Icon(
                    imageVector = Icons.Filled.Clear,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = "Скинути фільтри",
                    modifier = Modifier.padding(start = 4.dp)
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeDropdown(
    selectedPreset: DateRangePreset,
    onPresetChange: (DateRangePreset) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val presetLabels = mapOf(
        DateRangePreset.ALL to "Всі дати",
        DateRangePreset.TODAY to "Сьогодні",
        DateRangePreset.THIS_WEEK to "Цей тиждень",
        DateRangePreset.THIS_MONTH to "Цей місяць"
    )

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = presetLabels[selectedPreset] ?: "Всі дати",
            onValueChange = {},
            readOnly = true,
            label = { Text("Період") },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .menuAnchor()
                .fillMaxWidth()
        )

        ExposedDropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            presetLabels.forEach { (preset, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onPresetChange(preset)
                        expanded = false
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LocationDropdown(
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
            value = selectedLocation?.name ?: "Всі локації",
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
            DropdownMenuItem(
                text = { Text("Всі локації") },
                onClick = {
                    onLocationChange(null)
                    expanded = false
                }
            )
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

@Composable
private fun HistoryItemCard(
    item: HistoryDisplayItem,
    decimalFormat: DecimalFormat,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    val typeText = when (item.type) {
        TransactionType.PURCHASE -> "Закупка"
        TransactionType.SALE -> "Продаж"
        TransactionType.TRANSFER_OUT -> "Переміщення (вих.)"
        TransactionType.TRANSFER_IN -> "Переміщення (вх.)"
    }

    val containerColor = when (item.type) {
        TransactionType.PURCHASE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
        TransactionType.SALE -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
        TransactionType.TRANSFER_OUT, TransactionType.TRANSFER_IN -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
    }

    val typeColor = when (item.type) {
        TransactionType.PURCHASE -> MaterialTheme.colorScheme.primary
        TransactionType.SALE -> MaterialTheme.colorScheme.error
        TransactionType.TRANSFER_OUT -> MaterialTheme.colorScheme.tertiary
        TransactionType.TRANSFER_IN -> MaterialTheme.colorScheme.tertiary
    }

    val localTime = item.createdAt.atZone(ZoneId.systemDefault())
    val weightText = "${decimalFormat.format(item.weightKg.abs())} кг"
    val amountText = item.totalAmount?.let { "₴${decimalFormat.format(it)}" }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = item.productName,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = item.locationName,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = weightText,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    if (amountText != null) {
                        Text(
                            text = amountText,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        text = typeText,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Medium,
                        color = typeColor
                    )
                    if (item.isSynced) {
                        Icon(
                            imageVector = Icons.Filled.Check,
                            contentDescription = "Синхронізовано",
                            modifier = Modifier.size(16.dp),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Text(
                    text = dateFormatter.format(localTime),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
