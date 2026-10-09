package com.hardtekpt.crux.ui.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
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
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.components.CruxCard
import com.hardtekpt.crux.ui.components.CruxListRow
import com.hardtekpt.crux.ui.components.CruxSegmentedButtons
import com.hardtekpt.crux.ui.components.CruxTopAppBar
import com.hardtekpt.crux.ui.components.Eyebrow
import com.hardtekpt.crux.ui.navigation.LocalNavBarClearance
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val scales: GradeScales = GradeScales(),
    val themeMode: ThemeMode = ThemeMode.DARK,
    val demoMode: Boolean = false,
    val units: UnitSystem = UnitSystem.METRIC,
    val timerSounds: Boolean = true,
)

@HiltViewModel
class SettingsViewModel @Inject constructor(private val preferences: UserPreferencesRepository) : ViewModel() {
    val uiState: StateFlow<SettingsUiState> = combine(
        preferences.gradeScales,
        preferences.themeMode,
        preferences.demoMode,
        preferences.units,
        preferences.timerSounds,
    ) { scales, theme, demo, units, sounds -> SettingsUiState(scales, theme, demo, units, sounds) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setGradeScale(scale: GradeScale) {
        viewModelScope.launch { preferences.setGradeScale(scale) }
    }

    fun setDemoMode(enabled: Boolean) {
        viewModelScope.launch { preferences.setDemoMode(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { preferences.setThemeMode(mode) }
    }

    fun setTimerSounds(enabled: Boolean) {
        viewModelScope.launch { preferences.setTimerSounds(enabled) }
    }

    fun setUnits(units: UnitSystem) {
        viewModelScope.launch { preferences.setUnits(units) }
    }
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    openAbout: () -> Unit = {},
    openBackups: () -> Unit = {},
    viewModel: SettingsViewModel = hiltViewModel(),
    crashReportsViewModel: CrashReportsViewModel = hiltViewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val crashCount by crashReportsViewModel.count.collectAsStateWithLifecycle()
    SettingsContent(
        uiState = uiState,
        onBack = onBack,
        onGradeScale = viewModel::setGradeScale,
        onThemeMode = viewModel::setThemeMode,
        onDemoMode = viewModel::setDemoMode,
        onUnits = viewModel::setUnits,
        onTimerSounds = viewModel::setTimerSounds,
        onBackups = openBackups,
        diagnostics = { CrashReportsCard(crashCount, crashReportsViewModel) },
        onAbout = openAbout,
    )
}

@Composable
fun SettingsContent(
    uiState: SettingsUiState,
    onBack: () -> Unit,
    onGradeScale: (GradeScale) -> Unit,
    onThemeMode: (ThemeMode) -> Unit,
    onDemoMode: (Boolean) -> Unit = {},
    modifier: Modifier = Modifier,
    onUnits: (UnitSystem) -> Unit = {},
    onBackups: () -> Unit = {},
    diagnostics: @Composable () -> Unit = {},
    onAbout: () -> Unit = {},
    onTimerSounds: (Boolean) -> Unit = {},
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

            Eyebrow("Units", Modifier.padding(top = space.s4))
            CruxCard {
                Text("Weight and length", style = MaterialTheme.typography.titleMedium)
                Text(
                    if (uiState.units == UnitSystem.IMPERIAL) "Pounds, and feet and inches" else "Kilograms and centimetres",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = space.s2),
                )
                CruxSegmentedButtons(
                    options = UnitSystem.entries,
                    selected = uiState.units,
                    label = { it.label },
                    onSelect = onUnits,
                )
            }
            Text(
                "Only what you see changes. Everything is stored in kilograms and centimetres, so switching back and forth loses nothing.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )

            Eyebrow("Sessions", Modifier.padding(top = space.s4))
            CruxCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Timer sounds", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Beeps for the last three seconds of a rest or interval. The phone buzzes either way.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = uiState.timerSounds,
                        onCheckedChange = onTimerSounds,
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .padding(start = space.s3)
                            .testTag("timer_sounds"),
                    )
                }
            }

            Eyebrow("Your data", Modifier.padding(top = space.s4))
            CruxCard {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Demo mode", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (uiState.demoMode) {
                                "Showing sample climbs, plans and weigh-ins. Your own data is kept apart and comes back when you turn this off."
                            } else {
                                "Explore Crux with sample climbs, plans and weigh-ins. Your own data stays untouched."
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(
                        checked = uiState.demoMode,
                        onCheckedChange = onDemoMode,
                        colors = SwitchDefaults.colors(checkedTrackColor = MaterialTheme.colorScheme.secondary),
                        modifier = Modifier
                            .padding(start = space.s3)
                            .testTag("demo_mode"),
                    )
                }
            }
            CruxListRow(
                title = "Backups",
                supporting = "Save your data to a file, or bring a backup in",
                trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                onClick = onBackups,
                modifier = Modifier.testTag("open_backups"),
            )

            Eyebrow("Diagnostics", Modifier.padding(top = space.s4))
            diagnostics()

            Eyebrow("About", Modifier.padding(top = space.s4))
            CruxListRow(
                title = "About Crux",
                supporting = "Version, licence, source code and open-source libraries",
                trailing = { Icon(Icons.AutoMirrored.Rounded.KeyboardArrowRight, contentDescription = null) },
                onClick = onAbout,
                modifier = Modifier.testTag("open_about"),
            )
        }
    }
}
