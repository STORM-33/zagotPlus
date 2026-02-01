package com.zagot.zagotplus.ui.screens.reports

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
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
import com.zagot.zagotplus.ui.components.DateRange
import com.zagot.zagotplus.ui.components.DateRangePreset
import com.zagot.zagotplus.ui.components.EmptyState
import com.zagot.zagotplus.ui.components.EmptyStateIcons
import com.zagot.zagotplus.ui.components.adaptiveHorizontalPadding
import com.zagot.zagotplus.ui.components.adaptiveItemSpacing
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ReportsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currencyFormat = remember { DecimalFormat("#,##0") }
    val weightFormat = remember { DecimalFormat("#,##0.0") }
    
    // Dialog states for long-press panels
    var showSpendingsDialog by remember { mutableStateOf(false) }
    var showEarningsDialog by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }
    
    // Spendings detail dialog
    if (showSpendingsDialog) {
        SpendingsDetailDialog(
            purchaseTotal = uiState.purchaseTotal,
            paymentsByCategory = uiState.paymentsByCategory,
            currencyFormat = currencyFormat,
            onDismiss = { showSpendingsDialog = false }
        )
    }
    
    // Earnings detail dialog
    if (showEarningsDialog) {
        EarningsDetailDialog(
            salesItems = uiState.salesItems,
            totalEarnings = uiState.totalEarnings,
            totalSpendings = uiState.totalSpendings,
            inventoryValue = uiState.inventoryValue,
            currencyFormat = currencyFormat,
            weightFormat = weightFormat,
            onDismiss = { showEarningsDialog = false }
        )
    }

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text("Звіти") },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
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
            // Date range selector (with "all time" option)
            DateRangeSelector(
                dateRange = uiState.dateRange,
                onDateRangeChange = { viewModel.setDateRange(it) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            )

            // Summary panels with long-press support
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                SummaryPanel(
                    title = "Витрати",
                    amount = uiState.totalSpendings,
                    currencyFormat = currencyFormat,
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer,
                    onLongPress = { showSpendingsDialog = true },
                    modifier = Modifier.weight(1f)
                )
                SummaryPanel(
                    title = "Прибуток",
                    amount = uiState.totalEarnings,
                    currencyFormat = currencyFormat,
                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                    contentColor = MaterialTheme.colorScheme.onPrimaryContainer,
                    onLongPress = { showEarningsDialog = true },
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Location selector (scrollable chips)
            if (uiState.locations.isNotEmpty()) {
                LocationSelector(
                    locations = uiState.locations,
                    selectedLocationId = uiState.selectedLocationId,
                    onLocationSelect = { viewModel.selectLocation(it) },
                    onTotalSelect = { viewModel.selectTotalView() },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Content
            when {
                uiState.isLoading -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        CircularProgressIndicator()
                    }
                }
                !uiState.hasData -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = EmptyStateIcons.Transactions,
                            title = "Немає даних",
                            description = "За вибраний період закупівель не знайдено"
                        )
                    }
                }
                uiState.productItems.isEmpty() -> {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center
                    ) {
                        EmptyState(
                            icon = EmptyStateIcons.Products,
                            title = "Немає закупівель",
                            description = "За вибраний період товарів не закуповували"
                        )
                    }
                }
                else -> {
                    val horizontalPadding = adaptiveHorizontalPadding()
                    val itemSpacing = adaptiveItemSpacing()
                    
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(horizontal = horizontalPadding, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(itemSpacing)
                    ) {
                        items(uiState.productItems, key = { it.productId }) { item ->
                            ProductReportCard(
                                item = item,
                                currencyFormat = currencyFormat,
                                weightFormat = weightFormat
                            )
                        }
                        
                        item(key = "summary") {
                            ReportsSummaryPanel(
                                totalWeightKg = uiState.totalWeightKg,
                                weightFormat = weightFormat
                            )
                        }
                        
                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SummaryPanel(
    title: String,
    amount: BigDecimal,
    currencyFormat: DecimalFormat,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    onLongPress: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .combinedClickable(
                onClick = { },
                onLongClick = onLongPress
            ),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        border = BorderStroke(1.dp, containerColor),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = contentColor
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "₴${currencyFormat.format(amount)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = contentColor
            )
        }
    }
}

@Composable
private fun SpendingsDetailDialog(
    purchaseTotal: BigDecimal,
    paymentsByCategory: List<PaymentCategoryItem>,
    currencyFormat: DecimalFormat,
    onDismiss: () -> Unit
) {
    val paymentsTotal = paymentsByCategory.sumOf { it.amount }
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Деталі витрат") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Purchases section
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Закупки товарів", style = MaterialTheme.typography.bodyLarge)
                    Text(
                        "₴${currencyFormat.format(purchaseTotal)}",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
                
                // Payments section with categories
                if (paymentsByCategory.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "Оплати:",
                        style = MaterialTheme.typography.bodyLarge
                    )
                    paymentsByCategory.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(start = 16.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                category.categoryName,
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                "₴${currencyFormat.format(category.amount)}",
                                style = MaterialTheme.typography.bodyMedium
                            )
                        }
                    }
                } else if (paymentsTotal > BigDecimal.ZERO) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Оплати", style = MaterialTheme.typography.bodyLarge)
                        Text(
                            "₴${currencyFormat.format(paymentsTotal)}",
                            style = MaterialTheme.typography.bodyLarge,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text("Всього", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                    Text(
                        "₴${currencyFormat.format(purchaseTotal + paymentsTotal)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.error
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити")
            }
        }
    )
}

