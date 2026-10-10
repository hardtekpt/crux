package com.hardtekpt.crux

import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.hardtekpt.crux.data.prefs.Accent
import com.hardtekpt.crux.data.prefs.TextSize
import com.hardtekpt.crux.data.prefs.ThemeMode
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.CruxApp
import com.hardtekpt.crux.ui.LocalUnits
import com.hardtekpt.crux.ui.theme.CruxTheme
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.AndroidEntryPoint
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.components.SingletonComponent
import javax.inject.Inject
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.runBlocking

@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    /** The text size this Activity was created with; a different one recreates it. */
    private var textSize = TextSize.DEFAULT

    // Text size goes through the Activity's configuration, not Compose's LocalDensity: dialogs,
    // sheets and menus have windows of their own that read the configuration and nothing else.
    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(newBase)
        val preferences = EntryPointAccessors.fromApplication<PreferencesEntryPoint>(newBase.applicationContext).preferences()
        textSize = runBlocking { preferences.textSize.first() }
        if (textSize != TextSize.DEFAULT) {
            applyOverrideConfiguration(Configuration().apply { fontScale = newBase.resources.configuration.fontScale * textSize.scale })
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(darkBars(), darkBars())
        super.onCreate(savedInstanceState)
        setContent {
            val themeMode by viewModel.themeMode.collectAsStateWithLifecycle()
            val accent by viewModel.accent.collectAsStateWithLifecycle()
            val pickedTextSize by viewModel.textSize.collectAsStateWithLifecycle()
            LaunchedEffect(pickedTextSize) {
                if (pickedTextSize != null && pickedTextSize != textSize) recreate()
            }
            val units by viewModel.units.collectAsStateWithLifecycle()
            val gradeScales by viewModel.gradeScales.collectAsStateWithLifecycle()
            val darkTheme = themeMode.isDark()

            // Keep status/nav bar icons readable against whichever theme is in use.
            DisposableEffect(darkTheme) {
                val style = if (darkTheme) darkBars() else lightBars()
                enableEdgeToEdge(style, style)
                onDispose {}
            }

            CruxTheme(darkTheme = darkTheme, accent = accent) {
                CompositionLocalProvider(LocalUnits provides units, com.hardtekpt.crux.ui.LocalGradeScales provides gradeScales) {
                    CruxApp()
                }
            }
        }
    }

    private fun darkBars() = SystemBarStyle.dark(Color.TRANSPARENT)
    private fun lightBars() = SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
}

@Composable
private fun ThemeMode.isDark(): Boolean = when (this) {
    ThemeMode.DARK -> true
    ThemeMode.LIGHT -> false
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
}

/** Reaches the preferences before Hilt has injected the Activity, in attachBaseContext. */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface PreferencesEntryPoint {
    fun preferences(): UserPreferencesRepository
}

@HiltViewModel
class MainViewModel @Inject constructor(preferences: UserPreferencesRepository) : ViewModel() {
    val themeMode: StateFlow<ThemeMode> = preferences.themeMode.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = ThemeMode.DARK,
    )

    val accent: StateFlow<Accent> = preferences.accent.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = Accent.TEAL,
    )

    /** Null until read, so the Activity doesn't recreate itself for a default it never had. */
    val textSize: StateFlow<TextSize?> = preferences.textSize.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = null,
    )

    val units: StateFlow<UnitSystem> = preferences.units.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = UnitSystem.METRIC,
    )

    val gradeScales: StateFlow<com.hardtekpt.crux.data.prefs.GradeScales> = preferences.gradeScales.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = com.hardtekpt.crux.data.prefs.GradeScales(),
    )
}
