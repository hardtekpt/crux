package com.hardtekpt.crux.ui.datasets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.SheetButton
import com.hardtekpt.crux.ui.theme.CruxTheme

/** A range of grades in [scale], by index; an open end has no limit. */
data class GradeRange(val scale: GradeScale?, val from: Int? = null, val to: Int? = null) {
    val active: Boolean get() = from != null || to != null

    fun contains(index: Int): Boolean = (from == null || index >= from) && (to == null || index <= to)

    /** "6A to 7A", "from 6A", "up to 7A". */
    fun label(): String? = if (scale == null) {
        null
    } else {
        when {
            from != null && to != null -> if (from == to) scale.label(from) else "${scale.label(from)} to ${scale.label(to)}"
            from != null -> "from ${scale.label(from)}"
            to != null -> "up to ${scale.label(to)}"
            else -> null
        }
    }
}

/** Filters, filled in the accent with a count when anything in the sheet is set. */
@Composable
internal fun FiltersButton(active: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    IconButton(
        onClick = onClick,
        colors = if (active > 0) IconButtonDefaults.filledTonalIconButtonColors() else IconButtonDefaults.iconButtonColors(),
        modifier = modifier,
    ) {
        BadgedBox(
            badge = {
                if (active > 0) {
                    Badge(containerColor = colors.primary, contentColor = colors.onPrimary) { Text(active.toString()) }
                }
            },
        ) {
            Icon(Icons.Rounded.Tune, contentDescription = "Filters")
        }
    }
}

/**
 * The filters of a dataset or a board catalogue: from which grade and up to which, in the
 * climber's scale. The list updates as you pick; [extra] adds a section of the page's own.
 */
@Composable
internal fun GradeFilterSheet(range: GradeRange, shown: Int, onRange: (GradeRange) -> Unit, onDismiss: () -> Unit, extra: @Composable () -> Unit = {}) {
    val space = CruxTheme.space
    val scale = range.scale ?: return
    CruxSheet(
        onDismiss = onDismiss,
        modifier = Modifier.testTag("grade_filter_sheet"),
        title = "Filters",
        action = SheetButton("Clear all", { onRange(range.copy(from = null, to = null)) }, enabled = range.active),
        confirm = SheetButton(if (shown == 1) "Show 1 climb" else "Show $shown climbs", onDismiss, tag = "apply_filters"),
    ) {
        Eyebrow("Easiest grade", Modifier.padding(top = space.s2))
        GradeRow(scale, range.from, "grade_from") { onRange(range.copy(from = it, to = range.to?.takeIf { to -> it == null || to >= it })) }
        Eyebrow("Hardest grade", Modifier.padding(top = space.s2))
        GradeRow(scale, range.to, "grade_to") { onRange(range.copy(to = it, from = range.from?.takeIf { from -> it == null || from <= it })) }
        extra()
    }
}

@Composable
private fun GradeRow(scale: GradeScale, picked: Int?, tag: String, onPick: (Int?) -> Unit) {
    LazyRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.testTag(tag)) {
        item { CruxFilterChip("Any", picked == null, { onPick(null) }, Modifier.testTag("${tag}_any")) }
        itemsIndexed(scale.grades) { index, grade ->
            CruxFilterChip(grade, picked == index, { onPick(if (picked == index) null else index) }, Modifier.testTag("${tag}_$grade"))
        }
    }
}
