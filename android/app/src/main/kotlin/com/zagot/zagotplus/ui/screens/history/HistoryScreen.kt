package com.zagot.zagotplus.ui.screens.history

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.ui.components.DateRange
import com.zagot.zagotplus.ui.components.DateRangePreset
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.ui.components.EmptyState
import com.zagot.zagotplus.ui.components.EmptyStateIcons
import java.text.DecimalFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateToEditPurchase: (batchId: String) -> Unit = {},
    onNavigateToEditSale: (batchId: String) -> Unit = {}
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // State for void confirmation dialog
    var pendingVoidBatch by remember { mutableStateOf<Pair<UUID, BatchType>?>(null) }

    // Show snackbar on success message
    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSuccess()
        }
    }

    // Show snackbar on error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    // Void confirmation dialog
    pendingVoidBatch?.let { (batchId, batchType) ->
        AlertDialog(
            onDismissRequest = { pendingVoidBatch = null },
            title = { Text("Підтвердіть анулювання") },
            text = { Text("Анульовану партію неможливо відновити. Ви впевнені?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.voidBatch(batchId, batchType)
                        pendingVoidBatch = null
                    }
                ) {
                    Text("Анулювати", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingVoidBatch = null }) {
                    Text("Скасувати")
                }
            }
        )
    }

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
            viewModel.loadMoreBatches()
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
            dateRange = uiState.dateRange,
            onDateRangeChange = viewModel::setDateRange,
            selectedLocationId = uiState.selectedLocationId,
            locations = uiState.locations,
            onLocationChange = viewModel::setLocationFilter,
            hasActiveFilters = uiState.hasActiveFilters,
            onClearFilters = viewModel::clearFilters
        )

        HorizontalDivider()

        // Content with pull-to-refresh
        val swipeRefreshState = rememberSwipeRefreshState(uiState.isLoading)
        
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            when {
                uiState.isLoading && uiState.batches.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                uiState.batches.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = EmptyStateIcons.History,
                            title = if (uiState.hasActiveFilters) {
                                "Немає результатів"
                            } else {
                                "Немає операцій"
                            },
                            description = if (uiState.hasActiveFilters) {
                                "Спробуйте змінити фільтри"
                            } else {
                                "Операції з'являться тут після закупівель або продажів"
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
                        items(uiState.batches, key = { it.id }) { batch ->
                            val isExpanded = batch.id in uiState.expandedBatchIds
                            val transactions = uiState.expandedBatchTransactions[batch.id]
                            val isLoadingTransactions = batch.id in uiState.isLoadingBatchDetails

                            ExpandableBatchCard(
                                batch = batch,
                                isExpanded = isExpanded,
                                transactions = transactions,
                                isLoadingTransactions = isLoadingTransactions,
                                onClick = { viewModel.toggleBatchExpansion(batch.id) },
                                onEditClick = { batchId ->
                                    when (batch) {
                                        is HistoryBatchDisplayItem.RealBatch -> 
                                            onNavigateToEditPurchase(batchId.toString())
                                        is HistoryBatchDisplayItem.RealSaleBatch -> 
                                            onNavigateToEditSale(batchId.toString())
                                        is HistoryBatchDisplayItem.VirtualBatch,
                                        is HistoryBatchDisplayItem.TransferBatch -> 
                                            { /* Virtual/transfer batches cannot be edited */ }
                                    }
                                },
                                onDeleteClick = { batchId ->
                                    when (batch) {
                                        is HistoryBatchDisplayItem.RealBatch -> 
                                            pendingVoidBatch = batchId to BatchType.PURCHASE
                                        is HistoryBatchDisplayItem.RealSaleBatch -> 
                                            pendingVoidBatch = batchId to BatchType.SALE
                                        is HistoryBatchDisplayItem.VirtualBatch,
                                        is HistoryBatchDisplayItem.TransferBatch -> 
                                            { /* Virtual/transfer batches cannot be deleted */ }
                                    }
                                },
                                decimalFormat = decimalFormat,
                                dateFormatter = dateFormatter,
                                modifier = Modifier.animateItemPlacement()
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
    selectedTypes: Set<BatchType>,
    onTypeToggle: (BatchType) -> Unit,
    dateRange: DateRange?,
    onDateRangeChange: (DateRange?) -> Unit,
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
                Icon(Icons.Filled.Search, contentDescription = "Пошук")
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

        // Batch type filter chips
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BatchType.entries.forEach { type ->
                FilterChip(
                    selected = type in selectedTypes,
                    onClick = { onTypeToggle(type) },
                    label = { Text(type.toDisplayString()) }
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
                dateRange = dateRange,
                onDateRangeChange = onDateRangeChange,
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
                    contentDescription = "Очистити фільтри",
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
    dateRange: DateRange?,
    onDateRangeChange: (DateRange?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableStateOf(java.time.LocalDate.now()) }

    // Map of preset to label
    val presetOptions = listOf(
        null to "Весь час",
        DateRangePreset.TODAY to "Сьогодні",
        DateRangePreset.YESTERDAY to "Вчора",
        DateRangePreset.LAST_7_DAYS to "7 днів",
        DateRangePreset.LAST_30_DAYS to "30 днів",
        DateRangePreset.THIS_MONTH to "Цей місяць",
        DateRangePreset.LAST_MONTH to "Минулий місяць"
    )

    // Get display text for current selection
    val displayText = when {
        dateRange == null -> "Весь час"
        dateRange.preset == DateRangePreset.CUSTOM -> {
            val formatter = java.time.format.DateTimeFormatter.ofPattern("dd.MM")
            if (dateRange.startDate == dateRange.endDate) {
                dateRange.startDate.format(formatter)
            } else {
                "${dateRange.startDate.format(formatter)} - ${dateRange.endDate.format(formatter)}"
            }
        }
        else -> presetOptions.find { it.first == dateRange.preset }?.second ?: dateRange.preset.label
    }

    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        OutlinedTextField(
            value = displayText,
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
            presetOptions.forEach { (preset, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        if (preset == null) {
                            onDateRangeChange(null)
                        } else {
                            onDateRangeChange(DateRange.fromPreset(preset))
                        }
                        expanded = false
                    }
                )
            }
            // Custom date range option
            DropdownMenuItem(
                text = { Text("Вибрати період...") },
                onClick = {
                    expanded = false
                    customStartDate = dateRange?.startDate ?: java.time.LocalDate.now()
                    showStartDatePicker = true
                }
            )
        }
    }

    // Start date picker dialog
    if (showStartDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = customStartDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )

        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        customStartDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        showStartDatePicker = false
                        showEndDatePicker = true
                    }
                }) {
                    Text("Далі")
                }
            },
            dismissButton = {
                TextButton(onClick = { showStartDatePicker = false }) {
                    Text("Скасувати")
                }
            }
        ) {
            androidx.compose.material3.DatePicker(
                state = datePickerState,
                title = { Text("Початкова дата", modifier = Modifier.padding(16.dp)) }
            )
        }
    }

    // End date picker dialog
    if (showEndDatePicker) {
        val datePickerState = androidx.compose.material3.rememberDatePickerState(
            initialSelectedDateMillis = (dateRange?.endDate ?: java.time.LocalDate.now())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )

        androidx.compose.material3.DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val endDate = java.time.Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        onDateRangeChange(
                            DateRange(
                                startDate = customStartDate,
                                endDate = if (endDate.isBefore(customStartDate)) customStartDate else endDate,
                                preset = DateRangePreset.CUSTOM
                            )
                        )
                        showEndDatePicker = false
                    }
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showEndDatePicker = false }) {
                    Text("Скасувати")
                }
            }
        ) {
            androidx.compose.material3.DatePicker(
                state = datePickerState,
                title = { Text("Кінцева дата", modifier = Modifier.padding(16.dp)) }
            )
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ExpandableBatchCard(
    batch: HistoryBatchDisplayItem,
    isExpanded: Boolean,
    transactions: List<HistoryDisplayItem>?,
    isLoadingTransactions: Boolean,
    onClick: () -> Unit,
    onEditClick: (batchId: java.util.UUID) -> Unit,
    onDeleteClick: (batchId: java.util.UUID) -> Unit,
    decimalFormat: DecimalFormat,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }
    
    // Determine card color based on batch state
    val containerColor = when {
        batch.isVoided -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        batch.isCorrection -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        else -> when (batch.batchType) {
            BatchType.PURCHASE -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.3f)
            BatchType.SALE -> MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.3f)
            BatchType.TRANSFER -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
            BatchType.ADJUSTMENT -> MaterialTheme.colorScheme.tertiaryContainer.copy(alpha = 0.3f)
        }
    }

    val typeColor = when (batch.batchType) {
        BatchType.PURCHASE -> MaterialTheme.colorScheme.primary
        BatchType.SALE -> MaterialTheme.colorScheme.error
        BatchType.TRANSFER -> MaterialTheme.colorScheme.tertiary
        BatchType.ADJUSTMENT -> MaterialTheme.colorScheme.tertiary
    }

    val typeIcon: ImageVector = when (batch.batchType) {
        BatchType.PURCHASE -> Icons.Filled.ShoppingCart
        BatchType.SALE -> Icons.Filled.ShoppingCart
        BatchType.TRANSFER -> Icons.Filled.SwapHoriz
        BatchType.ADJUSTMENT -> Icons.Filled.Edit
    }

    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "expand_rotation"
    )

    val localTime = batch.createdAt.atZone(ZoneId.systemDefault())
    val weightText = if (batch.batchType == BatchType.ADJUSTMENT) {
        val sign = if (batch.totalWeightKg >= java.math.BigDecimal.ZERO) "+" else ""
        "$sign${decimalFormat.format(batch.totalWeightKg)} кг"
    } else {
        "${decimalFormat.format(batch.totalWeightKg.abs())} кг"
    }
    val amountText = batch.totalAmount?.let { "₴${decimalFormat.format(it)}" }
    
    val canEdit = !batch.isVoided && 
                  batch !is HistoryBatchDisplayItem.VirtualBatch && 
                  batch !is HistoryBatchDisplayItem.TransferBatch
    
    Box {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (canEdit) showContextMenu = true }
                ),
            colors = CardDefaults.cardColors(containerColor = containerColor)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                // Batch header (always visible)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Type icon + info
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            imageVector = typeIcon,
                            contentDescription = "Тип операції",
                            tint = typeColor,
                            modifier = Modifier.size(24.dp)
                        )
                        Column {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = batch.batchType.toDisplayString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Medium,
                                    color = typeColor
                                )
                                // Show voided badge
                                if (batch.isVoided) {
                                    Text(
                                        text = "Скасовано",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                // Show correction badge
                                if (batch.isCorrection) {
                                    Text(
                                        text = "Виправлення",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                if (batch.isSynced) {
                                    Icon(
                                        imageVector = Icons.Filled.Check,
                                        contentDescription = "Синхронізовано",
                                        modifier = Modifier.size(16.dp),
                                        tint = MaterialTheme.colorScheme.primary
                                    )
                                }
                            }
                            // Show correction reason if present
                            batch.correctionReason?.let { reason ->
                                Text(
                                    text = reason,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Text(
                                text = "${batch.itemCount} позицій • ${batch.locationName}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = dateFormatter.format(localTime),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    // Right: Totals + expand indicator
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
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
                        Icon(
                            imageVector = Icons.Filled.KeyboardArrowDown,
                            contentDescription = if (isExpanded) "Згорнути" else "Розгорнути",
                            modifier = Modifier
                                .size(24.dp)
                                .rotate(rotationAngle),
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                // Expanded content
                if (isExpanded) {
                    HorizontalDivider(modifier = Modifier.padding(horizontal = 16.dp))

                    when {
                        isLoadingTransactions -> {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(modifier = Modifier.size(24.dp))
                            }
                        }
                        transactions != null -> {
                            Column(
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                transactions.forEach { transaction ->
                                    TransactionRow(
                                        transaction = transaction,
                                        decimalFormat = decimalFormat
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
        
        // Context menu for long press
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text("Редагувати") },
                onClick = {
                    showContextMenu = false
                    val batchId = when (batch) {
                        is HistoryBatchDisplayItem.RealBatch -> batch.batchId
                        is HistoryBatchDisplayItem.RealSaleBatch -> batch.batchId
                        is HistoryBatchDisplayItem.VirtualBatch,
                        is HistoryBatchDisplayItem.TransferBatch -> null
                    }
                    batchId?.let { onEditClick(it) }
                },
                leadingIcon = {
                    Icon(Icons.Filled.Edit, contentDescription = "Редагувати")
                }
            )
            DropdownMenuItem(
                text = { Text("Видалити", color = MaterialTheme.colorScheme.error) },
                onClick = {
                    showContextMenu = false
                    val batchId = when (batch) {
                        is HistoryBatchDisplayItem.RealBatch -> batch.batchId
                        is HistoryBatchDisplayItem.RealSaleBatch -> batch.batchId
                        is HistoryBatchDisplayItem.VirtualBatch,
                        is HistoryBatchDisplayItem.TransferBatch -> null
                    }
                    batchId?.let { onDeleteClick(it) }
                },
                leadingIcon = {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = "Видалити",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            )
        }
    }
}

@Composable
private fun TransactionRow(
    transaction: HistoryDisplayItem,
    decimalFormat: DecimalFormat,
    modifier: Modifier = Modifier
) {
    val pricePerKg = transaction.totalAmount?.let { amount ->
        val weight = transaction.weightKg.abs()
        if (weight.compareTo(java.math.BigDecimal.ZERO) > 0) {
            amount.abs().divide(weight, 2, java.math.RoundingMode.HALF_UP)
        } else null
    }
    
    val isTransfer = transaction.type == TransactionType.TRANSFER_IN || 
                     transaction.type == TransactionType.TRANSFER_OUT
    val isAdjustment = transaction.type == TransactionType.ADJUSTMENT
    
    // For transfers and adjustments, show signed weight
    // For transfer items in the dropdown, we only show TRANSFER_IN (positive weight arriving)
    val weightText = when {
        isTransfer -> {
            // Always show positive weight for transfers in dropdown (we only show TRANSFER_IN)
            "${decimalFormat.format(transaction.weightKg.abs())} кг"
        }
        isAdjustment -> {
            val sign = if (transaction.weightKg >= java.math.BigDecimal.ZERO) "+" else ""
            "$sign${decimalFormat.format(transaction.weightKg)} кг"
        }
        else -> "${decimalFormat.format(transaction.weightKg.abs())} кг"
    }
    
    // For transfers, show "from → to" direction
    // TRANSFER_IN: locationName = destination, transferLocationName = source
    val locationText = when {
        isTransfer && transaction.transferLocationName != null -> {
            "${transaction.transferLocationName} → ${transaction.locationName}"
        }
        else -> null
    }
    
    // No special color for transfer weight in dropdown (neutral display)
    val weightColor = MaterialTheme.colorScheme.onSurface

    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = transaction.productName,
                style = MaterialTheme.typography.bodyMedium
            )
            if (locationText != null) {
                Text(
                    text = locationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                pricePerKg?.let { price ->
                    Text(
                        text = "₴${decimalFormat.format(price)}/кг",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = weightText,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = weightColor
            )
            transaction.totalAmount?.let { amount ->
                Text(
                    text = "₴${decimalFormat.format(amount.abs())}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
