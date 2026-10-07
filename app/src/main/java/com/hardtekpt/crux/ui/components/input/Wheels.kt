package com.hardtekpt.crux.ui.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.data.model.formatKg
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxTextField
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono
import kotlin.math.abs
import kotlin.math.roundToInt

/** One row of a wheel; the selection band is the same height. */
val WheelItemHeight: Dp = 40.dp

private fun wheelHeight(visible: Int) = WheelItemHeight * visible
private fun bandTop(visible: Int) = WheelItemHeight * ((visible - 1) / 2)

private fun wheelText(mono: Boolean, selected: Boolean) = TextStyle(
    fontFamily = if (mono) JetBrainsMono else Archivo,
    fontWeight = when {
        mono && selected -> FontWeight.Bold
        mono -> FontWeight.Medium
        selected -> FontWeight.ExtraBold
        else -> FontWeight.Bold
    },
    fontSize = 22.sp,
    lineHeight = 24.sp,
    fontFeatureSettings = "tnum",
)

/**
 * A vertical list of values that snaps one to the centre band: flick, or tap a neighbour to
 * jump to it. Tapping the centred value calls [onTapSelected], used to type a value in.
 * TalkBack reads it as one control ("Rest seconds, 30") and can set it directly.
 */
@Composable
fun <T> CruxWheel(
    items: List<T>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    label: (T) -> String,
    description: String,
    modifier: Modifier = Modifier,
    width: Dp = 64.dp,
    visibleCount: Int = 3,
    mono: Boolean = false,
    strongAt: (Int) -> Boolean = { false },
    onTapSelected: (() -> Unit)? = null,
) {
    val picker = rememberPickerScroll(items.size, selectedIndex, onSelect, strongAt)
    val colors = MaterialTheme.colorScheme
    val selected = selectedIndex.coerceIn(0, (items.size - 1).coerceAtLeast(0))
    LazyColumn(
        state = picker.list,
        flingBehavior = picker.fling,
        contentPadding = PaddingValues(vertical = bandTop(visibleCount)),
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = modifier
            .width(width)
            .height(wheelHeight(visibleCount))
            .fadeEdges(vertical = true)
            .onKeyEvent { event ->
                if (event.type != KeyEventType.KeyDown) return@onKeyEvent false
                when (event.key) {
                    Key.DirectionUp, Key.VolumeUp -> {
                        onSelect((selected - 1).coerceAtLeast(0))
                        true
                    }

                    Key.DirectionDown, Key.VolumeDown -> {
                        onSelect((selected + 1).coerceAtMost(items.lastIndex))
                        true
                    }

                    else -> false
                }
            }
            .focusable()
            .clearAndSetSemantics {
                contentDescription = description
                if (items.isNotEmpty()) stateDescription = label(items[selected])
                progressBarRangeInfo = ProgressBarRangeInfo(
                    current = selected.toFloat(),
                    range = 0f..(items.size - 1).coerceAtLeast(1).toFloat(),
                    steps = (items.size - 2).coerceAtLeast(0),
                )
                setProgress { target ->
                    onSelect(target.roundToInt().coerceIn(0, items.lastIndex))
                    true
                }
            },
    ) {
        itemsIndexed(items) { index, item ->
            val isSelected = index == selected
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(WheelItemHeight)
                    .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) {
                        if (index == selected) onTapSelected?.invoke() else picker.scrollTo(index)
                    },
            ) {
                Text(
                    label(item),
                    style = wheelText(mono, isSelected),
                    color = if (isSelected) colors.onPrimaryContainer else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/**
 * Wheels side by side over one selection band, with separators and units between them.
 * Put [CruxWheel]s, [WheelText]s and [WheelColumn]s in [content].
 */
@Composable
fun WheelGroup(modifier: Modifier = Modifier, visibleCount: Int = 3, content: @Composable RowScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Box(modifier.fillMaxWidth()) {
        Box(
            Modifier
                .padding(top = bandTop(visibleCount))
                .fillMaxWidth()
                .height(WheelItemHeight)
                .background(colors.primaryContainer, shape)
                .border(CruxTheme.size.borderEmphasis, colors.primary, shape),
        )
        Row(
            horizontalArrangement = Arrangement.spacedBy(2.dp, Alignment.CenterHorizontally),
            verticalAlignment = Alignment.Top,
            modifier = Modifier.fillMaxWidth(),
            content = content,
        )
    }
}

/** A separator (":") or unit drawn on the band, between or after wheels. */
@Composable
fun WheelText(text: String, visibleCount: Int = 3, unit: Boolean = false, mono: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    Box(
        contentAlignment = Alignment.CenterStart,
        modifier = Modifier
            .height(wheelHeight(visibleCount))
            .padding(horizontal = if (unit) 4.dp else 0.dp),
    ) {
        Text(
            if (unit) text.uppercase() else text,
            style = if (unit) {
                TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.3.sp)
            } else {
                wheelText(mono, selected = true)
            },
            color = colors.onPrimaryContainer,
        )
    }
}

/** A wheel with a small caption under it, like MIN and SEC. */
@Composable
fun WheelColumn(caption: String, wheel: @Composable () -> Unit) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        wheel()
        Text(
            caption.uppercase(),
            style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 10.sp, letterSpacing = 1.2.sp),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(top = 4.dp),
        )
    }
}

