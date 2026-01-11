package com.zagot.zagotplus.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assessment
import androidx.compose.material.icons.filled.Category
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Inventory
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Sell
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
        title = "Закупівля",
        icon = Icons.Filled.ShoppingCart
    )
    
    data object Sale : Destination(
        route = "sale",
        title = "Продаж",
        icon = Icons.Filled.Sell
    )
    
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
    
    companion object {
        val bottomNavItems = listOf(Purchase, Sale, Inventory, History)
    }
}
