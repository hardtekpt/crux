package com.hardtekpt.crux.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import com.hardtekpt.crux.data.prefs.Accent

private val LocalCruxColors = staticCompositionLocalOf { CruxDarkExtendedColors }
private val LocalCruxType = staticCompositionLocalOf { CruxType() }
private val LocalCruxDark = staticCompositionLocalOf { true }

/**
 * The Crux design system on Material 3. Dynamic colour is deliberately off: the
 * palette is the brand, in the [accent] the climber picked. Dark is the app default;
 * callers pass the resolved mode.
 */
@Composable
fun CruxTheme(darkTheme: Boolean = true, accent: Accent = Accent.TEAL, content: @Composable () -> Unit) {
    val colorScheme = remember(darkTheme, accent) { cruxColorScheme(darkTheme, accent) }
    CompositionLocalProvider(
        LocalCruxColors provides if (darkTheme) CruxDarkExtendedColors else CruxLightExtendedColors,
        LocalCruxType provides CruxType(),
        LocalCruxDark provides darkTheme,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = CruxTypography,
            shapes = CruxShapes,
            content = content,
        )
    }
}

/** Entry point for the tokens Material 3 has no slot for. */
object CruxTheme {
    val colors: CruxColors
        @Composable @ReadOnlyComposable
        get() = LocalCruxColors.current
    val type: CruxType
        @Composable @ReadOnlyComposable
        get() = LocalCruxType.current

    /** Whether the dark scheme is in use, whatever the climber's theme setting resolved to. */
    val isDark: Boolean
        @Composable @ReadOnlyComposable
        get() = LocalCruxDark.current
    val space = CruxSpace
    val size = CruxSize
    val shape = CruxShape
}
