package com.hardtekpt.crux.ui.session

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import com.hardtekpt.crux.data.Playlist
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.theme.CruxTheme

/**
 * The playlist being climbed in a session: the climb that's up, its grade and how it's gone today,
 * Prev and Next, and + Fell and + Sent, which add a go to it straight away (with Undo). Tapping the
 * climb opens Log climb on it; ✕ stops climbing the playlist.
 */
@Composable
internal fun PlaylistCard(
    playlist: Playlist,
    index: Int,
    /** The climb's goes in this session, as one entry; null before the first. */
    today: Climb?,
    onMove: (Int) -> Unit,
    onGo: (Boolean) -> Unit,
    onOpen: () -> Unit,
    onStop: () -> Unit,
    onUndo: (() -> Unit)?,
) {
    val at = index.coerceIn(0, (playlist.items.size - 1).coerceAtLeast(0))
    val item = playlist.items.getOrNull(at)
    val space = CruxTheme.space
    CruxCard(outline = MaterialTheme.colorScheme.primary, modifier = Modifier.testTag("session_playlist")) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Eyebrow("Playlist · ${playlist.name} · ${at + 1} of ${playlist.items.size}", Modifier.weight(1f))
            IconButton(onClick = onStop, modifier = Modifier.testTag("session_playlist_stop")) {
                Icon(Icons.Rounded.Close, contentDescription = "Stop climbing this playlist")
            }
        }
        if (item == null) {
            Text("This playlist is empty.", style = MaterialTheme.typography.bodyMedium)
            return@CruxCard
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = item.matched, onClickLabel = "Log this climb", onClick = onOpen)
                .testTag("session_playlist_climb"),
        ) {
            Column(Modifier.weight(1f)) {
                Text(item.name, style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        item.datasetName,
                        item.climb?.setter,
                        when {
                            !item.matched -> "not in your datasets"
                            today == null -> "not tried yet"
                            else -> "${today.attempts} ${if (today.attempts == 1) "go" else "goes"}${if (today.style.isSend) " · sent" else ""}"
                        },
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.testTag("session_playlist_status"),
                )
            }
            Text(item.gradeLabel ?: "–", style = CruxTheme.type.grade)
        }
        // A board climb's holds, big enough to read from the mat; tap for the whole board.
        val holds = item.climb?.holds
        val board = item.dataset?.holdsBoard
        if (holds != null && board != null) {
            com.hardtekpt.crux.ui.components.BoardDiagramCard(board, holds, item.name, Modifier.fillMaxWidth(), maxHeight = 260)
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalAlignment = Alignment.CenterVertically) {
            CruxButton(
                "Prev",
                { onMove(at - 1) },
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowLeft,
                variant = CruxButtonVariant.Outlined,
                size = CruxButtonSize.Small,
                enabled = at > 0,
                modifier = Modifier.weight(1f).testTag("session_playlist_prev"),
            )
            CruxButton(
                "Next",
                { onMove(at + 1) },
                icon = Icons.AutoMirrored.Rounded.KeyboardArrowRight,
                variant = CruxButtonVariant.Outlined,
                size = CruxButtonSize.Small,
                enabled = at < playlist.items.lastIndex,
                modifier = Modifier.weight(1f).testTag("session_playlist_next"),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(space.s2), verticalAlignment = Alignment.CenterVertically) {
            CruxButton(
                "+ Fell",
                { onGo(false) },
                variant = CruxButtonVariant.Tonal,
                enabled = item.matched,
                modifier = Modifier.weight(1f).testTag("session_playlist_fell"),
            )
            CruxButton(
                "+ Sent",
                { onGo(true) },
                enabled = item.matched,
                modifier = Modifier.weight(1f).testTag("session_playlist_sent"),
            )
        }
        onUndo?.let {
            TextButton(onClick = it, modifier = Modifier.testTag("session_playlist_undo")) { Text("Undo that go") }
        }
    }
}
