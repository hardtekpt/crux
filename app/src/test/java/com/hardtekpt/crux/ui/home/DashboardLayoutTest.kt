package com.hardtekpt.crux.ui.home

import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.v2.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.data.dashboard.DashboardWidget
import com.hardtekpt.crux.data.dashboard.WidgetType
import com.hardtekpt.crux.ui.theme.CruxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Every widget, in every size it comes in, lays out on Home without crashing. A build after 0.4.0 had a
 * Home that asked full-width widgets for their intrinsic height, which some can't answer.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DashboardLayoutTest {
    @get:Rule val composeRule = createComposeRule()

    @Test
    fun everyWidgetInEverySizeLaysOut() {
        val widgets = WidgetType.entries.flatMap { type -> type.sizes.map { size -> DashboardWidget(type = type, size = size) } }
        composeRule.setContent {
            CruxTheme { HomeContent(uiState = HomeUiState(isLoading = false), dashboard = DashboardState(widgets = widgets)) }
        }
        widgets.forEach { widget ->
            composeRule.onNodeWithTag("home_list").performScrollToNode(hasTestTag("widget_${widget.type.name}"))
        }
    }
}
