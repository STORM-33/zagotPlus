package com.zagot.zagotplus.ui.screens.cash

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.Payment
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.DpOffset
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zagot.zagotplus.R
import com.zagot.zagotplus.domain.model.CashHistoryItem
import com.zagot.zagotplus.domain.model.CashHistoryItemType
import com.zagot.zagotplus.domain.model.DayCashGroup
import com.zagot.zagotplus.domain.model.ExpenseCategory
import com.zagot.zagotplus.domain.model.Location
import com.zagot.zagotplus.ui.components.adaptiveHorizontalPadding
import com.zagot.zagotplus.ui.components.adaptiveItemSpacing
import com.zagot.zagotplus.ui.components.isTablet
import com.zagot.zagotplus.ui.components.roundBalanceForDisplay
import com.zagot.zagotplus.ui.theme.CashInfo
import com.zagot.zagotplus.ui.theme.CashNegative
import com.zagot.zagotplus.ui.theme.CashPositive
import com.zagot.zagotplus.ui.theme.CashTransfer
import com.zagot.zagotplus.ui.theme.CashWarning
import kotlinx.coroutines.flow.distinctUntilChanged
import java.math.BigDecimal
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
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

    LaunchedEffect(uiState.successMessage) {
        uiState.successMessage?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissSuccess()
        }
    }

    // Determine tablet layout early for TopAppBar actions
    val isTabletLayout = isTablet()

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
                    // Only show categories icon on phone (on tablet it's inline)
                    if (!isTabletLayout) {
                        IconButton(onClick = { viewModel.showCategoriesDialog() }) {
                            Icon(Icons.Filled.Category, contentDescription = stringResource(R.string.cash_categories_title))
                        }
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        // Only show full-screen loading on initial load (no locations yet)
        // When switching tabs, keep showing content to avoid flash
        val showFullScreenLoading = uiState.isLoading && uiState.locations.isEmpty()
        
        if (showFullScreenLoading) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }
        } else {
            val horizontalPadding = adaptiveHorizontalPadding()
            val itemSpacing = adaptiveItemSpacing()

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                // Location tabs + Total tab (always full-width)
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

                // Content: Split layout on tablet, stacked on phone
                if (isTabletLayout) {
                    // Tablet: Balance/Actions on left, History on right
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = horizontalPadding, vertical = 16.dp),
                        horizontalArrangement = Arrangement.spacedBy(24.dp)
                    ) {
                        // Left panel: Balance + Actions + Categories (scrollable)
                        Column(
                            modifier = Modifier
                                .weight(0.35f)
                                .fillMaxHeight()
                                .verticalScroll(rememberScrollState()),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            BalanceCard(
                                balance = uiState.balance.roundBalanceForDisplay(),
                                dailyChange = uiState.dailyChange,
                                dailyAddition = uiState.dailyAddition,
                                selectedLocationId = uiState.selectedLocationId,
                                modifier = Modifier.fillMaxWidth()
                            )

                            // Show action buttons only in location view (not in totals view)
                            if (!uiState.isTotalsView) {
                                ActionButtons(
                                    onDeposit = { viewModel.showDepositDialog() },
                                    onWithdraw = { viewModel.showWithdrawDialog() },
                                    onPayment = { viewModel.showPaymentDialog() },
                                    onTransfer = { viewModel.showTransferDialog() },
                                    enabled = true,
                                    hasMultipleLocations = uiState.locations.size > 1,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }

                            // Inline Categories Panel for tablet (visible in both location and totals view)
                            CategoriesPanel(
                                categories = uiState.categories,
                                newCategoryName = uiState.newCategoryName,
                                onNewCategoryNameChange = viewModel::onNewCategoryNameChange,
                                onAddCategory = viewModel::addCategory,
                                onDeactivateCategory = viewModel::deactivateCategory,
                                modifier = Modifier.fillMaxWidth()
                            )
                        }

                        // Right panel: History
                        Column(
                            modifier = Modifier
                                .weight(0.65f)
                                .fillMaxHeight()
                        ) {
                            // Operations List Header
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(bottom = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = stringResource(R.string.cash_history_title),
                                    style = MaterialTheme.typography.titleMedium
                                )
                                if (uiState.dayGroups.isNotEmpty()) {
                                    Text(
                                        text = "${uiState.dayGroups.size} днів",
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
                                contentPadding = PaddingValues(vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(itemSpacing)
                            ) {
                                items(
                                    items = uiState.dayGroups,
                                    key = { it.date.toString() }
                                ) { dayGroup ->
                                    ExpandableDayCard(
                                        dayGroup = dayGroup,
                                        isExpanded = dayGroup.date in uiState.expandedDays,
                                        showLocationName = uiState.isTotalsView,
                                        onToggle = { viewModel.toggleDayExpansion(dayGroup.date) },
                                        onEditItem = { item -> viewModel.showEditDialog(item) },
                                        canModifyItem = { item -> viewModel.canModifyItem(item) },
                                        modifier = Modifier.animateItemPlacement()
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

                                if (uiState.dayGroups.isEmpty() && !uiState.isLoading) {
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
                                if (!uiState.hasMoreItems && uiState.dayGroups.isNotEmpty()) {
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
                } else {
                    // Phone: Original stacked layout
                    // Balance Card
                    BalanceCard(
                        balance = uiState.balance.roundBalanceForDisplay(),
                        dailyChange = uiState.dailyChange,
                        dailyAddition = uiState.dailyAddition,
                        selectedLocationId = uiState.selectedLocationId,
                        modifier = Modifier.padding(16.dp)
                    )

                    // Show action buttons only in location view (not in totals view)
                    if (!uiState.isTotalsView) {
                        ActionButtons(
                            onDeposit = { viewModel.showDepositDialog() },
                            onWithdraw = { viewModel.showWithdrawDialog() },
                            onPayment = { viewModel.showPaymentDialog() },
                            onTransfer = { viewModel.showTransferDialog() },
                            enabled = true,
                            hasMultipleLocations = uiState.locations.size > 1,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                    }

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
                        if (uiState.dayGroups.isNotEmpty()) {
                            Text(
                                text = "${uiState.dayGroups.size} днів",
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
                            items = uiState.dayGroups,
                            key = { it.date.toString() }
                        ) { dayGroup ->
                            ExpandableDayCard(
                                dayGroup = dayGroup,
                                isExpanded = dayGroup.date in uiState.expandedDays,
                                showLocationName = uiState.isTotalsView,
                                onToggle = { viewModel.toggleDayExpansion(dayGroup.date) },
                                onEditItem = { item -> viewModel.showEditDialog(item) },
                                canModifyItem = { item -> viewModel.canModifyItem(item) },
                                modifier = Modifier.animateItemPlacement()
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

                        if (uiState.dayGroups.isEmpty() && !uiState.isLoading) {
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
                        if (!uiState.hasMoreItems && uiState.dayGroups.isNotEmpty()) {
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
        CashDialogType.TRANSFER -> TransferDialog(
            amount = uiState.dialogAmount,
            notes = uiState.dialogNotes,
            destinations = uiState.transferDestinations,
            selectedDestinationId = uiState.dialogTransferDestinationId,
            balance = uiState.balance,
            onAmountChange = viewModel::onAmountChange,
            onNotesChange = viewModel::onNotesChange,
            onDestinationSelect = viewModel::onTransferDestinationSelect,
            onConfirm = viewModel::confirmTransfer,
            onDismiss = viewModel::dismissDialog,
            canConfirm = uiState.canConfirmTransfer,
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
        CashDialogType.EDIT_DEPOSIT -> EditDepositDialog(
            amount = uiState.dialogAmount,
            notes = uiState.dialogNotes,
            onAmountChange = viewModel::onAmountChange,
            onNotesChange = viewModel::onNotesChange,
            onConfirm = viewModel::confirmEdit,
            onDismiss = viewModel::dismissDialog,
            canConfirm = uiState.canConfirmDeposit,
            isSaving = uiState.isSaving
        )
        CashDialogType.EDIT_WITHDRAW -> EditWithdrawDialog(
            amount = uiState.dialogAmount,
            notes = uiState.dialogNotes,
            onAmountChange = viewModel::onAmountChange,
            onNotesChange = viewModel::onNotesChange,
            onConfirm = viewModel::confirmEdit,
            onDismiss = viewModel::dismissDialog,
            canConfirm = uiState.dialogAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true,
            isSaving = uiState.isSaving
        )
        CashDialogType.EDIT_PAYMENT -> EditPaymentDialog(
            amount = uiState.dialogAmount,
            notes = uiState.dialogNotes,
            categories = uiState.categories,
            selectedCategoryId = uiState.dialogCategoryId,
            onAmountChange = viewModel::onAmountChange,
            onNotesChange = viewModel::onNotesChange,
            onCategorySelect = viewModel::onCategorySelect,
            onConfirm = viewModel::confirmEdit,
            onDismiss = viewModel::dismissDialog,
            canConfirm = uiState.dialogAmount.toBigDecimalOrNull()?.let { it > BigDecimal.ZERO } == true,
            isSaving = uiState.isSaving
        )
        CashDialogType.NONE -> { /* No dialog */ }
    }
}

@Composable
private fun BalanceCard(
    balance: BigDecimal,
    dailyChange: BigDecimal,
    dailyAddition: BigDecimal = BigDecimal.ZERO,
    selectedLocationId: UUID? = null,
    modifier: Modifier = Modifier
) {
    // Use Animatable to control animation manually on tab switch
    val animatedBalance = remember { Animatable(0f) }
    
    // Animate to new balance when it changes OR when location changes
    LaunchedEffect(balance, selectedLocationId) {
        // Animate from current value to new balance
        animatedBalance.animateTo(
            targetValue = balance.toFloat(),
            animationSpec = spring(stiffness = 300f)
        )
    }
    
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
                text = "${BigDecimal(animatedBalance.value.toDouble()).setScale(0, java.math.RoundingMode.HALF_UP)} ₴",
                style = MaterialTheme.typography.displayMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Don't show red if there were deposits (additions) today -
                // negative change due to transfers shouldn't look alarming
                val changeColor = when {
                    dailyChange > BigDecimal.ZERO -> CashPositive
                    dailyChange < BigDecimal.ZERO && dailyAddition > BigDecimal.ZERO -> 
                        MaterialTheme.colorScheme.onPrimaryContainer
                    dailyChange < BigDecimal.ZERO -> CashNegative
                    else -> MaterialTheme.colorScheme.onPrimaryContainer
                }
                val changePrefix = if (dailyChange > BigDecimal.ZERO) "+" else ""
                Text(
                    text = stringResource(R.string.cash_today, "$changePrefix${dailyChange.setScale(0, java.math.RoundingMode.HALF_UP)} ₴"),
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
    onTransfer: () -> Unit,
    enabled: Boolean = true,
    hasMultipleLocations: Boolean = false,
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
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = onPayment,
                modifier = Modifier.weight(1f),
                enabled = enabled
            ) {
                Icon(Icons.Filled.Payment, contentDescription = "Витрати", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.cash_payment_action))
            }
            Button(
                onClick = onTransfer,
                modifier = Modifier.weight(1f),
                enabled = enabled && hasMultipleLocations,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CashTransfer
                )
            ) {
                Icon(Icons.Filled.SwapHoriz, contentDescription = "Переказ", modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(4.dp))
                Text(stringResource(R.string.cash_transfer_action))
            }
        }
    }
}

/**
 * Inline categories panel for tablet layout.
 * Displays expense categories with add/delete functionality.
 */
@Composable
private fun CategoriesPanel(
    categories: List<ExpenseCategory>,
    newCategoryName: String,
    onNewCategoryNameChange: (String) -> Unit,
    onAddCategory: () -> Unit,
    onDeactivateCategory: (UUID) -> Unit,
    modifier: Modifier = Modifier
) {
    var pendingDeactivateCategoryId by remember { mutableStateOf<UUID?>(null) }
    var pendingDeactivateCategoryName by remember { mutableStateOf<String?>(null) }

    // Deactivate confirmation dialog
    if (pendingDeactivateCategoryId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeactivateCategoryId = null; pendingDeactivateCategoryName = null },
            title = { Text("Видалити категорію?") },
            text = { Text("Категорію \"${pendingDeactivateCategoryName}\" буде видалено. Ви впевнені?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeactivateCategoryId?.let { onDeactivateCategory(it) }
                        pendingDeactivateCategoryId = null
                        pendingDeactivateCategoryName = null
                    }
                ) {
                    Text("Видалити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeactivateCategoryId = null; pendingDeactivateCategoryName = null }) {
                    Text("Скасувати")
                }
            }
        )
    }

    Card(
        modifier = modifier,
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = stringResource(R.string.cash_categories_title),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.primary
            )

            // Add new category row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = newCategoryName,
                    onValueChange = onNewCategoryNameChange,
                    label = { Text(stringResource(R.string.cash_new_category_label)) },
                    singleLine = true,
                    modifier = Modifier.weight(1f),
                    textStyle = MaterialTheme.typography.bodyMedium
                )
                IconButton(
                    onClick = onAddCategory,
                    enabled = newCategoryName.isNotBlank()
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = stringResource(R.string.save),
                        tint = if (newCategoryName.isNotBlank())
                            MaterialTheme.colorScheme.primary
                        else
                            MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Categories list
            if (categories.isEmpty()) {
                Text(
                    text = stringResource(R.string.cash_no_categories),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    categories.forEach { category ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = category.name,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = {
                                    pendingDeactivateCategoryId = category.id
                                    pendingDeactivateCategoryName = category.name
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(
                                    Icons.Filled.Delete,
                                    contentDescription = stringResource(R.string.delete),
                                    tint = MaterialTheme.colorScheme.error,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun HistoryItem(
    item: CashHistoryItem,
    showLocationName: Boolean = false,
    canModify: Boolean = false,
    onEdit: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }

    val depositLabel = stringResource(R.string.cash_history_deposit)
    val withdrawalLabel = stringResource(R.string.cash_history_withdrawal)
    val paymentLabel = stringResource(R.string.cash_history_payment)
    val purchaseLabel = stringResource(R.string.cash_history_purchase)
    val saleLabel = stringResource(R.string.cash_history_sale)
    val transferLabel = "Переказ"
    val editLabel = stringResource(R.string.edit)
    
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
            Icons.Filled.ShoppingCart,
            CashInfo,
            purchaseLabel
        )
        CashHistoryItemType.SALE -> Triple(
            Icons.Filled.Add,
            CashPositive,
            saleLabel
        )
        CashHistoryItemType.TRANSFER -> Triple(
            Icons.Filled.SwapHoriz,
            CashTransfer,
            transferLabel
        )
    }

    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM") }

    Box(modifier = modifier) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { },
                    onLongClick = { if (canModify) showContextMenu = true }
                ),
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
                        "+${item.amount.setScale(0, java.math.RoundingMode.HALF_UP)}"
                    } else {
                        "-${item.amount.setScale(0, java.math.RoundingMode.HALF_UP)}"
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

        // Context menu for edit
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false },
            offset = DpOffset(16.dp, 0.dp)
        ) {
            DropdownMenuItem(
                text = { Text(editLabel) },
                onClick = {
                    showContextMenu = false
                    onEdit()
                },
                leadingIcon = {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                }
            )
        }
    }
}

/**
 * Expandable day card showing operations grouped by day.
 */
@Composable
private fun ExpandableDayCard(
    dayGroup: DayCashGroup,
    isExpanded: Boolean,
    showLocationName: Boolean,
    onToggle: () -> Unit,
    onEditItem: (CashHistoryItem) -> Unit,
    canModifyItem: (CashHistoryItem) -> Boolean,
    modifier: Modifier = Modifier
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("dd.MM.yyyy (EEEE)") }
    val rotationAngle by animateFloatAsState(
        targetValue = if (isExpanded) 180f else 0f,
        label = "expand_rotation"
    )
    
    val dayTotalColor = when {
        dayGroup.dayTotal > BigDecimal.ZERO -> CashPositive
        dayGroup.dayTotal < BigDecimal.ZERO -> CashNegative
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }
    
    Card(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
        )
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Header - always visible
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggle() }
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = dayGroup.date.format(dateFormatter),
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "${dayGroup.items.size} операцій",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val prefix = if (dayGroup.dayTotal >= BigDecimal.ZERO) "+" else ""
                    Text(
                        text = "$prefix${dayGroup.dayTotal.setScale(0, java.math.RoundingMode.HALF_UP)} ₴",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = dayTotalColor
                    )
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
            
            // Expanded content - operation sections
            if (isExpanded) {
                HorizontalDivider()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    dayGroup.activeTypes.forEach { type ->
                        val items = dayGroup.itemsByType[type] ?: return@forEach
                        OperationTypeSection(
                            type = type,
                            items = items,
                            showLocationName = showLocationName,
                            onEditItem = onEditItem,
                            canModifyItem = canModifyItem
                        )
                    }
                }
            }
        }
    }
}

/**
 * Section for a specific operation type within a day.
 */
@Composable
private fun OperationTypeSection(
    type: CashHistoryItemType,
    items: List<CashHistoryItem>,
    showLocationName: Boolean,
    onEditItem: (CashHistoryItem) -> Unit,
    canModifyItem: (CashHistoryItem) -> Boolean,
    modifier: Modifier = Modifier
) {
    val (icon, color) = getTypeIconAndColor(type)
    val sectionTotal = items.sumOf { it.signedAmount }
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = color.copy(alpha = 0.1f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            // Section header
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = type.displayName(),
                        tint = color,
                        modifier = Modifier.size(20.dp)
                    )
                    Text(
                        text = type.displayName(),
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Medium,
                        color = color
                    )
                }
                val prefix = if (sectionTotal >= BigDecimal.ZERO) "+" else ""
                Text(
                    text = "$prefix${sectionTotal.setScale(0, java.math.RoundingMode.HALF_UP)} ₴",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = color
                )
            }
            
            // Individual items
            items.forEach { item ->
                CompactHistoryItem(
                    item = item,
                    showLocationName = showLocationName,
                    canModify = canModifyItem(item),
                    onEdit = { onEditItem(item) }
                )
            }
        }
    }
}

