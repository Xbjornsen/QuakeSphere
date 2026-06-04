package com.quakesphere.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.quakesphere.ui.detail.EarthquakeDetailScreen
import com.quakesphere.ui.globe.GlobeScreen
import com.quakesphere.ui.list.EarthquakeListScreen
import com.quakesphere.ui.settings.SettingsScreen

@Composable
fun NavGraph(
    startDestination: String = Screen.GLOBE_ROUTE
) {
    val navController = rememberNavController()

    NavHost(
        navController = navController,
        startDestination = startDestination
    ) {
        composable(Screen.GLOBE_ROUTE) {
            GlobeScreen(
                onNavigateToList = { navController.navigate(Screen.LIST_ROUTE) },
                onNavigateToDetail = { id -> navController.navigate(Screen.detailRoute(id)) },
                onNavigateToSettings = { navController.navigate(Screen.SETTINGS_ROUTE) }
            )
        }

        composable(Screen.LIST_ROUTE) {
            EarthquakeListScreen(
                onNavigateBack = { navController.popBackStack() },
                onNavigateToDetail = { id -> navController.navigate(Screen.detailRoute(id)) }
            )
        }

        composable(
            route = Screen.DETAIL_ROUTE,
            arguments = listOf(navArgument("earthquakeId") { type = NavType.StringType })
        ) { backStackEntry ->
            val earthquakeId = backStackEntry.arguments?.getString("earthquakeId") ?: ""
            EarthquakeDetailScreen(
                earthquakeId = earthquakeId,
                onNavigateBack = { navController.popBackStack() }
            )
        }

        composable(Screen.SETTINGS_ROUTE) {
            SettingsScreen(
                onNavigateBack = { navController.popBackStack() }
            )
        }
    }
}
