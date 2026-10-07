package com.hardtekpt.crux.ui.navigation

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.ui.theme.CruxTheme

/** Height of the floating bar itself, without its margin. */
val FloatingNavBarHeight = 60.dp
private val TabWidth = 54.dp
private val IndicatorSize = 42.dp
private val LogButtonSize = 60.dp

/**
 * How much bottom space a scrolling screen needs so its last row clears the floating bar.
 * Zero on screens where the bar is hidden.
 */
val LocalNavBarClearance = staticCompositionLocalOf { 0.dp }

/**
 * The app's signature control: a floating, icon-only pill of the five destinations with a
 * sliding accent indicator, and the Log button docked beside it as the strongest action.
 */
@Composable
fun FloatingNavBar(
    selected: TopLevelDestination?,
    onNavigate: (TopLevelDestination) -> Unit,
    logOpen: Boolean,
    onLog: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val haptics = LocalHapticFeedback.current
    val destinations = TopLevelDestination.entries
    val selectedIndex = selected?.ordinal ?: -1

    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
    ) {
        Box(
            modifier = Modifier
                .height(FloatingNavBarHeight)
                .shadow(elevation = 12.dp, shape = CircleShape, clip = false)
                .clip(CircleShape)
                .background(colors.surfaceContainerHigh)
                .border(CruxTheme.size.borderHairline, colors.outlineVariant, CircleShape)
                .padding(horizontal = CruxTheme.space.s1),
            contentAlignment = Alignment.CenterStart,
        ) {
            // Sliding accent behind the active icon.
            if (selectedIndex >= 0) {
                val indicatorOffset by animateDpAsState(
                    targetValue = TabWidth * selectedIndex + (TabWidth - IndicatorSize) / 2,
                    animationSpec = spring(dampingRatio = 0.7f, stiffness = Spring.StiffnessMediumLow),
                    label = "indicator",
                )
                Box(
                    Modifier
                        .offset(x = indicatorOffset)
                        .size(IndicatorSize)
                        .background(colors.primaryContainer, CircleShape)
                        .border(CruxTheme.size.borderEmphasis, colors.primary, CircleShape),
                )
            }
            Row {
                destinations.forEach { destination ->
                    NavTab(
                        destination = destination,
                        selected = destination == selected,
                        onClick = {
                            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            onNavigate(destination)
                        },
                    )
                }
            }
        }
        LogButton(
            open = logOpen,
            onClick = {
                haptics.performHapticFeedback(HapticFeedbackType.ContextClick)
                onLog()
            },
        )
    }
}

@Composable
private fun NavTab(destination: TopLevelDestination, selected: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val tint by animateColorAsState(
        targetValue = if (selected) colors.primary else colors.onSurfaceVariant,
        animationSpec = tween(150),
        label = "tint",
    )
    val scale by animateFloatAsState(if (selected) 1.1f else 1f, tween(150), label = "scale")
    Box(
        modifier = Modifier
            .width(TabWidth)
            .fillMaxHeight()
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = ripple(bounded = false, radius = IndicatorSize / 2),
                role = Role.Tab,
                onClick = onClick,
            )
            .semantics {
                this.selected = selected
                contentDescription = destination.label
            }
            .testTag("nav_${destination.name}"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            destination.icon,
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(CruxTheme.size.iconMd * scale),
        )
    }
}

@Composable
private fun LogButton(open: Boolean, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    val rotation by animateFloatAsState(if (open) 45f else 0f, tween(250), label = "rotation")
    Box(
        modifier = Modifier
            .size(LogButtonSize)
            .shadow(elevation = 16.dp, shape = CircleShape, ambientColor = colors.primary, spotColor = colors.primary)
            .clip(CircleShape)
            .background(colors.primary)
            .clickable(role = Role.Button, onClickLabel = "Log", onClick = onClick)
            .semantics { contentDescription = "Log" }
            .testTag("log_fab"),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.Add,
            contentDescription = null,
            tint = colors.onPrimary,
            modifier = Modifier
                .size(CruxTheme.size.iconLg)
                .rotate(rotation),
        )
    }
}

/** Bar plus its margins; screens add the system navigation inset on top. */
fun navBarClearance(bottomInset: Dp): Dp = FloatingNavBarHeight + 16.dp + 16.dp + bottomInset
