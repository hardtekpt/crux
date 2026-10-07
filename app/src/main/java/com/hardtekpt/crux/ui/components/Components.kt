package com.hardtekpt.crux.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.TopAppBarScrollBehavior
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import com.hardtekpt.crux.ui.theme.CruxTheme

/** `label-small` uppercase eyebrow above a group: THIS WEEK. */
@Composable
fun Eyebrow(text: String, modifier: Modifier = Modifier) {
    Text(
        text = text.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = modifier,
    )
}

/** Small top app bar for the root destinations: `surface` at rest, `surface-container` once scrolled under. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CruxTopAppBar(
    title: String,
    modifier: Modifier = Modifier,
    scrollBehavior: TopAppBarScrollBehavior? = null,
    onBack: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {},
) {
    TopAppBar(
        title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        navigationIcon = {
            if (onBack != null) {
                IconButton(onClick = onBack, modifier = Modifier.testTag("back")) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Back")
                }
            }
        },
        actions = actions,
        modifier = modifier,
        expandedHeight = CruxTheme.size.appBarHeight,
        scrollBehavior = scrollBehavior,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = MaterialTheme.colorScheme.surface,
            scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
            titleContentColor = MaterialTheme.colorScheme.onSurface,
        ),
    )
}

enum class CruxCardFill { Default, Low, Outlined }

/** The container everything sits in: `radius-lg`, `space-4` padding, no shadow. */
@Composable
fun CruxCard(modifier: Modifier = Modifier, fill: CruxCardFill = CruxCardFill.Default, content: @Composable ColumnScope.() -> Unit) {
    val colors = MaterialTheme.colorScheme
    Card(
        modifier = modifier,
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = when (fill) {
                CruxCardFill.Default -> colors.surfaceContainerLow
                CruxCardFill.Low -> colors.surfaceContainer
                CruxCardFill.Outlined -> colors.surface
            },
            contentColor = colors.onSurface,
        ),
        // Every card carries a hairline so it stands off the screen behind it.
        border = BorderStroke(CruxTheme.size.borderHairline, colors.outlineVariant),
        elevation = CardDefaults.cardElevation(0.dp),
    ) {
        Column(
            modifier = Modifier.padding(CruxTheme.space.s4),
            verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s1),
            content = content,
        )
    }
}

enum class TrendDirection { Wanted, Neutral }

/**
 * One figure, its label and its trend. The unit rides beside the figure in `muted`.
 * `isPersonalBest` marks the tile with a gold edge and a gold "New best"; one per screen.
 * The tile keeps its normal fill so a best reads as an accent, not a block of colour.
 */
@Composable
fun StatTile(
    label: String,
    value: String,
    modifier: Modifier = Modifier,
    unit: String? = null,
    delta: String? = null,
    direction: TrendDirection = TrendDirection.Neutral,
    isPersonalBest: Boolean = false,
    valueModifier: Modifier = Modifier,
) {
    val colors = MaterialTheme.colorScheme
    val container = colors.surfaceContainerLow
    val content = colors.onSurface
    val secondaryText = colors.onSurfaceVariant

    Column(
        modifier = modifier
            .background(container, MaterialTheme.shapes.large)
            // A personal best gets a 2dp gold border; everything else a hairline.
            .border(
                if (isPersonalBest) 1.5.dp else CruxTheme.size.borderHairline,
                if (isPersonalBest) colors.secondary else colors.outlineVariant,
                MaterialTheme.shapes.large,
            )
            .padding(horizontal = CruxTheme.space.s4, vertical = CruxTheme.space.s4),
        verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s1),
    ) {
        Text(
            text = label.uppercase(),
            style = MaterialTheme.typography.labelSmall,
            color = secondaryText,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = buildAnnotatedString {
                withStyle(CruxTheme.type.metricLarge.toSpanStyle().copy(color = content)) { append(value) }
                if (unit != null) {
                    withStyle(
                        MaterialTheme.typography.titleMedium.toSpanStyle().copy(color = secondaryText),
                    ) { append(" $unit") }
                }
            },
            style = CruxTheme.type.metricLarge,
            maxLines = 1,
            modifier = valueModifier,
        )
        when {
            isPersonalBest -> Row(
                horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s1),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Rounded.Star,
                    contentDescription = null,
                    tint = colors.secondary,
                    modifier = Modifier.size(CruxTheme.size.iconSm),
                )
                Text("New best", style = MaterialTheme.typography.labelMedium, color = colors.secondary)
            }

            delta != null -> Text(
                text = delta,
                style = MaterialTheme.typography.bodySmall,
                color = if (direction == TrendDirection.Wanted) CruxTheme.colors.success else secondaryText,
            )
        }
    }
}

/** Full empty state: icon in a circle, what is missing, the first action. */
@Composable
fun EmptyState(icon: ImageVector, headline: String, sentence: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = CruxTheme.space.s8),
        verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s3, Alignment.CenterVertically),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .background(MaterialTheme.colorScheme.surfaceContainerHigh, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(CruxTheme.size.iconLg),
            )
        }
        Text(headline, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            sentence,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(Modifier.padding(top = CruxTheme.space.s2)) { action() }
        }
    }
}

/** Inline empty state for one empty section of a populated screen. */
@Composable
fun InlineEmptyState(icon: ImageVector, text: String, modifier: Modifier = Modifier) {
    CruxCard(modifier = modifier.fillMaxWidth(), fill = CruxCardFill.Low) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s3),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(
                text,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
