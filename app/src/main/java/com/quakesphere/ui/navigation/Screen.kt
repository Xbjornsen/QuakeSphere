package com.quakesphere.ui.navigation

sealed class Screen(val route: String) {
    object Globe : Screen("globe")
    object List : Screen("list")
    object Settings : Screen("settings")
    data class Detail(val earthquakeId: String = "{earthquakeId}") : Screen("detail/{earthquakeId}") {
        fun createRoute(id: String) = "detail/$id"
    }

    companion object {
        const val GLOBE_ROUTE = "globe"
        const val LIST_ROUTE = "list"
        const val SETTINGS_ROUTE = "settings"
        const val DETAIL_ROUTE = "detail/{earthquakeId}"
        fun detailRoute(id: String) = "detail/$id"
    }
}
