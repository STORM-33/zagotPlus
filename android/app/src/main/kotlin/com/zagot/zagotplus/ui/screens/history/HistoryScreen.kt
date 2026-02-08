package com.zagot.zagotplus.ui.screens.history

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.verticalScroll
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
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.material3.Surface
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.ui.components.BatchCardSkeleton
import com.zagot.zagotplus.ui.components.DateRange
import com.zagot.zagotplus.ui.components.DateRangePreset
import com.zagot.zagotplus.domain.model.TransactionType
import com.zagot.zagotplus.ui.components.EmptyState
import com.zagot.zagotplus.ui.components.EmptyStateIcons
import com.zagot.zagotplus.ui.components.SkeletonList
import com.zagot.zagotplus.ui.components.adaptiveHorizontalPadding
import com.zagot.zagotplus.ui.components.adaptiveItemSpacing
import com.zagot.zagotplus.ui.components.isTablet
import java.text.DecimalFormat
import java.time.LocalDate
import com.zagot.zagotplus.ui.components.AnimatedCounter
import com.zagot.zagotplus.ui.components.AnimatedListItem
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun HistoryScreen(
    modifier: Modifier = Modifier,
    viewModel: HistoryViewModel = hiltViewModel(),
    onNavigateToEditPurchase: (batchId: String) -> Unit = {},
    onNavigateToEditSale: (batchId: String) -> Unit = {},
    isRestrictedMode: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // Separate state flows for expansion - only recomposes affected items
    val expandedBatchIds by viewModel.expandedBatchIds.collectAsStateWithLifecycle()
    val expandedBatchTransactions by viewModel.expandedBatchTransactions.collectAsStateWithLifecycle()
    val isLoadingBatchDetails by viewModel.isLoadingBatchDetails.collectAsStateWithLifecycle()
    
    val currencyFormat = remember { DecimalFormat("#,##0") }
    val weightFormat = remember { DecimalFormat("#,##0.0") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm") }
    val listState = rememberLazyListState()
    val snackbarHostState = remember { SnackbarHostState() }
    
    // In restricted mode, lock location filter to current location
    LaunchedEffect(isRestrictedMode) {
        if (isRestrictedMode) {
            viewModel.setRestrictedMode(true)
        }
    }
    
    // State for void confirmation dialog
    var pendingVoidBatch by remember { mutableStateOf<Pair<UUID, BatchType>?>(null) }
    
    // State for filter bottom sheet
    var showFilterSheet by remember { mutableStateOf(false) }
    val sheetState = rememberModalBottomSheetState()
    
    // Calculate active filter count (excluding search which is always visible)
    // In restricted mode, don't count location filter since it's forced
    val activeFilterCount = remember(uiState.selectedTypes, uiState.dateRange, uiState.selectedLocationId, isRestrictedMode) {
        var count = 0
        if (uiState.selectedTypes.size != BatchType.entries.size) count++ // Type filter active
        if (uiState.dateRange != null) count++ // Date filter active
        if (uiState.selectedLocationId != null && !isRestrictedMode) count++ // Location filter active (not in restricted)
        count
    }

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

    val isTabletLayout = isTablet()
    val horizontalPadding = adaptiveHorizontalPadding()

    // Filter bottom sheet (phone only)
    if (showFilterSheet && !isTabletLayout) {
        ModalBottomSheet(
            onDismissRequest = { showFilterSheet = false },
            sheetState = sheetState
        ) {
            FilterContent(
                uiState = uiState,
                isRestrictedMode = isRestrictedMode,
                onToggleTypeFilter = viewModel::toggleTypeFilter,
                onDateRangeChange = viewModel::setDateRange,
                onLocationChange = viewModel::setLocationFilter,
                onToggleShowDeleted = viewModel::toggleShowDeleted,
                onClearFilters = viewModel::clearFilters,
                onApply = { showFilterSheet = false }
            )
        }
    }

    if (isTabletLayout) {
        // Tablet: Side-by-side layout with inline filters
        Row(
            modifier = modifier
                .fillMaxSize()
                .padding(horizontal = horizontalPadding, vertical = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Left panel: Filters
            Card(
                modifier = Modifier
                    .width(280.dp)
                    .fillMaxHeight(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "Фільтри",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )

                    // Batch type filter chips
                    Text("Тип операції", style = MaterialTheme.typography.labelMedium)
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        BatchType.entries.forEach { type ->
                            FilterChip(
                                selected = type in uiState.selectedTypes,
                                onClick = { viewModel.toggleTypeFilter(type) },
                                label = { Text(type.toDisplayString()) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    HorizontalDivider()

                    // Date range dropdown
                    Text("Період", style = MaterialTheme.typography.labelMedium)
                    DateRangeDropdown(
                        dateRange = uiState.dateRange,
                        onDateRangeChange = viewModel::setDateRange,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Location dropdown - hide in restricted mode
                    if (!isRestrictedMode) {
                        Text("Локація", style = MaterialTheme.typography.labelMedium)
                        LocationDropdown(
                            selectedLocationId = uiState.selectedLocationId,
                            locations = uiState.locations,
                            onLocationChange = viewModel::setLocationFilter,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    HorizontalDivider()

                    // Show deleted toggle
                    FilterChip(
                        selected = uiState.showDeleted,
                        onClick = { viewModel.toggleShowDeleted() },
                        label = { Text("Показати видалені") },
                        leadingIcon = if (uiState.showDeleted) {
                            { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Clear filters button
                    if (uiState.hasActiveFilters) {
                        TextButton(
                            onClick = { viewModel.clearFilters() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.Clear, null, modifier = Modifier.size(18.dp))
                            Text("Скинути фільтри")
                        }
                    }
                }
            }

            // Right panel: Content
            HistoryContent(
                uiState = uiState,
                expandedBatchIds = expandedBatchIds,
                expandedBatchTransactions = expandedBatchTransactions,
                isLoadingBatchDetails = isLoadingBatchDetails,
                listState = listState,
                horizontalPadding = 0.dp,
                currencyFormat = currencyFormat,
                weightFormat = weightFormat,
                dateFormatter = dateFormatter,
                onRefresh = viewModel::refresh,
                onToggleBatchExpansion = viewModel::toggleBatchExpansion,
                onNavigateToEditPurchase = onNavigateToEditPurchase,
                onNavigateToEditSale = onNavigateToEditSale,
                onVoidBatch = { batchId, batchType -> pendingVoidBatch = batchId to batchType },
                onClearFilters = viewModel::clearFilters,
                modifier = Modifier.weight(1f)
            )
        }
    } else {
        // Phone: Stacked layout with filter button
        Column(
            modifier = modifier.fillMaxSize()
        ) {
            // Filter chip row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalArrangement = Arrangement.End,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = activeFilterCount > 0,
                    onClick = { showFilterSheet = true },
                    label = { Text("Фільтри${if (activeFilterCount > 0) " ($activeFilterCount)" else ""}") },
                    leadingIcon = { Icon(Icons.Default.FilterList, null) }
                )
            }

            HorizontalDivider()

            // Content with pull-to-refresh
            HistoryContent(
                uiState = uiState,
                expandedBatchIds = expandedBatchIds,
                expandedBatchTransactions = expandedBatchTransactions,
                isLoadingBatchDetails = isLoadingBatchDetails,
                listState = listState,
                horizontalPadding = 16.dp,
                currencyFormat = currencyFormat,
                weightFormat = weightFormat,
                dateFormatter = dateFormatter,
                onRefresh = viewModel::refresh,
                onToggleBatchExpansion = viewModel::toggleBatchExpansion,
                onNavigateToEditPurchase = onNavigateToEditPurchase,
                onNavigateToEditSale = onNavigateToEditSale,
                onVoidBatch = { batchId, batchType -> pendingVoidBatch = batchId to batchType },
                onClearFilters = viewModel::clearFilters,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun FilterContent(
    uiState: HistoryUiState,
    isRestrictedMode: Boolean,
    onToggleTypeFilter: (BatchType) -> Unit,
    onDateRangeChange: (DateRange?) -> Unit,
    onLocationChange: (UUID?) -> Unit,
    onToggleShowDeleted: () -> Unit,
    onClearFilters: () -> Unit,
    onApply: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Фільтри", style = MaterialTheme.typography.titleMedium)

        // Batch type filter chips
        Text("Тип операції", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            BatchType.entries.forEach { type ->
                FilterChip(
                    selected = type in uiState.selectedTypes,
                    onClick = { onToggleTypeFilter(type) },
                    label = { Text(type.toDisplayString()) }
                )
            }
        }

        // Date range dropdown
        Text("Період", style = MaterialTheme.typography.labelMedium)
        DateRangeDropdown(
            dateRange = uiState.dateRange,
            onDateRangeChange = onDateRangeChange,
            modifier = Modifier.fillMaxWidth()
        )

        // Location dropdown - hide in restricted mode
        if (!isRestrictedMode) {
            Text("Локація", style = MaterialTheme.typography.labelMedium)
            LocationDropdown(
                selectedLocationId = uiState.selectedLocationId,
                locations = uiState.locations,
                onLocationChange = onLocationChange,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // Show deleted toggle
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            FilterChip(
                selected = uiState.showDeleted,
                onClick = onToggleShowDeleted,
                label = { Text("Показати видалені") },
                leadingIcon = if (uiState.showDeleted) {
                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(16.dp)) }
                } else null
            )
        }

        // Action buttons
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (uiState.hasActiveFilters) {
                TextButton(
                    onClick = onClearFilters,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Скинути")
                }
            }
            Button(
                onClick = onApply,
                modifier = Modifier.weight(1f)
            ) {
                Text("Застосувати")
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryContent(
    uiState: HistoryUiState,
    expandedBatchIds: Set<String>,
    expandedBatchTransactions: Map<String, List<HistoryDisplayItem>>,
    isLoadingBatchDetails: Set<String>,
    listState: androidx.compose.foundation.lazy.LazyListState,
    horizontalPadding: androidx.compose.ui.unit.Dp,
    currencyFormat: DecimalFormat,
    weightFormat: DecimalFormat,
    dateFormatter: DateTimeFormatter,
    onRefresh: () -> Unit,
    onToggleBatchExpansion: (String) -> Unit,
    onNavigateToEditPurchase: (String) -> Unit,
    onNavigateToEditSale: (String) -> Unit,
    onVoidBatch: (UUID, BatchType) -> Unit,
    onClearFilters: () -> Unit,
    modifier: Modifier = Modifier
) {
    val swipeRefreshState = rememberSwipeRefreshState(uiState.isLoading)

    SwipeRefresh(
        state = swipeRefreshState,
        onRefresh = onRefresh,
        modifier = modifier.fillMaxSize()
    ) {
        when {
            uiState.isLoading && uiState.batches.isEmpty() -> {
                SkeletonList(itemCount = 5) { BatchCardSkeleton() }
            }
            uiState.batches.isEmpty() -> {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = EmptyStateIcons.History,
                        title = if (uiState.hasActiveFilters) "Немає результатів" else "Немає операцій",
                        description = if (uiState.hasActiveFilters) "Спробуйте змінити фільтри" else "Операції з'являться тут після закупівель або продажів",
                        actionLabel = if (uiState.hasActiveFilters) "Скинути фільтри" else null,
                        onAction = if (uiState.hasActiveFilters) onClearFilters else null
                    )
                }
            }
            else -> {
                val dateDividerFormatter = remember { DateTimeFormatter.ofPattern("dd.MM") }
                val batchesByDate = remember(uiState.batches) {
                    uiState.batches.groupBy { batch ->
                        batch.createdAt.atZone(ZoneId.systemDefault()).toLocalDate()
                    }.toSortedMap(compareByDescending { it })
                }
                val itemSpacing = adaptiveItemSpacing()

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    state = listState,
                    contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    batchesByDate.forEach { (date, batchesForDate) ->
                        stickyHeader(key = "date_$date") {
                            DateDivider(
                                date = date,
                                formatter = dateDividerFormatter,
                                itemCount = batchesForDate.size
                            )
                        }

                        items(batchesForDate, key = { it.id }) { batch ->
                            val isExpanded = batch.id in expandedBatchIds
                            val transactions = expandedBatchTransactions[batch.id]
                            val isLoadingTransactions = batch.id in isLoadingBatchDetails

                            AnimatedListItem {
                                ExpandableBatchCard(
                                    batch = batch,
                                    isExpanded = isExpanded,
                                    transactions = transactions,
                                    isLoadingTransactions = isLoadingTransactions,
                                    onClick = { onToggleBatchExpansion(batch.id) },
                                    onEditClick = { batchId ->
                                        when (batch) {
                                            is HistoryBatchDisplayItem.RealBatch ->
                                                onNavigateToEditPurchase(batchId.toString())
                                            is HistoryBatchDisplayItem.RealSaleBatch ->
                                                onNavigateToEditSale(batchId.toString())
                                            is HistoryBatchDisplayItem.EditedBatch -> {
                                                when (batch.batchType) {
                                                    BatchType.PURCHASE -> onNavigateToEditPurchase(batchId.toString())
                                                    BatchType.SALE -> onNavigateToEditSale(batchId.toString())
                                                    else -> { }
                                                }
                                            }
                                            is HistoryBatchDisplayItem.VirtualBatch,
                                            is HistoryBatchDisplayItem.TransferBatch -> { }
                                        }
                                    },
                                    onDeleteClick = { batchId ->
                                        when (batch) {
                                            is HistoryBatchDisplayItem.RealBatch ->
                                                onVoidBatch(batchId, BatchType.PURCHASE)
                                            is HistoryBatchDisplayItem.RealSaleBatch ->
                                                onVoidBatch(batchId, BatchType.SALE)
                                            is HistoryBatchDisplayItem.EditedBatch ->
                                                onVoidBatch(batchId, batch.batchType)
                                            is HistoryBatchDisplayItem.VirtualBatch,
                                            is HistoryBatchDisplayItem.TransferBatch -> { }
                                        }
                                    },
                                    currencyFormat = currencyFormat,
                                    weightFormat = weightFormat,
                                    dateFormatter = dateFormatter,
                                )
                            }
                        }
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

@Composable
private fun DateDivider(
    date: LocalDate,
    formatter: DateTimeFormatter,
    itemCount: Int,
    modifier: Modifier = Modifier
) {
    val today = remember { LocalDate.now() }
    val yesterday = remember { LocalDate.now().minusDays(1) }
    
    val dateText = when (date) {
        today -> "Сьогодні"
        yesterday -> "Вчора"
        else -> date.format(formatter)
    }
    
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = dateText,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "$itemCount операцій",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
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
    currencyFormat: DecimalFormat,
    weightFormat: DecimalFormat,
    dateFormatter: DateTimeFormatter,
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }
    // Auto-expand original data for edited batches
    var showOriginalData by remember(batch.isEdited) { mutableStateOf(batch.isEdited) }
    var showNotes by remember { mutableStateOf(false) }
    
    // Determine card color based on batch state
    val containerColor = when {
        batch.isVoided -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        batch.isEdited -> MaterialTheme.colorScheme.surface
        batch.isCorrection -> MaterialTheme.colorScheme.surface
        else -> MaterialTheme.colorScheme.surface
    }

    val typeColor = when (batch.batchType) {
        BatchType.PURCHASE -> MaterialTheme.colorScheme.primary
        BatchType.SALE -> MaterialTheme.colorScheme.error
        BatchType.TRANSFER -> MaterialTheme.colorScheme.tertiary
        BatchType.ADJUSTMENT -> MaterialTheme.colorScheme.tertiary
    }

    // Border color based on batch type/state
    val borderColor = when {
        batch.isVoided -> MaterialTheme.colorScheme.outline.copy(alpha = 0.3f)
        batch.isEdited -> MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)
        batch.isCorrection -> MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)
        else -> typeColor.copy(alpha = 0.3f)
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
    val isAdjustment = batch.batchType == BatchType.ADJUSTMENT
    val weightPrefix = if (isAdjustment) {
        if (batch.totalWeightKg >= java.math.BigDecimal.ZERO) "+" else "-"
    } else ""
    val weightValue = batch.totalWeightKg.abs()
    val amountValue = batch.totalAmount
    
    val canEdit = !batch.isVoided && 
                  batch !is HistoryBatchDisplayItem.VirtualBatch && 
                  batch !is HistoryBatchDisplayItem.TransferBatch &&
                  batch !is HistoryBatchDisplayItem.EditedBatch  // Edited batches shouldn't be re-edited
    
    Box {
        Card(
            modifier = modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = onClick,
                    onLongClick = { if (canEdit) showContextMenu = true }
                ),
            colors = CardDefaults.cardColors(containerColor = containerColor),
            border = BorderStroke(1.dp, borderColor),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
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
                                // Show edited badge with prominent styling
                                if (batch.isEdited) {
                                    Surface(
                                        color = MaterialTheme.colorScheme.tertiaryContainer,
                                        shape = RoundedCornerShape(4.dp)
                                    ) {
                                        Text(
                                            text = "Ред.",
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = FontWeight.Medium,
                                            color = MaterialTheme.colorScheme.onTertiaryContainer,
                                            modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                                // Show voided badge
                                if (batch.isVoided) {
                                    Text(
                                        text = "Видалено",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier
                                            .padding(horizontal = 4.dp, vertical = 2.dp)
                                    )
                                }
                                // Show correction badge (only if not merged as edited)
                                if (batch.isCorrection && !batch.isEdited) {
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
                            // Show correction reason only for non-edited correction batches
                            if (!batch.isEdited) {
                                batch.correctionReason?.let { reason ->
                                    Text(
                                        text = reason,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
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
                            // For edited batches, show current value with strikethrough of original
                            if (batch is HistoryBatchDisplayItem.EditedBatch) {
                                val weightDiff = batch.totalWeightKg - batch.originalTotalWeightKg
                                val hasWeightChange = weightDiff.compareTo(java.math.BigDecimal.ZERO) != 0

                                if (hasWeightChange) {
                                    // Show original weight with strikethrough
                                    AnimatedCounter(
                                        targetValue = batch.originalTotalWeightKg,
                                        formatter = { "${weightFormat.format(it)} кг" },
                                        style = MaterialTheme.typography.bodySmall.copy(textDecoration = TextDecoration.LineThrough),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.6f)
                                    )
                                }
                                // Current weight
                                AnimatedCounter(
                                    targetValue = weightValue,
                                    formatter = { "${weightPrefix}${weightFormat.format(it)} кг" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                // Show difference
                                if (hasWeightChange) {
                                    val sign = if (weightDiff > java.math.BigDecimal.ZERO) "+" else ""
                                    AnimatedCounter(
                                        targetValue = weightDiff,
                                        formatter = { "$sign${weightFormat.format(it)} кг" },
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Medium,
                                        color = if (weightDiff > java.math.BigDecimal.ZERO)
                                            MaterialTheme.colorScheme.primary
                                        else
                                            MaterialTheme.colorScheme.error
                                    )
                                }
                                // Amount with change
                                if (batch.totalAmount != null || batch.originalTotalAmount != null) {
                                    val amountDiff = (batch.totalAmount ?: java.math.BigDecimal.ZERO) -
                                        (batch.originalTotalAmount ?: java.math.BigDecimal.ZERO)
                                    val hasAmountChange = amountDiff.compareTo(java.math.BigDecimal.ZERO) != 0

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        amountValue?.let { amount ->
                                            AnimatedCounter(
                                                targetValue = amount,
                                                formatter = { "₴${currencyFormat.format(it)}" },
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                        }
                                        if (hasAmountChange) {
                                            val sign = if (amountDiff > java.math.BigDecimal.ZERO) "+" else "-"
                                            AnimatedCounter(
                                                targetValue = amountDiff.abs(),
                                                formatter = { "(${sign}₴${currencyFormat.format(it)})" },
                                                style = MaterialTheme.typography.labelSmall,
                                                color = if (amountDiff > java.math.BigDecimal.ZERO)
                                                    MaterialTheme.colorScheme.primary
                                                else
                                                    MaterialTheme.colorScheme.error
                                            )
                                        }
                                    }
                                }
                            } else {
                                // Standard display for non-edited batches
                                AnimatedCounter(
                                    targetValue = weightValue,
                                    formatter = { "${weightPrefix}${weightFormat.format(it)} кг" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold
                                )
                                amountValue?.let { amount ->
                                    AnimatedCounter(
                                        targetValue = amount,
                                        formatter = { "₴${currencyFormat.format(it)}" },
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
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
                                        currencyFormat = currencyFormat,
                                        weightFormat = weightFormat
                                    )
                                }
                                
                                // Notes section (collapsible)
                                val batchNotes = batch.notes
                                if (!batchNotes.isNullOrBlank()) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showNotes = !showNotes }
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Примітки",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = if (showNotes) "Згорнути" else "Розгорнути",
                                            modifier = Modifier
                                                .size(20.dp)
                                                .rotate(if (showNotes) 180f else 0f),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (showNotes) {
                                        Text(
                                            text = batchNotes,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurface,
                                            modifier = Modifier.padding(vertical = 4.dp)
                                        )
                                    }
                                }
                                
                                // Original data section for edited batches (collapsible)
                                if (batch is HistoryBatchDisplayItem.EditedBatch) {
                                    HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

                                    // Header for changes section
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable { showOriginalData = !showOriginalData }
                                            .padding(vertical = 4.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Filled.Edit,
                                                contentDescription = null,
                                                modifier = Modifier.size(16.dp),
                                                tint = MaterialTheme.colorScheme.tertiary
                                            )
                                            Text(
                                                text = "Зміни після редагування",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.Medium,
                                                color = MaterialTheme.colorScheme.tertiary
                                            )
                                        }
                                        Icon(
                                            imageVector = Icons.Filled.KeyboardArrowDown,
                                            contentDescription = if (showOriginalData) "Згорнути" else "Розгорнути",
                                            modifier = Modifier
                                                .size(20.dp)
                                                .rotate(if (showOriginalData) 180f else 0f),
                                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }

                                    if (showOriginalData) {
                                        val weightDiff = batch.totalWeightKg - batch.originalTotalWeightKg
                                        val amountDiff = (batch.totalAmount ?: java.math.BigDecimal.ZERO) -
                                            (batch.originalTotalAmount ?: java.math.BigDecimal.ZERO)
                                        val hasWeightChange = weightDiff.compareTo(java.math.BigDecimal.ZERO) != 0
                                        val hasAmountChange = amountDiff.compareTo(java.math.BigDecimal.ZERO) != 0
                                        val hasItemCountChange = batch.itemCount != batch.originalItemCount
                                        val hasNotesChange = batch.originalNotes != batch.notes
                                        val originalLocalTime = batch.originalCreatedAt.atZone(ZoneId.systemDefault())

                                        Surface(
                                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(
                                                modifier = Modifier.padding(12.dp),
                                                verticalArrangement = Arrangement.spacedBy(8.dp)
                                            ) {
                                                // Correction reason and original date
                                                batch.correctionReason?.let { reason ->
                                                    Text(
                                                        text = "Причина: $reason",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.tertiary
                                                    )
                                                }
                                                Text(
                                                    text = "Оригінал від ${dateFormatter.format(originalLocalTime)}",
                                                    style = MaterialTheme.typography.bodySmall,
                                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                                )

                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                                                // Changes table header
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween
                                                ) {
                                                    Text(
                                                        text = "",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        modifier = Modifier.width(60.dp)
                                                    )
                                                    Text(
                                                        text = "Було",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = "Стало",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    Text(
                                                        text = "Різниця",
                                                        style = MaterialTheme.typography.labelSmall,
                                                        fontWeight = FontWeight.Medium,
                                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                }

                                                HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))

                                                // Weight row
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.SpaceBetween,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    Text(
                                                        text = "Вага",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = FontWeight.Medium,
                                                        modifier = Modifier.width(60.dp)
                                                    )
                                                    AnimatedCounter(
                                                        targetValue = batch.originalTotalWeightKg,
                                                        formatter = { "${weightFormat.format(it)} кг" },
                                                        style = MaterialTheme.typography.bodySmall.copy(
                                                            textDecoration = if (hasWeightChange) TextDecoration.LineThrough else null
                                                        ),
                                                        color = if (hasWeightChange)
                                                            MaterialTheme.colorScheme.onSurfaceVariant
                                                        else
                                                            MaterialTheme.colorScheme.onSurface,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    AnimatedCounter(
                                                        targetValue = batch.totalWeightKg,
                                                        formatter = { "${weightFormat.format(it)} кг" },
                                                        style = MaterialTheme.typography.bodySmall,
                                                        fontWeight = if (hasWeightChange) FontWeight.Medium else FontWeight.Normal,
                                                        modifier = Modifier.weight(1f)
                                                    )
                                                    if (hasWeightChange) {
                                                        val sign = if (weightDiff > java.math.BigDecimal.ZERO) "+" else ""
                                                        AnimatedCounter(
                                                            targetValue = weightDiff,
                                                            formatter = { "$sign${weightFormat.format(it)}" },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (weightDiff > java.math.BigDecimal.ZERO)
                                                                MaterialTheme.colorScheme.primary
                                                            else
                                                                MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    } else {
                                                        Text(
                                                            text = "—",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }

                                                // Amount row
                                                if (batch.originalTotalAmount != null || batch.totalAmount != null) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Сума",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.width(60.dp)
                                                        )
                                                        AnimatedCounter(
                                                            targetValue = batch.originalTotalAmount ?: java.math.BigDecimal.ZERO,
                                                            formatter = { "₴${currencyFormat.format(it)}" },
                                                            style = MaterialTheme.typography.bodySmall.copy(
                                                                textDecoration = if (hasAmountChange) TextDecoration.LineThrough else null
                                                            ),
                                                            color = if (hasAmountChange)
                                                                MaterialTheme.colorScheme.onSurfaceVariant
                                                            else
                                                                MaterialTheme.colorScheme.onSurface,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        AnimatedCounter(
                                                            targetValue = batch.totalAmount ?: java.math.BigDecimal.ZERO,
                                                            formatter = { "₴${currencyFormat.format(it)}" },
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = if (hasAmountChange) FontWeight.Medium else FontWeight.Normal,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        if (hasAmountChange) {
                                                            val sign = if (amountDiff > java.math.BigDecimal.ZERO) "+" else "-"
                                                            AnimatedCounter(
                                                                targetValue = amountDiff.abs(),
                                                                formatter = { "${sign}₴${currencyFormat.format(it)}" },
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Bold,
                                                                color = if (amountDiff > java.math.BigDecimal.ZERO)
                                                                    MaterialTheme.colorScheme.primary
                                                                else
                                                                    MaterialTheme.colorScheme.error,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        } else {
                                                            Text(
                                                                text = "—",
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                modifier = Modifier.weight(1f)
                                                            )
                                                        }
                                                    }
                                                }

                                                // Item count row (only if changed)
                                                if (hasItemCountChange) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            text = "Позицій",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.width(60.dp)
                                                        )
                                                        Text(
                                                            text = "${batch.originalItemCount}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                            textDecoration = TextDecoration.LineThrough,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        Text(
                                                            text = "${batch.itemCount}",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Medium,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        val itemDiff = batch.itemCount - batch.originalItemCount
                                                        val sign = if (itemDiff > 0) "+" else ""
                                                        Text(
                                                            text = "$sign$itemDiff",
                                                            style = MaterialTheme.typography.bodySmall,
                                                            fontWeight = FontWeight.Bold,
                                                            color = if (itemDiff > 0)
                                                                MaterialTheme.colorScheme.primary
                                                            else
                                                                MaterialTheme.colorScheme.error,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                    }
                                                }

                                                // Notes change (if different)
                                                if (hasNotesChange) {
                                                    HorizontalDivider(color = MaterialTheme.colorScheme.outline.copy(alpha = 0.3f))
                                                    if (!batch.originalNotes.isNullOrBlank()) {
                                                        Column {
                                                            Text(
                                                                text = "Попередні примітки:",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                text = batch.originalNotes,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                                textDecoration = TextDecoration.LineThrough
                                                            )
                                                        }
                                                    }
                                                    val currentNotes = batch.notes
                                                    if (!currentNotes.isNullOrBlank()) {
                                                        Column {
                                                            Text(
                                                                text = "Нові примітки:",
                                                                style = MaterialTheme.typography.labelSmall,
                                                                color = MaterialTheme.colorScheme.onSurfaceVariant
                                                            )
                                                            Text(
                                                                text = currentNotes,
                                                                style = MaterialTheme.typography.bodySmall,
                                                                fontWeight = FontWeight.Medium
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
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
                        is HistoryBatchDisplayItem.EditedBatch -> batch.currentBatchId
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
                        is HistoryBatchDisplayItem.EditedBatch -> batch.currentBatchId
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
    currencyFormat: DecimalFormat,
    weightFormat: DecimalFormat,
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
    
    val weightPrefix = if (isAdjustment) {
        if (transaction.weightKg >= java.math.BigDecimal.ZERO) "+" else "-"
    } else ""
    val weightValue = transaction.weightKg.abs()
    
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
                    AnimatedCounter(
                        targetValue = price,
                        formatter = { "₴${currencyFormat.format(it)}/кг" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            AnimatedCounter(
                targetValue = weightValue,
                formatter = { "${weightPrefix}${weightFormat.format(it)} кг" },
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                color = weightColor
            )
            transaction.totalAmount?.let { amount ->
                AnimatedCounter(
                    targetValue = amount.abs(),
                    formatter = { "₴${currencyFormat.format(it)}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
