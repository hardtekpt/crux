package com.hardtekpt.crux.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsSelected
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.ui.home.HomeContent
import com.hardtekpt.crux.ui.home.HomeUiState
import com.hardtekpt.crux.ui.navigation.TopLevelDestination
import com.hardtekpt.crux.ui.theme.CruxTheme
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Compose UI test that runs on the JVM through Robolectric, no emulator needed. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CruxAppNavigationTest {

    @get:Rule val composeRule = createComposeRule()

    @Before
    fun setUp() {
        composeRule.setContent {
            CruxTheme(dynamicColor = false) {
                CruxApp(home = { HomeContent(HomeUiState(isLoading = false), onLogClimb = {}) })
            }
        }
    }

    @Test
    fun startsOnHome() {
        composeRule.onNodeWithTag("screen_Home").assertIsDisplayed()
        composeRule.onNodeWithTag("nav_Home").assertIsSelected()
    }

    @Test
    fun bottomBarSwitchesBetweenTopLevelScreens() {
        TopLevelDestination.entries.forEach { destination ->
            composeRule.onNodeWithTag("nav_${destination.name}").performClick()
            composeRule.onNodeWithTag("screen_${destination.label}").assertIsDisplayed()
            composeRule.onNodeWithTag("nav_${destination.name}").assertIsSelected()
        }
    }
}
