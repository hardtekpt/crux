package com.hardtekpt.crux.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onRoot
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.OfflineBodyRepository
import com.hardtekpt.crux.data.OfflineClimbRepository
import com.hardtekpt.crux.data.OfflineNoteRepository
import com.hardtekpt.crux.data.OfflinePlaceRepository
import com.hardtekpt.crux.data.OfflineRecordRepository
import com.hardtekpt.crux.data.OfflineSessionRepository
import com.hardtekpt.crux.data.OfflineTemplateRepository
import com.hardtekpt.crux.data.dashboard.DashboardRepository
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.DatabaseFactory
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.data.seed.StarterDataSeeder
import com.hardtekpt.crux.ui.home.HomeContent
import com.hardtekpt.crux.ui.home.HomeViewModel
import com.hardtekpt.crux.ui.journal.JournalContent
import com.hardtekpt.crux.ui.journal.JournalViewModel
import com.hardtekpt.crux.ui.progress.ProgressContent
import com.hardtekpt.crux.ui.progress.ProgressViewModel
import com.hardtekpt.crux.ui.settings.SettingsContent
import com.hardtekpt.crux.ui.settings.SettingsUiState
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.you.ProfileActions
import com.hardtekpt.crux.ui.you.YouContent
import com.hardtekpt.crux.ui.you.YouViewModel
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode

/**
 * The main screens with the demo data, in both themes, rendered on the JVM and compared with
 * the goldens in `src/test/screenshots`. `./gradlew recordRoborazziDebug` refreshes them after
 * an intended change; `verifyRoborazziDebug` (CI) fails on any other difference.
 */
@RunWith(AndroidJUnit4::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [35], qualifiers = RobolectricDeviceQualifiers.Pixel7)
class ScreenshotTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    @get:Rule val compose = createComposeRule()

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CruxDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { runBlocking { StarterDataSeeder(FIXED_CLOCK).seed(it, includeSampleData = true) } }

    private val dataStore by lazy { mainDispatcherRule.preferencesDataStore(File(tmp.root, "prefs.preferences_pb")) }
    private val preferences by lazy { UserPreferencesRepository(dataStore) }
    private val dbs by lazy {
        CruxDatabases(preferences, DatabaseFactory { db }, CoroutineScope(mainDispatcherRule.testDispatcher + SupervisorJob()))
    }
    private val climbs by lazy { OfflineClimbRepository(dbs, FIXED_CLOCK) }
    private val places by lazy { OfflinePlaceRepository(dbs, FIXED_CLOCK) }
    private val notes by lazy { OfflineNoteRepository(dbs, FIXED_CLOCK) }
    private val records by lazy { OfflineRecordRepository(dbs, FIXED_CLOCK) }
    private val body by lazy { OfflineBodyRepository(dbs, FIXED_CLOCK) }

    @After
    fun close() = db.close()

    @Test
    fun home() {
        val vm = HomeViewModel(climbs, body, OfflineTemplateRepository(dbs), places, preferences, DashboardRepository(dataStore), FIXED_CLOCK)
        val state = loaded(vm.uiState) { !it.isLoading }
        val dashboard = loaded(vm.dashboard) { it.widgets.isNotEmpty() }
        bothThemes("home") { HomeContent(uiState = state, dashboard = dashboard) }
    }

    @Test
    fun journal() {
        val sessions = OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK)
        val state = loaded(JournalViewModel(climbs, records, notes, sessions, FIXED_CLOCK).uiState) { !it.isLoading }
        bothThemes("journal") { JournalContent(uiState = state) }
    }

    @Test
    fun progress() {
        val state = loaded(ProgressViewModel(climbs, places, preferences, FIXED_CLOCK).uiState) { !it.isLoading }
        bothThemes("progress") { ProgressContent(uiState = state) }
    }

    @Test
    fun you() {
        val state = loaded(YouViewModel(body, climbs, records, notes, places, preferences, FIXED_CLOCK).uiState) { !it.isLoading }
        bothThemes("you") { YouContent(uiState = state, actions = ProfileActions()) }
    }

    @Test
    fun settings() = bothThemes("settings") {
        SettingsContent(uiState = SettingsUiState(), onBack = {}, onGradeScale = {}, onThemeMode = {})
    }

    /** The view model's state once it has loaded, so the screenshot never catches a half-filled screen. */
    private fun <T> loaded(state: Flow<T>, isLoaded: (T) -> Boolean): T = runBlocking { withTimeout(10_000) { state.first(isLoaded) } }

    /** Renders [content] dark (the app's default) and light. */
    private fun bothThemes(name: String, content: @Composable () -> Unit) {
        var dark by mutableStateOf(true)
        compose.setContent {
            CruxTheme(darkTheme = dark) {
                Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
            }
        }
        listOf(true, false).forEach { theme ->
            dark = theme
            compose.waitForIdle()
            compose.onRoot().captureRoboImage(
                "src/test/screenshots/${name}_${if (theme) "dark" else "light"}.png",
            )
        }
    }
}
