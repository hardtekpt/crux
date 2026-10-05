package com.hardtekpt.crux.ui

import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
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
import com.hardtekpt.crux.ui.navigation.ProgressRoute
import com.hardtekpt.crux.ui.navigation.TopLevelDestination
import com.hardtekpt.crux.ui.navigation.TrainRoute
import com.hardtekpt.crux.ui.navigation.YouRoute
import com.hardtekpt.crux.ui.theme.CruxTheme

@Composable
fun CruxApp(home: @Composable () -> Unit = { HomeScreen() }) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            CruxNavBar(
                isSelected = { destination ->
                    currentDestination?.hierarchy?.any { it.hasRoute(destination.route::class) } == true
                },
                onNavigate = { destination ->
                    navController.navigate(destination.route) {
                        popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                        launchSingleTop = true
                        restoreState = true
                    }
                },
            )
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeRoute,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            composable<HomeRoute> { home() }
            composable<TrainRoute> {
                PlaceholderScreen(
                    title = "Train",
                    icon = Icons.Rounded.FitnessCenter,
                    headline = "No workouts yet",
                    sentence = "Build a workout from your own exercises, then start a session from here.",
                )
            }
            composable<JournalRoute> {
                PlaceholderScreen(
                    title = "Journal",
                    icon = Icons.AutoMirrored.Rounded.MenuBook,
                    headline = "No climbs logged yet",
                    sentence = "Every climb, session and note you log lands here, newest first.",
                )
            }
            composable<ProgressRoute> {
                PlaceholderScreen(
                    title = "Progress",
                    icon = Icons.Rounded.Insights,
                    headline = "No progress to show yet",
                    sentence = "Personal bests, volume and grade trends appear once you log a few sessions.",
                )
            }
            composable<YouRoute> {
                PlaceholderScreen(
                    title = "You",
                    icon = Icons.Rounded.Person,
                    headline = "No body stats yet",
                    sentence = "Add your weight, height and ape index to track them over a season.",
                )
            }
        }
    }
}

/** NavBar: `surface-container`, `primary-container` pill on the active item, labels always shown. */
@Composable
private fun CruxNavBar(
    isSelected: (TopLevelDestination) -> Boolean,
    onNavigate: (TopLevelDestination) -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    NavigationBar(
        containerColor = colors.surfaceContainer,
        tonalElevation = CruxTheme.space.s0,
    ) {
        TopLevelDestination.entries.forEach { destination ->
            NavigationBarItem(
                modifier = Modifier
                    .height(CruxTheme.size.navBarHeight)
                    .testTag("nav_${destination.name}"),
                selected = isSelected(destination),
                onClick = { onNavigate(destination) },
                icon = { Icon(destination.icon, contentDescription = null) },
                label = { Text(destination.label, style = MaterialTheme.typography.labelMedium) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onPrimaryContainer,
                    selectedTextColor = colors.onPrimaryContainer,
                    indicatorColor = colors.primaryContainer,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}
