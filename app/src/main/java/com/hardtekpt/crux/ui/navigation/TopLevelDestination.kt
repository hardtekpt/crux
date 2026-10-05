package com.hardtekpt.crux.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

@Serializable data object HomeRoute
@Serializable data object TrainRoute
@Serializable data object JournalRoute
@Serializable data object ProgressRoute
@Serializable data object YouRoute

/** The five root destinations from the app blueprint. */
enum class TopLevelDestination(
    val route: Any,
    val label: String,
    val icon: ImageVector,
) {
    Home(HomeRoute, "Home", Icons.Rounded.Home),
    Train(TrainRoute, "Train", Icons.Rounded.FitnessCenter),
    Journal(JournalRoute, "Journal", Icons.AutoMirrored.Rounded.MenuBook),
    Progress(ProgressRoute, "Progress", Icons.Rounded.Insights),
    You(YouRoute, "You", Icons.Rounded.Person),
}
