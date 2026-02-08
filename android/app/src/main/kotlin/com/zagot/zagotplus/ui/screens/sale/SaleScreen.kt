package com.zagot.zagotplus.ui.screens.sale

import androidx.compose.foundation.ExperimentalFoundationApi
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
import androidx.compose.material3.Button
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import com.zagot.zagotplus.domain.model.SaleBatch
import com.zagot.zagotplus.ui.components.AnimatedCounter
import com.zagot.zagotplus.ui.components.AnimatedListItem
import com.zagot.zagotplus.ui.components.BatchCardSkeleton
import com.zagot.zagotplus.ui.components.EmptyState
import com.zagot.zagotplus.ui.components.EmptyStateIcons
import com.zagot.zagotplus.ui.components.SkeletonList
import com.zagot.zagotplus.ui.navigation.SaleMode
import java.text.DecimalFormat
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import kotlin.math.abs

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SaleScreen(
    modifier: Modifier = Modifier,
    viewModel: SaleViewModel = hiltViewModel(),
    onNavigateToNewSale: (SaleMode) -> Unit = {},
    isRestrictedMode: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    LaunchedEffect(uiState.navigateToNewSale, uiState.selectedSaleMode) {
        val mode = uiState.selectedSaleMode
        if (uiState.navigateToNewSale && mode != null) {
            onNavigateToNewSale(mode)
            viewModel.onNavigationHandled()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    val swipeRefreshState = rememberSwipeRefreshState(uiState.isLoading)

    Box(modifier = modifier.fillMaxSize()) {
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(16.dp)
            ) {
                // Section header
            Text(
                text = "Продажі сьогодні",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Batches list or empty state
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    SkeletonList(itemCount = 4) { BatchCardSkeleton() }
                }
            } else if (uiState.todaysBatches.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = EmptyStateIcons.Sale,
                        title = "Продажів ще немає",
                        description = "Натисніть кнопку нижче, щоб почати новий продаж"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        items = uiState.todaysBatches,
                        key = { it.id }
                    ) { batch ->
                        AnimatedListItem {
                            SaleBatchItem(
                                batch = batch,
                            )
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Mode selection - different UI for restricted vs full mode
            if (isRestrictedMode) {
                // RESTRICTED MODE: Single button for regular sale only
                Button(
                    onClick = { viewModel.onNewSaleClick(SaleMode.REGULAR) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                ) {
                    Text(
                        text = "НОВИЙ ПРОДАЖ",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                }
            } else {
                // FULL MODE: Two buttons horizontal - regular (prominent) and wholesale (smaller)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Regular mode button - PRIMARY (prominent, larger)
                    Button(
                        onClick = { viewModel.onNewSaleClick(SaleMode.REGULAR) },
                        modifier = Modifier
                            .weight(1.5f)
                            .height(56.dp)
                    ) {
                        Text(
                            text = "НОВИЙ ПРОДАЖ",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Wholesale mode button - SECONDARY (less prominent, smaller)
                    Card(
                        modifier = Modifier
                            .weight(1f)
                            .height(56.dp),
                        colors = CardDefaults.cardColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        )
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable { viewModel.onNewSaleClick(SaleMode.WHOLESALE) }
                                .padding(8.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Оптовий",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Text(
                                text = "продаж",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                            )
                        }
                    }
                }
            }
        }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter)
        )
    }
}

@Composable
private fun SaleBatchItem(
    batch: SaleBatch,
    modifier: Modifier = Modifier
) {
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm") }
    val decimalFormat = remember { DecimalFormat("#,##0.0") }
    val currencyFormat = remember { DecimalFormat("#,##0") }

    val time = batch.createdAt
        .atZone(ZoneId.systemDefault())
        .format(timeFormatter)
    val positions = "${batch.itemCount ?: 0} поз"

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = time,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium
            )
            batch.totalWeightKg?.let { totalWeight ->
                AnimatedCounter(
                    targetValue = totalWeight.abs(),
                    formatter = { "${decimalFormat.format(it)} кг" },
                    style = MaterialTheme.typography.bodyMedium
                )
            } ?: Text(
                text = "-- кг",
                style = MaterialTheme.typography.bodyMedium
            )
            batch.totalAmount?.let { totalAmount ->
                AnimatedCounter(
                    targetValue = totalAmount,
                    formatter = { "₴${currencyFormat.format(it)}" },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary
                )
            } ?: Text(
                text = "₴--",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                text = positions,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}
