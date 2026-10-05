package com.hardtekpt.crux.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.slideOutVertically
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy

/*
 * App-wide page motion, following the design system: short and physical, Material 3
 * emphasized easing, about 150 ms for leaving and 250 ms for arriving.
 *
 * - Switching tabs is a fade-through: the old screen fades out fast, the new one fades in
 *   with a slight scale-up. Tabs are peers, so nothing slides.
 * - Drilling into a screen (a plan, a setting) is a shared-axis slide: the new screen comes
 *   in from the right a short way while the old one drifts left.
 * - Going back follows Material's predictive back: the screen being left shrinks, rounds its
 *   corners (see the page wrapper in CruxApp) and slides off toward the edge, while the one
 *   underneath settles in from a slight parallax offset. The back gesture scrubs this motion
 *   with the finger, so it reads as pulling the card away.
 * - Forms (log a climb, edit a plan) rise from below, like a sheet, and drop back down.
 */

private val Emphasized = CubicBezierEasing(0.2f, 0f, 0f, 1f)
private val EmphasizedDecelerate = CubicBezierEasing(0.05f, 0.7f, 0.1f, 1f)
private val EmphasizedAccelerate = CubicBezierEasing(0.3f, 0f, 0.8f, 0.15f)

private const val ENTER_MS = 250
private const val EXIT_MS = 150
private const val FADE_THROUGH_OUT_MS = 75
private const val FADE_THROUGH_IN_MS = 175
private const val BACK_MS = 300

/** Shared-axis travel: a fraction of the width, so it reads as direction, not a full swipe. */
private fun travel(fullWidth: Int) = (fullWidth * 0.08f).toInt()

private fun NavDestination.tabIndex(): Int? =
    TopLevelDestination.entries.firstOrNull { tab -> hierarchy.any { it.hasRoute(tab.graph::class) } }?.ordinal

private fun NavDestination.isForm(): Boolean =
    hasRoute(LogClimbRoute::class) || hasRoute(LogWeightRoute::class) ||
        hasRoute(PlanEditorRoute::class) || hasRoute(ExerciseEditorRoute::class)

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

val cruxEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    when (move()) {
        Move.TabSwitch -> fadeIn(tween(FADE_THROUGH_IN_MS, delayMillis = FADE_THROUGH_OUT_MS, easing = EmphasizedDecelerate)) +
            scaleIn(tween(FADE_THROUGH_IN_MS, delayMillis = FADE_THROUGH_OUT_MS, easing = EmphasizedDecelerate), initialScale = 0.96f)
        Move.FormOpen -> slideInVertically(tween(ENTER_MS + 50, easing = EmphasizedDecelerate)) { it / 6 } +
            fadeIn(tween(ENTER_MS, easing = EmphasizedDecelerate))
        Move.FormClose -> fadeIn(tween(ENTER_MS, easing = Emphasized))
        Move.Push -> slideInHorizontally(tween(ENTER_MS, easing = EmphasizedDecelerate), ::travel) +
            fadeIn(tween(ENTER_MS, easing = EmphasizedDecelerate))
    }
}

val cruxExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    when (move()) {
        Move.TabSwitch -> fadeOut(tween(FADE_THROUGH_OUT_MS, easing = EmphasizedAccelerate))
        Move.FormOpen -> fadeOut(tween(EXIT_MS, easing = EmphasizedAccelerate))
        Move.FormClose -> slideOutVertically(tween(EXIT_MS + 50, easing = EmphasizedAccelerate)) { it / 6 } +
            fadeOut(tween(EXIT_MS, easing = EmphasizedAccelerate))
        Move.Push -> slideOutHorizontally(tween(EXIT_MS, easing = EmphasizedAccelerate)) { -travel(it) } +
            fadeOut(tween(EXIT_MS, easing = EmphasizedAccelerate))
    }
}

val cruxPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    when (move()) {
        // The screen underneath was parked a quarter-width left; it settles back as the
        // top card is pulled away, with a touch of scale so it feels like it rises.
        Move.Push -> slideInHorizontally(tween(BACK_MS, easing = Emphasized)) { -(it * 0.25f).toInt() } +
            scaleIn(tween(BACK_MS, easing = Emphasized), initialScale = 0.94f)
        else -> cruxEnter()
    }
}

val cruxPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    when (move()) {
        // The card being left shrinks and slides off to the right; corners round in the
        // page wrapper as it goes.
        // No fade: an opaque card moving over the page underneath reads cleaner.
        Move.Push -> scaleOut(tween(BACK_MS, easing = Emphasized), targetScale = 0.9f) +
            slideOutHorizontally(tween(BACK_MS, easing = EmphasizedAccelerate)) { it }
        Move.FormClose -> slideOutVertically(tween(BACK_MS, easing = EmphasizedAccelerate)) { it / 3 } +
            scaleOut(tween(BACK_MS, easing = Emphasized), targetScale = 0.94f) +
            fadeOut(tween(BACK_MS, easing = EmphasizedAccelerate))
        else -> cruxExit()
    }
}
