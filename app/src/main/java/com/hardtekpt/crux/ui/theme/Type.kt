package com.hardtekpt.crux.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.R

/** Space Mono: the voice of the app. Only 400 and 700 exist. */
val SpaceMono = FontFamily(
    Font(R.font.space_mono_regular, FontWeight.Normal),
    Font(R.font.space_mono_bold, FontWeight.Bold),
)

/** Archivo (variable): body text only, where the climber reads at length. */
@OptIn(androidx.compose.ui.text.ExperimentalTextApi::class)
val Archivo = FontFamily(
    listOf(400, 500, 600, 700).map { weight ->
        Font(
            R.font.archivo,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

private const val TABULAR = "tnum"

private fun mono(size: Int, line: Int, weight: FontWeight = FontWeight.Bold, tracking: Double = 0.0) =
    TextStyle(
        fontFamily = SpaceMono,
        fontWeight = weight,
        fontSize = size.sp,
        lineHeight = line.sp,
        letterSpacing = tracking.em,
    )

private fun text(size: Int, line: Int) = TextStyle(
    fontFamily = Archivo,
    fontWeight = FontWeight.Normal,
    fontSize = size.sp,
    lineHeight = line.sp,
    fontFeatureSettings = TABULAR,
)

// Slot mapping from the design system's compose-theme.md. M3 displayLarge is unused.
val CruxTypography = Typography(
    displayLarge = mono(44, 48, tracking = -0.02),
    displayMedium = mono(44, 48, tracking = -0.02),
    displaySmall = mono(34, 40, tracking = -0.02),
    headlineLarge = mono(26, 32, tracking = -0.01),
    headlineMedium = mono(22, 28, tracking = -0.01),
    headlineSmall = mono(19, 24),
    titleLarge = mono(19, 24),
    titleMedium = mono(15, 22),
    titleSmall = mono(13, 18, tracking = 0.01),
    bodyLarge = text(16, 24),
    bodyMedium = text(14, 20),
    bodySmall = text(12, 16),
    labelLarge = mono(14, 18, tracking = 0.02),
    labelMedium = mono(12, 16, tracking = 0.03),
    // Eyebrow: uppercase the string at the call site (see Eyebrow composable).
    labelSmall = mono(11, 14, weight = FontWeight.Normal, tracking = 0.14),
)

/** Crux text styles with no Material 3 slot. Read through `CruxTheme.type`. */
@Immutable
data class CruxType(
    val metricLarge: TextStyle = mono(40, 44, tracking = -0.03).copy(fontFeatureSettings = TABULAR),
    val metricMedium: TextStyle = mono(26, 30, tracking = -0.02).copy(fontFeatureSettings = TABULAR),
    val grade: TextStyle = mono(15, 18),
    val timer: TextStyle = mono(32, 34).copy(fontFeatureSettings = TABULAR),
    val code: TextStyle = mono(12, 18, weight = FontWeight.Normal),
)
