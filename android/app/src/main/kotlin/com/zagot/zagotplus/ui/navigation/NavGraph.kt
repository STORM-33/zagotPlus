package com.zagot.zagotplus.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.zagot.zagotplus.sync.SyncStatus
import com.zagot.zagotplus.ui.components.SyncStatusIcon
import com.zagot.zagotplus.ui.screens.history.HistoryScreen
import com.zagot.zagotplus.ui.screens.inventory.InventoryScreen
import com.zagot.zagotplus.ui.screens.products.ProductsScreen
import com.zagot.zagotplus.ui.screens.purchase.PurchaseEntryScreen
import com.zagot.zagotplus.ui.screens.purchase.PurchaseScreen
import com.zagot.zagotplus.ui.screens.reports.ReportsScreen
import com.zagot.zagotplus.ui.screens.sale.SaleEntryScreen
import com.zagot.zagotplus.ui.screens.sale.SaleScreen
import com.zagot.zagotplus.ui.screens.settings.SettingsScreen
import kotlinx.coroutines.flow.Flow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(
    syncStatusFlow: Flow<SyncStatus>,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var showMenu by remember { mutableStateOf(false) }
    
    // Check if current route is a bottom nav item
    val isBottomNavRoute = Destination.bottomNavItems.any { dest ->
        currentDestination?.route == dest.route
    }
    
    val currentTitle = Destination.bottomNavItems.find { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }?.title ?: Destination.Purchase.title
    
    Scaffold(
        modifier = modifier,
        topBar = {
            if (isBottomNavRoute) {
                TopAppBar(
                    title = { Text(text = currentTitle) },
                    actions = {
                        SyncStatusIcon(
                            syncStatusFlow = syncStatusFlow,
                            onSyncClick = onSyncClick
                        )
                        IconButton(onClick = { showMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Меню")
                        }
                        DropdownMenu(
                            expanded = showMenu,
                            onDismissRequest = { showMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("Товари") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Destination.Products.route)
                                },
                                leadingIcon = {
                                    Icon(Destination.Products.icon, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Звіти") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Destination.Reports.route)
                                },
                                leadingIcon = {
                                    Icon(Destination.Reports.icon, contentDescription = null)
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Налаштування") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Destination.Settings.route)
                                },
                                leadingIcon = {
                                    Icon(Destination.Settings.icon, contentDescription = null)
                                }
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            if (isBottomNavRoute) {
                NavigationBar {
                    Destination.bottomNavItems.forEach { destination ->
                        NavigationBarItem(
                            icon = { Icon(destination.icon, contentDescription = destination.title) },
                            label = { Text(destination.title) },
                            selected = currentDestination?.hierarchy?.any { 
                                it.route == destination.route 
                            } == true,
                            onClick = {
                                navController.navigate(destination.route) {
                                    popUpTo(navController.graph.findStartDestination().id) {
                                        saveState = true
                                    }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            }
                        )
                    }
                }
            }
        }
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Purchase.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Purchase.route) {
                PurchaseScreen(
                    onNavigateToNewClient = {
                        navController.navigate(Destination.PurchaseEntry.route)
                    }
                )
            }
            composable(Destination.PurchaseEntry.route) {
                PurchaseEntryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Destination.Sale.route) {
                SaleScreen(
                    onNavigateToNewSale = {
                        navController.navigate(Destination.SaleEntry.route)
                    }
                )
            }
            composable(Destination.SaleEntry.route) {
                SaleEntryScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Destination.Inventory.route) {
                InventoryScreen()
            }
            composable(Destination.History.route) {
                HistoryScreen()
            }
            composable(Destination.Products.route) {
                ProductsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Destination.Reports.route) {
                ReportsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(Destination.Settings.route) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProducts = { navController.navigate(Destination.Products.route) }
                )
            }
        }
    }
}
