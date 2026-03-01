package com.newsticker

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.newsticker.ui.screens.articles.ArticlesScreen
import com.newsticker.ui.screens.settings.SettingsScreen

@Composable
fun NewsTickerApp() {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = "articles"
    ) {
        composable("articles") {
            ArticlesScreen(
                onNavigateToSettings = { navController.navigate("settings") }
            )
        }
        composable("settings") {
            SettingsScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
