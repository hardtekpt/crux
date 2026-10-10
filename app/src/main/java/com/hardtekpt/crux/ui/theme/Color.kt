package com.hardtekpt.crux.ui.theme

import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color

// Crux design system colour tokens. Names follow tokens.json; the Material 3 role
// each one fills is set in the schemes below (see the system's compose-theme.md).

private object Light {
    val surface = Color(0xFFEAF0EF)
    val surfaceDim = Color(0xFFDDE6E4)
    val surfaceContainerLow = Color(0xFFFFFFFF)
    val surfaceContainer = Color(0xFFF4F8F7)
    val surfaceContainerHigh = Color(0xFFE0EBE9)
    val surfaceContainerHighest = Color(0xFFD3E1DE)
    val ink = Color(0xFF121715)
    val muted = Color(0xFF434B49)
    val outline = Color(0xFF66716F)
    val outlineVariant = Color(0xFFC1D0CC)
    val scrim = Color(0xA6121715)
    val primary = Color(0xFF00747E)
    val onPrimary = Color(0xFFFFFFFF)
    val primaryContainer = Color(0xFF9FEDF1)
    val onPrimaryContainer = Color(0xFF00262A)
    val secondary = Color(0xFF8F5600)
    val onSecondary = Color(0xFFFFFFFF)
    val secondaryContainer = Color(0xFFFFD27A)
    val onSecondaryContainer = Color(0xFF2E1C00)
    val success = Color(0xFF1F6B40)
    val onSuccess = Color(0xFFFFFFFF)
    val successContainer = Color(0xFF9FF2BF)
    val onSuccessContainer = Color(0xFF00210F)
    val danger = Color(0xFFB3261E)
    val onDanger = Color(0xFFFFFFFF)
    val dangerContainer = Color(0xFFFFDAD5)
    val onDangerContainer = Color(0xFF410E0B)
    val tape = listOf(
        Color(0xFFFFD9D4),
        Color(0xFFFFE2B8),
        Color(0xFFF0E9A8),
        Color(0xFFC6EFCF),
        Color(0xFFCDE3FB),
        Color(0xFFE3DAF9),
    )
    val chart = listOf(
        Color(0xFF00939D), // verdigris
        Color(0xFFC26A1E), // sandstone
        Color(0xFF5A4AB8), // violet
        Color(0xFFC2457A), // magenta
        Color(0xFF2A78D6), // blue
    )
    val timerWork = Color(0xFFC2410C)
    val timerRest = Color(0xFF2F5BB7)
    val timerCycleRest = Color(0xFF6D45C4)
}

private object Dark {
    val surface = Color(0xFF0B0F0E)
    val surfaceDim = Color(0xFF070A09)
    val surfaceContainerLow = Color(0xFF141C1B)
    val surfaceContainer = Color(0xFF1A2422)
    val surfaceContainerHigh = Color(0xFF243231)
    val surfaceContainerHighest = Color(0xFF2F403E)
    val ink = Color(0xFFE4EAE8)
    val muted = Color(0xFFB6C0BD)
    val outline = Color(0xFF8A9693)
    val outlineVariant = Color(0xFF3A4B48)
    val scrim = Color(0xB3000000)
    val primary = Color(0xFF3FDDE8)
    val onPrimary = Color(0xFF00363A)
    val primaryContainer = Color(0xFF00565D)
    val onPrimaryContainer = Color(0xFFA8F5F8)
    val secondary = Color(0xFFFFBD45)
    val onSecondary = Color(0xFF452B00)
    val secondaryContainer = Color(0xFF714600)
    val onSecondaryContainer = Color(0xFFFFE0A8)
    val success = Color(0xFF5EE391)
    val onSuccess = Color(0xFF00391C)
    val successContainer = Color(0xFF0B5A33)
    val onSuccessContainer = Color(0xFFB5F7CF)
    val danger = Color(0xFFFFB4AB)
    val onDanger = Color(0xFF690005)
    val dangerContainer = Color(0xFF93000A)
    val onDangerContainer = Color(0xFFFFDAD6)
    val tape = listOf(
        Color(0xFF5A2420),
        Color(0xFF553615),
        Color(0xFF4B4414),
        Color(0xFF1E4A2C),
        Color(0xFF1E3A5C),
        Color(0xFF3A2F5E),
    )
    val chart = listOf(
        Color(0xFF1FA3AD),
        Color(0xFFC97A1E),
        Color(0xFF9085E9),
        Color(0xFFD55181),
        Color(0xFF3987E5),
    )
    val timerWork = Color(0xFFFF8A65)
    val timerRest = Color(0xFF8FB3FF)
    val timerCycleRest = Color(0xFFC9A7FF)
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
        surfaceContainerLowest = surfaceContainerLow, surfaceContainerLow = surfaceContainerLow,
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
        surfaceContainerLowest = surfaceContainerLow, surfaceContainerLow = surfaceContainerLow,
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
    /**
     * Categorical colours for charts with more than one series (donut slices). Fixed order,
     * never cycled; anything past the last slot folds into "Other". Both sets were run
     * through the colour-blind and contrast checks against the Crux card surfaces.
     */
    val chart: List<Color>,
    /** Timer phases: work warm, rest cool, the longer rest between cycles apart from both. */
    val timerWork: Color,
    val timerRest: Color,
    val timerCycleRest: Color,
    /** Boards' accent, beside gyms (primary) and crags (secondary). */
    val board: Color,
    /** The stripe down the side of the climber card on You. Decoration, not route tape. */
    val profileTape: List<Color>,
)

private val Board = Color(0xFFB39DDB)
private val ProfileTape = listOf(Color(0xFFF2C94C), Color(0xFF4CAF50), Color(0xFF2F80ED), Color(0xFF9B51E0), Color(0xFFEB5757))

internal val CruxLightExtendedColors = with(Light) {
    CruxColors(success, onSuccess, successContainer, onSuccessContainer, tape, chart, timerWork, timerRest, timerCycleRest, Board, ProfileTape)
}

internal val CruxDarkExtendedColors = with(Dark) {
    CruxColors(success, onSuccess, successContainer, onSuccessContainer, tape, chart, timerWork, timerRest, timerCycleRest, Board, ProfileTape)
}
