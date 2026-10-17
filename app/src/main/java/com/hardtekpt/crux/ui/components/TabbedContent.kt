package com.hardtekpt.crux.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import com.hardtekpt.crux.ui.navigation.swipeBetweenTabs
import com.hardtekpt.crux.ui.theme.CruxTheme

/**
 * A page's own tabs (Train's Week, Plans and Exercises; the Journal's types; Progress; Datasets)
 * and what's under them. The tabs stay put: only the selected segment changes (its colours fade
 * across), and only the content swaps, built fresh for each tab so it starts at the top. A
 * sideways swipe on the content moves to the next or previous tab; nothing slides or shakes.
 * The page's header goes above this, outside it.
 */
@Composable
fun <T> TabbedContent(
    tabs: List<T>,
    selected: T,
    onSelect: (T) -> Unit,
    label: (T) -> String,
    modifier: Modifier = Modifier,
    tabsModifier: Modifier = Modifier,
    /** The test tag of a tab, when its label changes (a count in it). */
    tagOf: ((T) -> String)? = null,
    /** Rows of five: a name over a count. */
    compact: Boolean = false,
    content: @Composable ColumnScope.(T) -> Unit,
) {
    Column(modifier) {
        CruxSegmentedButtons(
            options = tabs,
            selected = selected,
            label = label,
            onSelect = onSelect,
            modifier = Modifier.padding(horizontal = CruxTheme.space.s4, vertical = CruxTheme.space.s2).then(tabsModifier),
            tagOf = tagOf ?: { "segment_${label(it)}" },
            compact = compact,
        )
        Column(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                .swipeBetweenTabs(follow = false) { direction -> tabs.getOrNull(tabs.indexOf(selected) + direction)?.let(onSelect) },
        ) {
            key(selected) { content(selected) }
        }
    }
}
