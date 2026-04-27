package com.budgettracker.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.budgettracker.ui.screens.accounts.AccountsScreen
import com.budgettracker.ui.screens.addtransaction.AddTransactionScreen
import com.budgettracker.ui.screens.categories.CategoriesScreen
import com.budgettracker.ui.screens.chat.ChatScreen
import com.budgettracker.ui.screens.home.HomeScreen
import com.budgettracker.ui.screens.pdfimport.ImportScreen
import com.budgettracker.ui.screens.reports.ReportsScreen
import com.budgettracker.ui.screens.settings.SettingsScreen
import com.budgettracker.ui.screens.setup.SetupScreen

sealed class Screen(val route: String) {
    data object Home : Screen("home")
    data object AddTransaction : Screen("add_transaction")
    data object EditTransaction : Screen("edit_transaction")
    data object Settings : Screen("settings")
    data object Reports : Screen("reports")
    data object Categories : Screen("categories")
    data object Chat : Screen("chat")
    data object Import : Screen("import_pdf")
    data object Accounts : Screen("accounts")
    data object Setup : Screen("setup")
}

@Composable
fun BudgetNavigation(
    shortcutAction: String? = null,
    needsSetup: Boolean = false
) {
    val navController = rememberNavController()
    val navBackStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = navBackStackEntry?.destination?.route
    
    // Handle shortcut action
    LaunchedEffect(shortcutAction) {
        when (shortcutAction) {
            "add_expense" -> navController.navigate(Screen.AddTransaction.route)
            "voice_input" -> navController.navigate(Screen.AddTransaction.route)
        }
    }
    
    // Show bottom bar for main screens only
    val showBottomBar = currentRoute in listOf(
        Screen.Home.route,
        Screen.Reports.route,
        Screen.Accounts.route,
        Screen.Chat.route,
        Screen.Settings.route
    )
    
    Scaffold(
        bottomBar = {
            if (showBottomBar) {
                AppBottomNavigationBar(
                    currentRoute = currentRoute,
                    onNavigate = { item ->
                        when (item) {
                            BottomNavItem.Home -> navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Home.route) { inclusive = true }
                            }
                            BottomNavItem.Reports -> navController.navigate(Screen.Reports.route) {
                                popUpTo(Screen.Home.route)
                            }
                            BottomNavItem.Add -> navController.navigate(Screen.AddTransaction.route)
                            BottomNavItem.Accounts -> navController.navigate(Screen.Accounts.route) {
                                popUpTo(Screen.Home.route)
                            }
                            BottomNavItem.Chat -> navController.navigate(Screen.Chat.route) {
                                popUpTo(Screen.Home.route)
                            }
                            BottomNavItem.Settings -> navController.navigate(Screen.Settings.route) {
                                popUpTo(Screen.Home.route)
                            }
                        }
                    }
                )
            }
        }
    ) { paddingValues ->
        Box(modifier = Modifier.padding(paddingValues)) {
            NavHost(
                navController = navController,
                startDestination = if (needsSetup) Screen.Setup.route else Screen.Home.route
            ) {
                composable(Screen.Setup.route) {
                    SetupScreen(
                        onSetupComplete = {
                            navController.navigate(Screen.Home.route) {
                                popUpTo(Screen.Setup.route) { inclusive = true }
                            }
                        }
                    )
                }

                composable(Screen.Accounts.route) {
                    AccountsScreen()
                }

                composable(Screen.Home.route) {
                    HomeScreen(
                        onAddTransaction = { text -> 
                            if (text != null) {
                                navController.navigate("${Screen.AddTransaction.route}?voiceInput=$text")
                            } else {
                                navController.navigate(Screen.AddTransaction.route)
                            }
                        },
                        onEditTransaction = { transactionId ->
                            navController.navigate("${Screen.EditTransaction.route}/$transactionId")
                        },
                        onNavigateToSettings = { navController.navigate(Screen.Settings.route) },
                        onNavigateToReports = { navController.navigate(Screen.Reports.route) },
                        onNavigateToCategories = { navController.navigate(Screen.Categories.route) },
                        onNavigateToChat = { navController.navigate(Screen.Chat.route) },
                        onNavigateToImport = { navController.navigate(Screen.Import.route) }
                    )
                }
                
                composable(
                    route = "${Screen.AddTransaction.route}?voiceInput={voiceInput}",
                    arguments = listOf(
                        navArgument("voiceInput") {
                            type = NavType.StringType
                            defaultValue = ""
                            nullable = true
                        }
                    )
                ) { backStackEntry ->
                    val voiceInput = backStackEntry.arguments?.getString("voiceInput")
                    AddTransactionScreen(
                        voiceInput = voiceInput?.takeIf { it.isNotEmpty() },
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                
                composable(Screen.AddTransaction.route) {
                    AddTransactionScreen(
                        voiceInput = null,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                
                composable(
                    route = "${Screen.EditTransaction.route}/{transactionId}",
                    arguments = listOf(
                        navArgument("transactionId") {
                            type = NavType.LongType
                        }
                    )
                ) { backStackEntry ->
                    val transactionId = backStackEntry.arguments?.getLong("transactionId") ?: 0L
                    AddTransactionScreen(
                        voiceInput = null,
                        editTransactionId = transactionId,
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                
                composable(Screen.Settings.route) {
                    SettingsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                
                composable(Screen.Reports.route) {
                    ReportsScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                
                composable(Screen.Categories.route) {
                    CategoriesScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                
                composable(Screen.Chat.route) {
                    ChatScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
                
                composable(Screen.Import.route) {
                    ImportScreen(
                        onNavigateBack = { navController.popBackStack() }
                    )
                }
            }
        }
    }
}
