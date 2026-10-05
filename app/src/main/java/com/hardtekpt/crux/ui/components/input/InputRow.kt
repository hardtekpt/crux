package com.hardtekpt.crux.ui.components.input

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.hardtekpt.crux.ui.theme.Archivo
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.theme.JetBrainsMono

/**
 * A labelled value that opens in place to edit it, usually with a wheel. Keep one open at a
 * time: the caller holds which row is open.
 */
@Composable
fun InputRow(
    label: String,
    value: String,
    open: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier,
    unit: String? = null,
    mono: Boolean = false,
    testTag: String = "row",
    content: @Composable ColumnScope.() -> Unit,
) {
    val colors = MaterialTheme.colorScheme
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (open) colors.surfaceContainer else colors.surfaceContainerLow, shape)
            .border(
                if (open) CruxTheme.size.borderEmphasis else CruxTheme.size.borderHairline,
                if (open) colors.primary else colors.outlineVariant,
                shape,
            )
            .animateContentSize(tween(150)),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
            modifier = Modifier
                .fillMaxWidth()
                .heightIn(min = 48.dp)
                .clickable(role = Role.Button, onClickLabel = if (open) "Close" else "Change", onClick = onToggle)
                .padding(horizontal = CruxTheme.space.s3)
                .testTag("row_$testTag"),
        ) {
            Text(label, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
            Text(
                value,
                style = TextStyle(
                    fontFamily = if (mono) JetBrainsMono else Archivo,
                    fontWeight = if (mono) FontWeight.Bold else FontWeight.ExtraBold,
                    fontSize = 18.sp,
                    fontFeatureSettings = "tnum",
                ),
                color = if (open) colors.primary else colors.onSurface,
                modifier = Modifier.testTag("${testTag}_value"),
            )
            if (unit != null) {
                Text(
                    unit.uppercase(),
                    style = TextStyle(fontFamily = JetBrainsMono, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 1.1.sp),
                    color = colors.onSurfaceVariant,
                )
            }
        }
        if (open) {
            Column(
                verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = CruxTheme.space.s3, end = CruxTheme.space.s3, bottom = CruxTheme.space.s3),
                content = content,
            )
        }
    }
}
