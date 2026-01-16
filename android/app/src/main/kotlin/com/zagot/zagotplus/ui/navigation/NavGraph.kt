package com.zagot.zagotplus.ui.navigation

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
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
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
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
import com.zagot.zagotplus.ui.screens.cash.CashScreen
import com.zagot.zagotplus.ui.screens.settings.SettingsScreen
import com.zagot.zagotplus.ui.screens.transfer.TransferScreen
import androidx.navigation.NavType
import androidx.navigation.navArgument
import kotlinx.coroutines.flow.Flow

private const val BACK_PRESS_INTERVAL = 2000L // 2 seconds

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NavGraph(
    syncStatusFlow: Flow<SyncStatus>,
    isOnline: Boolean,
    onSyncClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = navBackStackEntry?.destination
    var showMenu by remember { mutableStateOf(false) }
    val context = LocalContext.current
    
    // Track last back press time for double-tap exit
    var lastBackPressTime by remember { mutableLongStateOf(0L) }
    
    // Check if current route is a bottom nav item
    val isBottomNavRoute = Destination.bottomNavItems.any { dest ->
        currentDestination?.route == dest.route
    }
    
    // Handle back press - require double tap to exit on main screens
    BackHandler(enabled = isBottomNavRoute) {
        val currentTime = System.currentTimeMillis()
        if (currentTime - lastBackPressTime < BACK_PRESS_INTERVAL) {
            // Second press within interval - exit app
            (context as? android.app.Activity)?.finish()
        } else {
            // First press - show toast and record time
            lastBackPressTime = currentTime
            Toast.makeText(context, "Натисніть ще раз для виходу", Toast.LENGTH_SHORT).show()
        }
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
                            isOnline = isOnline,
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
                                    Icon(Destination.Products.icon, contentDescription = "Товари")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Каса") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Destination.Cash.route)
                                },
                                leadingIcon = {
                                    Icon(Destination.Cash.icon, contentDescription = "Каса")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Переміщення") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Destination.Transfer.route)
                                },
                                leadingIcon = {
                                    Icon(Destination.Transfer.icon, contentDescription = "Переміщення")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Звіти") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Destination.Reports.route)
                                },
                                leadingIcon = {
                                    Icon(Destination.Reports.icon, contentDescription = "Звіти")
                                }
                            )
                            DropdownMenuItem(
                                text = { Text("Налаштування") },
                                onClick = {
                                    showMenu = false
                                    navController.navigate(Destination.Settings.route)
                                },
                                leadingIcon = {
                                    Icon(Destination.Settings.icon, contentDescription = "Налаштування")
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
            modifier = Modifier.padding(innerPadding),
            // Default transitions for bottom nav (subtle fade)
            enterTransition = { fadeIn(animationSpec = tween(200)) },
            exitTransition = { fadeOut(animationSpec = tween(200)) },
            popEnterTransition = { fadeIn(animationSpec = tween(200)) },
            popExitTransition = { fadeOut(animationSpec = tween(200)) }
        ) {
            composable(Destination.Purchase.route) {
                PurchaseScreen(
                    onNavigateToNewClient = {
                        navController.navigate(Destination.PurchaseEntry.route)
                    }
                )
            }
            // Detail screens get slide transitions
            composable(
                route = Destination.PurchaseEntry.ROUTE_WITH_ARGS,
                arguments = listOf(
                    navArgument(Destination.PurchaseEntry.ARG_BATCH_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
            ) { backStackEntry ->
                val batchId = backStackEntry.arguments?.getString(Destination.PurchaseEntry.ARG_BATCH_ID)
                PurchaseEntryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    editingBatchId = batchId
                )
            }
            composable(Destination.Sale.route) {
                SaleScreen(
                    onNavigateToNewSale = {
                        navController.navigate(Destination.SaleEntry.route)
                    }
                )
            }
            // Detail screens get slide transitions
            composable(
                route = Destination.SaleEntry.ROUTE_WITH_ARGS,
                arguments = listOf(
                    navArgument(Destination.SaleEntry.ARG_BATCH_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
            ) { backStackEntry ->
                val batchId = backStackEntry.arguments?.getString(Destination.SaleEntry.ARG_BATCH_ID)
                SaleEntryScreen(
                    onNavigateBack = { navController.popBackStack() },
                    editingBatchId = batchId
                )
            }
            composable(Destination.Inventory.route) {
                InventoryScreen(
                    onNavigateToTransfer = { productId, sourceLocationId ->
                        navController.navigate(
                            Destination.Transfer.createRoute(productId, sourceLocationId, null)
                        )
                    }
                )
            }
            composable(Destination.History.route) {
                HistoryScreen(
                    onNavigateToEditPurchase = { batchId ->
                        navController.navigate(Destination.PurchaseEntry.createRoute(batchId))
                    },
                    onNavigateToEditSale = { batchId ->
                        navController.navigate(Destination.SaleEntry.createRoute(batchId))
                    }
                )
            }
            // Menu screens get slide transitions
            composable(
                route = Destination.Products.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
            ) {
                ProductsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Destination.Reports.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
            ) {
                ReportsScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
            composable(
                route = Destination.Settings.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
            ) {
                SettingsScreen(
                    onNavigateBack = { navController.popBackStack() },
                    onNavigateToProducts = { navController.navigate(Destination.Products.route) }
                )
            }
            composable(
                route = Destination.Transfer.ROUTE_WITH_ARGS,
                arguments = listOf(
                    navArgument(Destination.Transfer.ARG_PRODUCT_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Destination.Transfer.ARG_SOURCE_LOCATION_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    },
                    navArgument(Destination.Transfer.ARG_DESTINATION_LOCATION_ID) {
                        type = NavType.StringType
                        nullable = true
                        defaultValue = null
                    }
                ),
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
            ) { backStackEntry ->
                val productId = backStackEntry.arguments?.getString(Destination.Transfer.ARG_PRODUCT_ID)
                val sourceLocationId = backStackEntry.arguments?.getString(Destination.Transfer.ARG_SOURCE_LOCATION_ID)
                val destinationLocationId = backStackEntry.arguments?.getString(Destination.Transfer.ARG_DESTINATION_LOCATION_ID)
                TransferScreen(
                    onNavigateBack = { navController.popBackStack() },
                    prefilledProductId = productId,
                    prefilledSourceLocationId = sourceLocationId,
                    prefilledDestinationLocationId = destinationLocationId
                )
            }
            composable(
                route = Destination.Cash.route,
                enterTransition = { slideInHorizontally(initialOffsetX = { it }, animationSpec = tween(300)) },
                exitTransition = { slideOutHorizontally(targetOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popEnterTransition = { slideInHorizontally(initialOffsetX = { -it / 4 }, animationSpec = tween(300)) },
                popExitTransition = { slideOutHorizontally(targetOffsetX = { it }, animationSpec = tween(300)) }
            ) {
                CashScreen(
                    onNavigateBack = { navController.popBackStack() }
                )
            }
        }
    }
}