@Composable
private fun EarningsDetailDialog(
    salesItems: List<SalesReportItem>,
    totalEarnings: BigDecimal,
    totalSpendings: BigDecimal,
    inventoryValue: BigDecimal,
    currencyFormat: DecimalFormat,
    weightFormat: DecimalFormat,
    onDismiss: () -> Unit
) {
    // Calculate net profit: Total earnings - Total spendings
    val netProfit = totalEarnings.subtract(totalSpendings)
    
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Деталі прибутку") },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (salesItems.isEmpty()) {
                    Text(
                        "Немає даних про продажі",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    Text(
                        "Прибуток по товарах:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier.height(200.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(salesItems, key = { it.productId }) { item ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        item.productName,
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Text(
                                        "${weightFormat.format(item.totalWeightKg)} кг",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                                Text(
                                    "₴${currencyFormat.format(item.totalEarned)}",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                    HorizontalDivider()
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Всього прибуток", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(
                            "₴${currencyFormat.format(totalEarnings)}",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
                // Net profit (always shown, even if no sales)
                Spacer(modifier = Modifier.height(8.dp))
                HorizontalDivider()
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "Чистий прибуток",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "₴${currencyFormat.format(netProfit)}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = if (netProfit >= BigDecimal.ZERO) 
                            MaterialTheme.colorScheme.primary 
                        else 
                            MaterialTheme.colorScheme.error
                    )
                }

                // Inventory value (potential revenue)
                // Always shown, unaffected by date range
                Spacer(modifier = Modifier.height(4.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        "В наявності товар на",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        "₴${currencyFormat.format(inventoryValue)}",
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.tertiary
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрити")
            }
        }
    )
}

@Composable
private fun ReportsSummaryPanel(
    totalWeightKg: BigDecimal,
    weightFormat: DecimalFormat,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Всього закуплено",
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${weightFormat.format(totalWeightKg)} кг",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun LocationSelector(
    locations: List<com.zagot.zagotplus.domain.model.Location>,
    selectedLocationId: java.util.UUID?,
    onLocationSelect: (java.util.UUID) -> Unit,
    onTotalSelect: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Individual locations
        locations.forEach { location ->
            FilterChip(
                selected = selectedLocationId == location.id,
                onClick = { onLocationSelect(location.id) },
                label = { Text(location.name) },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                    selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                )
            )
        }
        
        // "Всього" option at the end
        FilterChip(
            selected = selectedLocationId == null,
            onClick = { onTotalSelect() },
            label = { Text("Всього") },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        )
    }
}

@Composable
private fun ProductReportCard(
    item: ProductReportItem,
    currencyFormat: DecimalFormat,
    weightFormat: DecimalFormat,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Product image
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                if (item.imageUri != null) {
                    AsyncImage(
                        model = item.imageUri,
                        contentDescription = item.productName,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = "Фото товару",
                        modifier = Modifier.size(24.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Spacer(modifier = Modifier.width(12.dp))

            // Product info
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.productName,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    text = "${weightFormat.format(item.totalWeightKg)} кг",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Amount spent
            Text(
                text = "₴${currencyFormat.format(item.totalSpent)}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateRangeSelector(
    dateRange: DateRange?,
    onDateRangeChange: (DateRange?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    var customStartDate by remember { mutableStateOf(LocalDate.now()) }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM") }

    val presetOptions = listOf(
        null to "Весь час",
        DateRangePreset.TODAY to "Сьогодні",
        DateRangePreset.YESTERDAY to "Вчора",
        DateRangePreset.LAST_7_DAYS to "7 днів",
        DateRangePreset.LAST_30_DAYS to "30 днів",
        DateRangePreset.THIS_MONTH to "Цей місяць",
        DateRangePreset.LAST_MONTH to "Минулий місяць"
    )

    val displayText = when {
        dateRange == null -> "Весь час"
        dateRange.preset == DateRangePreset.CUSTOM -> {
            if (dateRange.startDate == dateRange.endDate) {
                dateRange.startDate.format(dateFormatter)
            } else {
                "${dateRange.startDate.format(dateFormatter)} - ${dateRange.endDate.format(dateFormatter)}"
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
            DropdownMenuItem(
                text = { Text("Вибрати період...") },
                onClick = {
                    expanded = false
                    customStartDate = dateRange?.startDate ?: LocalDate.now()
                    showStartDatePicker = true
                }
            )
        }
    }

    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = customStartDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        customStartDate = date
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
            DatePicker(
                state = datePickerState,
                title = { Text("Початкова дата", modifier = Modifier.padding(16.dp)) }
            )
        }
    }

    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = (dateRange?.endDate ?: LocalDate.now())
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )

        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val endDate = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        val finalEndDate = if (endDate.isBefore(customStartDate)) customStartDate else endDate
                        onDateRangeChange(
                            DateRange(
                                startDate = customStartDate,
                                endDate = finalEndDate,
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
            DatePicker(
                state = datePickerState,
                title = { Text("Кінцева дата", modifier = Modifier.padding(16.dp)) }
            )
        }
    }
}
