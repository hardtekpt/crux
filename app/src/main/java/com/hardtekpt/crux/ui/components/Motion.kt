package com.hardtekpt.crux.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle

/**
 * Crux's motion: short and quiet. State changes take 100–200 ms on decelerating curves, never
 * overshoot, and never hold up a tap. The system's "remove animations" setting scales them all
 * to nothing.
 */
object CruxMotion {
    /**
     * Reduce motion is on (the setting, or Android's Remove animations): page changes only fade.
     * Set by MainActivity; read by the navigation transitions, which run outside composition.
     */
    @Volatile var reduced: Boolean = false

    const val FAST = 120
    const val MEDIUM = 160
    const val SLOW = 200

    fun <T> fast() = tween<T>(FAST, easing = FastOutSlowInEasing)
    fun <T> medium() = tween<T>(MEDIUM, easing = FastOutSlowInEasing)
    fun <T> slow() = tween<T>(SLOW, easing = FastOutSlowInEasing)

    /** Page arrivals and departures: decelerating in, accelerating out. */
    const val ENTER = 180
    const val EXIT = 120
    val Decelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
    val Accelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)
    fun <T> enter() = tween<T>(ENTER, easing = Decelerate)
    fun <T> exit() = tween<T>(EXIT, easing = Accelerate)
}

/**
 * Content that fades in once its data is [ready], instead of showing empty values first. Data
 * that is already there on the first frame shows at once.
 */
@Composable
fun Modifier.revealWhen(ready: Boolean): Modifier {
    val alpha by animateFloatAsState(if (ready) 1f else 0f, tween(CruxMotion.FAST, easing = LinearOutSlowInEasing), label = "reveal")
    return graphicsLayer { this.alpha = alpha }
}

/** A slight press-in for big tap targets: 0.97 while held, no bounce. */
@Composable
fun Modifier.pressScale(interaction: MutableInteractionSource): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if (pressed) 0.97f else 1f, CruxMotion.fast(), label = "press")
    return graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/** A number that rolls up when it grows and down when it shrinks. */
@Composable
fun AnimatedCount(value: String, style: TextStyle, modifier: Modifier = Modifier, color: Color = Color.Unspecified) {
    AnimatedContent(
        targetState = value,
        transitionSpec = {
            val up = (targetState.toIntOrNull() ?: 0) >= (initialState.toIntOrNull() ?: 0)
            (slideInVertically(CruxMotion.medium()) { if (up) it / 2 else -it / 2 } + fadeIn(CruxMotion.medium()))
                .togetherWith(slideOutVertically(CruxMotion.medium()) { if (up) -it / 2 else it / 2 } + fadeOut(CruxMotion.fast()))
        },
        label = "count",
        modifier = modifier,
    ) { text ->
        Text(text, style = style, color = color)
    }
}

/** How a row in a lazy list comes, goes and moves: a short fade in, a quicker fade out, a slide to its new place. */
fun androidx.compose.foundation.lazy.LazyItemScope.listItemMotion(): Modifier =
    Modifier.animateItem(fadeInSpec = CruxMotion.medium(), placementSpec = CruxMotion.medium(), fadeOutSpec = CruxMotion.fast())
