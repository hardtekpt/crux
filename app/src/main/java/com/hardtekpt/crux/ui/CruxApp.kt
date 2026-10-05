package com.hardtekpt.crux.ui

import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.hardtekpt.crux.ui.home.HomeScreen
import com.hardtekpt.crux.ui.navigation.HomeRoute
import com.hardtekpt.crux.ui.navigation.JournalRoute
import com.hardtekpt.crux.ui.navigation.StatsRoute
import com.hardtekpt.crux.ui.navigation.TopLevelDestination
import com.hardtekpt.crux.ui.navigation.WorkoutsRoute

@Composable
fun CruxApp(home: @Composable () -> Unit = { HomeScreen() }) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        bottomBar = {
            NavigationBar {
                TopLevelDestination.entries.forEach { destination ->
                    val selected = currentDestination?.hierarchy
                        ?.any { it.hasRoute(destination.route::class) } == true
                    NavigationBarItem(
                        modifier = Modifier.testTag("nav_${destination.name}"),
                        selected = selected,
                        onClick = {
                            navController.navigate(destination.route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(destination.icon, contentDescription = null) },
                        label = { Text(destination.label) },
                    )
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier.padding(innerPadding),
        ) {
            composable<HomeRoute> { home() }
            composable<WorkoutsRoute> {
                PlaceholderScreen("Workouts", "Plan and run custom climbing workouts.")
            }
            composable<JournalRoute> {
                PlaceholderScreen("Journal", "Log climbs, sessions and notes.")
            }
            composable<StatsRoute> {
                PlaceholderScreen("Stats", "Track PBs, body metrics and progress.")
            }
        }
    }
}
