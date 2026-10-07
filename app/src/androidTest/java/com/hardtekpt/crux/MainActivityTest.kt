package com.hardtekpt.crux

import androidx.compose.ui.semantics.SemanticsActions
import androidx.compose.ui.test.SemanticsNodeInteraction
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasAnyAncestor
import androidx.compose.ui.test.hasContentDescription
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
import androidx.compose.ui.test.performSemanticsAction
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipe
import androidx.compose.ui.test.swipeLeft
import androidx.compose.ui.test.swipeRight
import com.hardtekpt.crux.data.seed.StarterData
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import javax.inject.Inject
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** End-to-end Compose tests on a device: real Activity, Hilt graph and Room (in memory). */
@HiltAndroidTest
class MainActivityTest {

    @get:Rule(order = 0)
    val hiltRule = HiltAndroidRule(this)

    @get:Rule(order = 1)
    val composeRule = createAndroidComposeRule<MainActivity>()

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
        // Measurements sit below the climber card, consistency and records on the profile.
        // Scrolling only until the tile shows can leave it under the floating nav bar, where a
        // touch would land on the bar, so trigger the tile's click action directly.
        composeRule.onNodeWithTag("you_list").performScrollToNode(hasTestTag("log_weight"))
        composeRule.onNodeWithTag("log_weight").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForTag("screen_LogWeight")
        composeRule.onNodeWithTag("ruler_weight_value").performClick()
        composeRule.textFieldIn("field_type_value").performTextReplacement("72.5")
        composeRule.onNodeWithTag("confirm_type_value").performClick()
        composeRule.onNodeWithTag("save_weight").performClick()

