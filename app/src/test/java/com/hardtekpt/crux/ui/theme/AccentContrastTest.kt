package com.hardtekpt.crux.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.hardtekpt.crux.data.prefs.Accent
import org.junit.Assert.assertTrue
import org.junit.Test

/** Every accent, dark and light, keeps text and controls readable (WCAG contrast ratios). */
class AccentContrastTest {

    private fun contrast(a: Color, b: Color): Double {
        val (hi, lo) = listOf(a.luminance(), b.luminance()).sortedDescending()
        return (hi + 0.05) / (lo + 0.05)
    }

    private fun assertContrast(min: Double, fg: Color, bg: Color, what: String) {
        val ratio = contrast(fg, bg)
        assertTrue("$what: %.2f, needs %.1f".format(ratio, min), ratio >= min)
    }

    @Test
    fun accentsAreReadable() {
        Accent.entries.forEach { accent ->
            listOf(true, false).forEach { dark ->
                val s = cruxColorScheme(dark, accent)
                val name = "$accent ${if (dark) "dark" else "light"}"
                assertContrast(4.5, s.onPrimary, s.primary, "$name onPrimary on primary")
                assertContrast(4.5, s.onPrimaryContainer, s.primaryContainer, "$name onPrimaryContainer on primaryContainer")
                assertContrast(3.0, s.primary, s.surface, "$name primary on surface")
                assertContrast(3.0, s.primary, s.surfaceContainerLow, "$name primary on cards")
                assertContrast(7.0, s.onSurface, s.surface, "$name text on surface")
                assertContrast(4.5, s.onSurfaceVariant, s.surfaceContainerHighest, "$name muted text on the highest container")
            }
        }
    }
}
