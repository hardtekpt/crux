package com.hardtekpt.crux.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.ui.components.input.rememberTicker
import com.hardtekpt.crux.ui.theme.CruxTheme
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Filter chip: outline hairline at rest; selected takes `primary-container` with a check. */
@Composable
fun CruxFilterChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    labelStyle: TextStyle = MaterialTheme.typography.labelMedium,
) {
    val colors = MaterialTheme.colorScheme
    FilterChip(
        selected = selected,
        onClick = onClick,
        enabled = enabled,
        label = { Text(label, style = labelStyle) },
        leadingIcon = if (selected) {
            { Icon(Icons.Rounded.Check, null, Modifier.size(CruxTheme.size.iconSm)) }
        } else {
            null
        },
        shape = CircleShape,
        colors = FilterChipDefaults.filterChipColors(
            labelColor = colors.onSurface,
            selectedContainerColor = colors.primaryContainer,
            selectedLabelColor = colors.onPrimaryContainer,
            selectedLeadingIconColor = colors.onPrimaryContainer,
        ),
        border = FilterChipDefaults.filterChipBorder(
            enabled = enabled,
            selected = selected,
            borderColor = colors.outline,
            selectedBorderColor = colors.primary,
            selectedBorderWidth = CruxTheme.size.borderEmphasis,
        ),
        modifier = modifier.height(CruxTheme.size.controlHeightSmall),
    )
}

/** A few colours as round swatches with their names; exactly one is selected. */
@Composable
fun <T> CruxSwatchPicker(
    options: List<T>,
    selected: T,
    color: (T) -> Color,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
    tag: (T) -> String = { "swatch_${label(it)}" },
) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth().selectableGroup(), horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        options.forEach { option ->
            val on = option == selected
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s1),
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .selectable(selected = on, role = Role.RadioButton, onClick = { onSelect(option) })
                    .widthIn(min = CruxTheme.size.touchTarget + CruxTheme.space.s4)
                    .padding(CruxTheme.space.s1)
                    .testTag(tag(option)),
            ) {
                // The picked swatch gets a ring with a gap and a check; the colour stays whole.
                Box(
                    contentAlignment = Alignment.Center,
                    modifier = Modifier
                        .size(CruxTheme.size.controlHeight)
                        .border(CruxTheme.size.borderEmphasis, if (on) colors.onSurface else Color.Transparent, CircleShape)
                        .padding(CruxTheme.space.s1)
                        .background(color(option), CircleShape),
                ) {
                    if (on) Icon(Icons.Rounded.Check, contentDescription = null, tint = colors.surface, modifier = Modifier.size(CruxTheme.size.iconSm))
                }
                Text(
                    label(option),
                    style = MaterialTheme.typography.labelMedium,
                    color = if (on) colors.onSurface else colors.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }
    }
}

/** Two to four views of the same thing; exactly one is selected. */
@Composable
fun <T> CruxSegmentedButtons(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth().height(CruxTheme.size.controlHeight)) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                    baseShape = MaterialTheme.shapes.small,
                ),
                // The selected segment gets a 2dp primary border; the rest a hairline.
                border = if (option == selected) {
                    BorderStroke(CruxTheme.size.borderEmphasis, colors.primary)
                } else {
                    BorderStroke(CruxTheme.size.borderHairline, colors.outline)
                },
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.primaryContainer,
                    activeContentColor = colors.onPrimaryContainer,
                    inactiveContainerColor = colors.surfaceContainerLow,
                    inactiveContentColor = colors.onSurface,
                    activeBorderColor = colors.outline,
                    inactiveBorderColor = colors.outline,
                ),
                icon = {},
                label = { Text(label(option), style = MaterialTheme.typography.labelLarge, maxLines = 1) },
                modifier = Modifier.testTag("segment_${label(option)}"),
            )
        }
    }
}

/**
 * Outlined text field with its label above (never floating) and a helper line that is
 * always laid out, so a validation message never shifts the form.
 */
@Composable
fun CruxTextField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    modifier: Modifier = Modifier,
    placeholder: String? = null,
    helper: String? = null,
    error: String? = null,
    keyboardType: KeyboardType = KeyboardType.Text,
    singleLine: Boolean = true,
    minLines: Int = 1,
) {
    val colors = MaterialTheme.colorScheme
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s1)) {
        if (label.isNotBlank()) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        }
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CruxTheme.size.controlHeightLarge),
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
            textStyle = MaterialTheme.typography.bodyLarge,
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surfaceContainerLow,
                unfocusedContainerColor = colors.surfaceContainerLow,
                unfocusedBorderColor = colors.outline,
                focusedBorderColor = colors.primary,
                errorBorderColor = colors.error,
            ),
        )
        // Only a field with something to say gets the line under it, so forms stay compact.
        (error ?: helper)?.let {
            Text(
                text = it,
                style = MaterialTheme.typography.bodySmall,
                color = if (error != null) colors.error else colors.onSurfaceVariant,
            )
        }
    }
}

