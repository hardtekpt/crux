package com.hardtekpt.crux

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextEquals
import androidx.compose.ui.test.junit4.v2.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import dagger.hilt.android.testing.HiltAndroidRule
import dagger.hilt.android.testing.HiltAndroidTest
import org.junit.Before
import org.junit.Rule
import org.junit.Test

/** End-to-end Compose test on a device: real Activity, Hilt graph and Room (in memory). */
@HiltAndroidTest
class MainActivityTest {

    @get:Rule(order = 0) val hiltRule = HiltAndroidRule(this)
    @get:Rule(order = 1) val composeRule = createAndroidComposeRule<MainActivity>()

    @Before
    fun setUp() = hiltRule.inject()

    @Test
    fun loggingAClimbUpdatesTheHomeDashboard() {
        composeRule.onNodeWithTag("total_climbs").assertTextEquals("0")

        composeRule.onNodeWithTag("log_climb").performClick()

        composeRule.waitUntil(timeoutMillis = 5_000) {
            runCatching { composeRule.onNodeWithTag("total_climbs").assertTextEquals("1") }.isSuccess
        }
    }

    @Test
    fun bottomBarReachesEveryTopLevelScreen() {
        listOf("Workouts", "Journal", "Stats", "Home").forEach { name ->
            composeRule.onNodeWithTag("nav_$name").performClick()
            composeRule.onNodeWithTag("screen_$name").assertIsDisplayed()
        }
    }
}
