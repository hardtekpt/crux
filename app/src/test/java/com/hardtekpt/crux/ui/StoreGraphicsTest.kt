package com.hardtekpt.crux.ui

import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.captureRoboImage
import com.hardtekpt.crux.R
import com.hardtekpt.crux.ui.theme.CruxTheme
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The store icon (512 px) and feature graphic (1024 × 500 px) in fastlane/, drawn from the
 * launcher icon so they never drift from it. Checked like any screenshot golden.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = "w1200dp-h1200dp-mdpi")
class StoreGraphicsTest {

    @get:Rule val compose = createComposeRule()

    @Test
    fun icon() {
        compose.setContent {
            Box(Modifier.requiredSize(512.dp).background(LAUNCHER_BACKGROUND).testTag("graphic")) {
                Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(512.dp))
            }
        }
        compose.onNodeWithTag("graphic").captureRoboImage("$IMAGES/icon.png")
    }

    @Test
    fun featureGraphic() {
        compose.setContent {
            CruxTheme(darkTheme = true) {
                Row(
                    Modifier.testTag("graphic").requiredSize(1024.dp, 500.dp).background(LAUNCHER_BACKGROUND).padding(horizontal = 72.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(32.dp),
                ) {
                    Image(painterResource(R.drawable.ic_launcher_foreground), contentDescription = null, Modifier.size(300.dp))
                    Column {
                        Text("Crux", style = MaterialTheme.typography.displayLarge.copy(fontSize = 120.sp), color = Color.White)
                        Text(
                            "Climbing journal and training",
                            style = MaterialTheme.typography.headlineMedium.copy(fontSize = 40.sp),
                            color = MaterialTheme.colorScheme.primary,
                        )
                    }
                }
            }
        }
        compose.onNodeWithTag("graphic").captureRoboImage("$IMAGES/featureGraphic.png")
    }

    private companion object {
        const val IMAGES = "../fastlane/metadata/android/en-US/images"
        val LAUNCHER_BACKGROUND = Color(0xFF0B0F0E)
    }
}
