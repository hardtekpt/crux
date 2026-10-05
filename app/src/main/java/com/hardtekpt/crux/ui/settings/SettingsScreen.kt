package com.hardtekpt.crux.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.prefs.GradeScales
import com.hardtekpt.crux.data.prefs.ThemeMode
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val scales: GradeScales = GradeScales(),
    val themeMode: ThemeMode = ThemeMode.DARK,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val preferences: UserPreferencesRepository,
) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.gradeScales,
        preferences.themeMode,
    ) { scales, theme -> SettingsUiState(scales, theme) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setGradeScale(scale: GradeScale) {
        viewModelScope.launch { preferences.setGradeScale(scale) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
    backupViewModel: BackupViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val backupState by backupViewModel.state.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onGradeScale = viewModel::setGradeScale,
        onThemeMode = viewModel::setThemeMode,
        backup = { BackupCard(backupState, backupViewModel) },
    )
}

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onGradeScale: (GradeScale) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    modifier: Modifier = Modifier,
    backup: @Composable () -> Unit = {},
) {
    val space = CruxTheme.space
    Column(modifier.fillMaxSize().testTag("screen_Settings")) {
        CruxTopAppBar(title = "Settings", onBack = onBack)
        Column(
            modifier = Modifier
                .verticalScroll(rememberScrollState())
                .padding(horizontal = space.s4)
                .padding(bottom = space.s4 + LocalNavBarClearance.current),
            verticalArrangement = Arrangement.spacedBy(space.s3),
        ) {
            Eyebrow("Grades")
            Discipline.entries.forEach { discipline ->
                CruxCard {
                    Text(
                        if (discipline == Discipline.BOULDER) "Bouldering" else "Routes",
                        style = MaterialTheme.typography.titleMedium,
                    )
                    Text(
                        discipline.scales.joinToString("  ·  ") { scale ->
                            "${scale.label} ${scale.grades[scale.defaultIndex]}"
                        },
                        style = CruxTheme.type.code,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(bottom = space.s2),
                    )
                    CruxSegmentedButtons(
                        options = discipline.scales,
                        selected = uiState.scales.forDiscipline(discipline),
                        label = { it.label },
                        onSelect = onGradeScale,
                    )
                }
            }
            Text(
                "New climbs are logged in the scale picked here. Climbs you already logged keep " +
                    "the scale they were logged in; grades are never converted.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Eyebrow("Appearance", Modifier.padding(top = space.s4))
            CruxCard {
                Text("Theme", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = space.s2))
                CruxSegmentedButtons(
                    options = ThemeMode.entries,
                    selected = uiState.themeMode,
                    label = { it.label },
                    onSelect = onThemeMode,
                )
            }

            Eyebrow("Your data", Modifier.padding(top = space.s4))
            backup()
        }
    }
}
