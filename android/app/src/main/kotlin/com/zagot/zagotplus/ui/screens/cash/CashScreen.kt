package com.zagot.zagotplus.ui.screens.cash

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zagot.zagotplus.R
import com.zagot.zagotplus.domain.model.CashHistoryItem
import com.zagot.zagotplus.domain.model.CashHistoryItemType
import com.zagot.zagotplus.domain.model.ExpenseCategory
import com.zagot.zagotplus.ui.theme.CashInfo
import com.zagot.zagotplus.ui.theme.CashNegative
import com.zagot.zagotplus.ui.theme.CashPositive
import com.zagot.zagotplus.ui.theme.CashWarning
import kotlinx.coroutines.flow.distinctUntilChanged
import java.math.BigDecimal
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CashScreen(
    onNavigateBack: () -> Unit,
    viewModel: CashViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.cash_title)) },
                navigationIcon = {
                    IconButton(onClick = onNavigateBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { viewModel.showCategoriesDialog() }) {
                        Icon(Icons.Filled.Category, contentDescription = stringResource(R.string.cash_categories_title))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        if (uiState.isLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Location tabs + Total tab
                if (uiState.locations.isNotEmpty()) {
                    val selectedIndex = if (uiState.isTotalsView) {
                        uiState.locations.size // Total tab is last
                    } else {
                        uiState.locations.indexOfFirst { 
                            it.id == uiState.selectedLocationId 
                        }.coerceAtLeast(0)
                    }
                    
                    TabRow(selectedTabIndex = selectedIndex) {
                        uiState.locations.forEachIndexed { index, location ->
                            Tab(
                                selected = index == selectedIndex,
                                onClick = { viewModel.selectLocation(location.id) },
                                text = { Text(location.name) }
                            )
                        }
                        // Total tab
                        Tab(
                            selected = uiState.isTotalsView,
                            onClick = { viewModel.selectTotalView() },
                            text = { Text("Всього") }
                        )
                    }
                }

                // Balance Card
                BalanceCard(
                    balance = uiState.balance,
                    dailyChange = uiState.dailyChange,
                    modifier = Modifier.padding(16.dp)
                )

                // Action Buttons (disabled in totals view - user must select a location)
                ActionButtons(
                    onDeposit = { viewModel.showDepositDialog() },
                    onWithdraw = { viewModel.showWithdrawDialog() },
                    onPayment = { viewModel.showPaymentDialog() },
                    enabled = !uiState.isTotalsView,
                    modifier = Modifier.padding(horizontal = 16.dp)
                )

                Spacer(modifier = Modifier.height(16.dp))

                // Operations List Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.cash_history_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (uiState.totalItemsCount > 0) {
                        Text(
                            text = "${uiState.historyItems.size} з ${uiState.totalItemsCount}",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                val listState = rememberLazyListState()

                // Trigger load more when reaching end
                val shouldLoadMore by remember {
                    derivedStateOf {
                        val lastVisibleItem = listState.layoutInfo.visibleItemsInfo.lastOrNull()
                            ?: return@derivedStateOf false
                        lastVisibleItem.index >= listState.layoutInfo.totalItemsCount - 3
                    }
                }

                LaunchedEffect(shouldLoadMore) {
                    snapshotFlow { shouldLoadMore }
                        .distinctUntilChanged()
                        .collect { shouldLoad ->
                            if (shouldLoad && !uiState.isLoadingMore && uiState.hasMoreItems) {
                                viewModel.loadMoreOperations()
                            }
                        }
                }

                LazyColumn(
                    state = listState,
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(
                        items = uiState.historyItems,
                        key = { it.id.toString() }
                    ) { item ->
                        HistoryItem(
                            item = item,
                            showLocationName = uiState.isTotalsView
                        )
                    }

                    // Loading indicator at bottom
                    if (uiState.isLoadingMore) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                            }
                        }
                    }

                    if (uiState.historyItems.isEmpty() && !uiState.isLoading) {
                        item {
                            Text(
                                text = stringResource(R.string.cash_no_operations),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }

                    // End of list indicator
                    if (!uiState.hasMoreItems && uiState.historyItems.isNotEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.cash_all_loaded),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                        }
                    }
                }
            }
        }
    }

    // Dialogs
    when (uiState.dialogType) {
        CashDialogType.DEPOSIT -> DepositDialog(
            amount = uiState.dialogAmount,
            notes = uiState.dialogNotes,
            onAmountChange = viewModel::onAmountChange,
            onNotesChange = viewModel::onNotesChange,
            onConfirm = viewModel::confirmDeposit,
            onDismiss = viewModel::dismissDialog,
            canConfirm = uiState.canConfirmDeposit,
            isSaving = uiState.isSaving
        )
        CashDialogType.WITHDRAW -> WithdrawDialog(
            amount = uiState.dialogAmount,
            notes = uiState.dialogNotes,
            balance = uiState.balance,
            onAmountChange = viewModel::onAmountChange,
            onNotesChange = viewModel::onNotesChange,
            onConfirm = viewModel::confirmWithdraw,
            onDismiss = viewModel::dismissDialog,
            canConfirm = uiState.canConfirmWithdraw,
            isSaving = uiState.isSaving
        )
        CashDialogType.PAYMENT -> PaymentDialog(
            amount = uiState.dialogAmount,
            notes = uiState.dialogNotes,
            categories = uiState.categories,
            selectedCategoryId = uiState.dialogCategoryId,
            balance = uiState.balance,
            onAmountChange = viewModel::onAmountChange,
            onNotesChange = viewModel::onNotesChange,
            onCategorySelect = viewModel::onCategorySelect,
            onConfirm = viewModel::confirmPayment,
            onDismiss = viewModel::dismissDialog,
            canConfirm = uiState.canConfirmPayment,
            isSaving = uiState.isSaving
        )
        CashDialogType.CATEGORIES -> CategoriesDialog(
            categories = uiState.categories,
            newCategoryName = uiState.newCategoryName,
            onNewCategoryNameChange = viewModel::onNewCategoryNameChange,
            onAddCategory = viewModel::addCategory,
            onDeactivateCategory = viewModel::deactivateCategory,
            onDismiss = viewModel::dismissDialog
        )
        CashDialogType.NONE -> { /* No dialog */ }
    }
}

