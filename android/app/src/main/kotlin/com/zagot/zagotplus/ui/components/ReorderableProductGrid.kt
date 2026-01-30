package com.zagot.zagotplus.ui.components

import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    inventoryMap: Map<UUID, BigDecimal>? = null,
    selectedProductId: UUID? = null
) {
    // Mutable copy of product order (IDs only)
    // Use products list identity to preserve incoming order from ViewModel
    var orderedProductIds by remember(products) { 
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
                        isSelected = product.id == selectedProductId,
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
    isSelected: Boolean = false,
    modifier: Modifier = Modifier
) {
    val decimalFormat = remember { DecimalFormat("#,##0.0") }
    val isLowStock = availableKg != null && availableKg <= BigDecimal.ZERO
    
    // Selection border color (green) for product grid
    val selectionBorderColor = Color(0xFF4CAF50)
    
    // Animate selection: scale up and elevate shadow
    val scale by animateFloatAsState(
        targetValue = if (isSelected) 1.05f else 1f,
        animationSpec = tween(200),
        label = "productTileScale"
    )
    val shadowElevation by animateDpAsState(
        targetValue = if (isSelected) 8.dp else 2.dp,
        animationSpec = tween(200),
        label = "productTileShadow"
    )
    
    Card(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .shadow(shadowElevation, RoundedCornerShape(12.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isLowStock) 
                MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.3f)
            else 
                MaterialTheme.colorScheme.surfaceVariant
        ),
        border = if (isSelected) BorderStroke(3.dp, selectionBorderColor) else null
    ) {
        // Full-tile image with text overlay
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .aspectRatio(1.5f)
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
                    contentDescription = "Фото товару",
                    modifier = Modifier.size(36.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            // Text overlay on image - includes name and price/stock
            Box(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.65f))
                    .padding(horizontal = 6.dp, vertical = 4.dp)
            ) {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = product.name,
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.fillMaxWidth()
                    )
                    // Price or inventory in overlay
                    when {
                        availableKg != null -> {
                            Text(
                                text = "${decimalFormat.format(availableKg)} кг",
                                style = MaterialTheme.typography.labelSmall,
                                fontSize = 10.sp,
                                color = if (isLowStock) MaterialTheme.colorScheme.error else Color.White.copy(alpha = 0.9f)
                            )
                        }
                        showPrice -> {
                            val price = when (priceType) {
                                PriceType.BUY -> product.defaultBuyPrice
                                PriceType.SELL -> product.defaultSellPrice
                            }
                            price?.let {
                                Text(
                                    text = "₴${it.toPlainString()}/кг",
                                    style = MaterialTheme.typography.labelSmall,
                                    fontSize = 10.sp,
                                    color = Color.White.copy(alpha = 0.9f)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
