package com.hardtekpt.crux.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.core.tween
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
import com.hardtekpt.crux.ui.navigation.cruxEnter
import com.hardtekpt.crux.ui.navigation.cruxExit
import com.hardtekpt.crux.ui.navigation.cruxPopEnter
import com.hardtekpt.crux.ui.navigation.swipeBetweenTabs
import com.hardtekpt.crux.ui.navigation.cruxPopExit
import com.hardtekpt.crux.ui.navigation.cruxPredictivePopEnter
import com.hardtekpt.crux.ui.navigation.cruxPredictivePopExit
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.navigation.navBarClearance
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.LocalContentColor
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
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavBackStackEntry
import androidx.compose.animation.AnimatedContentScope
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
import com.hardtekpt.crux.ui.navigation.PlaceDetailRoute
import com.hardtekpt.crux.ui.navigation.PlaceEditorRoute
import com.hardtekpt.crux.ui.navigation.ProblemDetailRoute
import com.hardtekpt.crux.ui.navigation.ProblemEditorRoute
import com.hardtekpt.crux.ui.places.PlaceDetailScreen
import com.hardtekpt.crux.ui.places.PlaceEditorScreen
import com.hardtekpt.crux.ui.places.ProblemDetailScreen
import com.hardtekpt.crux.ui.places.ProblemEditorScreen
import androidx.navigation.toRoute
import com.hardtekpt.crux.ui.navigation.ProgressGraph
import com.hardtekpt.crux.ui.navigation.ProgressRoute
import com.hardtekpt.crux.ui.navigation.ExerciseEditorRoute
import com.hardtekpt.crux.ui.navigation.PlanEditorRoute
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
import com.hardtekpt.crux.ui.train.ExerciseEditorScreen
import com.hardtekpt.crux.ui.train.PlanEditorScreen
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

    val onForm = currentDestination.isAny(
        LogClimbRoute::class,
        LogWeightRoute::class,
        PlanEditorRoute::class,
        ExerciseEditorRoute::class,
        PlaceEditorRoute::class,
        ProblemEditorRoute::class,
    )
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
        // On a tab's own screen, a sideways swipe moves to the next or previous tab.
        val onTabRoot = currentDestination.isAny(HomeRoute::class, TrainRoute::class, JournalRoute::class, ProgressRoute::class, YouRoute::class)
        NavHost(
            navController = navController,
            startDestination = HomeGraph,
            modifier = Modifier
                .fillMaxSize()
                .swipeBetweenTabs(enabled = onTabRoot && !showQuickLog) { direction ->
                    val tabs = TopLevelDestination.entries
                    val from = selectedTab?.ordinal ?: return@swipeBetweenTabs
                    tabs.getOrNull(from + direction)?.let(navController::navigateToTab)
                },
            enterTransition = cruxEnter,
            exitTransition = cruxExit,
            popEnterTransition = cruxPopEnter,
            popExitTransition = cruxPopExit,
            predictivePopEnterTransition = cruxPredictivePopEnter,
            predictivePopExitTransition = cruxPredictivePopExit,
        ) {
            navigation<HomeGraph>(startDestination = HomeRoute) {
                page<HomeRoute> {
                    HomeScreen(
                        onOpenTemplate = { navController.navigate(TemplateDetailRoute(it)) },
                        onOpenJournal = { navController.navigateToTab(TopLevelDestination.Journal) },
                        onOpenProgress = { navController.navigateToTab(TopLevelDestination.Progress) },
                        onOpenYou = { navController.navigateToTab(TopLevelDestination.You) },
                        onOpenProblem = { navController.navigate(ProblemDetailRoute(it)) },
                    )
                }
            }
            navigation<TrainGraph>(startDestination = TrainRoute) {
                page<TrainRoute> {
                    TrainScreen(
                        onOpenPlan = { navController.navigate(TemplateDetailRoute(it)) },
                        onNewPlan = { navController.navigate(PlanEditorRoute()) },
                        onOpenExercise = { navController.navigate(ExerciseEditorRoute(it)) },
                        onNewExercise = { navController.navigate(ExerciseEditorRoute()) },
                    )
                }
                page<TemplateDetailRoute> {
                    TemplateDetailScreen(
                        onBack = navController::popBackStack,
                        onEdit = { navController.navigate(PlanEditorRoute(it)) },
                    )
                }
                page<PlanEditorRoute> { PlanEditorScreen(onDone = navController::popBackStack) }
                page<ExerciseEditorRoute> { ExerciseEditorScreen(onDone = navController::popBackStack) }
            }
            navigation<JournalGraph>(startDestination = JournalRoute) {
                page<JournalRoute> {
                    JournalScreen(
                        onOpenClimb = { navController.navigate(LogClimbRoute(climbId = it)) },
                        onOpenPlace = { navController.navigate(PlaceDetailRoute(it)) },
                        onNewPlace = { navController.navigate(PlaceEditorRoute()) },
                    )
                }
                page<PlaceDetailRoute> {
                    PlaceDetailScreen(
                        onBack = navController::popBackStack,
                        onEdit = { navController.navigate(PlaceEditorRoute(it)) },
                        onOpenProblem = { navController.navigate(ProblemDetailRoute(it)) },
                        onNewProblem = { navController.navigate(ProblemEditorRoute(placeId = it)) },
                        onLogHere = { navController.navigate(LogClimbRoute(placeId = it)) },
                    )
                }
                page<PlaceEditorRoute> { entry ->
                    val editingId = entry.toRoute<PlaceEditorRoute>().placeId
                    PlaceEditorScreen(
                        onBack = navController::popBackStack,
                        onSaved = { id ->
                            when {
                                // Deleted: leave the editor and the place it belonged to.
                                id == 0L -> navController.popBackStack<JournalRoute>(inclusive = false)
                                // Created: swap the editor for the new place.
                                editingId == 0L -> navController.navigate(PlaceDetailRoute(id)) {
                                    popUpTo<PlaceEditorRoute> { inclusive = true }
                                }
                                else -> navController.popBackStack()
                            }
                        },
                    )
                }
                page<ProblemDetailRoute> {
                    ProblemDetailScreen(
                        onBack = navController::popBackStack,
                        onEdit = { placeId, problemId -> navController.navigate(ProblemEditorRoute(placeId, problemId)) },
                        onLogGo = { navController.navigate(LogClimbRoute(problemId = it)) },
                        onOpenClimb = { navController.navigate(LogClimbRoute(climbId = it)) },
                    )
                }
                page<ProblemEditorRoute> {
                    ProblemEditorScreen(
                        onDone = { deleted ->
                            // Deleting from the editor also leaves the problem's own page.
                            val fromDetail = navController.previousBackStackEntry?.destination
                                ?.hasRoute(ProblemDetailRoute::class) == true
                            if (deleted && fromDetail) {
                                navController.popBackStack<ProblemDetailRoute>(inclusive = true)
                            } else {
                                navController.popBackStack()
                            }
                        },
                    )
                }
            }
            navigation<ProgressGraph>(startDestination = ProgressRoute) {
                page<ProgressRoute> { ProgressScreen(onOpenProblem = { navController.navigate(ProblemDetailRoute(it)) }) }
            }
            navigation<YouGraph>(startDestination = YouRoute) {
                page<YouRoute> {
                    YouScreen(
                        onLogWeight = { navController.navigate(LogWeightRoute) },
                        onOpenSettings = { navController.navigate(SettingsRoute) },
                    )
                }
                page<SettingsRoute> { SettingsScreen(onBack = navController::popBackStack) }
            }
            page<LogClimbRoute> { LogClimbScreen(onDone = navController::popBackStack) }
            page<LogWeightRoute> { LogWeightScreen(onDone = navController::popBackStack) }
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
            enter = fadeIn(tween(180, delayMillis = 60)) + slideInVertically(tween(180, delayMillis = 60)) { it / 4 },
            exit = fadeOut(tween(100)) + slideOutVertically(tween(120)) { it / 4 },
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
                    QuickLogAction.LogClimb -> navController.navigate(LogClimbRoute())
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

/**
 * A destination on an opaque surface, so screens passing each other during a transition
 * never show through one another.
 */
private inline fun <reified T : Any> NavGraphBuilder.page(
    noinline content: @Composable AnimatedContentScope.(NavBackStackEntry) -> Unit,
) {
    composable<T> { entry ->
        Box(
            Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surface),
        ) {
            // Text without an explicit colour reads as ink on the page, not the platform default.
            CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.onSurface) { content(entry) }
        }
    }
}
