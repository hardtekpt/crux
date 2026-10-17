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
                // Control edges (fields, outlined buttons, unselected segments) and the secondary accent.
                assertContrast(3.0, s.outline, s.surface, "$name control outline on surface")
                assertContrast(3.0, s.outline, s.surfaceContainerLow, "$name control outline on cards")
                assertContrast(4.5, s.secondary, s.surface, "$name secondary text on surface")
            }
        }
    }

    /**
     * Colours outside the Material roles that carry meaning: sends, timer phases, boards and the
     * hold roles on a diagram. Text in them needs 4.5:1, marks 3:1, on the screen and on cards.
     */
    @Test
    fun meaningfulColoursAreReadable() {
        listOf(true to CruxDarkExtendedColors, false to CruxLightExtendedColors).forEach { (dark, c) ->
            val s = cruxColorScheme(dark, Accent.TEAL)
            val name = if (dark) "dark" else "light"
            listOf(s.surface, s.surfaceContainerLow).forEach { bg ->
                assertContrast(4.5, c.success, bg, "$name sent text")
                assertContrast(3.0, c.timerWork, bg, "$name work phase")
                assertContrast(3.0, c.timerRest, bg, "$name rest phase")
                assertContrast(3.0, c.timerCycleRest, bg, "$name rest between cycles")
                assertContrast(3.0, c.board, bg, "$name board accent")
                assertContrast(3.0, c.holdFinish, bg, "$name finish hold")
            }
        }
    }
}
