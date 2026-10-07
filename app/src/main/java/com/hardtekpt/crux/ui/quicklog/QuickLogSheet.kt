package com.hardtekpt.crux.ui.quicklog

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Notes
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.MonitorWeight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.theme.CruxTheme

enum class QuickLogAction(val label: String, val description: String, val icon: ImageVector, val available: Boolean) {
    LogClimb("Log climb", "A send or an attempt, with grade and style", Icons.Rounded.Landscape, true),
    LogWeight("Log weight", "Today's bodyweight", Icons.Rounded.MonitorWeight, true),
    StartWorkout("Start a workout", "Coming next, with the session logger", Icons.Rounded.FitnessCenter, false),
    AddNote("Add a note", "A thought, a niggle, beta to remember", Icons.AutoMirrored.Rounded.Notes, true),
}

/** The Log FAB's sheet: the four things a climber logs, two of them live in the MVP. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuickLogSheet(onDismiss: () -> Unit, onAction: (QuickLogAction) -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.testTag("quick_log_sheet"),
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = CruxTheme.space.s4)
                .padding(bottom = CruxTheme.space.s6)
                .navigationBarsPadding(),
            verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
        ) {
            Text("Log", style = MaterialTheme.typography.headlineSmall)
            Eyebrow("What did you do?", Modifier.padding(bottom = CruxTheme.space.s2))
            QuickLogAction.entries.forEach { action ->
                val tint = if (action.available) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.38f)
                }
                CruxListRow(
                    title = action.label,
                    supporting = action.description,
                    leading = { Icon(action.icon, contentDescription = null, tint = tint) },
                    trailing = if (action.available) {
                        { Icon(Icons.AutoMirrored.Rounded.ArrowForward, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
                    } else {
                        null
                    },
                    enabled = action.available,
                    onClick = { onAction(action) },
                    modifier = Modifier.testTag("quick_${action.name}"),
                )
            }
        }
    }
}
