package com.hardtekpt.crux

import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
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
import androidx.compose.ui.test.performTouchInput
import com.hardtekpt.crux.data.seed.StarterDataSeeder
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

    @Inject lateinit var seeder: StarterDataSeeder

    @Before
    fun setUp() {
        hiltRule.inject()
        runBlocking { seeder.seed(includeSampleData = false) }
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
        composeRule.textFieldIn("field_weight").performTextInput("72.5")
        composeRule.onNodeWithTag("save_weight").performClick()

        composeRule.waitForTag("weight_row")
        composeRule.onNodeWithTag("you_weight").assertTextEquals("72.5 kg")
        assertEquals(1, composeRule.onAllNodesWithTag("weight_row").fetchSemanticsNodes().size)
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
        composeRule.textFieldIn("exercise_search").performTextInput("Campus")
        composeRule.onNodeWithTag("pick_Campus ladders").performClick()
        composeRule.waitForTag("target_sheet")
        composeRule.onNodeWithTag("sets_plus").performClick()
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
        assert(trendTop < recentTop) { "Weight trend should have moved above Recent climbs" }
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
