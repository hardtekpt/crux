package com.hardtekpt.crux.ui

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.ui.components.input.RulerScale
import java.util.Locale
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.roundToInt

/*
 * Display units. Everything is stored metric (kg, cm); Imperial only changes what is shown
 * and what the inputs step in.
 */

/** The climber's unit system, provided at the app root. */
val LocalUnits = staticCompositionLocalOf { UnitSystem.METRIC }

const val LB_PER_KG = 2.2046226218
const val CM_PER_INCH = 2.54

/** A value as shown: the figure and its unit (null when the figure carries it, as in 5′10″). */
data class Shown(val value: String, val unit: String?) {
    override fun toString(): String = when (unit) {
        null -> value
        "%" -> "$value%"
        else -> "$value $unit"
    }
}

fun UnitSystem.weightUnit(): String = if (this == UnitSystem.IMPERIAL) "lb" else "kg"

/** Kilograms in the display unit, as a number (for charts and differences). */
fun UnitSystem.weightValue(kg: Double): Double = if (this == UnitSystem.IMPERIAL) kg * LB_PER_KG else kg

fun UnitSystem.weight(kg: Double): Shown = Shown(weightValue(kg).oneDecimal(), weightUnit())

/** A change in weight, signed: `−0.4 kg` or `+1.1 lb`. */
fun UnitSystem.weightChange(kg: Double): String = "${weightValue(kg).signedOneDecimal()} ${weightUnit()}"

/** A body measurement in the display units. Lengths in Imperial read as feet and inches. */
fun UnitSystem.measurement(type: MeasurementType, value: Double): Shown = when {
    type == MeasurementType.WEIGHT -> weight(value)
    type.isLength && this == UnitSystem.IMPERIAL -> Shown(feetInches(value / CM_PER_INCH), null)
    type.isCircumference && this == UnitSystem.IMPERIAL -> Shown((value / CM_PER_INCH).wholeOrOneDecimal(), "in")
    else -> Shown(value.wholeOrOneDecimal(), type.unit)
}

/** A length difference such as the ape index: `+6 cm` or `+2.4 in`. */
fun UnitSystem.lengthDifference(cm: Double): Shown = if (this == UnitSystem.IMPERIAL) {
    Shown(signed((cm / CM_PER_INCH * 10).roundToInt() / 10.0), "in")
} else {
    Shown(signed(cm.roundToInt().toDouble()), "cm")
}

private fun signed(value: Double): String {
    val text = if (value % 1.0 == 0.0) abs(value).toInt().toString() else String.format(Locale.UK, "%.1f", abs(value))
    return when {
        value > 0 -> "+$text"
        value < 0 -> "−$text"
        else -> "±0"
    }
}

val MeasurementType.isLength: Boolean
    get() = this == MeasurementType.HEIGHT || this == MeasurementType.WINGSPAN || this == MeasurementType.STANDING_REACH

/** `5′10″`, `5′10.5″`. */
fun feetInches(inches: Double): String {
    val halves = (inches * 2).roundToInt()
    val feet = halves / 24
    val rest = (halves % 24) / 2.0
    val restText = if (rest % 1.0 == 0.0) rest.toInt().toString() else rest.toString()
    return "$feet′$restText″"
}

/** Reads `5'10`, `5′10.5″`, `5 10` or plain inches (`70`) as inches. */
fun parseFeetInches(text: String): Double? {
    val clean = text.trim().replace('′', '\'').replace('’', '\'').replace("″", "").replace("\"", "").replace(',', '.')
    Regex("""^(\d+)\s*(?:'|ft|\s)\s*(\d+(?:\.\d+)?)?\s*(?:in)?$""").matchEntire(clean)?.let { m ->
        val feet = m.groupValues[1].toDouble()
        val inches = m.groupValues[2].toDoubleOrNull() ?: 0.0
        return feet * 12 + inches
    }
    return clean.removeSuffix("in").trim().toDoubleOrNull()
}

/**
 * How a body measurement is entered: the ruler's scale in display units, and the
 * conversions to and from what is stored.
 */
data class MeasureInput(
    val scale: RulerScale,
    val unit: String?,
    val toDisplay: (Double) -> Double,
    val toStored: (Double) -> Double,
    val display: (Double) -> String,
    val parse: (String) -> Double?,
    val typeUnit: String,
)

