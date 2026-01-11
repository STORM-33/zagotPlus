package com.zagot.zagotplus.ui.screens.purchase

import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.zagot.zagotplus.domain.model.Product
import java.math.BigDecimal

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PurchaseEntryScreen(
    onNavigateBack: () -> Unit,
    onNavigateToSummary: () -> Unit,
    viewModel: PurchaseEntryViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    // Handle navigation
    LaunchedEffect(uiState.navigateBack) {
        if (uiState.navigateBack) {
            viewModel.onNavigationHandled()
            onNavigateBack()
        }
    }

    LaunchedEffect(uiState.navigateToSummary) {
        if (uiState.navigateToSummary) {
            viewModel.onNavigationHandled()
            onNavigateToSummary()
        }
    }

    // Show error
    LaunchedEffect(uiState.error) {
        uiState.error?.let { error ->
            snackbarHostState.showSnackbar(error)
            viewModel.dismissError()
        }
    }

    val topBarTitle = when (uiState.screenState) {
        PurchaseEntryScreenState.PRODUCT_GRID -> "Оберіть товар"
        PurchaseEntryScreenState.WEIGHT_ENTRY -> uiState.selectedProduct?.name ?: "Введіть дані"
        PurchaseEntryScreenState.POSITIONS_LIST -> "Позиції (${uiState.positions.size})"
    }

    val showBackToGrid = uiState.screenState != PurchaseEntryScreenState.PRODUCT_GRID

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(topBarTitle) },
                navigationIcon = {
                    IconButton(
                        onClick = {
                            if (showBackToGrid) {
                                viewModel.backToGrid()
                            } else {
                                viewModel.cancel()
                            }
                        }
                    ) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Назад")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            when {
                uiState.isLoading -> {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center)
                    )
                }
                uiState.isSaving -> {
                    Column(
                        modifier = Modifier.align(Alignment.Center),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        CircularProgressIndicator()
                        Spacer(modifier = Modifier.height(16.dp))
                        Text("Збереження...")
                    }
                }
                else -> {
                    when (uiState.screenState) {
                        PurchaseEntryScreenState.PRODUCT_GRID -> {
                            ProductGrid(
                                products = uiState.products,
                                onProductClick = viewModel::selectProduct,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        PurchaseEntryScreenState.WEIGHT_ENTRY -> {
                            WeightEntry(
                                product = uiState.selectedProduct,
                                weight = uiState.currentWeight,
                                price = uiState.currentPrice,
                                total = uiState.currentTotal,
                                scaleWeight = uiState.scaleWeight,
                                canAdd = uiState.canAddPosition,
                                onWeightChange = viewModel::onWeightChange,
                                onPriceChange = viewModel::onPriceChange,
                                onAddPosition = viewModel::addPosition,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                        PurchaseEntryScreenState.POSITIONS_LIST -> {
                            PositionsList(
                                positions = uiState.positions,
                                notes = uiState.notes,
                                totalWeight = uiState.totalWeight,
                                totalAmount = uiState.totalAmount,
                                canFinalize = uiState.canFinalize,
                                onNotesChange = viewModel::onNotesChange,
                                onRemovePosition = viewModel::removePosition,
                                onAddAnother = viewModel::addAnotherProduct,
                                onFinalize = viewModel::finalize,
                                onCancel = viewModel::cancel,
                                modifier = Modifier.fillMaxSize()
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ProductGrid(
    products: List<Product>,
    onProductClick: (Product) -> Unit,
    modifier: Modifier = Modifier
) {
    if (products.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = "Немає активних товарів",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 120.dp),
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            modifier = modifier
        ) {
            items(products, key = { it.id }) { product ->
                ProductTile(
                    product = product,
                    onClick = { onProductClick(product) }
                )
            }
        }
    }
}

@Composable
private fun ProductTile(
    product: Product,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Product image or placeholder
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(MaterialTheme.colorScheme.surface),
                contentAlignment = Alignment.Center
            ) {
                if (product.imageUri != null) {
                    AsyncImage(
                        model = product.imageUri,
                        contentDescription = product.name,
                        modifier = Modifier.fillMaxSize(),
                        contentScale = ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Filled.Image,
                        contentDescription = null,
                        modifier = Modifier.size(40.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(8.dp))
            
            // Product name
            Text(
                text = product.name,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Default price
            product.defaultBuyPrice?.let { price ->
                Text(
                    text = "₴${price.toPlainString()}/кг",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun WeightEntry(
    product: Product?,
    weight: String,
    price: String,
    total: BigDecimal?,
    scaleWeight: BigDecimal?,
    canAdd: Boolean,
    onWeightChange: (String) -> Unit,
    onPriceChange: (String) -> Unit,
    onAddPosition: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier.padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // Weight from scales (placeholder)
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer
            )
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "Вага",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = scaleWeight?.let { "${it.toPlainString()} кг" } ?: "-- кг",
                    style = MaterialTheme.typography.displayMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onPrimaryContainer
                )
                Text(
                    text = "(ваги не підключено)",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }

        Spacer(modifier = Modifier.height(24.dp))

        // Manual weight input
        OutlinedTextField(
            value = weight,
            onValueChange = onWeightChange,
            label = { Text("Вага (кг)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Price input
        OutlinedTextField(
            value = price,
            onValueChange = onPriceChange,
            label = { Text("Ціна за кг (₴)") },
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(modifier = Modifier.height(24.dp))

        // Calculated total
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Сума:",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = total?.let { "₴${it.toPlainString()}" } ?: "₴0.00",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }

        Spacer(modifier = Modifier.weight(1f))

        // Add position button
        Button(
            onClick = onAddPosition,
            enabled = canAdd,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Додати позицію")
        }
    }
}

@Composable
private fun PositionsList(
    positions: List<PurchasePosition>,
    notes: String,
    totalWeight: BigDecimal,
    totalAmount: BigDecimal,
    canFinalize: Boolean,
    onNotesChange: (String) -> Unit,
    onRemovePosition: (String) -> Unit,
    onAddAnother: () -> Unit,
    onFinalize: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(modifier = modifier) {
        // Positions list
        LazyColumn(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(positions, key = { it.id }) { position ->
                PositionItem(
                    position = position,
                    onRemove = { onRemovePosition(position.id) }
                )
            }

            item {
                Spacer(modifier = Modifier.height(8.dp))
                
                // Add another product button
                OutlinedButton(
                    onClick = onAddAnother,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Додати ще товар")
                }
            }

            item {
                Spacer(modifier = Modifier.height(16.dp))
                
                // Notes field
                OutlinedTextField(
                    value = notes,
                    onValueChange = onNotesChange,
                    label = { Text("Примітки") },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2,
                    maxLines = 4
                )
            }
        }

        // Footer with totals and actions
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(MaterialTheme.colorScheme.surface)
                .padding(16.dp)
        ) {
            Divider()
            Spacer(modifier = Modifier.height(16.dp))
            
            // Totals
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "Всього:",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.Bold
                )
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${totalWeight.toPlainString()} кг",
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = "₴${totalAmount.toPlainString()}",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Action buttons
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                OutlinedButton(
                    onClick = onCancel,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    ),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error)
                ) {
                    Text("Скасувати")
                }
                Button(
                    onClick = onFinalize,
                    enabled = canFinalize,
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.primary
                    )
                ) {
                    Text("Розрахувати")
                }
            }
        }
    }
}

@Composable
private fun PositionItem(
    position: PurchasePosition,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier
) {
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
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = position.product.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = "${position.weightKg.toPlainString()} кг × ₴${position.pricePerKg.toPlainString()}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            
            Text(
                text = "₴${position.totalAmount.toPlainString()}",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            
            IconButton(onClick = onRemove) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "Видалити",
                    tint = MaterialTheme.colorScheme.error
                )
            }
        }
    }
}
