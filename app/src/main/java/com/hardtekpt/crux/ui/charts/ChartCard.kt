package com.hardtekpt.crux.ui.charts

import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.theme.CruxTheme
import java.time.LocalDate

/** A chart lives in a card whose header names the series; no legend needed. */
@Composable
fun ChartCard(
    title: String,
    modifier: Modifier = Modifier,
    trailing: String? = null,
    content: @Composable () -> Unit,
) {
    CruxCard(modifier = modifier.fillMaxWidth()) {
        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = CruxTheme.space.s2)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (trailing != null) {
                Text(trailing, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        content()
    }
}

/** Time windows offered above time-series charts. */
enum class ChartRange(val label: String, val days: Long?) {
    Month("1M", 30),
    Quarter("3M", 91),
    Year("1Y", 365),
    All("All", null),
    ;

    fun start(today: LocalDate): LocalDate? = days?.let { today.minusDays(it) }
}