@Composable
private fun BalanceCard(
    balance: BigDecimal,
    dailyChange: BigDecimal,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = stringResource(R.string.cash_title),
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "${balance.setScale(2)} ₴",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                val changeColor = when {
                    dailyChange > BigDecimal.ZERO -> CashPositive
                    dailyChange < BigDecimal.ZERO -> CashNegative
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
                val changePrefix = if (dailyChange > BigDecimal.ZERO) "+" else ""
                Text(
                    text = stringResource(R.string.cash_today, "$changePrefix${dailyChange.setScale(2)} ₴"),
                    style = MaterialTheme.typography.bodyMedium,
                    color = changeColor
                )
            }
        }
    }
}

@Composable
private fun ActionButtons(
    onDeposit: () -> Unit,
    onWithdraw: () -> Unit,
    onPayment: () -> Unit,
    enabled: Boolean = true,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onDeposit,
                modifier = Modifier.weight(1f),
                enabled = enabled,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CashPositive
                )
            ) {
                Icon(Icons.Filled.Add, contentDescription = "Поповнення", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.cash_deposit_action))
            }
            OutlinedButton(
                onClick = onWithdraw,
                modifier = Modifier.weight(1f),
                enabled = enabled
            ) {
                Icon(Icons.Filled.Remove, contentDescription = "Видача", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.cash_withdraw_action))
            }
        }
        Button(
            onClick = onPayment,
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled
        ) {
            Icon(Icons.Filled.Payment, contentDescription = "Витрати", modifier = Modifier.size(18.dp))
            Spacer(modifier = Modifier.width(4.dp))
            Text(stringResource(R.string.cash_payment_action))
        }
    }
}