/** Whole numbers on a wheel, with a unit on the band. Tap the picked number to type one. */
@Composable
fun NumberWheel(value: Int, onValueChange: (Int) -> Unit, range: IntRange, unit: String, description: String, modifier: Modifier = Modifier, step: Int = 1) {
    val values = remember(range, step) { (range step step).toList() }
    var typing by rememberSaveable { mutableStateOf(false) }
    WheelGroup(modifier) {
        CruxWheel(
            items = values,
            selectedIndex = nearestIndex(values, value),
            onSelect = { onValueChange(values[it]) },
            label = { it.toString() },
            description = description,
            width = 72.dp,
            onTapSelected = { typing = true },
        )
        WheelText(unit, unit = true)
    }
    if (typing) {
        TypeValueDialog(
            title = description,
            initial = value.toString(),
            unit = unit,
            onDismiss = { typing = false },
            parse = { text -> text.toIntOrNull()?.takeIf { it in range }?.let { nearest(values, it) } },
            error = "Pick ${range.first} to ${range.last}",
            onConfirm = {
                onValueChange(it)
                typing = false
            },
        )
    }
}

/** Minutes and seconds on two wheels, with preset chips for common values. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun DurationWheel(
    seconds: Int,
    onSecondsChange: (Int) -> Unit,
    description: String,
    modifier: Modifier = Modifier,
    maxMinutes: Int = 30,
    secondStep: Int = 1,
    minSeconds: Int = 0,
    presets: List<Int> = emptyList(),
) {
    val minutes = remember(maxMinutes) { (0..maxMinutes).toList() }
    val secondValues = remember(secondStep) { (0 until 60 step secondStep).toList() }
    val clamp = { s: Int -> s.coerceIn(minSeconds, maxMinutes * 60 + secondValues.last()) }
    var typing by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        WheelGroup {
            WheelColumn("min") {
                CruxWheel(
                    items = minutes,
                    selectedIndex = (seconds / 60).coerceAtMost(maxMinutes),
                    onSelect = { onSecondsChange(clamp(minutes[it] * 60 + seconds % 60)) },
                    label = { it.toString() },
                    description = "$description minutes",
                    mono = true,
                    onTapSelected = { typing = true },
                )
            }
            WheelText(":", mono = true)
            WheelColumn("sec") {
                CruxWheel(
                    items = secondValues,
                    selectedIndex = nearestIndex(secondValues, seconds % 60),
                    onSelect = { onSecondsChange(clamp(seconds / 60 * 60 + secondValues[it])) },
                    label = { it.toString().padStart(2, '0') },
                    description = "$description seconds",
                    mono = true,
                    strongAt = { it == 0 },
                    onTapSelected = { typing = true },
                )
            }
        }
        if (presets.isNotEmpty()) {
            FlowRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
                presets.forEach { preset ->
                    CruxFilterChip(
                        label = formatDuration(preset),
                        selected = preset == seconds,
                        onClick = { onSecondsChange(clamp(preset)) },
                        labelStyle = CruxTheme.type.gradeSmall,
                        modifier = Modifier.testTag("preset_$preset"),
                    )
                }
            }
        }
    }
    if (typing) {
        TypeValueDialog(
            title = description,
            initial = formatDuration(seconds).removeSuffix(" s"),
            unit = "seconds, or m:ss",
            keyboardType = KeyboardType.Text,
            onDismiss = { typing = false },
            parse = { text -> parseDuration(text)?.takeIf { it in minSeconds..(maxMinutes * 60 + 59) } },
            error = "Use seconds (90) or minutes and seconds (1:30)",
            onConfirm = {
                onSecondsChange(clamp(it))
                typing = false
            },
        )
    }
}

/**
 * Added load: sign, whole kilos and a plate fraction on three wheels. Minus is assisted
 * (pulley or band). Chips add or take off the small plates. Under Imperial the wheels step
 * in pounds and half pounds; the value is still kept in kilograms.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun LoadWheel(kg: Double, onKgChange: (Double) -> Unit, modifier: Modifier = Modifier, minKg: Double = -50.0, maxKg: Double = 150.0) {
    val imperial = com.hardtekpt.crux.ui.LocalUnits.current == com.hardtekpt.crux.data.prefs.UnitSystem.IMPERIAL
    // The wheels work in the display unit; changes go back out in kilograms.
    val toKg = { shown: Double -> if (imperial) com.hardtekpt.crux.data.model.poundsToKg(shown) else shown }
    val shownValue = com.hardtekpt.crux.data.model.loadValue(kg, imperial)
    val fractions = if (imperial) LoadParts.HALVES else LoadParts.FRACTIONS
    val parts = if (imperial) LoadParts.ofHalves(shownValue) else LoadParts.of(kg)
    val minShown = com.hardtekpt.crux.data.model.loadValue(minKg, imperial)
    val maxShown = com.hardtekpt.crux.data.model.loadValue(maxKg, imperial)
    val wholes = remember(minShown, maxShown) { (0..maxOf(abs(minShown), maxShown).toInt()).toList() }
    val set = { next: LoadParts -> onKgChange(toKg(next.value(imperial)).coerceIn(minKg, maxKg)) }
    val unitLabel = com.hardtekpt.crux.data.model.loadUnit(imperial)
    var typing by rememberSaveable { mutableStateOf(false) }
    Column(modifier, verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        WheelGroup {
            CruxWheel(
                items = LoadParts.SIGNS,
                selectedIndex = if (parts.negative) 1 else 0,
                onSelect = { set(parts.copy(negative = it == 1)) },
                label = { it },
                description = "Added or assisted",
                width = 44.dp,
            )
            CruxWheel(
                items = wholes,
                selectedIndex = parts.whole.coerceAtMost(wholes.last()),
                onSelect = { set(parts.copy(whole = wholes[it])) },
                label = { it.toString() },
                description = if (imperial) "Pounds" else "Kilograms",
                strongAt = { wholes[it] % 5 == 0 },
                onTapSelected = { typing = true },
            )
            CruxWheel(
                items = fractions,
                selectedIndex = parts.quarter.coerceAtMost(fractions.lastIndex),
                onSelect = { set(parts.copy(quarter = it)) },
                label = { it },
                description = if (imperial) "Half a pound" else "Fraction of a kilogram",
                width = 56.dp,
            )
            WheelText(unitLabel, unit = true)
        }
        FlowRow(
            horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2, Alignment.CenterHorizontally),
            modifier = Modifier.fillMaxWidth(),
        ) {
            // The small plates: 1.25 and 2.5 kg, or 2.5 and 5 lb.
            (if (imperial) listOf(-5.0, -2.5, 2.5, 5.0) else listOf(-2.5, -1.25, 1.25, 2.5)).forEach { delta ->
                CruxFilterChip(
                    label = (if (delta > 0) "+" else "−") + formatKg(abs(delta)),
                    selected = false,
                    onClick = { onKgChange(toKg(shownValue + delta).coerceIn(minKg, maxKg)) },
                    modifier = Modifier.testTag("load_${if (delta > 0) "plus" else "minus"}_${abs(delta)}"),
                )
            }
        }
    }
    if (typing) {
        TypeValueDialog(
            title = "Added load",
            initial = formatKg(shownValue),
            unit = "$unitLabel, minus for assisted",
            keyboardType = KeyboardType.Text,
            onDismiss = { typing = false },
            parse = { text -> text.replace(',', '.').replace('−', '-').trim().toDoubleOrNull()?.takeIf { it in minShown..maxShown } },
            error = "Pick ${formatKg(minShown)} to ${formatKg(maxShown)} $unitLabel",
            onConfirm = {
                onKgChange(if (imperial) toKg((it * 2).roundToInt() / 2.0) else LoadParts.of(it).kg)
                typing = false
            },
        )
    }
}

/** A load split into what the wheels show; quarters of a kilo cover the small plates. */
data class LoadParts(val negative: Boolean, val whole: Int, val quarter: Int) {
    val kg: Double get() = (whole + quarter * 0.25).let { if (negative) -it else it }

