package com.hardtekpt.crux.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.hardtekpt.crux.ui.theme.CruxTheme

/** One figure in a [StatStrip]: the number, what it counts, and whether it is worth noticing. */
@Immutable
data class StatFigure(val value: String, val label: String, val notable: Boolean = false, val tag: String? = null)

/**
 * A row of figures with no border of their own, for numbers that sit inside a card or a sheet:
 * the figure in Archivo, its label in the mono eyebrow under it. A [StatFigure.notable] figure
 * turns amber (an open project, a best); the strip itself never gets a fill. Standalone figures
 * on the screen's ground use [StatTile] instead.
 */
@Composable
fun StatStrip(figures: List<StatFigure>, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s4)) {
        figures.forEach { figure ->
            Column(Modifier.weight(1f).then(figure.tag?.let { Modifier.testTag(it) } ?: Modifier)) {
                Text(
                    figure.value,
                    style = CruxTheme.type.figure,
                    color = if (figure.notable) colors.secondary else colors.onSurface,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    figure.label.uppercase(),
                    style = MaterialTheme.typography.labelSmall,
                    color = colors.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}
