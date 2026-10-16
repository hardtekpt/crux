package com.hardtekpt.crux.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.rounded.ExpandMore
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.ui.theme.CruxTheme

enum class GradeState { Attempted, Sent, PersonalBest }

/** A grade, printed exactly as picked, in the mono `grade` style. Colour only says attempted/sent/best. */
@Composable
fun GradeBadge(grade: String, state: GradeState, modifier: Modifier = Modifier, small: Boolean = false) {
    val colors = MaterialTheme.colorScheme
    val (container, content) = when (state) {
        GradeState.Attempted -> colors.surfaceContainerHighest to colors.onSurface
        GradeState.Sent -> CruxTheme.colors.successContainer to CruxTheme.colors.onSuccessContainer
        GradeState.PersonalBest -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Box(
        modifier = modifier
            .defaultMinSize(minWidth = 46.dp, minHeight = if (small) 24.dp else 30.dp)
            .background(container, CruxTheme.shape.xl)
            .then(
                if (state == GradeState.PersonalBest) {
                    Modifier.border(CruxTheme.size.borderEmphasis, colors.secondary, CruxTheme.shape.xl)
                } else {
                    Modifier
                },
            )
            .padding(horizontal = CruxTheme.space.s2),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = grade,
            style = if (small) CruxTheme.type.gradeSmall else CruxTheme.type.grade,
            color = content,
            textAlign = TextAlign.Center,
            maxLines = 1,
        )
    }
}

/**
 * One climb, one exercise, one set: leading slot, two text lines, trailing slot.
 * Standalone rows are `surface-container` cards at `radius-md`.
 */
@Composable
fun CruxListRow(
    title: String,
    modifier: Modifier = Modifier,
    supporting: String? = null,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    selected: Boolean = false,
    enabled: Boolean = true,
    onClick: (() -> Unit)? = null,
    /** A long press, such as starting a selection. */
    onLongClick: (() -> Unit)? = null,
) {
    val colors = MaterialTheme.colorScheme
    val contentAlpha = if (enabled) 1f else DISABLED_CONTENT
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .background(if (selected) colors.primaryContainer.copy(alpha = 0.35f) else colors.surfaceContainerLow)
            .border(
                if (selected) CruxTheme.size.borderEmphasis else CruxTheme.size.borderHairline,
                if (selected) colors.primary else colors.outlineVariant,
                MaterialTheme.shapes.medium,
            )
            .then(
                when {
                    !enabled -> Modifier
                    onLongClick != null -> Modifier.combinedClickable(onClick = { onClick?.invoke() }, onLongClick = onLongClick)
                    onClick != null -> Modifier.clickable(onClick = onClick)
                    else -> Modifier
                },
            )
            .heightIn(min = CruxTheme.size.touchTarget)
            .padding(horizontal = CruxTheme.space.s4, vertical = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        leading?.invoke()
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.titleMedium,
                color = colors.onSurface.copy(alpha = contentAlpha),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (supporting != null) {
                Text(
                    supporting,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colors.onSurfaceVariant.copy(alpha = contentAlpha),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
        trailing?.invoke()
    }
}

/**
 * The grade at the start of a climb row: mono, a fixed width so names line up down a list,
 * "–" when there is none. Lists of climbs (datasets, catalogues, playlists, the Climb step)
 * all lead with it; a climb you logged uses [GradeBadge] instead, which also says sent or best.
 */
@Composable
fun ClimbGrade(grade: String?, modifier: Modifier = Modifier, color: androidx.compose.ui.graphics.Color = MaterialTheme.colorScheme.onSurface) {
    Text(
        grade ?: "–",
        style = CruxTheme.type.grade,
        color = color,
        textAlign = TextAlign.Start,
        maxLines = 1,
        modifier = modifier.width(44.dp),
    )
}

/**
 * A setting with its value: the label on the left, the value in the accent with a ▾ on the right;
 * tapping opens its picker. The one row for "Board · Kilter Original", "Boulder grades · My
 * settings", "Datasets · None" and the like, in forms and sheets alike.
 */
@Composable
fun SettingRow(label: String, value: String, tag: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = CruxTheme.size.touchTarget)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClickLabel = "Change $label", onClick = onClick)
            .padding(vertical = CruxTheme.space.s1)
            .testTag(tag),
    ) {
        Text(label, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
        Text(value, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.primary, maxLines = 1, overflow = TextOverflow.Ellipsis)
        androidx.compose.material3.Icon(
            androidx.compose.material.icons.Icons.Rounded.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** `disabled-content` opacity token. */
internal const val DISABLED_CONTENT = 0.38f
