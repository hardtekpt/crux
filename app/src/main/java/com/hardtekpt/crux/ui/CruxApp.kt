package com.hardtekpt.crux.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.ui.navigation.FloatingNavBar
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.navigation.navBarClearance
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
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
    val selectedTab = TopLevelDestination.entries.firstOrNull { destination ->
        currentDestination?.hierarchy?.any { it.hasRoute(destination.graph::class) } == true
    }
    val bottomInset = WindowInsets.navigationBars.asPaddingValues().calculateBottomPadding()

    // Content runs edge to edge and scrolls under the floating bar; screens pad their
    // lists by LocalNavBarClearance so the last row can still scroll clear of it.
    Box(
        Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.surface),
    ) {
        CompositionLocalProvider(LocalNavBarClearance provides if (onForm) 0.dp else navBarClearance(bottomInset)) {
        NavHost(
            navController = navController,
            startDestination = HomeGraph,
            modifier = Modifier.fillMaxSize(),
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

        // Content fades out under the floating bar instead of colliding with it.
        if (!onForm) {
            val surface = MaterialTheme.colorScheme.surface
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(navBarClearance(bottomInset))
                    .background(Brush.verticalGradient(listOf(surface.copy(alpha = 0f), surface.copy(alpha = 0.92f), surface))),
            )
        }

        AnimatedVisibility(
            visible = !onForm,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = CruxTheme.space.s4),
        ) {
            FloatingNavBar(
                selected = selectedTab,
                onNavigate = { navController.navigateToTab(it) },
                logOpen = showQuickLog,
                onLog = { showQuickLog = true },
            )
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
