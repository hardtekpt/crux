package com.hardtekpt.crux.ui.theme

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Immutable
import androidx.compose.ui.graphics.Color
import com.hardtekpt.crux.data.prefs.Accent

// Crux design system colour tokens. Names follow tokens.json; the Material 3 role
// each one fills is set in the schemes below (see the system's compose-theme.md).

private object Light {
    val ink = Color(0xFF121715)
    val muted = Color(0xFF434B49)
    val outline = Color(0xFF66716F)
    val scrim = Color(0xA6121715)
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
    val ink = Color(0xFFE4EAE8)
    val muted = Color(0xFFB6C0BD)
    val outline = Color(0xFF8A9693)
    val scrim = Color(0xB3000000)
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

/** The colours an accent sets in one mode: its own roles, and surfaces tinted towards it. */
private class AccentTones(
    val primary: Color,
    val onPrimary: Color,
    val primaryContainer: Color,
    val onPrimaryContainer: Color,
    val surface: Color,
    val surfaceDim: Color,
    val surfaceContainerLow: Color,
    val surfaceContainer: Color,
    val surfaceContainerHigh: Color,
    val surfaceContainerHighest: Color,
    val outlineVariant: Color,
)

private fun tones(vararg argb: Long) = argb.map { Color(it) }.let {
    AccentTones(it[0], it[1], it[2], it[3], it[4], it[5], it[6], it[7], it[8], it[9], it[10])
}

// Teal is the brand palette. The others turn its tones to their hue in OKLCH: same lightness, the
// same share of the chroma the hue allows (surfaces keep teal's slight tint). AccentContrastTest
// checks every pair. Order: primary, onPrimary, primaryContainer, onPrimaryContainer, surface,
// surfaceDim, surfaceContainerLow, surfaceContainer, surfaceContainerHigh, surfaceContainerHighest,
// outlineVariant.
private fun Accent.tones(dark: Boolean): AccentTones = when (this) {
    Accent.TEAL -> if (dark) {
        tones(0xFF3FDDE8, 0xFF00363A, 0xFF00565D, 0xFFA8F5F8, 0xFF0B0F0E, 0xFF070A09, 0xFF141C1B, 0xFF1A2422, 0xFF243231, 0xFF2F403E, 0xFF3A4B48)
    } else {
        tones(0xFF00747E, 0xFFFFFFFF, 0xFF9FEDF1, 0xFF00262A, 0xFFEAF0EF, 0xFFDDE6E4, 0xFFFFFFFF, 0xFFF4F8F7, 0xFFE0EBE9, 0xFFD3E1DE, 0xFFC1D0CC)
    }

    Accent.BLUE -> if (dark) {
        tones(0xFF80B0F8, 0xFF002A68, 0xFF0042A6, 0xFFD9E6F8, 0xFF0B0F11, 0xFF070A0B, 0xFF151B1F, 0xFF1B2328, 0xFF273038, 0xFF323E47, 0xFF3C4952)
    } else {
        tones(0xFF005FCF, 0xFFFFFFFF, 0xFFC2D5ED, 0xFF001E4A, 0xFFEBEFF2, 0xFFDEE5E9, 0xFFFFFFFF, 0xFFF4F8FA, 0xFFE1EAEF, 0xFFD4DFE6, 0xFFC1CED5)
    }

    Accent.VIOLET -> if (dark) {
        tones(0xFFB59EF8, 0xFF3A0076, 0xFF5F00B1, 0xFFE5E2F8, 0xFF0D0E11, 0xFF08090C, 0xFF191A20, 0xFF1F2129, 0xFF2D2E38, 0xFF3A3B48, 0xFF434653)
    } else {
        tones(0xFF7B00F0, 0xFFFFFFFF, 0xFFD2CFED, 0xFF290058, 0xFFEDEEF3, 0xFFE1E3EB, 0xFFFFFFFF, 0xFFF6F7FA, 0xFFE5E8F1, 0xFFD9DDE8, 0xFFC7CCD8)
    }

    Accent.PINK -> if (dark) {
        tones(0xFFF880BD, 0xFF580036, 0xFF890054, 0xFFF8DCEA, 0xFF100D10, 0xFF0A080B, 0xFF1E181C, 0xFF261F25, 0xFF352B32, 0xFF443841, 0xFF4E434C)
    } else {
        tones(0xFFB50077, 0xFFFFFFFF, 0xFFEEC6DB, 0xFF400027, 0xFFF1EDF1, 0xFFE7E2E7, 0xFFFFFFFF, 0xFFF9F6F9, 0xFFEDE6EC, 0xFFE3DAE3, 0xFFD2C9D3)
    }
}

// The roles no accent changes; cruxColorScheme lays the accent's on top.
private val LightBase = with(Light) {
    lightColorScheme(
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = secondary, onTertiary = onSecondary,
        tertiaryContainer = secondaryContainer, onTertiaryContainer = onSecondaryContainer,
        error = danger, onError = onDanger,
        errorContainer = dangerContainer, onErrorContainer = onDangerContainer,
        onBackground = ink, onSurface = ink, onSurfaceVariant = muted,
        outline = outline, scrim = scrim,
    )
}

private val DarkBase = with(Dark) {
    darkColorScheme(
        secondary = secondary, onSecondary = onSecondary,
        secondaryContainer = secondaryContainer, onSecondaryContainer = onSecondaryContainer,
        tertiary = secondary, onTertiary = onSecondary,
        tertiaryContainer = secondaryContainer, onTertiaryContainer = onSecondaryContainer,
        error = danger, onError = onDanger,
        errorContainer = dangerContainer, onErrorContainer = onDangerContainer,
        onBackground = ink, onSurface = ink, onSurfaceVariant = muted,
        outline = outline, scrim = scrim,
    )
}

/** The Material colour scheme for dark or light in [accent]. */
fun cruxColorScheme(dark: Boolean, accent: Accent): ColorScheme = with(accent.tones(dark)) {
    (if (dark) DarkBase else LightBase).copy(
        primary = primary, onPrimary = onPrimary,
        primaryContainer = primaryContainer, onPrimaryContainer = onPrimaryContainer,
        surfaceTint = primary,
        background = surface, surface = surface,
        surfaceVariant = surfaceContainerHighest,
        surfaceDim = surfaceDim, surfaceBright = if (dark) surfaceContainerHighest else surface,
        surfaceContainerLowest = surfaceContainerLow, surfaceContainerLow = surfaceContainerLow,
        surfaceContainer = surfaceContainer, surfaceContainerHigh = surfaceContainerHigh,
        surfaceContainerHighest = surfaceContainerHighest,
        outlineVariant = outlineVariant,
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
