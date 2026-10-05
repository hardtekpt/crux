package com.hardtekpt.crux.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.ui.theme.CruxTheme

enum class GradeState { Attempted, Sent, PersonalBest }

/** A grade, printed exactly as picked, in the mono `grade` style. Colour only says attempted/sent/best. */
@Composable
fun GradeBadge(
    grade: String,
    state: GradeState,
    modifier: Modifier = Modifier,
    small: Boolean = false,
) {
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
            .then(if (onClick != null && enabled) Modifier.clickable(onClick = onClick) else Modifier)
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

/** `disabled-content` opacity token. */
internal const val DISABLED_CONTENT = 0.38f
