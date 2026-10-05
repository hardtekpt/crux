package com.hardtekpt.crux.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.navigation
import androidx.navigation.compose.rememberNavController
import com.hardtekpt.crux.ui.components.CruxFab
import com.hardtekpt.crux.ui.home.HomeScreen
import com.hardtekpt.crux.ui.journal.JournalScreen
import com.hardtekpt.crux.ui.journal.LogClimbScreen
import com.hardtekpt.crux.ui.navigation.HomeGraph
import com.hardtekpt.crux.ui.navigation.HomeRoute
import com.hardtekpt.crux.ui.navigation.JournalGraph
import com.hardtekpt.crux.ui.navigation.JournalRoute
import com.hardtekpt.crux.ui.navigation.LogClimbRoute
import com.hardtekpt.crux.ui.navigation.LogWeightRoute
import com.hardtekpt.crux.ui.navigation.ProgressGraph
import com.hardtekpt.crux.ui.navigation.ProgressRoute
import com.hardtekpt.crux.ui.navigation.SettingsRoute
import com.hardtekpt.crux.ui.navigation.TemplateDetailRoute
import com.hardtekpt.crux.ui.navigation.TopLevelDestination
import com.hardtekpt.crux.ui.navigation.TrainGraph
import com.hardtekpt.crux.ui.navigation.TrainRoute
import com.hardtekpt.crux.ui.navigation.YouGraph
import com.hardtekpt.crux.ui.navigation.YouRoute
import com.hardtekpt.crux.ui.progress.ProgressScreen
import com.hardtekpt.crux.ui.quicklog.QuickLogAction
import com.hardtekpt.crux.ui.quicklog.QuickLogSheet
import com.hardtekpt.crux.ui.settings.SettingsScreen
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.train.TemplateDetailScreen
import com.hardtekpt.crux.ui.train.TrainScreen
import com.hardtekpt.crux.ui.you.LogWeightScreen
import com.hardtekpt.crux.ui.you.YouScreen

@Composable
fun CruxApp() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    var showQuickLog by rememberSaveable { mutableStateOf(false) }

    val onForm = currentDestination.isAny(LogClimbRoute::class, LogWeightRoute::class)
    val showFab = currentDestination.isAny(HomeRoute::class, JournalRoute::class)

    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        contentWindowInsets = WindowInsets(0),
        bottomBar = {
            AnimatedVisibility(visible = !onForm, enter = fadeIn(), exit = fadeOut()) {
                CruxNavBar(
                    isSelected = { destination ->
                        currentDestination?.hierarchy?.any { it.hasRoute(destination.graph::class) } == true
                    },
                    onNavigate = { destination -> navController.navigateToTab(destination) },
                )
            }
        },
        floatingActionButton = {
            if (showFab) {
                CruxFab(
                    text = "Log",
                    icon = Icons.Rounded.Add,
                    onClick = { showQuickLog = true },
                    modifier = Modifier.testTag("log_fab"),
                )
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = HomeGraph,
            modifier = Modifier
                .padding(innerPadding)
                .consumeWindowInsets(innerPadding),
        ) {
            navigation<HomeGraph>(startDestination = HomeRoute) {
                composable<HomeRoute> {
                    HomeScreen(
                        onOpenTemplate = { navController.navigate(TemplateDetailRoute(it)) },
                        onOpenJournal = { navController.navigateToTab(TopLevelDestination.Journal) },
                        onOpenProgress = { navController.navigateToTab(TopLevelDestination.Progress) },
                        onOpenYou = { navController.navigateToTab(TopLevelDestination.You) },
                    )
                }
            }
            navigation<TrainGraph>(startDestination = TrainRoute) {
                composable<TrainRoute> {
                    TrainScreen(onOpenTemplate = { navController.navigate(TemplateDetailRoute(it)) })
                }
                composable<TemplateDetailRoute> {
                    TemplateDetailScreen(onBack = navController::popBackStack)
                }
            }
            navigation<JournalGraph>(startDestination = JournalRoute) {
                composable<JournalRoute> { JournalScreen() }
            }
            navigation<ProgressGraph>(startDestination = ProgressRoute) {
                composable<ProgressRoute> { ProgressScreen() }
            }
            navigation<YouGraph>(startDestination = YouRoute) {
                composable<YouRoute> {
                    YouScreen(
                        onLogWeight = { navController.navigate(LogWeightRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                    )
                }
                composable<SettingsRoute> { SettingsScreen(onBack = navController::popBackStack) }
            }
            composable<LogClimbRoute> { LogClimbScreen(onDone = navController::popBackStack) }
            composable<LogWeightRoute> { LogWeightScreen(onDone = navController::popBackStack) }
        }
    }

    if (showQuickLog) {
        QuickLogSheet(
            onDismiss = { showQuickLog = false },
            onAction = { action ->
                showQuickLog = false
                when (action) {
                    QuickLogAction.LogClimb -> navController.navigate(LogClimbRoute)
                    QuickLogAction.LogWeight -> navController.navigate(LogWeightRoute)
                    QuickLogAction.StartWorkout, QuickLogAction.AddNote -> Unit
                }
            },
        )
    }
}

private fun NavDestination?.isAny(vararg routes: kotlin.reflect.KClass<*>): Boolean =
    this?.let { destination -> routes.any { destination.hasRoute(it) } } == true

private fun NavHostController.navigateToTab(destination: TopLevelDestination) {
    navigate(destination.graph) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
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
                label = { Text(destination.label, style = MaterialTheme.typography.labelMedium, maxLines = 1) },
                alwaysShowLabel = true,
                colors = NavigationBarItemDefaults.colors(
                    selectedIconColor = colors.onPrimaryContainer,
                    selectedTextColor = colors.onSurface,
                    indicatorColor = colors.primaryContainer,
                    unselectedIconColor = colors.onSurfaceVariant,
                    unselectedTextColor = colors.onSurfaceVariant,
                ),
            )
        }
    }
}