    /** The value in the wheels' unit: quarters of a kilo, or halves of a pound. */
    fun value(imperial: Boolean): Double = if (imperial) (whole + quarter * 0.5).let { if (negative) -it else it } else kg

    companion object {
        val SIGNS = listOf("+", "−")
        val FRACTIONS = listOf(".00", ".25", ".50", ".75")
        val HALVES = listOf(".0", ".5")

        /** A value in pounds, split into whole pounds and a half. */
        fun ofHalves(lb: Double): LoadParts {
            val halves = (abs(lb) * 2).roundToInt()
            return LoadParts(negative = lb < 0, whole = halves / 2, quarter = halves % 2)
        }

        fun of(kg: Double): LoadParts {
            val quarters = (abs(kg) * 4).roundToInt()
            return LoadParts(negative = kg < 0, whole = quarters / 4, quarter = quarters % 4)
        }
    }
}

/** A small dialog to type a value the wheels or ruler would take a while to reach. */
@Composable
fun <V : Any> TypeValueDialog(
    title: String,
    initial: String,
    unit: String,
    onDismiss: () -> Unit,
    parse: (String) -> V?,
    error: String,
    onConfirm: (V) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Decimal,
) {
    var text by rememberSaveable { mutableStateOf(initial) }
    var shownError by rememberSaveable { mutableStateOf<String?>(null) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        title = { Text(title, style = MaterialTheme.typography.headlineSmall) },
        text = {
            CruxTextField(
                label = "Type a value",
                value = text,
                onValueChange = {
                    text = it
                    shownError = null
                },
                helper = unit,
                error = shownError,
                keyboardType = keyboardType,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("field_type_value"),
            )
        },
        confirmButton = {
            TextButton(
                onClick = { parse(text.trim())?.let(onConfirm) ?: run { shownError = error } },
                modifier = Modifier.testTag("confirm_type_value"),
            ) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/** `45 s` under a minute, `1:30` from a minute up. */
fun formatDuration(seconds: Int): String = if (seconds < 60) "$seconds s" else "${seconds / 60}:${(seconds % 60).toString().padStart(2, '0')}"

/** Reads `90`, `90s`, `1:30` or `1m30` as seconds. */
fun parseDuration(text: String): Int? {
    val clean = text.lowercase().replace(" ", "").removeSuffix("s")
    Regex("""^(\d+)[:m](\d{1,2})$""").matchEntire(clean)?.let { m ->
        val (min, sec) = m.destructured
        return if (sec.toInt() < 60) min.toInt() * 60 + sec.toInt() else null
    }
    return clean.toIntOrNull()
}

internal fun nearestIndex(values: List<Int>, value: Int): Int = values.indices.minByOrNull { abs(values[it] - value) } ?: 0

private fun nearest(values: List<Int>, value: Int): Int = values[nearestIndex(values, value)]