/** Nudge a value without the keyboard. At a bound the button disables and the value holds. */
@Composable
fun CruxStepper(
    value: Int,
    onValueChange: (Int) -> Unit,
    range: IntRange,
    unit: String,
    modifier: Modifier = Modifier,
    step: Int = 1,
    enabled: Boolean = true,
    testTagPrefix: String = "stepper",
) {
    CruxValueStepper(
        display = value.toString(),
        unit = unit,
        canDecrease = enabled && value > range.first,
        canIncrease = enabled && value < range.last,
        onDecrease = { onValueChange((value - step).coerceAtLeast(range.first)) },
        onIncrease = { onValueChange((value + step).coerceAtMost(range.last)) },
        enabled = enabled,
        modifier = modifier,
        testTagPrefix = testTagPrefix,
    )
}

/**
 * Stepper with caller-defined steps, for values like load that step unevenly. Hold a button
 * to repeat (faster after a moment), or drag sideways on the value to scrub through steps.
 */
@Composable
fun CruxValueStepper(
    display: String,
    unit: String,
    canDecrease: Boolean,
    canIncrease: Boolean,
    onDecrease: () -> Unit,
    onIncrease: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    testTagPrefix: String = "stepper",
) {
    val colors = MaterialTheme.colorScheme
    val tick = rememberTicker()
    val decrease by rememberUpdatedState(if (canDecrease) onDecrease else null)
    val increase by rememberUpdatedState(if (canIncrease) onIncrease else null)
    var scrubbing by remember { mutableStateOf(false) }
    Row(
        modifier = modifier
            .background(colors.surfaceContainer, CircleShape)
            .border(CruxTheme.size.borderHairline, colors.outline, CircleShape)
            .padding(CruxTheme.space.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s1),
    ) {
        RepeatButton(Icons.Rounded.Remove, "Decrease $unit", enabled && canDecrease, onDecrease, Modifier.testTag("${testTagPrefix}_minus"))
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .widthIn(min = 56.dp)
                .clip(MaterialTheme.shapes.small)
                .background(if (scrubbing) colors.primaryContainer else Color.Transparent)
                .pointerInput(enabled) {
                    if (!enabled) return@pointerInput
                    val stepPx = SCRUB_STEP.toPx()
                    var travelled = 0f
                    detectHorizontalDragGestures(
                        onDragStart = {
                            scrubbing = true
                            travelled = 0f
                        },
                        onDragEnd = { scrubbing = false },
                        onDragCancel = { scrubbing = false },
                    ) { change, dx ->
                        change.consume()
                        travelled += dx
                        val step = when {
                            travelled >= stepPx -> increase
                            travelled <= -stepPx -> decrease
                            else -> return@detectHorizontalDragGestures
                        }
                        travelled = 0f
                        step?.let {
                            it()
                            tick(false)
                        }
                    }
                }
                .padding(vertical = 2.dp),
        ) {
            Text(
                display,
                style = CruxTheme.type.metricMedium,
                color = when {
                    !enabled -> colors.onSurface.copy(alpha = 0.38f)
                    scrubbing -> colors.onPrimaryContainer
                    else -> colors.onSurface
                },
                maxLines = 1,
                modifier = Modifier.testTag("${testTagPrefix}_value"),
            )
            Text(unit.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        RepeatButton(Icons.Rounded.Add, "Increase $unit", enabled && canIncrease, onIncrease, Modifier.testTag("${testTagPrefix}_plus"))
    }
}

/** 16 dp of drag on a stepper's value moves it one step. */
private val SCRUB_STEP = 16.dp

/**
 * One press steps once; holding repeats after 400 ms every 90 ms, then every 30 ms once ten
 * steps have gone by. Stops at a bound because the button disables.
 */
@Composable
private fun RepeatButton(icon: ImageVector, description: String, enabled: Boolean, onStep: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val tick = rememberTicker()
    val step by rememberUpdatedState(onStep)
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier
            .size(CruxTheme.size.controlHeight)
            .clip(CircleShape)
            .background(if (enabled) colors.surfaceContainerHigh else colors.surfaceContainerHigh.copy(alpha = 0.5f))
            .semantics(mergeDescendants = true) {
                role = Role.Button
                contentDescription = description
                if (!enabled) disabled()
                onClick {
                    if (enabled) step()
                    enabled
                }
            }
            .pointerInput(enabled) {
                if (!enabled) return@pointerInput
                coroutineScope {
                    awaitEachGesture {
                        awaitFirstDown()
                        step()
                        tick(false)
                        val repeat = launch {
                            delay(400)
                            var count = 0
                            while (true) {
                                step()
                                tick(false)
                                count++
                                delay(if (count > 10) 30 else 90)
                            }
                        }
                        waitForUpOrCancellation()
                        repeat.cancel()
                    }
                }
            },
    ) {
        Icon(icon, contentDescription = null, tint = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f))
    }
}
