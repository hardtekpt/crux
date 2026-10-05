package com.hardtekpt.crux.ui.components.input

import androidx.compose.foundation.gestures.FlingBehavior
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.launch

/**
 * The scrolling behind wheels and strips: a lazy list that snaps an item to its centre and
 * reports the centred index as the pick. A pick changed from outside (a preset, typing)
 * scrolls the list there without reporting the values it passes on the way.
 */
@Stable
class PickerScroll internal constructor(
    val list: LazyListState,
    val fling: FlingBehavior,
    private val scope: CoroutineScope,
    private val reduceMotion: Boolean,
) {
    internal var external by mutableStateOf(false)

    /** Scrolls to [index] as if the climber flicked there; values on the way are picked. */
    fun scrollTo(index: Int) {
        scope.launch { if (reduceMotion) list.scrollToItem(index) else list.animateScrollToItem(index) }
    }
}

@Composable
fun rememberPickerScroll(
    count: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    strongAt: (Int) -> Boolean = { false },
): PickerScroll {
    val list = rememberLazyListState(initialFirstVisibleItemIndex = selected.coerceIn(0, (count - 1).coerceAtLeast(0)))
    val fling = rememberSnapFlingBehavior(list)
    val scope = rememberCoroutineScope()
    val reduceMotion = rememberReduceMotion()
    val tick = rememberTicker()
    val picker = remember(list) { PickerScroll(list, fling, scope, reduceMotion) }
    val currentOnSelect by rememberUpdatedState(onSelect)
    val currentSelected by rememberUpdatedState(selected)
    val currentStrongAt by rememberUpdatedState(strongAt)

    LaunchedEffect(list) {
        snapshotFlow { list.layoutInfo.centredIndex() }
            .filterNotNull()
            .distinctUntilChanged()
            .collect { index ->
                if (!picker.external && index != currentSelected) {
                    tick(currentStrongAt(index))
                    currentOnSelect(index)
                }
            }
    }
    LaunchedEffect(selected, count) {
        if (count == 0) return@LaunchedEffect
        val target = selected.coerceIn(0, count - 1)
        val centred = list.layoutInfo.centredIndex()
        if (centred != target && !list.isScrollInProgress) {
            picker.external = true
            try {
                if (reduceMotion || centred == null) list.scrollToItem(target) else list.animateScrollToItem(target)
            } finally {
                picker.external = false
            }
        }
    }
    return picker
}
