package com.hardtekpt.crux.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.Dp
import com.hardtekpt.crux.ui.theme.CruxTheme

/** A sheet's one button: what it does, in the climber's words ("Save", "Import", "Show 32 entries"). */
data class SheetButton(val label: String, val onClick: () -> Unit, val enabled: Boolean = true, val icon: ImageVector? = null, val tag: String? = null)

/**
 * Every bottom sheet in Crux: the same handle, colour, corners and insets, and one rule for where
 * its action goes.
 *
 * - A sheet that commits something (create, save, import, apply) passes [confirm]: a full-width
 *   filled button at the bottom, where the thumb already is. It stays put while the content scrolls.
 * - A sheet that only picks passes neither: tapping a row picks and closes.
 * - A step inside a bigger flow (Where in Log climb) passes [action]: a text button at the top
 *   right, usually "Done".
 *
 * Never both a [confirm] and a filled button in the content. [title] is the headline; [eyebrow]
 * is the mono label an action sheet uses instead ("KILTER ORIGINAL"). The content scrolls unless
 * [scroll] is false (for sheets that hold their own lazy list).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CruxSheet(
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    title: String? = null,
    eyebrow: String? = null,
    action: SheetButton? = null,
    confirm: SheetButton? = null,
    scroll: Boolean = true,
    spacing: Dp = CruxTheme.space.s2,
    content: @Composable ColumnScope.() -> Unit,
) {
    val space = CruxTheme.space
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = modifier,
    ) {
        Column(
            Modifier.padding(horizontal = space.s4).padding(bottom = space.s4).navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            if (title != null || action != null) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.weight(1f)) {
                        title?.let { Text(it, style = MaterialTheme.typography.headlineSmall) }
                    }
                    action?.let { a ->
                        TextButton(
                            onClick = a.onClick,
                            enabled = a.enabled,
                            modifier = a.tag?.let { Modifier.testTag(it) } ?: Modifier,
                        ) { Text(a.label, style = MaterialTheme.typography.labelLarge) }
                    }
                }
            }
            eyebrow?.let { Eyebrow(it) }
            Column(
                Modifier
                    .weight(1f, fill = false)
                    .then(if (scroll) Modifier.verticalScroll(rememberScrollState()) else Modifier),
                verticalArrangement = Arrangement.spacedBy(spacing),
                content = content,
            )
            confirm?.let { c ->
                CruxButton(
                    c.label,
                    c.onClick,
                    enabled = c.enabled,
                    icon = c.icon,
                    size = CruxButtonSize.Large,
                    modifier = Modifier.fillMaxWidth().then(c.tag?.let { Modifier.testTag(it) } ?: Modifier),
                )
            }
        }
    }
}

/**
 * A sheet of actions on one thing: its name as the mono eyebrow, then a row per action
 * ([com.hardtekpt.crux.ui.components.CruxListRow] with a title and one line). Tapping a row acts
 * and closes; there is no confirm button.
 */
@Composable
fun ActionSheet(title: String, onDismiss: () -> Unit, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    CruxSheet(onDismiss = onDismiss, modifier = modifier, eyebrow = title, spacing = CruxTheme.space.s1, content = content)
}
