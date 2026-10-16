package com.hardtekpt.crux.ui.journal

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Checklist
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import com.hardtekpt.crux.data.ClimbSource
import com.hardtekpt.crux.data.Playlist
import com.hardtekpt.crux.data.model.PlaceDetail
import com.hardtekpt.crux.data.model.Problem
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxFilterChip
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxSheet
import com.hardtekpt.crux.ui.theme.CruxTheme

private const val PROJECTS = "projects"

/**
 * A button under Name that opens [ClimbPickerSheet]: your open projects here, or the climbs of a
 * playlist. It is left out when there is nothing to pick.
 */
@Composable
internal fun ClimbPickerButton(
    projects: List<com.hardtekpt.crux.data.model.ProblemWithStats>,
    playlists: List<Playlist>,
    choices: ClimbChoices,
    detail: PlaceDetail?,
    onPickProject: (Problem) -> Unit,
    onPickClimb: (ClimbSource) -> Unit,
) {
    val lists = playlists.filter { list -> list.items.any { it.climb != null && it.dataset != null } }
    if (projects.isEmpty() && lists.isEmpty()) return
    var open by rememberSaveable { mutableStateOf(false) }
    CruxButton(
        "Choose a climb",
        { open = true },
        variant = CruxButtonVariant.Outlined,
        size = CruxButtonSize.Small,
        icon = Icons.Rounded.Checklist,
        modifier = Modifier.testTag("choose_climb"),
    )
    if (open) {
        ClimbPickerSheet(
            projects = projects,
            playlists = lists,
            choices = choices,
            detail = detail,
            onPickProject = {
                onPickProject(it)
                open = false
            },
            onPickClimb = {
                onPickClimb(it)
                open = false
            },
            onDismiss = { open = false },
        )
    }
}

/** Open projects first, then a chip per playlist; picking a row fills the form with that climb. */
@Composable
private fun ClimbPickerSheet(
    projects: List<com.hardtekpt.crux.data.model.ProblemWithStats>,
    playlists: List<Playlist>,
    choices: ClimbChoices,
    detail: PlaceDetail?,
    onPickProject: (Problem) -> Unit,
    onPickClimb: (ClimbSource) -> Unit,
    onDismiss: () -> Unit,
) {
    var tab by rememberSaveable { mutableStateOf(if (projects.isNotEmpty()) PROJECTS else "p${playlists.first().id}") }
    CruxSheet(onDismiss = onDismiss, modifier = Modifier.testTag("climb_picker"), title = "Choose a climb") {
        LazyRow(horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), modifier = Modifier.fillMaxWidth()) {
            if (projects.isNotEmpty()) {
                item { CruxFilterChip("Open projects", tab == PROJECTS, { tab = PROJECTS }, Modifier.testTag("picker_tab_projects")) }
            }
            items(playlists, key = { it.id }) { list ->
                CruxFilterChip(list.name, tab == "p${list.id}", { tab = "p${list.id}" }, Modifier.testTag("picker_tab_${list.name}"))
            }
        }
        val list = playlists.firstOrNull { "p${it.id}" == tab }
        if (list == null) {
            projects.forEach { item ->
                CruxListRow(
                    title = item.problem.name,
                    supporting = problemLine(item, detail),
                    leading = { GradeText(item.problem.grade) },
                    trailing = { StatusDot(item.stats?.sent, item.stats != null) },
                    onClick = { onPickProject(item.problem) },
                    modifier = Modifier.testTag("picker_project_${item.problem.name}"),
                )
            }
            if (projects.isEmpty()) {
                Text(
                    "No open projects here.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            list.items.mapNotNull { item -> item.climb?.let { c -> item.dataset?.let { d -> ClimbSource(c, d) } } }.forEach { source ->
                DatasetClimbRow(source, choices, showDataset = true, onPick = onPickClimb)
            }
        }
    }
}
