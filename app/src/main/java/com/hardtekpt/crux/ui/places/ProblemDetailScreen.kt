package com.hardtekpt.crux.ui.places

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.ClimbRepository
import com.hardtekpt.crux.data.PlaceRepository
import com.hardtekpt.crux.data.model.Climb
import com.hardtekpt.crux.data.model.PlaceDetail
import com.hardtekpt.crux.data.model.ProblemWithStats
import com.hardtekpt.crux.data.model.Project
import com.hardtekpt.crux.ui.components.CruxButton
import com.hardtekpt.crux.ui.components.CruxButtonSize
import com.hardtekpt.crux.ui.components.CruxButtonVariant
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.components.GradeBadge
import com.hardtekpt.crux.ui.components.GradeState
import com.hardtekpt.crux.ui.components.ImageThumbnail
import com.hardtekpt.crux.ui.components.ImageViewer
import com.hardtekpt.crux.ui.components.StatTile
import com.hardtekpt.crux.ui.dayLabel
import com.hardtekpt.crux.ui.gradeState
import com.hardtekpt.crux.ui.journal.TapeDot
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.outcomeLine
import com.hardtekpt.crux.ui.shortLabel
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.filterNotNull
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

/** An open project in a list: grade, where it is, and how many goes so far. */
@Composable
fun ProjectRow(
    project: Project,
    onOpen: (Long) -> Unit,
    modifier: Modifier = Modifier,
    lastGo: com.hardtekpt.crux.data.LoggedGo? = null,
    onGo: ((Long) -> Unit)? = null,
    onUndo: () -> Unit = {},
) {
    val stats = project.stats
    CruxListRow(
        title = project.problem.name,
        supporting = listOfNotNull(
            listOfNotNull(project.placeName.ifBlank { null }, project.areaName).joinToString(" · ").ifBlank { null },
            "${stats.attempts} ${if (stats.attempts == 1) "go" else "goes"} over ${stats.sessions} ${if (stats.sessions == 1) "day" else "days"}",
        ).joinToString(" · "),
        leading = { GradeBadge(project.problem.grade, GradeState.Attempted) },
        trailing = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
                project.problem.tape?.let { TapeDot(it) }
                if (onGo != null) QuickGoButton(project.problem.id, lastGo, onGo, onUndo)
            }
        },
        onClick = { onOpen(project.problem.id) },
        modifier = modifier.testTag("project_row"),
    )
}

/**
 * "+1 go": logs an attempt on the problem today, straight from a list. For a few seconds after,
 * it turns into Undo.
 */
@Composable
fun QuickGoButton(problemId: Long, lastGo: com.hardtekpt.crux.data.LoggedGo?, onGo: (Long) -> Unit, onUndo: () -> Unit) {
    if (lastGo?.problemId == problemId) {
        CruxButton("Undo", onUndo, variant = CruxButtonVariant.Text, size = CruxButtonSize.Small, modifier = Modifier.testTag("project_undo"))
    } else {
        CruxButton("+1 go", { onGo(problemId) }, variant = CruxButtonVariant.Tonal, size = CruxButtonSize.Small, modifier = Modifier.testTag("project_go"))
    }
}
