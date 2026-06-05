package com.mangalens.ui.navigation

sealed class Screen(val route: String) {
	data object Home : Screen("home")
	data object Permissions : Screen("permissions")
}