fun UnitSystem.measureInput(type: MeasurementType): MeasureInput {
    val imperial = this == UnitSystem.IMPERIAL
    val decimal = { text: String -> text.replace(',', '.').trim().toDoubleOrNull() }
    return when {
        type == MeasurementType.WEIGHT && imperial -> MeasureInput(
            scale = RulerScale(65.0, 440.0, 0.2, midEvery = 5, majorEvery = 25, labelEvery = 50, spacing = 6.dp),
            unit = "lb",
            toDisplay = { it * LB_PER_KG },
            toStored = { (it / LB_PER_KG * 100).roundToInt() / 100.0 },
            display = { it.oneDecimal() },
            parse = decimal,
            typeUnit = "lb",
        )
        type == MeasurementType.WEIGHT -> MeasureInput(
            scale = RulerScale(30.0, 200.0, 0.1, midEvery = 5, majorEvery = 10, labelEvery = 10),
            unit = "kg",
            toDisplay = { it },
            toStored = { it },
            display = { it.oneDecimal() },
            parse = decimal,
            typeUnit = "kg",
        )
        type.isCircumference && imperial -> MeasureInput(
            scale = RulerScale(
                min = floor(type.range.start / CM_PER_INCH),
                max = floor(type.range.endInclusive / CM_PER_INCH),
                step = 0.25,
                midEvery = 2,
                majorEvery = 4,
                labelEvery = 8,
                spacing = 10.dp,
            ),
            unit = "in",
            toDisplay = { it / CM_PER_INCH },
            toStored = { (it * CM_PER_INCH * 10).roundToInt() / 10.0 },
            display = { it.wholeOrOneDecimal() },
            parse = decimal,
            typeUnit = "in",
        )
        type.isCircumference -> MeasureInput(
            scale = RulerScale(type.range.start, type.range.endInclusive, 0.5, midEvery = 2, majorEvery = 10, labelEvery = 10, spacing = 10.dp),
            unit = "cm",
            toDisplay = { it },
            toStored = { it },
            display = { it.wholeOrOneDecimal() },
            parse = decimal,
            typeUnit = "cm",
        )
        type.isLength && imperial -> MeasureInput(
            scale = RulerScale(
                min = floor(type.range.start / CM_PER_INCH),
                max = floor(type.range.endInclusive / CM_PER_INCH),
                step = 0.5,
                midEvery = 2,
                majorEvery = 12,
                labelEvery = 12,
                spacing = 10.dp,
                label = ::feetInches,
            ),
            unit = null,
            toDisplay = { it / CM_PER_INCH },
            toStored = { (it * CM_PER_INCH * 10).roundToInt() / 10.0 },
            display = ::feetInches,
            parse = ::parseFeetInches,
            typeUnit = "feet and inches, like 5'10",
        )
        type.isLength -> MeasureInput(
            scale = RulerScale(type.range.start, type.range.endInclusive, 0.5, midEvery = 10, majorEvery = 20, labelEvery = 20),
            unit = "cm",
            toDisplay = { it },
            toStored = { it },
            display = { it.wholeOrOneDecimal() },
            parse = decimal,
            typeUnit = "cm",
        )
        else -> MeasureInput(
            scale = RulerScale(type.range.start, type.range.endInclusive, 0.5, midEvery = 2, majorEvery = 10, labelEvery = 10, spacing = 10.dp),
            unit = type.unit,
            toDisplay = { it },
            toStored = { it },
            display = { it.wholeOrOneDecimal() },
            parse = decimal,
            typeUnit = type.unit,
        )
    }
}

/** Where a ruler opens when there is no earlier value. */
fun MeasurementType.typicalValue(): Double = when (this) {
    MeasurementType.WEIGHT -> 70.0
    MeasurementType.HEIGHT -> 175.0
    MeasurementType.WINGSPAN -> 178.0
    MeasurementType.STANDING_REACH -> 225.0
    MeasurementType.BODY_FAT -> 15.0
    MeasurementType.FOREARM, MeasurementType.FOREARM_RIGHT -> 29.0
    MeasurementType.BICEP, MeasurementType.BICEP_RIGHT -> 33.0
    MeasurementType.CHEST -> 98.0
    MeasurementType.WAIST -> 80.0
    MeasurementType.THIGH, MeasurementType.THIGH_RIGHT -> 55.0
}

/** Tape measurements around a limb or the body. */
val MeasurementType.isCircumference: Boolean
    get() = this in MeasurementType.circumferences
