package com.hardtekpt.crux.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.ui.unit.IntOffset
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import com.hardtekpt.crux.ui.components.CruxMotion

/*
 * App-wide page motion: fast, short and quiet. Every transition is a quick fade with at
 * most a small nudge (about 16 dp) to say which way you went; nothing scales or swoops.
 *
 * - Tabs: a straight cross-fade.
 * - Drilling in: the new screen fades in nudged from the right.
 * - Forms: fade in nudged up from below; closing drops them back down.
 * - Back, by button or swipe: a plain fade. Nothing moves or shrinks.
 *
 * Arrivals take 180 ms with a decelerating curve, departures 120 ms. The swipe-back gesture
 * scrubs the same fade instead of the library's default shrink.
 */

/** The nudge: ~16 dp on a phone, as a share of the width so it scales with the screen. */
private fun nudge(fullSize: Int) = (fullSize * 0.04f).toInt()

// The curves and durations live in CruxMotion with the rest of the app's motion.
private fun <T> enterSpec() = CruxMotion.enter<T>()
private fun <T> exitSpec() = CruxMotion.exit<T>()

private fun NavDestination.tabIndex(): Int? = TopLevelDestination.entries.firstOrNull { tab -> hierarchy.any { it.hasRoute(tab.graph::class) } }?.ordinal

private fun NavDestination.isForm(): Boolean = hasRoute(LogClimbRoute::class) || hasRoute(LogWeightRoute::class) ||
    hasRoute(PlanEditorRoute::class) || hasRoute(ExerciseEditorRoute::class) ||
    hasRoute(PlaceEditorRoute::class) ||
    hasRoute(NoteEditorRoute::class) || hasRoute(RecordEditorRoute::class)

private enum class Move { TabSwitch, FormOpen, FormClose, Push }

private fun AnimatedContentTransitionScope<NavBackStackEntry>.move(): Move {
    val from = initialState.destination
    val to = targetState.destination
    return when {
        to.isForm() && !from.isForm() -> Move.FormOpen
        from.isForm() && !to.isForm() -> Move.FormClose
        from.tabIndex() != to.tabIndex() -> Move.TabSwitch
        else -> Move.Push
    }
}

private fun fadeInOnly(): EnterTransition = fadeIn(enterSpec())
private fun fadeOutOnly(): ExitTransition = fadeOut(exitSpec())

val cruxEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    when (if (CruxMotion.reduced) Move.TabSwitch else move()) {
        Move.TabSwitch, Move.FormClose -> fadeInOnly()
        Move.FormOpen -> fadeInOnly() + slideInVertically(enterSpec<IntOffset>()) { nudge(it) * 2 }
        Move.Push -> fadeInOnly() + slideInHorizontally(enterSpec<IntOffset>(), ::nudge)
    }
}

val cruxExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    when (if (CruxMotion.reduced) Move.TabSwitch else move()) {
        Move.TabSwitch, Move.FormOpen, Move.Push -> fadeOutOnly()
        Move.FormClose -> fadeOutOnly() + slideOutVertically(exitSpec<IntOffset>()) { nudge(it) * 2 }
    }
}

val cruxPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeInOnly()
}

val cruxPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOutOnly()
}

/** Swipe-back: the same plain fades, so the closing page never scales toward the centre. */
val cruxPredictivePopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> EnterTransition = {
    fadeInOnly()
}

val cruxPredictivePopExit: AnimatedContentTransitionScope<NavBackStackEntry>.(Int) -> ExitTransition = {
    fadeOutOnly()
}
