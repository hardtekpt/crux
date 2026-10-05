package com.hardtekpt.crux.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.ui.theme.CruxTheme

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
            selectedBorderWidth = 0.dp,
        ),
        modifier = modifier.height(32.dp),
    )
}

/** Two to four views of the same thing; exactly one is selected. */
@Composable
fun <T> CruxSegmentedButtons(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    SingleChoiceSegmentedButtonRow(modifier = modifier.fillMaxWidth()) {
        options.forEachIndexed { index, option ->
            SegmentedButton(
                selected = option == selected,
                onClick = { onSelect(option) },
                shape = SegmentedButtonDefaults.itemShape(
                    index = index,
                    count = options.size,
                    baseShape = MaterialTheme.shapes.small,
                ),
                colors = SegmentedButtonDefaults.colors(
                    activeContainerColor = colors.primaryContainer,
                    activeContentColor = colors.onPrimaryContainer,
                    inactiveContainerColor = colors.surface,
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
        Text(label, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
        OutlinedTextField(
            value = value,
            onValueChange = onValueChange,
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = CruxTheme.size.touchTarget),
            placeholder = placeholder?.let { { Text(it, style = MaterialTheme.typography.bodyLarge) } },
            textStyle = MaterialTheme.typography.bodyLarge,
            isError = error != null,
            singleLine = singleLine,
            minLines = minLines,
            keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
            shape = MaterialTheme.shapes.small,
            colors = OutlinedTextFieldDefaults.colors(
                focusedContainerColor = colors.surface,
                unfocusedContainerColor = colors.surface,
                unfocusedBorderColor = colors.outline,
                focusedBorderColor = colors.primary,
                errorBorderColor = colors.error,
            ),
        )
        Text(
            text = error ?: helper ?: "",
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) colors.error else colors.onSurfaceVariant,
            minLines = 1,
        )
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

/** Stepper with caller-defined steps, for values like load that step unevenly. */
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
    val buttonColors = IconButtonDefaults.filledIconButtonColors(
        containerColor = colors.surface,
        contentColor = colors.onSurface,
    )
    Row(
        modifier = modifier
            .background(colors.surfaceContainer, CircleShape)
            .padding(CruxTheme.space.s1),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
    ) {
        FilledIconButton(
            onClick = onDecrease,
            enabled = canDecrease,
            colors = buttonColors,
            modifier = Modifier
                .size(CruxTheme.size.touchTarget)
                .testTag("${testTagPrefix}_minus"),
        ) { Icon(Icons.Rounded.Remove, contentDescription = "Decrease $unit") }
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.widthIn(min = 44.dp),
        ) {
            Text(
                display,
                style = CruxTheme.type.metricMedium,
                color = if (enabled) colors.onSurface else colors.onSurface.copy(alpha = 0.38f),
                maxLines = 1,
                modifier = Modifier.testTag("${testTagPrefix}_value"),
            )
            Text(unit.uppercase(), style = MaterialTheme.typography.labelSmall, color = colors.onSurfaceVariant)
        }
        FilledIconButton(
            onClick = onIncrease,
            enabled = canIncrease,
            colors = buttonColors,
            modifier = Modifier
                .size(CruxTheme.size.touchTarget)
                .testTag("${testTagPrefix}_plus"),
        ) { Icon(Icons.Rounded.Add, contentDescription = "Increase $unit") }
    }
}
