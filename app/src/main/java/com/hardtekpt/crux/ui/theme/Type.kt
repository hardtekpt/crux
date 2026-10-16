package com.hardtekpt.crux.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.runtime.Immutable
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.R

@OptIn(ExperimentalTextApi::class)
private fun variableFamily(resId: Int, weights: List<Int>) = FontFamily(
    weights.map { weight ->
        Font(
            resId,
            FontWeight(weight),
            variationSettings = FontVariation.Settings(FontVariation.weight(weight)),
        )
    },
)

/** Archivo (variable): headings at 800, titles and labels at 600, reading text at 400. */
val Archivo = variableFamily(R.font.archivo, listOf(400, 500, 600, 700, 800))

/** JetBrains Mono (variable): eyebrows, grades, timers and protocol strings. */
val JetBrainsMono = variableFamily(R.font.jetbrains_mono, listOf(400, 500, 600, 700))

private const val TABULAR = "tnum"

private fun style(family: FontFamily, size: Int, line: Int, weight: Int, tracking: Double = 0.0, tabular: Boolean = false) = TextStyle(
    fontFamily = family,
    fontWeight = FontWeight(weight),
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
    fontFeatureSettings = if (tabular) TABULAR else null,
)

// Slot mapping from the design system's compose-theme.md. M3 displayLarge is unused.
val CruxTypography = Typography(
    displayLarge = style(Archivo, 48, 52, 700, -0.02),
    displayMedium = style(Archivo, 48, 52, 700, -0.02),
    displaySmall = style(Archivo, 36, 40, 700, -0.02),
    headlineLarge = style(Archivo, 28, 34, 700, -0.015),
    headlineMedium = style(Archivo, 22, 28, 700, -0.01),
    headlineSmall = style(Archivo, 20, 26, 600, -0.01),
    titleLarge = style(Archivo, 20, 26, 600),
    titleMedium = style(Archivo, 16, 22, 600),
    titleSmall = style(Archivo, 14, 20, 600),
    bodyLarge = style(Archivo, 16, 24, 400, tabular = true),
    bodyMedium = style(Archivo, 14, 20, 400, tabular = true),
    bodySmall = style(Archivo, 12, 16, 400, tabular = true),
    labelLarge = style(Archivo, 14, 18, 600, 0.005),
    labelMedium = style(Archivo, 12, 16, 600, 0.02),
    // The mono eyebrow, the only uppercase style: uppercase the string at the call site (see Eyebrow).
    labelSmall = style(JetBrainsMono, 11, 14, 600, 0.12),
)

/** The mono micro label: day letters, tape labels, a value's caption. 11sp, medium, spaced for caps. */
val CruxMicro = style(JetBrainsMono, 11, 14, 500, 0.1)

/** The smaller micro label, where 11sp doesn't fit: weekday letters in a strip, figure captions. */
val CruxMicroSmall = style(JetBrainsMono, 10, 12, 500, 0.1)

/** Chart axes and the consistency grid's month and day marks. */
val CruxAxis = style(JetBrainsMono, 9, 11, 400)

/** A day of the month standing for its day: the Journal's and the Week's date blocks. */
val CruxDayNumber = style(Archivo, 18, 20, 700, tabular = true)

/** A section heading on a long page (You's Consistency, Personal records). */
val CruxSectionTitle = style(Archivo, 20, 24, 800, -0.01)

/** The timer dock's numbers, the full-screen timer's, and its phase label. */
val CruxDockNumber = style(JetBrainsMono, 28, 30, 700, tabular = true)
val CruxTimerHuge = style(JetBrainsMono, 88, 88, 700, tabular = true)
val CruxPhaseLabel = style(JetBrainsMono, 13, 16, 500, 0.12)

/** Crux text styles with no Material 3 slot. Read through `CruxTheme.type`. */
@Immutable
data class CruxType(
    val metricLarge: TextStyle = style(Archivo, 38, 42, 700, -0.02, tabular = true),
    val metricMedium: TextStyle = style(Archivo, 26, 30, 700, -0.015, tabular = true),
    /** A figure inside a card or sheet (a StatStrip): smaller than a tile's, still the figure voice. */
    val figure: TextStyle = style(Archivo, 20, 24, 800, -0.01, tabular = true),
    val grade: TextStyle = style(JetBrainsMono, 15, 18, 600, 0.02, tabular = true),
    val gradeSmall: TextStyle = style(JetBrainsMono, 13, 16, 600, 0.02, tabular = true),
    val timer: TextStyle = style(JetBrainsMono, 32, 34, 500, 0.01, tabular = true),
    val code: TextStyle = style(JetBrainsMono, 12, 18, 400),
    val micro: TextStyle = CruxMicro,
    val microSmall: TextStyle = CruxMicroSmall,
    val axis: TextStyle = CruxAxis,
    val dayNumber: TextStyle = CruxDayNumber,
    val sectionTitle: TextStyle = CruxSectionTitle,
)