@Composable
private fun HistoryItem(
    item: CashHistoryItem,
    showLocationName: Boolean = false,
    modifier: Modifier = Modifier
) {
    val depositLabel = stringResource(R.string.cash_history_deposit)
    val withdrawalLabel = stringResource(R.string.cash_history_withdrawal)
    val paymentLabel = stringResource(R.string.cash_history_payment)
    val purchaseLabel = stringResource(R.string.cash_history_purchase)
    val saleLabel = stringResource(R.string.cash_history_sale)
    
    val (icon, color, label) = when (item.type) {
        CashHistoryItemType.DEPOSIT -> Triple(
            Icons.Filled.ArrowDownward,
            CashPositive,
            depositLabel
        )
        CashHistoryItemType.WITHDRAWAL -> Triple(
            Icons.Filled.ArrowUpward,
            CashNegative,
            withdrawalLabel
        )
        CashHistoryItemType.PAYMENT -> Triple(
            Icons.Filled.Payment,
            CashWarning,
            item.categoryName ?: paymentLabel
        )
        CashHistoryItemType.PURCHASE -> Triple(
            Icons.Filled.Remove,
            CashInfo,
            purchaseLabel
        )
        CashHistoryItemType.SALE -> Triple(
            Icons.Filled.Add,
            CashInfo,
            saleLabel
        )
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM") }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(40.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(color.copy(alpha = 0.1f)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = "Тип операції",
                    tint = color,
                    modifier = Modifier.size(24.dp)
                )
            }

            Spacer(modifier = Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                // Show details based on type
                val detailText = when {
                    item.type == CashHistoryItemType.PURCHASE || item.type == CashHistoryItemType.SALE -> {
                        val parts = mutableListOf<String>()
                        item.batchCount?.let { count ->
                            if (count > 1) {
                                val clientWord = pluralizeUkrainian(count, "клієнт", "клієнти", "клієнтів")
                                parts.add("$count $clientWord")
                            }
                        }
                        item.itemCount?.let { parts.add("$it поз.") }
                        item.weightKg?.let { parts.add("${it.setScale(2)} кг") }
                        if (parts.isNotEmpty()) parts.joinToString(" • ") else null
                    }
                    !item.notes.isNullOrBlank() -> item.notes
                    else -> null
                }
                if (detailText != null) {
                    Text(
                        text = detailText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                val amountText = if (item.type.isInflow) {
                    "+${item.amount.setScale(2)}"
                } else {
                    "-${item.amount.setScale(2)}"
                }
                Text(
                    text = "$amountText ₴",
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = dateFormatter.format(item.createdAt.atZone(java.time.ZoneId.systemDefault())),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (showLocationName && item.locationName != null) {
                        Text(
                            text = "•",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = item.locationName,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun DepositDialog(
    amount: String,
    notes: String,
    onAmountChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    canConfirm: Boolean,
    isSaving: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cash_deposit_dialog_title)) },
        text = {
            Column {
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(R.string.cash_amount_label)) },
                    suffix = { Text("₴") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(R.string.cash_notes_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canConfirm && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.cash_deposit_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun WithdrawDialog(
    amount: String,
    notes: String,
    balance: BigDecimal,
    onAmountChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    canConfirm: Boolean,
    isSaving: Boolean
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cash_withdraw_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.cash_available, "${balance.setScale(2)} ₴"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(R.string.cash_amount_label)) },
                    suffix = { Text("₴") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(R.string.cash_notes_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canConfirm && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.cash_withdraw_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PaymentDialog(
    amount: String,
    notes: String,
    categories: List<ExpenseCategory>,
    selectedCategoryId: UUID?,
    balance: BigDecimal,
    onAmountChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onCategorySelect: (UUID?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    canConfirm: Boolean,
    isSaving: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedCategory = categories.find { it.id == selectedCategoryId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cash_payment_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.cash_available, "${balance.setScale(2)} ₴"),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = amount,
                    onValueChange = onAmountChange,
                    label = { Text(stringResource(R.string.cash_amount_label)) },
                    suffix = { Text("₴") },
                    keyboardOptions = KeyboardOptions(
                        keyboardType = KeyboardType.Decimal,
                        imeAction = ImeAction.Next
                    ),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))

                val noCategoryLabel = stringResource(R.string.cash_no_category)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedCategory?.name ?: noCategoryLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.cash_category_label)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text(noCategoryLabel) },
                            onClick = {
                                onCategorySelect(null)
                                expanded = false
                            }
                        )
                        categories.forEach { category ->
                            DropdownMenuItem(
                                text = { Text(category.name) },
                                onClick = {
                                    onCategorySelect(category.id)
                                    expanded = false
                                }
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text(stringResource(R.string.cash_notes_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirm,
                enabled = canConfirm && !isSaving
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.cash_payment_action))
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}

@Composable
private fun CategoriesDialog(
    categories: List<ExpenseCategory>,
    newCategoryName: String,
    onNewCategoryNameChange: (String) -> Unit,
    onAddCategory: () -> Unit,
    onDeactivateCategory: (UUID) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cash_categories_title)) },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = newCategoryName,
                        onValueChange = onNewCategoryNameChange,
                        label = { Text(stringResource(R.string.cash_new_category_label)) },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(
                        onClick = onAddCategory,
                        enabled = newCategoryName.isNotBlank()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = stringResource(R.string.save))
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                if (categories.isEmpty()) {
                    Text(
                        text = stringResource(R.string.cash_no_categories),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                } else {
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyLarge,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onDeactivateCategory(category.id) }
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cash_close))
            }
        }
    )
}

/**
 * Ukrainian pluralization helper.
 * Returns the correct form based on the number:
 * - 1, 21, 31... → singular (клієнт)
 * - 2-4, 22-24, 32-34... → few (клієнти)
 * - 0, 5-20, 25-30... → many (клієнтів)
 */
private fun pluralizeUkrainian(count: Int, one: String, few: String, many: String): String {
    val mod100 = count % 100
    val mod10 = count % 10
    return when {
        mod100 in 11..19 -> many
        mod10 == 1 -> one
        mod10 in 2..4 -> few
        else -> many
    }
}
