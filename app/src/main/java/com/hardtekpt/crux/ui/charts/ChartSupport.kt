package com.hardtekpt.crux.ui.charts

import kotlin.math.abs
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.log10
import kotlin.math.pow

/** Slots in `CruxTheme.colors.chart`; anything past the last folds into "Other". */
const val CATEGORICAL_SLOTS: Int = 5

/** Axis ticks on round numbers that bracket the data. */
data class AxisScale(val min: Double, val max: Double, val ticks: List<Double>) {
    val span: Double get() = (max - min).takeIf { it > 0 } ?: 1.0
    fun fraction(value: Double): Float = ((value - min) / span).toFloat()
}

/**
 * A y-axis that starts near the data rather than at zero (a 0–75 kg axis hides the only
 * thing worth seeing), with about [targetTicks] round steps.
 */
fun niceScale(dataMin: Double, dataMax: Double, targetTicks: Int = 4, includeZero: Boolean = false): AxisScale {
    var lo = if (includeZero) minOf(0.0, dataMin) else dataMin
    var hi = if (includeZero) maxOf(0.0, dataMax) else dataMax
    if (hi - lo < 1e-9) {
        val pad = if (abs(hi) < 1e-9) 1.0 else abs(hi) * 0.05
        lo -= pad
        hi += pad
        if (includeZero && dataMin >= 0) lo = maxOf(lo, 0.0)
    }
    val step = niceStep((hi - lo) / (targetTicks - 1).coerceAtLeast(1))
    val min = floor(lo / step) * step
    val max = ceil(hi / step) * step
    val ticks = generateSequence(min) { it + step }.takeWhile { it <= max + step / 2 }.toList()
    return AxisScale(min, max, ticks)
}

private fun niceStep(raw: Double): Double {
    val exponent = floor(log10(raw))
    val fraction = raw / 10.0.pow(exponent)
    val nice = when {
        fraction <= 1 -> 1.0
        fraction <= 2 -> 2.0
        fraction <= 2.5 -> 2.5
        fraction <= 5 -> 5.0
        else -> 10.0
    }
    return nice * 10.0.pow(exponent)
}

/** Whole numbers print bare; everything else to one decimal. */
fun formatTick(value: Double): String =
    if (abs(value - Math.round(value)) < 1e-6) Math.round(value).toString() else String.format(java.util.Locale.UK, "%.1f", value)
