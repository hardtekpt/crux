package com.hardtekpt.crux.ui.session

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.FitnessCenter
import androidx.compose.material.icons.rounded.Landscape
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.Session
import com.hardtekpt.crux.data.SessionRepository
import com.hardtekpt.crux.data.TemplateRepository
import com.hardtekpt.crux.data.model.WorkoutTemplate
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** App-wide session state: the running session (for the banner) and starting a new one. */
@HiltViewModel
class SessionsViewModel @Inject constructor(
    private val sessions: SessionRepository,
    templates: TemplateRepository,
    private val preferences: UserPreferencesRepository,
    private val clock: Clock,
) : ViewModel() {
    val running: StateFlow<Session?> = sessions.observeRunning()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val plans: StateFlow<List<WorkoutTemplate>> = templates.observeTemplates()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun now(): Long = clock.millis()

    /** Starts at the last place climbed, with a plan or without; [onStarted] gets the session. */
    fun start(templateId: Long?, onStarted: (Long) -> Unit) {
        viewModelScope.launch {
            val place = preferences.lastPlaceId.first()
            onStarted(sessions.start(templateId, place, null))
        }
    }
}

/** Pick how to start: without a plan for a climbing day, or one of the plans. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StartSessionSheet(plans: List<WorkoutTemplate>, onStart: (Long?) -> Unit, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        shape = MaterialTheme.shapes.extraLarge,
        modifier = Modifier.testTag("start_session_sheet"),
    ) {
        Column(Modifier.padding(horizontal = CruxTheme.space.s4).navigationBarsPadding(), verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2)) {
            Text("Start a session", style = MaterialTheme.typography.headlineSmall)
            Eyebrow("Log climbs and sets as you go", Modifier.padding(bottom = CruxTheme.space.s2))
            LazyColumn(verticalArrangement = Arrangement.spacedBy(CruxTheme.space.s2), contentPadding = PaddingValues(bottom = CruxTheme.space.s6)) {
                item(key = "free") {
                    CruxListRow(
                        title = "Without a plan",
                        supporting = "A climbing day or an unplanned workout",
                        leading = { Icon(Icons.Rounded.Landscape, contentDescription = null) },
                        trailing = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null) },
                        onClick = { onStart(null) },
                        modifier = Modifier.testTag("start_free"),
                    )
                }
                if (plans.isNotEmpty()) item(key = "plans") { Eyebrow("From a plan", Modifier.padding(top = CruxTheme.space.s2)) }
                items(plans, key = { it.id }) { plan ->
                    CruxListRow(
                        title = plan.name,
                        supporting = "${plan.exerciseCount} exercises · about ${plan.estimatedMinutes} min",
                        leading = { Icon(Icons.Rounded.FitnessCenter, contentDescription = null) },
                        trailing = { Icon(Icons.AutoMirrored.Rounded.ArrowForward, contentDescription = null) },
                        onClick = { onStart(plan.id) },
                        modifier = Modifier.testTag("start_plan_${plan.name}"),
                    )
                }
            }
        }
    }
}

/** A pill above the nav bar while a session runs: its name and clock; tap to go back to it. */
@Composable
fun RunningSessionBanner(session: Session?, nowMillis: () -> Long, visible: Boolean, onOpen: () -> Unit, modifier: Modifier = Modifier) {
    AnimatedVisibility(visible = visible && session != null, modifier = modifier) {
        val current = session ?: return@AnimatedVisibility
        var now by remember { mutableLongStateOf(nowMillis()) }
        LaunchedEffect(current.id) {
            while (true) {
                now = nowMillis()
                delay(1000)
            }
        }
        val colors = MaterialTheme.colorScheme
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(CruxTheme.space.s2),
            modifier = Modifier
                .background(colors.primaryContainer, CircleShape)
                .border(CruxTheme.size.borderEmphasis, colors.primary, CircleShape)
                .clickable(onClick = onOpen)
                .padding(horizontal = 16.dp, vertical = 10.dp)
                .testTag("session_banner"),
        ) {
            Icon(Icons.Rounded.Timer, contentDescription = null, tint = colors.onPrimaryContainer, modifier = Modifier.size(18.dp))
            Text(current.name, style = MaterialTheme.typography.labelLarge, color = colors.onPrimaryContainer, maxLines = 1)
            Text(clockLabel(current.durationMillis(now)), style = CruxTheme.type.gradeSmall, color = colors.primary)
            Text("Resume", style = MaterialTheme.typography.labelLarge, color = colors.primary)
        }
    }
}
