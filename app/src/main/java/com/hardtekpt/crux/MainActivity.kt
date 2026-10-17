package com.hardtekpt.crux

import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.Color
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTagsAsResourceId
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
import kotlinx.coroutines.launch
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

    /** Where a tap on the live notification asked to go, until the app has gone there. */
    private val openRequest = kotlinx.coroutines.flow.MutableStateFlow<OpenRequest?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        openRequest.value = OpenRequest.from(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(darkBars(), darkBars())
        super.onCreate(savedInstanceState)
        if (savedInstanceState == null) openRequest.value = OpenRequest.from(intent)
        LauncherShortcuts.publish(this)
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
                    // Test tags double as resource ids, so the benchmarks and profile generator can find screens.
                    androidx.compose.foundation.layout.Box(
                        androidx.compose.ui.Modifier.semantics { testTagsAsResourceId = true },
                    ) {
                        val request by openRequest.collectAsStateWithLifecycle()
                        // First-run setup once, on a fresh install; until it's known, just the background.
                        when (viewModel.setupNeeded.collectAsStateWithLifecycle().value) {
                            null -> androidx.compose.foundation.layout.Box(
                                androidx.compose.ui.Modifier.fillMaxSize().background(androidx.compose.material3.MaterialTheme.colorScheme.background),
                            )

                            true -> com.hardtekpt.crux.ui.setup.SetupFlow(onDone = viewModel::setupFinished)

                            false -> CruxApp(openRequest = request, onOpened = { openRequest.value = null })
                        }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_OPEN = "crux.open"
        const val EXTRA_SESSION = "crux.session"
        const val OPEN_SESSION = "session"
        const val OPEN_TIMER = "timer"
        const val OPEN_LOG_CLIMB = "log_climb"
        const val OPEN_LOG_WEIGHT = "log_weight"
        const val OPEN_START_SESSION = "start_session"
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
class MainViewModel @Inject constructor(preferences: UserPreferencesRepository, firstRun: com.hardtekpt.crux.data.FirstRun) : ViewModel() {
    /** Whether first-run setup shows; null while that is being read. */
    private val _setupNeeded = kotlinx.coroutines.flow.MutableStateFlow<Boolean?>(null)
    val setupNeeded: StateFlow<Boolean?> = _setupNeeded

    init {
        viewModelScope.launch { _setupNeeded.value = firstRun.needed() }
    }

    fun setupFinished() {
        _setupNeeded.value = false
    }

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

/** A place the live notification opens: a session, the Interval timer page, or Log climb. */
sealed interface OpenRequest {
    data class Session(val id: Long) : OpenRequest
    data object Timer : OpenRequest
    data object LogClimb : OpenRequest
    data object LogWeight : OpenRequest

    /** The running session, or the choice of what to start. */
    data object StartSession : OpenRequest

    companion object {
        fun from(intent: Intent?): OpenRequest? = when (intent?.getStringExtra(MainActivity.EXTRA_OPEN)) {
            MainActivity.OPEN_SESSION -> intent.getLongExtra(MainActivity.EXTRA_SESSION, 0L).takeIf { it != 0L }?.let(::Session)
            MainActivity.OPEN_TIMER -> Timer
            MainActivity.OPEN_LOG_CLIMB -> LogClimb
            MainActivity.OPEN_LOG_WEIGHT -> LogWeight
            MainActivity.OPEN_START_SESSION -> StartSession
            else -> null
        }
    }
}

/**
 * What a long press on Crux's icon offers: Log climb, Start a session, Log weight and the Interval
 * timer, each straight to its page. Set from code rather than res/xml so they point at whichever
 * build is installed (the debug and internal builds have their own app id).
 */
internal object LauncherShortcuts {
    fun publish(context: android.content.Context) {
        runCatching {
            fun shortcut(id: String, short: String, long: String, icon: Int, open: String) = androidx.core.content.pm.ShortcutInfoCompat.Builder(context, id)
                .setShortLabel(short)
                .setLongLabel(long)
                .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(context, icon))
                .setIntent(
                    Intent(context, MainActivity::class.java)
                        .setAction(Intent.ACTION_VIEW)
                        .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
                        .putExtra(MainActivity.EXTRA_OPEN, open),
                )
                .build()
            androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(
                context,
                listOf(
                    shortcut("log_climb", "Log climb", "Log a climb", R.drawable.ic_shortcut_climb, MainActivity.OPEN_LOG_CLIMB),
                    shortcut("start_session", "Session", "Start or resume a session", R.drawable.ic_shortcut_session, MainActivity.OPEN_START_SESSION),
                    shortcut("log_weight", "Log weight", "Log your weight", R.drawable.ic_shortcut_weight, MainActivity.OPEN_LOG_WEIGHT),
                    shortcut("timer", "Timer", "Interval timer", R.drawable.ic_shortcut_timer, MainActivity.OPEN_TIMER),
                ),
            )
        }
    }
}
