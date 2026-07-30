package com.sharawang.fridge

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.sharawang.fridge.ui.edit.ItemEditScreen
import com.sharawang.fridge.ui.edit.ItemEditViewModel
import com.sharawang.fridge.ui.history.HistoryScreen
import com.sharawang.fridge.ui.history.HistoryViewModel
import com.sharawang.fridge.ui.inventory.InventoryScreen
import com.sharawang.fridge.ui.inventory.InventoryViewModel
import com.sharawang.fridge.ui.scan.ScanScreen
import com.sharawang.fridge.ui.scan.ScanViewModel
import com.sharawang.fridge.ui.settings.SettingsScreen
import com.sharawang.fridge.ui.settings.SettingsViewModel
import com.sharawang.fridge.ui.theme.FridgeTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as FridgeApplication).container

        setContent {
            FridgeTheme {
                val navController = rememberNavController()

                NavHost(navController = navController, startDestination = "inventory") {

                    composable("inventory") {
                        InventoryScreen(
                            viewModel = viewModel(
                                factory = InventoryViewModel.factory(container)
                            ),
                            onAddManual = { navController.navigate("edit/0") },
                            onScanReceipt = { navController.navigate("scan") },
                            onOpenSettings = { navController.navigate("settings") },
                            onOpenHistory = { navController.navigate("history") },
                            onOpenItem = { id -> navController.navigate("edit/$id") }
                        )
                    }

                    composable(
                        route = "edit/{itemId}",
                        arguments = listOf(navArgument("itemId") { type = NavType.LongType })
                    ) { entry ->
                        val itemId = entry.arguments?.getLong("itemId") ?: 0L
                        ItemEditScreen(
                            viewModel = viewModel(
                                key = "edit-$itemId",
                                factory = ItemEditViewModel.factory(container, itemId)
                            ),
                            onDone = { navController.popBackStack() }
                        )
                    }

                    composable("history") {
                        HistoryScreen(
                            viewModel = viewModel(factory = HistoryViewModel.factory(container)),
                            onDone = { navController.popBackStack() }
                        )
                    }

                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel(factory = SettingsViewModel.factory(container)),
                            onDone = { navController.popBackStack() }
                        )
                    }

                    composable("scan") {
                        ScanScreen(
                            viewModel = viewModel(factory = ScanViewModel.factory(container)),
                            onDone = { navController.popBackStack() }
                        )
                    }
                }
            }
        }
    }
}
