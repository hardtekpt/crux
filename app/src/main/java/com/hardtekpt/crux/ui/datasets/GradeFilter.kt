package com.hardtekpt.crux.ui.datasets

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Badge
import androidx.compose.material3.BadgedBox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.SheetButton
import com.hardtekpt.crux.ui.theme.CruxTheme
import kotlin.math.roundToInt

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
