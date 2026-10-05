package com.hardtekpt.crux.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Crux design system colour tokens. Names follow tokens.json; the Material 3 role
// each one fills is set in the schemes below (see the system's compose-theme.md).

private object Light {
    val surface = Color(0xFFF7F9F8)
    val surfaceDim = Color(0xFFE6EBEA)
    val surfaceContainerLow = Color(0xFFF2F5F4)
    val surfaceContainer = Color(0xFFECF0EF)
    val surfaceContainerHigh = Color(0xFFE5EAE9)
    val surfaceContainerHighest = Color(0xFFDDE4E2)
    val ink = Color(0xFF121715)
    val muted = Color(0xFF434B49)
    val outline = Color(0xFF6E7876)
    val outlineVariant = Color(0xFFC6CECC)
    val scrim = Color(0xA6121715)
    val primary = Color(0xFF0B6B72)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFFB6ECEF)
    val onPrimaryContainer = Color(0xFF00272A)
    val secondary = Color(0xFF8A5A16)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFFFDFA8)
    val onSecondaryContainer = Color(0xFF2E1C00)
    val success = Color(0xFF1F6B40)
    val onSuccess = Color(0xFFFFFFFF)
    val successContainer = Color(0xFFB7F0CB)
    val onSuccessContainer = Color(0xFF00210F)
    val danger = Color(0xFFB3261E)
    val onDanger = Color(0xFFFFFFFF)
    val dangerContainer = Color(0xFFFFDAD5)
    val onDangerContainer = Color(0xFF410E0B)
    val tape = listOf(
        Color(0xFFFFD9D4), Color(0xFFFFE2B8), Color(0xFFF0E9A8),
        Color(0xFFC6EFCF), Color(0xFFCDE3FB), Color(0xFFE3DAF9),
    )
}

private object Dark {
    val surface = Color(0xFF0F1413)
    val surfaceDim = Color(0xFF0A0E0D)
    val surfaceContainerLow = Color(0xFF141A19)
    val surfaceContainer = Color(0xFF18201E)
    val surfaceContainerHigh = Color(0xFF222B29)
    val surfaceContainerHighest = Color(0xFF2C3634)
    val ink = Color(0xFFE4EAE8)
    val muted = Color(0xFFB6C0BD)
    val outline = Color(0xFF818C89)
    val outlineVariant = Color(0xFF3E4745)
    val scrim = Color(0xB3000000)
    val primary = Color(0xFF67D6DE)
    val onPrimary = Color(0xFF00363A)
    val primaryContainer = Color(0xFF0A5056)
    val onPrimaryContainer = Color(0xFFB6ECEF)
    val secondary = Color(0xFFF2C070)
    val onSecondary = Color(0xFF452B00)
    val secondaryContainer = Color(0xFF6A4408)
    val onSecondaryContainer = Color(0xFFFFDFA8)
    val success = Color(0xFF7CDBA0)
    val onSuccess = Color(0xFF00391C)
    val successContainer = Color(0xFF0C5231)
    val onSuccessContainer = Color(0xFFB7F0CB)
    val danger = Color(0xFFFFB4AB)
    val onDanger = Color(0xFF690005)
    val dangerContainer = Color(0xFF93000A)
    val onDangerContainer = Color(0xFFFFDAD6)
    val tape = listOf(
        Color(0xFF5A2420), Color(0xFF553615), Color(0xFF4B4414),
        Color(0xFF1E4A2C), Color(0xFF1E3A5C), Color(0xFF3A2F5E),
    )
}

internal val CruxLightColorScheme = with(Light) {
    lightColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = secondary, onTertiary = onSecondary,
        tertiaryContainer = secondaryContainer, onTertiaryContainer = onSecondaryContainer,
        error = danger, onError = onDanger,
        errorContainer = dangerContainer, onErrorContainer = onDangerContainer,
        background = surface, onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = surfaceContainerHighest, onSurfaceVariant = muted,
        surfaceDim = surfaceDim, surfaceBright = surface,
        surfaceContainerLowest = surface, surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        outline = outline, outlineVariant = outlineVariant, scrim = scrim,
    )
}

internal val CruxDarkColorScheme = with(Dark) {
    darkColorScheme(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = secondary, onTertiary = onSecondary,
        tertiaryContainer = secondaryContainer, onTertiaryContainer = onSecondaryContainer,
        error = danger, onError = onDanger,
        errorContainer = dangerContainer, onErrorContainer = onDangerContainer,
        background = surface, onBackground = ink,
        surface = surface, onSurface = ink,
        surfaceVariant = surfaceContainerHighest, onSurfaceVariant = muted,
        surfaceDim = surfaceDim, surfaceBright = surfaceContainerHighest,
        surfaceContainerLowest = surfaceDim, surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        outline = outline, outlineVariant = outlineVariant, scrim = scrim,
    )
}

/** Crux colours with no Material 3 role. Read through `CruxTheme.colors`. */
@Immutable
data class CruxColors(
    val success: Color,
    val onSuccess: Color,
    val successContainer: Color,
    val onSuccessContainer: Color,
    /** Route tape: red, orange, yellow, green, blue, purple. Only for climber-tagged chips. */
    val tape: List<Color>,
)

internal val CruxLightExtendedColors = with(Light) {
    CruxColors(success, onSuccess, successContainer, onSuccessContainer, tape)
}

internal val CruxDarkExtendedColors = with(Dark) {
    CruxColors(success, onSuccess, successContainer, onSuccessContainer, tape)
}
