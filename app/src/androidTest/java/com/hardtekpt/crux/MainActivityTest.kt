package com.hardtekpt.crux

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.ComposeTestRule
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import com.hardtekpt.crux.data.seed.StarterData
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import javax.inject.Inject

/** End-to-end Compose tests on a device: real Activity, Hilt graph and Room (in memory). */
@HiltAndroidTest
class MainActivityTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Inject lateinit var starterData: StarterData

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking {
            // The climber's own data set starts with the starter library; demo gets its full set.
            starterData.addStarterLibraryToCurrent()
            starterData.seedDemoIfEmpty()
        }
    }

    @Test
    fun loggingAClimbFromTheFabShowsUpEverywhere() {
        composeRule.onNodeWithTag("log_fab").performClick()
        composeRule.onNodeWithTag("quick_LogClimb").performClick()
        composeRule.waitForTag("screen_LogClimb")

        composeRule.onNodeWithTag("grade_picker").performScrollToNode(hasTestTag("grade_7A"))
        composeRule.onNodeWithTag("grade_7A").performClick()
        composeRule.onNodeWithTag("field_name").performScrollTo()
        composeRule.textFieldIn("field_name").performTextInput("Yellow dyno")
        composeRule.onNodeWithTag("save_climb").performClick()

        composeRule.waitForTag("screen_Home")
        composeRule.onNodeWithTag("week_climbs").assertTextEquals("1")
        // The tile is tappable, so its text merges into one node; read the figure from the unmerged tree.
        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasText("Latest best", substring = true, ignoreCase = true))
        composeRule.onNodeWithTag("latest_best", useUnmergedTree = true).assertTextEquals("7A flash")

        composeRule.onNodeWithTag("nav_Journal").performClick()
        composeRule.waitForTag("journal_climb")

        composeRule.onNodeWithTag("nav_Progress").performClick()
        composeRule.waitForTag("hardest_BOULDER")
        composeRule.onNodeWithTag("hardest_BOULDER").assertTextEquals("7A")
    }

    @Test
    fun loggingAWeightUpdatesYou() {
        composeRule.onNodeWithTag("nav_You").performClick()
        composeRule.onNodeWithTag("log_weight").performClick()
        composeRule.waitForTag("screen_LogWeight")
        composeRule.onNodeWithTag("ruler_weight_value").performClick()
        composeRule.textFieldIn("field_type_value").performTextReplacement("72.5")
        composeRule.onNodeWithTag("confirm_type_value").performClick()
        composeRule.onNodeWithTag("save_weight").performClick()

        composeRule.waitForTag("screen_You")
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("you_weight").assertTextEquals("72.5 kg") }.isSuccess
        }
        composeRule.onNodeWithTag("you_list").performScrollToNode(hasTestTag("weight_row"))
        assertEquals(1, composeRule.onAllNodesWithTag("weight_row").fetchSemanticsNodes().size)
    }

    @Test
    fun bodyStatsUpdateFromTheirTilesAndGiveTheApeIndex() {
        composeRule.onNodeWithTag("nav_You").performClick()
        listOf("HEIGHT" to "178", "WINGSPAN" to "184").forEach { (type, value) ->
            composeRule.onNodeWithTag("you_list").performScrollToNode(hasTestTag("stat_$type"))
            composeRule.onNodeWithTag("stat_$type").performClick()
            composeRule.onNodeWithTag("ruler_measurement_value").performClick()
            composeRule.textFieldIn("field_type_value").performTextReplacement(value)
            composeRule.onNodeWithTag("confirm_type_value").performClick()
            composeRule.onNodeWithTag("save_measurement").performClick()
        }
        composeRule.onNodeWithTag("you_list").performScrollToNode(hasTestTag("you_ape_index"))
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("you_ape_index").assertTextEquals("+6 cm") }.isSuccess
        }
    }

    @Test
    fun choosingVScaleInSettingsChangesTheClimbForm() {
        composeRule.onNodeWithTag("nav_You").performClick()
        composeRule.onNodeWithTag("open_settings").performClick()
        composeRule.waitForTag("screen_Settings")
        composeRule.onNodeWithTag("segment_V scale").performClick()
        composeRule.onNodeWithTag("back").performClick()

        composeRule.onNodeWithTag("nav_Home").performClick()
        composeRule.onNodeWithTag("log_fab").performClick()
        composeRule.onNodeWithTag("quick_LogClimb").performClick()
        composeRule.waitForTag("screen_LogClimb")
        composeRule.onNodeWithTag("grade_picker").performScrollToNode(hasTestTag("grade_V5"))
        composeRule.onNodeWithTag("grade_V5").performClick()
        composeRule.onNodeWithTag("save_climb").performClick()

        composeRule.waitForTag("screen_Home")
        composeRule.onNodeWithTag("nav_Progress").performClick()
        composeRule.waitForTag("hardest_BOULDER")
        composeRule.onNodeWithTag("hardest_BOULDER").assertTextEquals("V5")
    }

    @Test
    fun trainShowsStarterTemplatesAndTheirDetail() {
        composeRule.onNodeWithTag("nav_Train").performClick()
        composeRule.waitForTag("template_card")
        composeRule.onAllNodesWithTag("template_card")[0].performClick()
        composeRule.waitForTag("template_exercise")
        composeRule.onNodeWithTag("back").performClick()
        composeRule.waitForTag("screen_Train")
    }

    @Test
    fun newExerciseCanBeBuiltIntoAPlan() {
        composeRule.onNodeWithTag("nav_Train").performClick()
        composeRule.onNodeWithTag("segment_Exercises").performClick()
        composeRule.onNodeWithTag("new_exercise").performClick()
        composeRule.waitForTag("screen_ExerciseEditor")
        composeRule.textFieldIn("field_exercise_name").performTextInput("Campus ladders")
        composeRule.onNodeWithTag("category_CLIMBING").performClick()
        composeRule.onNodeWithTag("save_exercise").performClick()

        composeRule.waitForTag("screen_Train")
        composeRule.onNodeWithTag("segment_Plans").performClick()
        composeRule.onNodeWithTag("new_plan").performClick()
        composeRule.waitForTag("screen_PlanEditor")
        composeRule.textFieldIn("field_plan_name").performTextInput("Power day")
        composeRule.onNodeWithTag("add_exercise_to_block").performClick()
        composeRule.waitForTag("exercise_search")
        composeRule.textFieldIn("exercise_search").performTextInput("Campus")
        composeRule.onNodeWithTag("pick_Campus ladders").performClick()
        composeRule.waitForTag("target_sheet")
        // Sets opens a wheel in place; set it the way TalkBack would.
        composeRule.onNodeWithTag("row_sets").performClick()
        composeRule.onNode(hasContentDescription("Sets")).performSemanticsAction(SemanticsActions.SetProgress) { it(4f) }
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("sets_value", useUnmergedTree = true).assertTextEquals("5") }.isSuccess
        }
        composeRule.onNodeWithTag("target_done").performClick()
        composeRule.onNodeWithTag("save_plan").performClick()

        composeRule.waitForTag("screen_Train")
        composeRule.onNodeWithTag("train_list").performScrollToNode(hasText("Power day"))
        composeRule.onNode(hasText("Power day")).performClick()
        composeRule.waitForTag("template_exercise")
        composeRule.onNode(hasText("Campus ladders")).assertIsDisplayed()
    }

    @Test
    fun dashboardWidgetsCanBeAddedRemovedResizedAndMoved() {
        composeRule.onNodeWithTag("edit_dashboard").performClick()
        composeRule.onNodeWithTag("remove_TODAYS_PLAN").performClick()
        composeRule.onNodeWithTag("resize_WEEK_CLIMBS").performClick()
        // Keep the list short enough that the drag below needs no auto-scroll.
        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("remove_PROJECTS"))
        composeRule.onNodeWithTag("remove_PROJECTS").performClick()
        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("widget_WEEK_CLIMBS"))
        composeRule.onNodeWithTag("add_widget").performClick()
        composeRule.onNodeWithTag("catalog_WEIGHT_TREND").performScrollTo().performClick()

        // Drag the new widget (added last) above the one before it, by its handle.
        composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("drag_WEIGHT_TREND"))
        // Move in steps and let layout settle between them, like a real finger.
        val handle = composeRule.onNodeWithTag("drag_WEIGHT_TREND")
        val from = composeRule.onNodeWithTag("widget_WEIGHT_TREND").fetchSemanticsNode().boundsInRoot
        val to = composeRule.onNodeWithTag("widget_RECENT_CLIMBS").fetchSemanticsNode().boundsInRoot
        // Bring the dragged widget's centre onto the widget above it.
        val distance = to.center.y - from.center.y
        val steps = 30
        handle.performTouchInput { down(center) }
        repeat(steps) {
            handle.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, distance / steps)) }
            composeRule.waitForIdle()
        }
        handle.performTouchInput { up() }
        composeRule.onNodeWithTag("done_editing").performClick()

        assertEquals(0, composeRule.onAllNodesWithTag("widget_TODAYS_PLAN").fetchSemanticsNodes().size)
        val trendTop = composeRule.onNodeWithTag("widget_WEIGHT_TREND").fetchSemanticsNode().boundsInRoot.top
        val recentTop = composeRule.onNodeWithTag("widget_RECENT_CLIMBS").fetchSemanticsNode().boundsInRoot.top
        assert(trendTop < recentTop) {
            "Weight trend should have moved above Recent climbs: from=$from to=$to trend=$trendTop recent=$recentTop"
        }
    }

    @Test
    fun demoModeShowsSampleDataAndLeavesRealDataAlone() {
        composeRule.onNodeWithTag("nav_Journal").performClick()
        composeRule.waitForTag("screen_Journal")
        assertEquals(0, composeRule.onAllNodesWithTag("journal_climb").fetchSemanticsNodes().size)

        openSettings()
        composeRule.onNodeWithTag("demo_mode").performScrollTo().performClick()
        composeRule.onNodeWithTag("nav_Journal").performClick()
        composeRule.waitForTag("journal_climb")
        composeRule.onNodeWithTag("nav_Home").performClick()
        composeRule.onNodeWithTag("home_eyebrow").assertTextContains("DEMO DATA", substring = true)

        openSettings()
        composeRule.onNodeWithTag("demo_mode").performScrollTo().performClick()
        composeRule.onNodeWithTag("nav_Journal").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("journal_climb").fetchSemanticsNodes().isEmpty() }
    }

    @Test
    fun placesHoldProblemsAndGoesOnThemBecomeProjects() {
        composeRule.onNodeWithTag("nav_Journal").performClick()
        composeRule.onNode(hasText("Places") and hasAnyAncestor(hasTestTag("journal_view"))).performClick()
        composeRule.waitForTag("new_place")
        composeRule.onNodeWithTag("new_place").performClick()
        composeRule.waitForTag("screen_PlaceEditor")
        composeRule.textFieldIn("field_place_name").performTextInput("Test Gym")
        composeRule.onNodeWithTag("save_place").performClick()

        composeRule.waitForTag("screen_PlaceDetail")
        composeRule.onNodeWithTag("add_problem").performClick()
        composeRule.waitForTag("screen_ProblemEditor")
        composeRule.textFieldIn("field_problem_name").performTextInput("Pink crimps")
        composeRule.onNodeWithTag("save_problem").performClick()

        composeRule.waitForTag("problem_row")
        composeRule.onNodeWithTag("problem_row").performClick()
        composeRule.waitForTag("screen_ProblemDetail")
        composeRule.onNodeWithTag("log_go").performClick()
        composeRule.waitForTag("screen_LogClimb")
        composeRule.waitUntil(5_000) {
            composeRule.onAllNodes(hasTestTag("where_summary") and hasText("Pink crimps", substring = true)).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag("style_ATTEMPT").performScrollTo().performClick()
        composeRule.onNodeWithTag("effort_7").performScrollTo().performClick()
        composeRule.onNodeWithTag("effort_value").assertTextEquals("7")
        composeRule.onNodeWithTag("save_climb").performClick()

        composeRule.waitForTag("screen_ProblemDetail")
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("problem_goes", useUnmergedTree = true).assertTextEquals("1") }.isSuccess
        }

        composeRule.onNodeWithTag("nav_Progress").performClick()
        composeRule.waitForTag("project_row")
    }

    @Test
    fun aLoggedClimbCanBeEditedAndDeleted() {
        composeRule.onNodeWithTag("log_fab").performClick()
        composeRule.onNodeWithTag("quick_LogClimb").performClick()
        composeRule.waitForTag("screen_LogClimb")
        composeRule.onNodeWithTag("field_name").performScrollTo()
        composeRule.textFieldIn("field_name").performTextInput("Grey arete")
        composeRule.onNodeWithTag("save_climb").performClick()

        composeRule.waitForTag("screen_Home")
        composeRule.onNodeWithTag("nav_Journal").performClick()
        composeRule.waitForTag("journal_climb")
        composeRule.onNodeWithTag("journal_climb").performClick()
        composeRule.waitForTag("screen_LogClimb")
        composeRule.onNodeWithTag("field_name").performScrollTo()
        composeRule.textFieldIn("field_name").performTextReplacement("Grey arete sit")
        composeRule.onNodeWithTag("save_climb").performClick()

        composeRule.waitForTag("journal_climb")
        composeRule.onNodeWithTag("journal_climb").assertTextContains("Grey arete sit", substring = true)
        composeRule.onNodeWithTag("journal_climb").performClick()
        composeRule.waitForTag("delete_climb")
        composeRule.onNodeWithTag("delete_climb").performClick()
        composeRule.onNodeWithTag("confirm_delete").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("journal_climb").fetchSemanticsNodes().isEmpty() }
    }

    private fun openSettings() {
        composeRule.onNodeWithTag("nav_You").performClick()
        composeRule.waitForIdle()
        // The You tab keeps its own back stack, so Settings may still be open from last time.
        if (composeRule.onAllNodesWithTag("screen_Settings").fetchSemanticsNodes().isNotEmpty()) return
        composeRule.onNodeWithTag("open_settings").performClick()
        composeRule.waitForTag("screen_Settings")
    }

    @Test
    fun everyTabHasItsOwnScreen() {
        listOf("Train", "Journal", "Progress", "You", "Home").forEach { name ->
            composeRule.onNodeWithTag("nav_$name").performClick()
            composeRule.onNodeWithTag("screen_$name").assertIsDisplayed()
        }
    }
}

private fun ComposeTestRule.textFieldIn(tag: String): SemanticsNodeInteraction =
    onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

private fun ComposeTestRule.waitForTag(tag: String) {
    waitUntil(timeoutMillis = 5_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}
