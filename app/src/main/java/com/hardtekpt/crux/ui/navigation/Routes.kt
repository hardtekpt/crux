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

@Serializable data class PlanEditorRoute(val templateId: Long = 0)

@Serializable data class ExerciseEditorRoute(val exerciseId: Long = 0)

@Serializable data object JournalRoute

@Serializable data class PlaceDetailRoute(val placeId: Long)

@Serializable data class PlaceEditorRoute(val placeId: Long = 0)

@Serializable data class ProblemDetailRoute(val problemId: Long)

@Serializable data class ProblemEditorRoute(val placeId: Long, val problemId: Long = 0)

@Serializable data object ProgressRoute

@Serializable data object YouRoute

@Serializable data object SettingsRoute

@Serializable data object MeasurementsRoute

@Serializable data object CircumferencesRoute

@Serializable data object PlacesRoute

@Serializable data object GradeConverterRoute

// Full-screen forms above the tabs; the nav bar hides while they are open.

/** Log a climb, or edit one when [climbId] is set; [placeId]/[problemId] preselect where. */
@Serializable data class LogClimbRoute(val climbId: Long = 0, val placeId: Long = 0, val problemId: Long = 0, val sectionId: Long = 0)

@Serializable data object LogWeightRoute

@Serializable data object NotesRoute

@Serializable data class NoteEditorRoute(val noteId: Long = 0)

@Serializable data class RecordEditorRoute(val exerciseId: Long = 0)

@Serializable data class ExerciseRecordsRoute(val exerciseId: Long)

/** The five root destinations from the app blueprint. */
enum class TopLevelDestination(val graph: Any, val label: String, val icon: ImageVector) {
    Home(HomeGraph, "Home", Icons.Rounded.Home),
    Train(TrainGraph, "Train", Icons.Rounded.FitnessCenter),
    Journal(JournalGraph, "Journal", Icons.AutoMirrored.Rounded.MenuBook),
    Progress(ProgressGraph, "Progress", Icons.Rounded.Insights),
    You(YouGraph, "You", Icons.Rounded.Person),
}
