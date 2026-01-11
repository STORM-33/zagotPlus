package com.zagot.zagotplus.ui.screens.reports

import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedCard
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zagot.zagotplus.ui.components.EmptyState
import com.zagot.zagotplus.ui.components.EmptyStateIcons
import java.math.BigDecimal
import java.text.DecimalFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsScreen(
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ReportsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val context = LocalContext.current
    val decimalFormat = remember { DecimalFormat("#,##0.00") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy") }
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    LaunchedEffect(uiState.copySuccess) {
        if (uiState.copySuccess) {
            snackbarHostState.showSnackbar("Скопійовано в буфер обміну")
            viewModel.dismissCopySuccess()
        }
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = uiState.selectedDate
                .atStartOfDay(ZoneId.systemDefault())
                .toInstant()
                .toEpochMilli()
        )
        
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis)
                            .atZone(ZoneId.systemDefault())
                            .toLocalDate()
                        viewModel.selectDate(date)
                    }
                    showDatePicker = false
                }) {
                    Text("OK")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("Скасувати")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
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
                },
                actions = {
                    IconButton(onClick = { viewModel.copyReportToClipboard() }) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = "Копіювати")
                    }
                    IconButton(onClick = {
                        val intent = viewModel.createShareIntent()
                        context.startActivity(intent)
                    }) {
                        Icon(Icons.Filled.Share, contentDescription = "Поділитися")
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
            // Date selector
            OutlinedCard(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clickable { showDatePicker = true }
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormatter.format(uiState.selectedDate),
                        style = MaterialTheme.typography.titleLarge
                    )
                    Icon(
                        imageVector = Icons.Filled.DateRange,
                        contentDescription = "Вибрати дату"
                    )
                }
            }

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
                            description = "За ${dateFormatter.format(uiState.selectedDate)} транзакцій не знайдено"
                        )
                    }
                }
                else -> {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            horizontal = 16.dp,
                            vertical = 8.dp
                        )
                    ) {
                        // Summary cards
                        item {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                SummaryCard(
                                    title = "Закупки",
                                    weightKg = uiState.purchaseSummary.totalWeightKg,
                                    amount = uiState.purchaseSummary.totalAmount,
                                    decimalFormat = decimalFormat,
                                    containerColor = MaterialTheme.colorScheme.primaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                                SummaryCard(
                                    title = "Продажі",
                                    weightKg = uiState.saleSummary.totalWeightKg,
                                    amount = uiState.saleSummary.totalAmount,
                                    decimalFormat = decimalFormat,
                                    containerColor = MaterialTheme.colorScheme.secondaryContainer,
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }

                        // Product breakdown
                        if (uiState.productBreakdown.isNotEmpty()) {
                            item {
                                Text(
                                    text = "По товарах",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(uiState.productBreakdown) { item ->
                                ProductBreakdownRow(
                                    item = item,
                                    decimalFormat = decimalFormat
                                )
                            }
                        }

                        // Location breakdown
                        if (uiState.locationBreakdown.isNotEmpty()) {
                            item {
                                Text(
                                    text = "По точках",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(uiState.locationBreakdown) { item ->
                                LocationBreakdownRow(
                                    item = item,
                                    decimalFormat = decimalFormat
                                )
                            }
                        }

                        // Transfers
                        if (uiState.transferSummary.isNotEmpty()) {
                            item {
                                Text(
                                    text = "Переміщення",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.padding(top = 8.dp)
                                )
                            }
                            items(uiState.transferSummary) { transfer ->
                                TransferRow(
                                    transfer = transfer,
                                    decimalFormat = decimalFormat
                                )
                            }
                        }

                        item { Spacer(modifier = Modifier.height(16.dp)) }
                    }
                }
            }
        }
    }
}

@Composable
private fun SummaryCard(
    title: String,
    weightKg: BigDecimal,
    amount: BigDecimal,
    decimalFormat: DecimalFormat,
    containerColor: androidx.compose.ui.graphics.Color,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(containerColor = containerColor)
    ) {
        Column(
            modifier = Modifier.padding(16.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${decimalFormat.format(weightKg)} кг",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = "${decimalFormat.format(amount)} грн",
                style = MaterialTheme.typography.bodyLarge
            )
        }
    }
}

@Composable
private fun ProductBreakdownRow(
    item: ProductBreakdownItem,
    decimalFormat: DecimalFormat,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = item.productName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (item.purchaseWeightKg > BigDecimal.ZERO) {
                    Column {
                        Text(
                            text = "Закупка",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${decimalFormat.format(item.purchaseWeightKg)} кг",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${decimalFormat.format(item.purchaseAmount)} грн",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (item.saleWeightKg > BigDecimal.ZERO) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Продаж",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${decimalFormat.format(item.saleWeightKg)} кг",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${decimalFormat.format(item.saleAmount)} грн",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun LocationBreakdownRow(
    item: LocationBreakdownItem,
    decimalFormat: DecimalFormat,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp)
        ) {
            Text(
                text = item.locationName,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(4.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                if (item.purchaseWeightKg > BigDecimal.ZERO) {
                    Column {
                        Text(
                            text = "Закупка",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${decimalFormat.format(item.purchaseWeightKg)} кг",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${decimalFormat.format(item.purchaseAmount)} грн",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (item.saleWeightKg > BigDecimal.ZERO) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Продаж",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = "${decimalFormat.format(item.saleWeightKg)} кг",
                            style = MaterialTheme.typography.bodyMedium
                        )
                        Text(
                            text = "${decimalFormat.format(item.saleAmount)} грн",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun TransferRow(
    transfer: TransferSummaryItem,
    decimalFormat: DecimalFormat,
    modifier: Modifier = Modifier
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "${transfer.fromLocationName} → ${transfer.toLocationName}",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = transfer.productName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Text(
                text = "${decimalFormat.format(transfer.weightKg)} кг",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
    }
}