        composeRule.waitForTag("screen_You")
        composeRule.onNodeWithTag("you_list").performScrollToNode(hasTestTag("log_weight"))
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("you_weight", useUnmergedTree = true).assertTextEquals("72.5") }.isSuccess
        }
        // The full history is on Measurements.
        openFromProfileMenu("menu_measurements", "screen_Measurements")
        composeRule.onNodeWithTag("measurements_list").performScrollToNode(hasTestTag("weight_row"))
        assertEquals(1, composeRule.onAllNodesWithTag("weight_row").fetchSemanticsNodes().size)
    }

    @Test
    fun bodyStatsUpdateFromTheirTilesAndGiveTheApeIndex() {
        composeRule.onNodeWithTag("nav_You").performClick()
        openFromProfileMenu("menu_measurements", "screen_Measurements")
        listOf("HEIGHT" to "178", "WINGSPAN" to "184").forEach { (type, value) ->
            composeRule.onNodeWithTag("measurements_list").performScrollToNode(hasTestTag("stat_$type"))
            composeRule.onNodeWithTag("stat_$type").performClick()
            composeRule.onNodeWithTag("ruler_measurement_value").performClick()
            composeRule.textFieldIn("field_type_value").performTextReplacement(value)
            composeRule.onNodeWithTag("confirm_type_value").performClick()
            composeRule.onNodeWithTag("save_measurement").performClick()
        }
        composeRule.onNodeWithTag("measurements_list").performScrollToNode(hasTestTag("you_ape_index"))
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("you_ape_index").assertTextEquals("+6 cm") }.isSuccess
        }
    }

    @Test
    fun circumferencesKeepLeftAndRightApart() {
        composeRule.onNodeWithTag("nav_You").performClick()
        openFromProfileMenu("menu_circumferences", "screen_Circumferences")
        listOf("FOREARM" to "29", "FOREARM_RIGHT" to "31").forEach { (type, value) ->
            composeRule.onNodeWithTag("circumferences_list").performScrollToNode(hasTestTag("stat_$type"))
            composeRule.onNodeWithTag("stat_$type").performClick()
            composeRule.onNodeWithTag("ruler_measurement_value").performClick()
            composeRule.textFieldIn("field_type_value").performTextReplacement(value)
            composeRule.onNodeWithTag("confirm_type_value").performClick()
            composeRule.onNodeWithTag("save_measurement").performClick()
        }
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("you_forearm_right", useUnmergedTree = true).assertTextEquals("31 cm") }.isSuccess
        }
        composeRule.onNodeWithTag("you_forearm", useUnmergedTree = true).assertTextEquals("29 cm")
        composeRule.onNodeWithTag("diff_Forearm", useUnmergedTree = true).assertTextEquals("R +2 cm")
    }

    @Test
    fun theGradeConverterTranslatesBetweenSystems() {
        composeRule.onNodeWithTag("nav_You").performClick()
        openFromProfileMenu("menu_converter", "screen_GradeConverter")
        composeRule.onNodeWithTag("convert_7a+").performClick()
        composeRule.waitUntil(5_000) {
            runCatching { composeRule.onNodeWithTag("value_YDS", useUnmergedTree = true).assertTextEquals("5.12a") }.isSuccess
        }
        // Tapping a result converts from that system instead, at the same difficulty.
        composeRule.onNodeWithTag("converter_list").performScrollToNode(hasTestTag("result_YDS"))
        composeRule.onNodeWithTag("result_YDS").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("converter_list").performScrollToNode(hasTestTag("converter_grade"))
        composeRule.onNodeWithTag("converter_grade").assertTextEquals("5.12a")
        composeRule.onNodeWithTag("converter_list").performScrollToNode(hasTestTag("converter_discipline"))
        composeRule.onNodeWithTag("segment_Boulders").performSemanticsAction(SemanticsActions.OnClick)
        composeRule.onNodeWithTag("converter_list").performScrollToNode(hasTestTag("result_V"))
        composeRule.onNodeWithTag("value_V", useUnmergedTree = true).assertTextEquals("V5")
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
        composeRule.onNodeWithTag("add_exercise_to_block").performScrollTo().performClick()
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
        // Bring the dragged widget's centre well inside the widget above it (its upper third),
        // so a slow run that drops a few move events still lands on it.
        val distance = (to.top + to.height / 3f) - from.center.y
        val steps = 40
        handle.performTouchInput { down(center) }
        repeat(steps) {
            handle.performTouchInput { moveBy(androidx.compose.ui.geometry.Offset(0f, distance / steps)) }
            composeRule.waitForIdle()
        }
        // Hold still a moment so the swap lays out before the finger lifts.
        composeRule.mainClock.advanceTimeBy(200)
        composeRule.waitForIdle()
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
        composeRule.onNodeWithTag("nav_You").performClick()
        openFromProfileMenu("menu_places", "screen_Places")
        composeRule.waitForTag("new_place")
        composeRule.onNodeWithTag("new_place").performClick()
        composeRule.waitForTag("screen_PlaceEditor")
        composeRule.textFieldIn("field_place_name").performTextInput("Test Gym")
        composeRule.onNodeWithTag("place_favourite").performClick()
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

        // The favourite shows as a quick pick on a new log.
        composeRule.onNodeWithTag("log_fab").performClick()
        composeRule.onNodeWithTag("quick_LogClimb").performClick()
        composeRule.waitForTag("favourite_Test Gym")
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

    @Test
    fun swipingSidewaysMovesBetweenAPagesTabs() {
        composeRule.onNodeWithTag("nav_Train").performClick()
        composeRule.waitForTag("screen_Train")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_Train").performTouchInput { swipeLeft(startX = centerX + width * 0.35f, endX = centerX - width * 0.35f) }
        composeRule.waitForTag("exercise_row")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("screen_Train").performTouchInput {
            swipe(
                start = androidx.compose.ui.geometry.Offset(width * 0.15f, height * 0.08f),
                end = androidx.compose.ui.geometry.Offset(
                    width * 0.85f,
                    height * 0.08f,
                ),
            )
        }
        composeRule.waitUntil(10_000) { composeRule.onAllNodesWithTag("exercise_row").fetchSemanticsNodes().isEmpty() }
        // The main tab itself didn't change.
        composeRule.onNodeWithTag("screen_Train").assertIsDisplayed()
    }

    @Test
    fun aTaggedNoteFromTheLogButtonShowsInTheJournalAndNotes() {
        composeRule.onNodeWithTag("log_fab").performClick()
        composeRule.onNodeWithTag("quick_AddNote").performClick()
        composeRule.waitForTag("screen_NoteEditor")
        composeRule.onNodeWithTag("field_note").performTextInput("Left ring finger tweak")
        composeRule.onNodeWithTag("new_tag").performClick()
        composeRule.textFieldIn("field_tag").performTextInput("Injury")
        composeRule.onNodeWithTag("confirm_tag").performClick()
        composeRule.waitForTag("tag_injury")
        composeRule.onNodeWithTag("save_note").performClick()

        // The journal timeline shows it, and the Notes filter keeps it.
        composeRule.onNodeWithTag("nav_Journal").performClick()
        composeRule.waitForTag("journal_note")
        composeRule.onNodeWithTag("journal_filter_Notes").performClick()
        composeRule.onNode(hasTestTag("journal_note") and hasText("INJURY", substring = true)).assertIsDisplayed()
        composeRule.onNodeWithTag("journal_filter_Climbs").performClick()
        composeRule.waitUntil(5_000) { composeRule.onAllNodesWithTag("journal_note").fetchSemanticsNodes().isEmpty() }
        // Search finds it by its text; a tag filter from the sheet keeps it.
        composeRule.onNodeWithTag("journal_filter_All").performClick()
        composeRule.onNodeWithTag("journal_search").performTextInput("ring finger")
        composeRule.waitForTag("journal_note")
        composeRule.onNodeWithTag("journal_search_clear").performClick()
        composeRule.onNodeWithTag("journal_open_filters").performClick()
        composeRule.onNodeWithTag("tag_filter_injury").performClick()
        composeRule.onNodeWithTag("journal_apply_filters").performClick()
        composeRule.waitForTag("applied_#injury")
        composeRule.onNodeWithTag("journal_note").assertIsDisplayed()

        composeRule.onNodeWithTag("nav_You").performClick()
        openFromProfileMenu("menu_notes", "screen_Notes")
        composeRule.onNode(hasTestTag("note_card") and hasText("Left ring finger tweak", substring = true)).assertIsDisplayed()
    }

    /** Opens a page from the You tab's menu; the menu can sit under the nav bar, so use its click action. */
    private fun openFromProfileMenu(tag: String, screen: String) {
        composeRule.waitForTag("screen_You")
        composeRule.onNodeWithTag("you_list").performScrollToNode(hasTestTag(tag))
        composeRule.onNodeWithTag(tag).performSemanticsAction(SemanticsActions.OnClick)
        composeRule.waitForTag(screen)
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

private fun ComposeTestRule.textFieldIn(tag: String): SemanticsNodeInteraction = onNode(hasSetTextAction() and hasAnyAncestor(hasTestTag(tag)))

private fun ComposeTestRule.waitForTag(tag: String) {
    waitUntil(timeoutMillis = 10_000) { onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty() }
}
