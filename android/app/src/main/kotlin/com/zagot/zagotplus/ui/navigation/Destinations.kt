package com.zagot.zagotplus.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalanceWallet
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sell
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.ui.graphics.vector.ImageVector

/**
 * Navigation destinations for the app.
 */
sealed class Destination(
    val route: String,
    val title: String,
    val icon: ImageVector
) {
    data object Purchase : Destination(
        route = "purchase",
        title = "Закупка",
        icon = Icons.Filled.ShoppingCart
    )
    
    data object PurchaseEntry : Destination(
        route = "purchase_entry",
        title = "Нова закупка",
        icon = Icons.Filled.ShoppingCart
    ) {
        const val ROUTE_WITH_ARGS = "purchase_entry?batchId={batchId}"
        const val ARG_BATCH_ID = "batchId"
        
        fun createRoute(batchId: String? = null): String {
            return if (batchId != null) {
                "purchase_entry?batchId=$batchId"
            } else {
                "purchase_entry"
            }
        }
    }
    
    data object Sale : Destination(
        route = "sale",
        title = "Продаж",
        icon = Icons.Filled.Sell
    )
    
    data object SaleEntry : Destination(
        route = "sale_entry",
        title = "Новий продаж",
        icon = Icons.Filled.Sell
    ) {
        const val ROUTE_WITH_ARGS = "sale_entry?batchId={batchId}"
        const val ARG_BATCH_ID = "batchId"
        
        fun createRoute(batchId: String? = null): String {
            return if (batchId != null) {
                "sale_entry?batchId=$batchId"
            } else {
                "sale_entry"
            }
        }
    }
    
    data object Inventory : Destination(
        route = "inventory",
        title = "Залишки",
        icon = Icons.Filled.Inventory
    )
    
    data object History : Destination(
        route = "history",
        title = "Історія",
        icon = Icons.Filled.History
    )
    
    data object Cash : Destination(
        route = "cash",
        title = "Каса",
        icon = Icons.Filled.AccountBalanceWallet
    )
    
    data object Products : Destination(
        route = "products",
        title = "Товари",
        icon = Icons.Filled.Category
    )
    
    data object Reports : Destination(
        route = "reports",
        title = "Звіти",
        icon = Icons.Filled.Assessment
    )
    
    data object Settings : Destination(
        route = "settings",
        title = "Налаштування",
        icon = Icons.Filled.Settings
    )
    
    data object Transfer : Destination(
        route = "transfer",
        title = "Переміщення",
        icon = Icons.Filled.SwapHoriz
    ) {
        const val ROUTE_WITH_ARGS = "transfer?productId={productId}&sourceLocationId={sourceLocationId}&destinationLocationId={destinationLocationId}"
        const val ARG_PRODUCT_ID = "productId"
        const val ARG_SOURCE_LOCATION_ID = "sourceLocationId"
        const val ARG_DESTINATION_LOCATION_ID = "destinationLocationId"
        
        fun createRoute(
            productId: String? = null, 
            sourceLocationId: String? = null,
            destinationLocationId: String? = null
        ): String {
            return buildString {
                append("transfer")
                val params = mutableListOf<String>()
                productId?.let { params.add("productId=$it") }
                sourceLocationId?.let { params.add("sourceLocationId=$it") }
                destinationLocationId?.let { params.add("destinationLocationId=$it") }
                if (params.isNotEmpty()) {
                    append("?")
                    append(params.joinToString("&"))
                }
            }
        }
    }
    
    companion object {
        val bottomNavItems = listOf(Purchase, Sale, Inventory, History)
    }
}
