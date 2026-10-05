package com.hardtekpt.crux.ui.components.input

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import kotlin.math.roundToInt

/** How a 1 to 10 effort reads in words. */
fun effortWord(value: Int): String = when (value) {
    in 1..2 -> "Easy"
    in 3..4 -> "Steady"
    in 5..6 -> "Moderate"
    in 7..8 -> "Hard"
    9 -> "Very hard"
    else -> "Limit"
}

/**
 * How hard something felt, 1 to 10, as ten segments: tap one or drag across them. Optional:
 * it starts empty and Clear empties it again.
 */
@Composable
fun EffortScale(
    value: Int?,
    onValueChange: (Int?) -> Unit,
    modifier: Modifier = Modifier,
    title: String = "How hard it felt",
) {
    val colors = MaterialTheme.colorScheme
    val tick = rememberTicker()
    val current by rememberUpdatedState(value)
    val currentOnChange by rememberUpdatedState(onValueChange)
    val pick = { x: Float, width: Int ->
        val next = ((x / width) * 10).toInt().coerceIn(0, 9) + 1
        if (next != current) {
            tick(next == 10)
            currentOnChange(next)
        }
    }
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
            Eyebrow(title, Modifier.weight(1f))
            if (value != null) {
                Text(
                    value.toString(),
                    style = TextStyle(fontFamily = Archivo, fontWeight = FontWeight.ExtraBold, fontSize = 24.sp, fontFeatureSettings = "tnum"),
                    modifier = Modifier.testTag("effort_value"),
                )
                Text(
                    " ${effortWord(value)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant,
                )
                TextButton(onClick = { onValueChange(null) }) { Text("Clear") }
            } else {
                Text("Optional", style = MaterialTheme.typography.bodyMedium, color = colors.onSurfaceVariant)
            }
        }
        Row(
            horizontalArrangement = Arrangement.spacedBy(3.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(36.dp)
                .semantics {
                    contentDescription = title
                    stateDescription = value?.let { "$it, ${effortWord(it)}" } ?: "Not set"
                    progressBarRangeInfo = ProgressBarRangeInfo((value ?: 0).toFloat(), 1f..10f, steps = 8)
                    setProgress { target ->
                        onValueChange(target.roundToInt().coerceIn(1, 10))
                        true
                    }
                }
                // Taps and sideways drags pick; an up-or-down drag still scrolls the page.
                .pointerInput(Unit) {
                    detectTapGestures { pick(it.x, size.width) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        change.consume()
                        pick(change.position.x, size.width)
                    }
                }
                .testTag("effort"),
        ) {
            (1..10).forEach { segment ->
                val filled = value != null && segment <= value
                val shape = RoundedCornerShape(4.dp)
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .background(
                            when {
                                !filled -> colors.surfaceContainerHighest
                                segment == value -> colors.primary
                                else -> colors.primary.copy(alpha = 0.55f)
                            },
                            shape,
                        )
                        .border(CruxTheme.size.borderHairline, if (filled) colors.primary else colors.outlineVariant, shape)
                        .testTag("effort_$segment"),
                )
            }
        }
    }
}
