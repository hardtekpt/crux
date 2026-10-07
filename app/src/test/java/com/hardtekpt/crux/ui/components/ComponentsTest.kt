package com.hardtekpt.crux.ui.components

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.ui.home.HomeContent
import com.hardtekpt.crux.ui.home.HomeUiState
import com.hardtekpt.crux.ui.theme.CruxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Compose component tests on the JVM through Robolectric, no emulator needed. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class ComponentsTest {

    @get:Rule val composeRule = createComposeRule()

    @Test
    fun stepperHoldsAtItsBounds() {
        composeRule.setContent {
            var value by remember { mutableIntStateOf(1) }
            CruxTheme { CruxStepper(value = value, onValueChange = { value = it }, range = 1..3, unit = "goes") }
        }
        composeRule.onNodeWithTag("stepper_minus").assertIsNotEnabled()
        repeat(2) { composeRule.onNodeWithTag("stepper_plus").performClick() }
        composeRule.onNodeWithTag("stepper_value").assertTextEquals("3")
        composeRule.onNodeWithTag("stepper_plus").assertIsNotEnabled()
        composeRule.onNodeWithTag("stepper_minus").assertIsEnabled()
    }

    @Test
    fun segmentedButtonsKeepExactlyOneSelected() {
        composeRule.setContent {
            var selected by remember { mutableStateOf(Discipline.BOULDER) }
            CruxTheme {
                CruxSegmentedButtons(Discipline.entries, selected, { it.label }, { selected = it })
            }
        }
        composeRule.onNodeWithTag("segment_Route").performClick()
        composeRule.onNodeWithTag("segment_Route").assertIsSelected()
    }

    @Test
    fun gradeBadgePrintsTheGradeAsGiven() {
        composeRule.setContent { CruxTheme { GradeBadge("7B+", GradeState.Sent) } }
        composeRule.onNodeWithText("7B+").assertExists()
    }

    @Test
    fun textFieldShowsItsErrorInTheHelperLine() {
        composeRule.setContent {
            CruxTheme {
                CruxTextField(
                    label = "Bodyweight",
                    value = "900",
                    onValueChange = {},
                    helper = "kg",
                    error = "Enter a weight between 20 and 300 kg",
                )
            }
        }
        composeRule.onNodeWithText("Enter a weight between 20 and 300 kg").assertExists()
    }

    @Test
    fun chartsRenderWithTheirLabels() {
        composeRule.setContent {
            CruxTheme {
                androidx.compose.foundation.layout.Column {
                    com.hardtekpt.crux.ui.charts.TimeSeriesChart(
                        points = listOf(
                            com.hardtekpt.crux.ui.charts.SeriesPoint(1.0, 72.0),
                            com.hardtekpt.crux.ui.charts.SeriesPoint(2.0, 72.5),
                        ),
                        formatX = { "Day ${it.toInt()}" },
                        formatY = { it.toString() },
                        description = "Bodyweight trend",
                    )
                    com.hardtekpt.crux.ui.charts.DonutChart(
                        slices = listOf(
                            com.hardtekpt.crux.ui.charts.SliceDatum("Flash", 3.0),
                            com.hardtekpt.crux.ui.charts.SliceDatum("Redpoint", 1.0),
                        ),
                        centerValue = "4",
                        centerLabel = "sends",
                        formatValue = { it.toInt().toString() },
                    )
                }
            }
        }
        composeRule.onNodeWithTag("time_series_chart").assertExists()
        composeRule.onNodeWithText("Redpoint").performClick()
        // Selecting a slice puts its value in the middle of the donut.
        composeRule.onNodeWithText("REDPOINT").assertExists()
    }

    @Test
    fun emptyHomeNamesTheFirstAction() {
        composeRule.setContent { CruxTheme { HomeContent(HomeUiState(isLoading = false)) } }
        composeRule.onNodeWithTag("week_climbs").assertTextEquals("0")
        composeRule.onNodeWithTag("home_list")
            .performScrollToNode(hasText("No climbs logged yet", substring = true))
        composeRule.onNodeWithText("No climbs logged yet", substring = true).assertExists()
    }
}
