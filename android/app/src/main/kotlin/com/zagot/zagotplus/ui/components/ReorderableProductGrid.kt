package com.zagot.zagotplus.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.zagot.zagotplus.domain.model.Product
import sh.calvin.reorderable.ReorderableItem
import sh.calvin.reorderable.rememberReorderableLazyGridState
import java.math.BigDecimal
import java.text.DecimalFormat
import java.util.UUID

/**
 * A product grid that supports drag-and-drop reordering.
 * Long press on a product to start dragging.
 */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun ReorderableProductGrid(
    products: List<Product>,
    onProductClick: (Product) -> Unit,
    onOrderChanged: (List<UUID>) -> Unit,
    modifier: Modifier = Modifier,
    showPrice: Boolean = true,
    priceType: PriceType = PriceType.BUY,
    inventoryMap: Map<UUID, BigDecimal>? = null
) {
    // Mutable copy of product order (IDs only)
    var orderedProductIds by remember(products.map { it.id }) { 
        mutableStateOf(products.map { it.id }) 
    }
    
    // Build ordered product list from IDs
    val orderedProducts = remember(orderedProductIds, products) {
        val productMap = products.associateBy { it.id }
        orderedProductIds.mapNotNull { productMap[it] }
    }

    if (orderedProducts.isEmpty()) {
        Box(
            modifier = modifier,
            contentAlignment = Alignment.Center
        ) {
            EmptyState(
                icon = EmptyStateIcons.Products,
                title = "Немає активних товарів",
                description = "Додайте товари в налаштуваннях"
            )
        }
    } else {
        val lazyGridState = rememberLazyGridState()
        val reorderableLazyGridState = rememberReorderableLazyGridState(lazyGridState) { from, to ->
            val mutableIds = orderedProductIds.toMutableList()
            val item = mutableIds.removeAt(from.index)
            mutableIds.add(to.index, item)
            orderedProductIds = mutableIds
        }

        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 140.dp),
            modifier = modifier,
            state = lazyGridState,
            contentPadding = PaddingValues(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            items(orderedProducts, key = { it.id }) { product ->
                ReorderableItem(reorderableLazyGridState, key = product.id) { isDragging ->
                    val elevation = if (isDragging) 8.dp else 2.dp
                    
                    ProductTile(
                        product = product,
                        onClick = { onProductClick(product) },
                        showPrice = showPrice,
                        priceType = priceType,
                        availableKg = inventoryMap?.get(product.id),
                        modifier = Modifier
                            .shadow(elevation, RoundedCornerShape(16.dp))
                            .longPressDraggableHandle(
                                onDragStopped = {
                                    onOrderChanged(orderedProductIds)
                                }
                            )
                    )
                }
            }
        }
    }
}

enum class PriceType {
    BUY, SELL
}

@Composable
private fun ProductTile(
    product: Product,
    onClick: () -> Unit,
    showPrice: Boolean,
    priceType: PriceType,
    availableKg: BigDecimal?,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.0") }
    val isLowStock = availableKg != null && availableKg <= BigDecimal.ZERO
    
    Card(
        modifier = modifier.clickable(onClick = onClick),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLowStock) 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 12.dp, end = 12.dp, top = 12.dp, bottom = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Product image or placeholder
            Box(
                modifier = Modifier
                    .size(100.dp)
                    .clip(RoundedCornerShape(12.dp))
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
                        modifier = Modifier.size(48.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            
            Spacer(modifier = Modifier.height(12.dp))
            
            // Product name
            Text(
                text = product.name,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            
            // Price or inventory based on context
            when {
                availableKg != null -> {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "${decimalFormat.format(availableKg)} кг",
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (isLowStock) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                    )
                }
                showPrice -> {
                    val price = when (priceType) {
                        PriceType.BUY -> product.defaultBuyPrice
                        PriceType.SELL -> product.defaultSellPrice
                    }
                    price?.let {
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "₴${it.toPlainString()}/кг",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }
        }
    }
}
