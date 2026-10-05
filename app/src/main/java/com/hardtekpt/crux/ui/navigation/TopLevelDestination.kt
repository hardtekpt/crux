package com.hardtekpt.crux.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.MenuBook
import androidx.compose.material.icons.outlined.BarChart
import androidx.compose.material.icons.outlined.FitnessCenter
import androidx.compose.material.icons.outlined.Home
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object WorkoutsRoute
@Serializable data object JournalRoute
@Serializable data object StatsRoute

enum class TopLevelDestination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
) {
    Home(HomeRoute, "Home", Icons.Outlined.Home),
    Workouts(WorkoutsRoute, "Workouts", Icons.Outlined.FitnessCenter),
    Journal(JournalRoute, "Journal", Icons.AutoMirrored.Outlined.MenuBook),
    Stats(StatsRoute, "Stats", Icons.Outlined.BarChart),
}
