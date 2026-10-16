package com.hardtekpt.crux.ui.navigation

import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.ui.components.CruxMotion
import kotlin.math.abs
import kotlinx.coroutines.launch

/**
 * A sideways swipe moves between a page's own tabs (the Journal's types, Train's
 * Plans and Exercises): right to left goes to the next one, left to right to the
 * previous. It only listens after the content has
 * had its turn, so anything that handles its own horizontal drag (wheels, rulers, grade and
 * day strips, scrolling rows, the dashboard's drag) keeps the gesture and no tab switch
 * happens. Mostly-vertical drags are left to scrolling.
 */
fun Modifier.swipeBetweenTabs(enabled: Boolean = true, onSwipe: (direction: Int) -> Unit): Modifier = composed {
    val currentEnabled by rememberUpdatedState(enabled)
    val currentOnSwipe by rememberUpdatedState(onSwipe)
    // Once the swipe is claimed, the page follows the finger a little (a quarter of the drag, at
    // most FOLLOW), so it answers before the finger lifts; it settles back if the swipe falls short.
    val scope = rememberCoroutineScope()
    val shift = remember { Animatable(0f) }
    pointerInput(Unit) {
        val follow = FOLLOW.toPx()
        val distance = SWIPE_DISTANCE.toPx()
        awaitEachGesture {
            val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Final)
            // Swipes from the very edges belong to the system back gesture.
            val edge = EDGE.toPx()
            if (!currentEnabled || down.position.x < edge || down.position.x > size.width - edge) return@awaitEachGesture
            var dx = 0f
            var dy = 0f
            var claimedByScreen = false
            var swiping = false
            val slop = viewConfiguration.touchSlop
            while (true) {
                val event = awaitPointerEvent(PointerEventPass.Final)
                val change = event.changes.firstOrNull { it.id == down.id } ?: break
                if (event.changes.size > 1) claimedByScreen = true
                // Before we take the swipe, anything below that used the drag keeps it.
                if (!swiping && change.isConsumed) claimedByScreen = true
                val delta = change.positionChange()
                dx += delta.x
                dy += delta.y
                if (!swiping && !claimedByScreen && abs(dx) > slop && abs(dx) > abs(dy) * 2f) swiping = true
                // Once it is clearly a sideways swipe, claim it, so a card or row under the
                // finger doesn't also take it as a tap.
                if (swiping) {
                    change.consume()
                    val to = (dx * 0.25f).coerceIn(-follow, follow)
                    scope.launch { shift.snapTo(to) }
                }
                if (!change.pressed) break
            }
            if (swiping && !claimedByScreen && abs(dx) > distance && abs(dx) > abs(dy) * 2f) {
                currentOnSwipe(if (dx < 0) 1 else -1)
                scope.launch { shift.snapTo(0f) }
            } else if (shift.value != 0f) {
                scope.launch { shift.animateTo(0f, CruxMotion.fast()) }
            }
        }
    }.graphicsLayer {
        translationX = shift.value
        alpha = 1f - 0.2f * (abs(shift.value) / FOLLOW.toPx()).coerceIn(0f, 1f)
    }
}

/** How far the page follows a swipe before it lets go. */
private val FOLLOW = 24.dp

/** How far a finger must travel sideways to switch tabs. */
private val SWIPE_DISTANCE = 72.dp

/** The strip along each side reserved for the system back gesture. */
private val EDGE = 24.dp