/**
 * Compact version of history item for display inside expanded day card.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CompactHistoryItem(
    item: CashHistoryItem,
    showLocationName: Boolean,
    canModify: Boolean,
    onEdit: () -> Unit,
    modifier: Modifier = Modifier
) {
    var showContextMenu by remember { mutableStateOf(false) }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    
    Box(modifier = modifier) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(
                    onClick = { },
                    onLongClick = { if (canModify) showContextMenu = true }
                )
                .padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                val detailText = when {
                    item.categoryName != null -> item.categoryName
                    item.batchCount != null && item.batchCount > 1 -> {
                        val clientWord = pluralizeUkrainian(item.batchCount, "клієнт", "клієнти", "клієнтів")
                        "${item.batchCount} $clientWord"
                    }
                    !item.notes.isNullOrBlank() -> item.notes
                    else -> null
                }
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = timeFormatter.format(item.createdAt.atZone(java.time.ZoneId.systemDefault())),
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
                if (detailText != null) {
                    Text(
                        text = detailText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            val amountText = if (item.type.isInflow) "+${item.amount.setScale(0, java.math.RoundingMode.HALF_UP)}" else "-${item.amount.setScale(0, java.math.RoundingMode.HALF_UP)}"
            Text(
                text = "$amountText ₴",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium
            )
        }
        
        DropdownMenu(
            expanded = showContextMenu,
            onDismissRequest = { showContextMenu = false }
        ) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.edit)) },
                onClick = {
                    showContextMenu = false
                    onEdit()
                },
                leadingIcon = {
                    Icon(Icons.Filled.Edit, contentDescription = null)
                }
            )
        }
    }
}

/** Get icon and color for an operation type */
private fun getTypeIconAndColor(type: CashHistoryItemType): Pair<ImageVector, Color> {
    return when (type) {
        CashHistoryItemType.DEPOSIT -> Icons.Filled.ArrowDownward to CashPositive
        CashHistoryItemType.WITHDRAWAL -> Icons.Filled.ArrowUpward to CashNegative
        CashHistoryItemType.PAYMENT -> Icons.Filled.Payment to CashWarning
        CashHistoryItemType.PURCHASE -> Icons.Filled.ShoppingCart to CashInfo
        CashHistoryItemType.SALE -> Icons.Filled.Add to CashPositive
        CashHistoryItemType.TRANSFER -> Icons.Filled.SwapHoriz to CashTransfer
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
                    text = stringResource(R.string.cash_available, "${balance.setScale(0, java.math.RoundingMode.HALF_UP)} ₴"),
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
                    text = stringResource(R.string.cash_available, "${balance.setScale(0, java.math.RoundingMode.HALF_UP)} ₴"),
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TransferDialog(
    amount: String,
    notes: String,
    destinations: List<Location>,
    selectedDestinationId: UUID?,
    balance: BigDecimal,
    onAmountChange: (String) -> Unit,
    onNotesChange: (String) -> Unit,
    onDestinationSelect: (UUID?) -> Unit,
    onConfirm: () -> Unit,
    onDismiss: () -> Unit,
    canConfirm: Boolean,
    isSaving: Boolean
) {
    var expanded by remember { mutableStateOf(false) }
    val selectedDestination = destinations.find { it.id == selectedDestinationId }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.cash_transfer_dialog_title)) },
        text = {
            Column {
                Text(
                    text = stringResource(R.string.cash_available, "${balance.setScale(0, java.math.RoundingMode.HALF_UP)} ₴"),
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

                val selectLocationLabel = stringResource(R.string.cash_transfer_select_location)
                ExposedDropdownMenuBox(
                    expanded = expanded,
                    onExpandedChange = { expanded = !expanded }
                ) {
                    OutlinedTextField(
                        value = selectedDestination?.name ?: selectLocationLabel,
                        onValueChange = {},
                        readOnly = true,
                        label = { Text(stringResource(R.string.cash_transfer_destination)) },
                        trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .menuAnchor()
                    )
                    ExposedDropdownMenu(
                        expanded = expanded,
                        onDismissRequest = { expanded = false }
                    ) {
                        destinations.forEach { location ->
                            DropdownMenuItem(
                                text = { Text(location.name) },
                                onClick = {
                                    onDestinationSelect(location.id)
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
                enabled = canConfirm && !isSaving,
                colors = ButtonDefaults.buttonColors(
                    containerColor = CashTransfer
                )
            ) {
                if (isSaving) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp
                    )
                } else {
                    Text(stringResource(R.string.cash_transfer_action))
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
    var pendingDeactivateCategoryId by remember { mutableStateOf<UUID?>(null) }
    var pendingDeactivateCategoryName by remember { mutableStateOf<String?>(null) }

    // Deactivate confirmation dialog
    if (pendingDeactivateCategoryId != null) {
        AlertDialog(
            onDismissRequest = { pendingDeactivateCategoryId = null; pendingDeactivateCategoryName = null },
            title = { Text("Видалити категорію?") },
            text = { Text("Категорію \"${pendingDeactivateCategoryName}\" буде видалено. Ви впевнені?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        pendingDeactivateCategoryId?.let { onDeactivateCategory(it) }
                        pendingDeactivateCategoryId = null
                        pendingDeactivateCategoryName = null
                    }
                ) {
                    Text("Видалити", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeactivateCategoryId = null; pendingDeactivateCategoryName = null }) {
                    Text("Скасувати")
                }
            }
        )
    }

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
                                onClick = { 
                                    pendingDeactivateCategoryId = category.id
                                    pendingDeactivateCategoryName = category.name
                                }
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

@Composable
private fun EditDepositDialog(
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
        title = { Text(stringResource(R.string.cash_edit_deposit_title)) },
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
                    Text(stringResource(R.string.save))
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
private fun EditWithdrawDialog(
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
        title = { Text(stringResource(R.string.cash_edit_withdraw_title)) },
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
                    Text(stringResource(R.string.save))
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
private fun EditPaymentDialog(
    amount: String,
    notes: String,
    categories: List<ExpenseCategory>,
    selectedCategoryId: UUID?,
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
        title = { Text(stringResource(R.string.cash_edit_payment_title)) },
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
                    Text(stringResource(R.string.save))
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
