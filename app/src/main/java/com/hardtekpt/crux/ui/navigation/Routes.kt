package com.hardtekpt.crux.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.MenuBook
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Home
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material.icons.rounded.Person
import androidx.compose.ui.graphics.vector.ImageVector
import kotlinx.serialization.Serializable

// One nested graph per tab so each keeps its own back stack.
@Serializable data object HomeGraph
@Serializable data object TrainGraph
@Serializable data object JournalGraph
@Serializable data object ProgressGraph
@Serializable data object YouGraph

@Serializable data object HomeRoute
@Serializable data object TrainRoute
@Serializable data class TemplateDetailRoute(val templateId: Long)
@Serializable data object JournalRoute
@Serializable data object ProgressRoute
@Serializable data object YouRoute
@Serializable data object SettingsRoute

// Full-screen forms above the tabs; the nav bar hides while they are open.
@Serializable data object LogClimbRoute
@Serializable data object LogWeightRoute

/** The five root destinations from the app blueprint. */
enum class TopLevelDestination(
    val graph: Any,
    val label: String,
    val icon: ImageVector,
) {
    Home(HomeGraph, "Home", Icons.Rounded.Home),
    Train(TrainGraph, "Train", Icons.Rounded.FitnessCenter),
    Journal(JournalGraph, "Journal", Icons.AutoMirrored.Rounded.MenuBook),
    Progress(ProgressGraph, "Progress", Icons.Rounded.Insights),
    You(YouGraph, "You", Icons.Rounded.Person),
}
