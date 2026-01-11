package com.zagot.zagotplus.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.zagot.zagotplus.ui.screens.purchase.PurchaseScreen
import com.zagot.zagotplus.ui.screens.sale.SaleScreen
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
    
    val currentTitle = Destination.bottomNavItems.find { destination ->
        currentDestination?.hierarchy?.any { it.route == destination.route } == true
    }?.title ?: Destination.Purchase.title
    
    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(text = currentTitle) },
                actions = {
                    SyncStatusIcon(
                        syncStatusFlow = syncStatusFlow,
                        onSyncClick = onSyncClick
                    )
                }
            )
        },
        bottomBar = {
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
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = Destination.Purchase.route,
            modifier = Modifier.padding(innerPadding)
        ) {
            composable(Destination.Purchase.route) {
                PurchaseScreen()
            }
            composable(Destination.Sale.route) {
                SaleScreen()
            }
            composable(Destination.Inventory.route) {
                InventoryScreen()
            }
            composable(Destination.History.route) {
                HistoryScreen()
            }
        }
    }
}
