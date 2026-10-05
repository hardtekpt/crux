package com.hardtekpt.crux.ui.components.input

import android.content.Context
import android.os.Build
import android.provider.Settings
import android.view.HapticFeedbackConstants
import androidx.compose.foundation.lazy.LazyListLayoutInfo
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.CompositingStrategy
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import kotlin.math.abs

/*
 * Shared behaviour for the touch inputs: a haptic tick as the picked value changes, edge
 * fades, and the reduced-motion check.
 */

/**
 * A light tick each time the picked value changes; [strong] marks the big steps (whole
 * kilos, whole minutes). Follows the system touch-feedback setting.
 */
@Composable
fun rememberTicker(): (strong: Boolean) -> Unit {
    val view = LocalView.current
    return remember(view) {
        { strong ->
            val effect = when {
                Build.VERSION.SDK_INT >= 34 && strong -> HapticFeedbackConstants.SEGMENT_TICK
                Build.VERSION.SDK_INT >= 34 -> HapticFeedbackConstants.SEGMENT_FREQUENT_TICK
                strong -> HapticFeedbackConstants.KEYBOARD_TAP
                else -> HapticFeedbackConstants.CLOCK_TICK
            }
            view.performHapticFeedback(effect)
        }
    }
}

/** True when the system's animator scale is off: values jump instead of gliding. */
@Composable
fun rememberReduceMotion(): Boolean {
    val context = LocalContext.current
    return remember(context) { context.animationsOff() }
}

private fun Context.animationsOff(): Boolean =
    Settings.Global.getFloat(contentResolver, Settings.Global.ANIMATOR_DURATION_SCALE, 1f) == 0f

/** Fades content toward both ends along one axis; [edge] is the share faded at each end. */
fun Modifier.fadeEdges(vertical: Boolean, edge: Float = 0.3f): Modifier = this
    .graphicsLayer { compositingStrategy = CompositingStrategy.Offscreen }
    .drawWithContent {
        drawContent()
        val stops = arrayOf(0f to Color.Transparent, edge to Color.Black, (1f - edge) to Color.Black, 1f to Color.Transparent)
        val brush = if (vertical) {
            Brush.verticalGradient(*stops)
        } else {
            Brush.horizontalGradient(*stops, startX = 0f, endX = size.width)
        }
        drawRect(brush = brush, topLeft = Offset.Zero, size = size, blendMode = BlendMode.DstIn)
    }

/** The index of the item whose centre is nearest the middle of the viewport. */
internal fun LazyListLayoutInfo.centredIndex(): Int? {
    val middle = (viewportStartOffset + viewportEndOffset) / 2
    return visibleItemsInfo.minByOrNull { abs(it.offset + it.size / 2 - middle) }?.index
}
