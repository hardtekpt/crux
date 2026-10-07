package com.hardtekpt.crux.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf

private val LocalCruxColors = staticCompositionLocalOf { CruxDarkExtendedColors }
private val LocalCruxType = staticCompositionLocalOf { CruxType() }

/**
 * The Crux design system on Material 3. Dynamic colour is deliberately off: the
 * palette is the brand. Dark is the app default; callers pass the resolved mode.
 */
@Composable
fun CruxTheme(darkTheme: Boolean = true, content: @Composable () -> Unit) {
    CompositionLocalProvider(
        LocalCruxColors provides if (darkTheme) CruxDarkExtendedColors else CruxLightExtendedColors,
        LocalCruxType provides CruxType(),
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) CruxDarkColorScheme else CruxLightColorScheme,
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
    val space = CruxSpace
    val size = CruxSize
    val shape = CruxShape
}
