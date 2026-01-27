package com.zagot.zagotplus.ui.screens.purchase

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import com.google.accompanist.swiperefresh.SwipeRefresh
import com.google.accompanist.swiperefresh.rememberSwipeRefreshState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zagot.zagotplus.domain.model.ProductDailyTotal
import com.zagot.zagotplus.ui.components.BatchCardSkeleton
import com.zagot.zagotplus.ui.components.EmptyState
import com.zagot.zagotplus.ui.components.EmptyStateIcons
import com.zagot.zagotplus.ui.components.SkeletonList
import com.zagot.zagotplus.ui.components.adaptiveHorizontalPadding
import com.zagot.zagotplus.ui.components.adaptiveItemSpacing
import com.zagot.zagotplus.ui.components.isTablet
import com.zagot.zagotplus.ui.components.roundBalanceForDisplay
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.UUID

@Composable
fun PurchaseScreen(
    modifier: Modifier = Modifier,
    viewModel: PurchaseViewModel = hiltViewModel(),
    onNavigateToNewClient: () -> Unit = {},
    isRestrictedMode: Boolean = false
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val currencyFormat = remember { DecimalFormat("#,##0") }
    val weightFormat = remember { DecimalFormat("#,##0.0") }
    
    // Selection state for products (mirroring Inventory screen behavior)
    var selectedProductIds by remember { mutableStateOf<Set<UUID>>(emptySet()) }

    LaunchedEffect(uiState.navigateToNewClient) {
        if (uiState.navigateToNewClient) {
            onNavigateToNewClient()
            viewModel.onNavigationHandled()
        }
    }

    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }
    
    // Calculate summary based on selection (if nothing selected, use all)
    val summaryTotals = remember(uiState.todaysProductTotals, selectedProductIds) {
        if (selectedProductIds.isEmpty()) {
            uiState.todaysProductTotals
        } else {
            uiState.todaysProductTotals.filter { it.productId in selectedProductIds }
        }
    }

    val swipeRefreshState = rememberSwipeRefreshState(uiState.isLoading)
    val horizontalPadding = adaptiveHorizontalPadding()
    val itemSpacing = adaptiveItemSpacing()
    
    Box(modifier = modifier.fillMaxSize()) {
        SwipeRefresh(
            state = swipeRefreshState,
            onRefresh = { viewModel.refresh() },
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = horizontalPadding, vertical = 16.dp)
            ) {
                // Top panels - horizontal row (spendings left, cash right)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (isTablet()) 16.dp else 12.dp)
            ) {
                // Daily spendings card (orange) - left
                DailySpendingsCard(
                    totalSpendings = uiState.todaysProductTotals.sumOf { it.totalAmount },
                    currencyFormat = currencyFormat,
                    modifier = Modifier.weight(1f)
                )
                
                // Cash balance card - right
                CashBalanceCard(
                    balance = uiState.cashBalance.roundBalanceForDisplay(),
                    currencyFormat = currencyFormat,
                    modifier = Modifier.weight(1f)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Section header
            Text(
                text = "Закупки сьогодні",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            // Product totals or empty state
            if (uiState.isLoading) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    SkeletonList(itemCount = 4) { BatchCardSkeleton() }
                }
            } else if (uiState.todaysProductTotals.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    contentAlignment = Alignment.Center
                ) {
                    EmptyState(
                        icon = EmptyStateIcons.Purchase,
                        title = "Закупок ще немає",
                        description = "Натисніть кнопку нижче, щоб почати нову закупку"
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(itemSpacing)
                ) {
                    items(
                        items = uiState.todaysProductTotals,
                        key = { it.productId }
                    ) { productTotal ->
                        val isSelected = productTotal.productId in selectedProductIds
                        ProductTotalItem(
                            productTotal = productTotal,
                            weightFormat = weightFormat,
                            currencyFormat = currencyFormat,
                            isSelected = isSelected,
                            onClick = {
                                selectedProductIds = if (isSelected) {
                                    selectedProductIds - productTotal.productId
                                } else {
                                    selectedProductIds + productTotal.productId
                                }
                            }
                        )
                    }
                    
                    // Summary row at end of list (uses selected items or all)
                    item(key = "summary") {
                        SummaryTotalItem(
                            totalWeight = summaryTotals.sumOf { it.totalWeightKg },
                            plannedProfit = summaryTotals
                                .mapNotNull { it.plannedProfit }
                                .takeIf { it.isNotEmpty() }
                                ?.reduce { acc, profit -> acc + profit },
                            weightFormat = weightFormat,
                            currencyFormat = currencyFormat,
                            isRestrictedMode = isRestrictedMode,
                            hasSelection = selectedProductIds.isNotEmpty()
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // New client button - LARGER touch target
            Button(
                onClick = { viewModel.onNewClientClick() },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp)
            ) {
                Text(
                    text = "НОВИЙ КЛІЄНТ",
                    style = MaterialTheme.typography.titleMedium
                )
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
private fun DailySpendingsCard(
    totalSpendings: BigDecimal,
    currencyFormat: DecimalFormat,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.tertiary.copy(alpha = 0.5f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Витрати за день",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "₴${currencyFormat.format(totalSpendings)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.tertiary
            )
        }
    }
}

@Composable
private fun CashBalanceCard(
    balance: BigDecimal,
    currencyFormat: DecimalFormat,
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                text = "Залишок по касі",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = "₴${currencyFormat.format(balance)}",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
        }
    }
}

@Composable
private fun SummaryTotalItem(
    totalWeight: BigDecimal,
    plannedProfit: BigDecimal?,
    weightFormat: DecimalFormat,
    currencyFormat: DecimalFormat,
    isRestrictedMode: Boolean = false,
    hasSelection: Boolean = false,
    modifier: Modifier = Modifier
) {
    val title = if (hasSelection) "Підсумок (вибрані)" else "Всього за день"
    
    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        ),
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.primary.copy(alpha = 0.3f)),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = "${weightFormat.format(totalWeight)} кг",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            // Planned profit row - hide in restricted mode
            if (!isRestrictedMode) {
                Text(
                    text = plannedProfit?.let { "Плановий прибуток: ₴${currencyFormat.format(it)}" }
                        ?: "Плановий прибуток: —",
                    style = MaterialTheme.typography.bodySmall,
                    fontWeight = FontWeight.Medium,
                    textAlign = TextAlign.Center,
                    color = if (plannedProfit != null && plannedProfit > BigDecimal.ZERO)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun ProductTotalItem(
    productTotal: ProductDailyTotal,
    weightFormat: DecimalFormat,
    currencyFormat: DecimalFormat,
    isSelected: Boolean = false,
    onClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { onClick() },
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        border = if (isSelected) BorderStroke(2.dp, MaterialTheme.colorScheme.primary)
                 else BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant),
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
                    .size(48.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surfaceContainerHighest),
                contentAlignment = Alignment.Center
            ) {
                if (productTotal.imageUri != null) {
                    AsyncImage(
                        model = productTotal.imageUri,
                        contentDescription = productTotal.productName,
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
            Column(
                modifier = Modifier.weight(1f)
            ) {
                Text(
                    text = productTotal.productName,
                    style = MaterialTheme.typography.bodyLarge,
                    fontWeight = FontWeight.Medium
                )
                // Average price per kg - shown below if available
                productTotal.avgPricePerKg?.let { avgPrice ->
                    Text(
                        text = "Сер. ціна: ₴${currencyFormat.format(avgPrice)}/кг",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                    )
                }
            }
            
            // Amount and weight
            Column(
                horizontalAlignment = Alignment.End
            ) {
                // Kilograms - visually highlighted (larger, bold, primary color)
                Text(
                    text = "${weightFormat.format(productTotal.totalWeightKg)} кг",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                // Total amount - secondary display
                Text(
                    text = "₴${currencyFormat.format(productTotal.totalAmount)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
