package com.hardtekpt.crux.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.github.takahirom.roborazzi.RobolectricDeviceQualifiers
import com.github.takahirom.roborazzi.captureRoboImage
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.FakeImageFiles
import com.hardtekpt.crux.data.OfflineBodyRepository
import com.hardtekpt.crux.data.OfflineClimbRepository
import com.hardtekpt.crux.data.OfflineExerciseRepository
import com.hardtekpt.crux.data.OfflineNoteRepository
import com.hardtekpt.crux.data.OfflinePlaceRepository
import com.hardtekpt.crux.data.OfflineRecordRepository
import com.hardtekpt.crux.data.OfflineSessionRepository
import com.hardtekpt.crux.data.OfflineTemplateRepository
import com.hardtekpt.crux.data.dashboard.DashboardRepository
import com.hardtekpt.crux.data.images.AreaImageStore
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.DatabaseFactory
import com.hardtekpt.crux.data.prefs.Accent
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.data.seed.StarterData
import com.hardtekpt.crux.data.seed.StarterDataSeeder
import com.hardtekpt.crux.ui.home.HomeContent
import com.hardtekpt.crux.ui.home.HomeViewModel
import com.hardtekpt.crux.ui.journal.JournalContent
import com.hardtekpt.crux.ui.journal.JournalViewModel
import com.hardtekpt.crux.ui.journal.LogClimbScreen
import com.hardtekpt.crux.ui.journal.LogClimbViewModel
import com.hardtekpt.crux.ui.places.PlaceDetailScreen
import com.hardtekpt.crux.ui.places.PlaceDetailViewModel
import com.hardtekpt.crux.ui.progress.ProgressContent
import com.hardtekpt.crux.ui.progress.ProgressViewModel
import com.hardtekpt.crux.ui.session.IntervalPhase
import com.hardtekpt.crux.ui.session.IntervalRun
import com.hardtekpt.crux.ui.session.IntervalSpec
import com.hardtekpt.crux.ui.session.SessionScreen
import com.hardtekpt.crux.ui.session.SessionViewModel
import com.hardtekpt.crux.ui.settings.BackupContent
import com.hardtekpt.crux.ui.settings.BackupUiState
import com.hardtekpt.crux.ui.settings.SettingsContent
import com.hardtekpt.crux.ui.settings.SettingsUiState
import com.hardtekpt.crux.ui.theme.CruxTheme
import com.hardtekpt.crux.ui.timer.IntervalTimerRunning
import com.hardtekpt.crux.ui.timer.IntervalTimerSetup
import com.hardtekpt.crux.ui.train.TrainScreen
import com.hardtekpt.crux.ui.train.TrainView
import com.hardtekpt.crux.ui.train.TrainViewModel
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
 * the goldens in `src/test/screenshots` (the dark main screens are the store screenshots in
 * `fastlane/.../phoneScreenshots`). `./gradlew recordRoborazziDebug` refreshes them after
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
    private val playlistRepo by lazy { com.hardtekpt.crux.data.OfflinePlaylistRepository(dbs, FIXED_CLOCK) }
    private val places by lazy { OfflinePlaceRepository(dbs, FIXED_CLOCK) }
    private val notes by lazy { OfflineNoteRepository(dbs, FIXED_CLOCK) }
    private val records by lazy { OfflineRecordRepository(dbs, FIXED_CLOCK) }
    private val body by lazy { OfflineBodyRepository(dbs, FIXED_CLOCK) }

    @After
    fun close() = db.close()

    @Test
    fun home() = home("home")

    private fun homeViewModel() = HomeViewModel(
        climbs,
        body,
        OfflineTemplateRepository(dbs),
        places,
        com.hardtekpt.crux.data.OfflineScheduleRepository(dbs),
        OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK),
        preferences,
        DashboardRepository(dataStore),
        FIXED_CLOCK,
        training(),
    )

    private fun home(name: String, dark: List<Boolean> = BOTH, accent: Accent = Accent.TEAL) {
        val vm = homeViewModel()
        val state = loaded(vm.uiState) { !it.isLoading }
        val dashboard = loaded(vm.dashboard) { it.widgets.isNotEmpty() }
        bothThemes(name, dark = dark, accent = accent) { HomeContent(uiState = state, dashboard = dashboard) }
    }

    // First-run setup: Welcome in both themes, then each step as Boss's preview drew it.
    @Test
    fun setupWelcome() = bothThemes("setup_welcome") {
        com.hardtekpt.crux.ui.setup.SetupContent(com.hardtekpt.crux.ui.setup.SetupState(), com.hardtekpt.crux.ui.setup.SetupActions())
    }

    private fun setup(name: String, state: com.hardtekpt.crux.ui.setup.SetupState) = bothThemes(name, dark = DARK) {
        com.hardtekpt.crux.ui.setup.SetupContent(state, com.hardtekpt.crux.ui.setup.SetupActions())
    }

    @Test
    fun setupUnits() = setup("setup_units", com.hardtekpt.crux.ui.setup.SetupState(step = com.hardtekpt.crux.ui.setup.SetupStep.UNITS))

    @Test
    fun setupPlace() = setup(
        "setup_place",
        com.hardtekpt.crux.ui.setup.SetupState(
            step = com.hardtekpt.crux.ui.setup.SetupStep.PLACE,
            placeName = "Arco Boulder Gym",
            kinds = setOf(com.hardtekpt.crux.data.model.PlaceType.GYM, com.hardtekpt.crux.data.model.PlaceType.BOARD),
        ),
    )

    @Test
    fun setupBody() =
        setup("setup_body", com.hardtekpt.crux.ui.setup.SetupState(step = com.hardtekpt.crux.ui.setup.SetupStep.BODY, weight = 72.4, height = 178.0))

    @Test
    fun setupStart() = setup(
        "setup_start",
        com.hardtekpt.crux.ui.setup.SetupState(
            step = com.hardtekpt.crux.ui.setup.SetupStep.START,
            placeName = "Arco Boulder Gym",
            kinds = setOf(com.hardtekpt.crux.data.model.PlaceType.GYM, com.hardtekpt.crux.data.model.PlaceType.BOARD),
            placeAdded = true,
            weight = 72.4,
            height = 178.0,
        ),
    )

    // Home in each accent but the brand's teal, which every other screenshot shows.
    @Test
    fun homeBlue() = home("home_blue", accent = Accent.BLUE)

    @Test
    fun homeViolet() = home("home_violet", accent = Accent.VIOLET)

    @Test
    fun homePink() = home("home_pink", accent = Accent.PINK)

    @Test
    fun homeChalk() = home("home_chalk", accent = Accent.CHALK)

    @Test
    fun journal() {
        val sessions = OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK)
        val state = loaded(JournalViewModel(climbs, records, notes, sessions, FIXED_CLOCK).uiState) { !it.isLoading }
        bothThemes("journal") { JournalContent(uiState = state) }
    }

    // The Journal's Places tab: every place, as You → Places lists them.
    @Test
    fun journalPlaces() {
        val sessions = OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK)
        val vm = JournalViewModel(climbs, records, notes, sessions, FIXED_CLOCK)
        vm.update { it.copy(kind = com.hardtekpt.crux.ui.journal.JournalFilter.Places) }
        val state = loaded(vm.uiState) { !it.isLoading && it.query.kind == com.hardtekpt.crux.ui.journal.JournalFilter.Places }
        val placeList = loaded(places.observePlaces()) { it.isNotEmpty() }
        bothThemes("journal_places", readyText = "Block Lab", dark = DARK) { JournalContent(uiState = state, places = placeList) }
    }

    @Test
    fun daysOnWall() {
        val sessions = OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK)
        val state = loaded(com.hardtekpt.crux.ui.journal.DaysOnWallViewModel(climbs, records, notes, sessions, FIXED_CLOCK).uiState) { !it.isLoading }
        bothThemes("days_on_wall") { com.hardtekpt.crux.ui.journal.DaysOnWallContent(state) }
    }

    @Test
    fun weekClimbs() {
        val sessions = OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK)
        val state = loaded(com.hardtekpt.crux.ui.journal.WeekClimbsViewModel(climbs, records, notes, sessions, FIXED_CLOCK).uiState) { !it.isLoading }
        bothThemes("week_climbs") { com.hardtekpt.crux.ui.journal.WeekClimbsContent(state) }
    }

    @Test
    fun progress() {
        val state = loaded(ProgressViewModel(climbs, places, preferences, FIXED_CLOCK, training()).uiState) { !it.isLoading }
        bothThemes("progress") { ProgressContent(uiState = state) }
    }

    // Progress's Training tab: plan against done, three figures, then each exercise.
    @Test
    fun progressTraining() {
        val state = loaded(ProgressViewModel(climbs, places, preferences, FIXED_CLOCK, training()).uiState) { !it.isLoading && !it.training.isEmpty }
        bothThemes("progress_training") { ProgressContent(uiState = state, initialView = com.hardtekpt.crux.ui.progress.ProgressView.Training) }
    }

    // Your datasets, and the demo's gym circuit as a list of climbs.
    @Test
    fun datasets() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = com.hardtekpt.crux.data.OfflineDatasetRepository(dbs, FIXED_CLOCK)
        val vm = com.hardtekpt.crux.ui.datasets.DatasetsViewModel(context, repo, preferences, playlistRepo)
        loaded(vm.uiState) { !it.isLoading && it.datasets.isNotEmpty() }
        bothThemes("datasets", readyText = "Block Lab circuit", dark = DARK) {
            com.hardtekpt.crux.ui.datasets.DatasetsScreen(onBack = {}, onOpen = {}, viewModel = vm)
        }
    }

    // The Playlists tab, and the demo's playlist for tonight.
    @Test
    fun datasetsPlaylists() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vm = com.hardtekpt.crux.ui.datasets.DatasetsViewModel(
            context,
            com.hardtekpt.crux.data.OfflineDatasetRepository(dbs, FIXED_CLOCK),
            preferences,
            playlistRepo,
        )
        loaded(vm.uiState) { !it.isLoading && it.playlists.isNotEmpty() }
        bothThemes("datasets_playlists", readyText = "Tonight", dark = DARK) {
            com.hardtekpt.crux.ui.datasets.DatasetsScreen(onBack = {
            }, onOpen = {}, viewModel = vm, initialTab = com.hardtekpt.crux.ui.datasets.DatasetsTab.PLAYLISTS)
        }
    }

    @Test
    fun playlist() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val id = runBlocking { db.playlistDao().getPlaylists().first().id }
        val vm = com.hardtekpt.crux.ui.datasets.PlaylistViewModel(
            SavedStateHandle(mapOf("playlistId" to id)),
            context,
            playlistRepo,
            com.hardtekpt.crux.data.OfflineDatasetRepository(dbs, FIXED_CLOCK),
            places,
            sessions(),
            preferences,
            FIXED_CLOCK,
        )
        loaded(vm.uiState) { it.playlist != null }
        bothThemes("playlist", readyText = "Cave crimps", dark = DARK) {
            com.hardtekpt.crux.ui.datasets.PlaylistScreen(onBack = {}, onLogClimb = {}, onOpenSession = {}, viewModel = vm)
        }
    }

    // Hold diagrams: a Kilter Homewall climb, its thumbnail, and a Mini MoonBoard climb with its grid.
    @Test
    fun boardDiagrams() {
        bothThemes("board_diagrams", dark = DARK) {
            androidx.compose.foundation.layout.Column(
                androidx.compose.ui.Modifier.padding(16.dp),
                verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp),
            ) {
                androidx.compose.foundation.layout.Row(horizontalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)) {
                    com.hardtekpt.crux.ui.components.BoardDiagramCard(
                        "kilter:8:25",
                        "p4125r42p4146r45p4149r45p4180r45p4212r43p4214r43p4235r45p4238r42p4268r45p4272r43p4274r43p4334r44p4363r44",
                        "Moon Landing",
                        androidx.compose.ui.Modifier.weight(1f),
                        maxHeight = 300,
                    )
                    com.hardtekpt.crux.ui.components.BoardDiagramCard(
                        "moonboard:7",
                        "p45r43p124r44p37r43p93r43p127r43p62r42p95r43p53r42",
                        "Easy Does It",
                        androidx.compose.ui.Modifier.weight(1f),
                        maxHeight = 300,
                    )
                }
                com.hardtekpt.crux.ui.components.HoldLegend("p4125r42p4146r45p4212r43p4334r44")
                com.hardtekpt.crux.ui.components.BoardDiagramCard(
                    "kilter:8:25",
                    "p4125r42p4146r45p4149r45p4180r45p4212r43p4214r43p4235r45p4238r42p4268r45p4272r43p4274r43p4334r44p4363r44",
                    "Moon Landing",
                    androidx.compose.ui.Modifier.size(width = 40.dp, height = 52.dp),
                    small = true,
                )
            }
        }
    }

    // A board catalogue: its climbs at 40°, most sent first.
    @Test
    fun catalogue() {
        val catalogueDb = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), com.hardtekpt.crux.data.CatalogueDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        val catalogues = com.hardtekpt.crux.data.CatalogueDatabases(ApplicationProvider.getApplicationContext()).also { it.override = catalogueDb }
        val datasets = com.hardtekpt.crux.data.OfflineDatasetRepository(dbs, FIXED_CLOCK)
        val key = "boardsesh:kilter:8:25"
        val climbs = listOf(
            Triple("Moon Landing", "KilterStudio", 16.0),
            Triple("Guessing Games", "griffin", 18.0),
            Triple("Half Mast", "KilterStudio", 16.0),
            Triple("Angel Eyes", "jakey", 20.0),
            Triple("Lightest Shorts", "KilterStudio", 18.0),
            Triple("Crimp City", "tbagg", 21.0),
            Triple("Swooped", "jwebxl", 22.0),
        )
        val id = runBlocking {
            val dao = catalogueDb.catalogueDao()
            dao.insertClimbs(
                climbs.mapIndexed { i, (name, setter, _) ->
                    com.hardtekpt.crux.data.CatalogueClimbEntity(key, "u$i", name, setter, setterAngle = 40)
                },
            )
            dao.insertGrades(
                climbs.mapIndexed { i, (_, _, d) ->
                    com.hardtekpt.crux.data.CatalogueGradeEntity(key, "u$i", 40, d, benchmark = i % 3 == 0, ascents = 7500 - i * 900, quality = 3.2 - i * 0.05)
                } + climbs.indices.map { com.hardtekpt.crux.data.CatalogueGradeEntity(key, "u$it", 30, 15.0, ascents = 10) },
            )
            datasets.saveCatalogue(
                key,
                "Kilter Homewall",
                com.hardtekpt.crux.data.model.BoardModel.KILTER_HOMEWALL,
                "10x12",
                com.hardtekpt.crux.data.model.BoardHolds.FULL_RIDE,
                28_979,
            )
        }
        val vm = com.hardtekpt.crux.ui.datasets.CatalogueViewModel(
            SavedStateHandle(mapOf("datasetId" to id)),
            datasets,
            com.hardtekpt.crux.data.CatalogueRepository(catalogues),
            playlistRepo,
            preferences,
            FIXED_CLOCK,
            com.hardtekpt.crux.data.BoardBrowser(com.hardtekpt.crux.data.CatalogueRepository(catalogues)),
            com.hardtekpt.crux.data.BrowseStore(dataStore),
        )
        loaded(vm.browse.ui) { it.climbs.isNotEmpty() }
        bothThemes("catalogue", readyText = "Moon Landing", dark = DARK) {
            com.hardtekpt.crux.ui.datasets.CatalogueScreen(onBack = {}, onLogClimb = {}, viewModel = vm)
        }
        catalogueDb.close()
    }

    // Log this climb, from the circuit: Block Lab's main gym, the linked name and the grade hint.
    @Test
    fun logClimbFromDataset() {
        val climbId = runBlocking { db.datasetDao().getAllClimbs().first { it.name == "Pinch roof" }.id }
        val vm = LogClimbViewModel(
            SavedStateHandle(mapOf("datasetClimbId" to climbId)),
            climbs,
            places,
            FIXED_CLOCK,
            preferences,
            FakeImageFiles(),
            sessions(),
            com.hardtekpt.crux.data.OfflineDatasetRepository(dbs, FIXED_CLOCK),
            playlistRepo,
        )
        loaded(vm.draft) { it.source != null }
        bothThemes("logclimb_dataset", readyText = "from Block Lab circuit", dark = DARK) { LogClimbScreen(onDone = {}, viewModel = vm) }
    }

    // A climbing session with tonight's playlist: the climb that's up, with + Fell and + Sent.
    @Test
    fun sessionPlaylist() {
        val sessions = sessions()
        val sessionId = runBlocking {
            val place = db.placeDao().getPlaces().first { it.name == "Block Lab" }
            val id = sessions.start(null, place.id, db.placeDao().getSections(place.id).first().id)
            sessions.setPlaylist(id, db.playlistDao().getPlaylists().first().id, 1)
            id
        }
        val vm = SessionViewModel(
            SavedStateHandle(mapOf("sessionId" to sessionId)),
            sessions,
            exercises,
            places,
            preferences,
            FIXED_CLOCK,
            com.hardtekpt.crux.live.LiveTimers(),
            playlistRepository = playlistRepo,
        )
        loaded(vm.playlist) { it != null }
        bothThemes("session_playlist", readyText = "Cave jugs", dark = DARK) {
            SessionScreen(onLeave = {}, onLogClimb = {}, onFinished = {}, viewModel = vm)
        }
    }

    @Test
    fun dataset() {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val repo = com.hardtekpt.crux.data.OfflineDatasetRepository(dbs, FIXED_CLOCK)
        val id = runBlocking { db.datasetDao().getDatasets().first().id }
        val vm = com.hardtekpt.crux.ui.datasets.DatasetViewModel(
            SavedStateHandle(mapOf("datasetId" to id)),
            context,
            repo,
            preferences,
            FIXED_CLOCK,
            playlistRepo,
            com.hardtekpt.crux.data.BoardBrowser(
                com.hardtekpt.crux.data.CatalogueRepository(com.hardtekpt.crux.data.CatalogueDatabases(ApplicationProvider.getApplicationContext())),
            ),
            com.hardtekpt.crux.data.BrowseStore(dataStore),
        )
        loaded(vm.uiState) { it.dataset != null }
        bothThemes("dataset", readyText = "Blue slab", dark = DARK) { com.hardtekpt.crux.ui.datasets.DatasetScreen(onBack = {}, viewModel = vm) }
    }

    // An exercise's page: its best, its chart and each day it was trained.
    @Test
    fun exerciseProgress() {
        val exerciseId = runBlocking { db.exerciseDao().getAll().first { it.name == "Half-crimp hang" }.id }
        val vm = com.hardtekpt.crux.ui.you.ExerciseRecordsViewModel(
            SavedStateHandle(mapOf("exerciseId" to exerciseId)),
            exercises,
            records,
            OfflineTemplateRepository(dbs),
            com.hardtekpt.crux.data.OfflineScheduleRepository(dbs),
        )
        loaded(vm.state) { it.results.isNotEmpty() && it.plans.isNotEmpty() }
        bothThemes("exercise_progress", readyText = "Half-crimp hang", dark = DARK) {
            com.hardtekpt.crux.ui.you.ExerciseRecordsScreen(onBack = {}, onAdd = {}, viewModel = vm)
        }
    }

    // Home with the Training and Exercise widgets, each showing what was chosen for it.
    @Test
    fun homeTraining() {
        val vm = homeViewModel()
        val state = loaded(vm.uiState) { !it.isLoading && it.training.exercises.isNotEmpty() }
        val hangs = state.training.exercises.first { it.exercise.name == "Half-crimp hang" }.exercise.id
        val pullUps = state.training.exercises.first { it.exercise.name == "Weighted pull-ups" }.exercise.id
        val widgets = listOf(
            com.hardtekpt.crux.data.dashboard.DashboardWidget(type = com.hardtekpt.crux.data.dashboard.WidgetType.MY_GYM),
            com.hardtekpt.crux.data.dashboard.DashboardWidget(
                type = com.hardtekpt.crux.data.dashboard.WidgetType.TRAINING,
                stat = com.hardtekpt.crux.data.TrainingStat.ON_PLAN,
            ),
            com.hardtekpt.crux.data.dashboard.DashboardWidget(
                type = com.hardtekpt.crux.data.dashboard.WidgetType.EXERCISE,
                exerciseId = pullUps,
            ),
            com.hardtekpt.crux.data.dashboard.DashboardWidget(
                type = com.hardtekpt.crux.data.dashboard.WidgetType.EXERCISE,
                size = com.hardtekpt.crux.data.dashboard.WidgetSize.WIDE,
                exerciseId = hangs,
            ),
            com.hardtekpt.crux.data.dashboard.DashboardWidget(
                type = com.hardtekpt.crux.data.dashboard.WidgetType.TRAINING,
                size = com.hardtekpt.crux.data.dashboard.WidgetSize.WIDE,
                stat = com.hardtekpt.crux.data.TrainingStat.SETS,
            ),
        )
        bothThemes("home_training", dark = DARK) { HomeContent(uiState = state, dashboard = com.hardtekpt.crux.ui.home.DashboardState(widgets)) }
    }

    private fun training() = com.hardtekpt.crux.ui.progress.TrainingProgressSource(
        sessions(),
        records,
        exercises,
        com.hardtekpt.crux.data.OfflineScheduleRepository(dbs),
        climbs,
        FIXED_CLOCK,
    )

    @Test
    fun you() {
        val state = loaded(YouViewModel(body, climbs, records, notes, places, preferences, FIXED_CLOCK).uiState) { !it.isLoading }
        bothThemes("you") { YouContent(uiState = state, actions = ProfileActions()) }
    }

    @Test
    fun settings() = settings("settings")

    private fun settings(name: String, dark: List<Boolean> = BOTH) = bothThemes(name, dark = dark) {
        SettingsContent(uiState = SettingsUiState(), onBack = {}, onGradeScale = {}, onThemeMode = {})
    }

    @Test
    fun backups() = bothThemes("backups") { BackupContent(state = BackupUiState(), onBack = {}) }

    @Test
    fun intervalTimer() = bothThemes("interval_timer") { IntervalTimerSetup(IntervalSpec.REPEATERS, onSpec = {}, onStart = {}, onBack = {}) }

    @Test
    fun intervalTimerRunning() = bothThemes("interval_timer_running") { RunningTimer() }

    @Test
    @Config(qualifiers = "+land")
    fun intervalTimerLandscape() = bothThemes("interval_timer_landscape", dark = DARK) { RunningTimer() }

    @Test
    @Config(fontScale = LARGEST_TEXT)
    fun intervalTimerLargestText() = bothThemes("interval_timer_running_text_largest", dark = DARK) { RunningTimer() }

    /** Repeaters, 3.5 s into the fourth repeat of the second cycle. */
    @Composable
    private fun RunningTimer() {
        val run = IntervalRun(IntervalSpec.REPEATERS, startedAtMillis = 0)
        val index = run.spec.phases.indexOfFirst { it.kind == IntervalPhase.Kind.WORK && it.cycle == 1 && it.repeat == 3 }
        val now = run.startOf(index) + 3_500
        IntervalTimerRunning(run, now, { now }, onPause = {}, onResume = {}, onStop = {}, onRestart = {}, onSkip = {})
    }

    @Test
    fun train() {
        bothThemes("train", readyText = "Max hangs") {
            TrainScreen(onOpenPlan = {}, onNewPlan = {}, onOpenExercise = {}, onNewExercise = {}, viewModel = trainViewModel(), initialView = TrainView.Plans)
        }
    }

    @Test
    fun trainWeek() {
        bothThemes("train_week", readyText = "Strength day") {
            TrainScreen(onOpenPlan = {}, onNewPlan = {}, onOpenExercise = {}, onNewExercise = {}, viewModel = trainViewModel())
        }
    }

    private fun trainViewModel() = TrainViewModel(
        OfflineTemplateRepository(dbs),
        exercises,
        StarterData(dbs, preferences, FIXED_CLOCK),
        com.hardtekpt.crux.data.OfflineScheduleRepository(dbs),
        OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK),
        climbs,
        places,
        FIXED_CLOCK,
    )

    @Test
    fun session() = session("session")

    // A rest running in the dock after a set.
    @Test
    fun sessionResting() = session("session_resting", dark = DARK) { live, _ ->
        live.rest.value = com.hardtekpt.crux.ui.session.Rest(FIXED_CLOCK.millis() + 48_000, 90)
    }

    // An exercise's interval timer, made big.
    @Test
    fun sessionTimerBig() = session("session_timer_big", dark = DARK, bigTimer = true) { live, itemId ->
        live.timer.value = com.hardtekpt.crux.ui.session.SessionTimer(
            com.hardtekpt.crux.ui.session.IntervalRun(com.hardtekpt.crux.ui.session.IntervalSpec.REPEATERS, FIXED_CLOCK.millis() - 26_000),
            itemId,
        )
    }

    private fun session(
        name: String,
        dark: List<Boolean> = BOTH,
        bigTimer: Boolean = false,
        running: (com.hardtekpt.crux.live.LiveTimers, Long) -> Unit = { _, _ -> },
    ) {
        val sessions = sessions()
        val sessionId = runBlocking {
            val plan = OfflineTemplateRepository(dbs).observeTemplates().first().first()
            val place = db.placeDao().getPlaces().first { it.name == "Block Lab" }
            val id = sessions.start(plan.id, place.id, db.placeDao().getSections(place.id).first().id)
            // The warm-up done, so the screen shows a session under way.
            val warmUp = sessions.observeSession(id).first { it != null }!!.items.first()
            repeat(warmUp.target.sets) { sessions.logSet(warmUp.id, it, warmUp.target.reps, warmUp.target.seconds, warmUp.target.loadKg) }
            id
        }
        val live = com.hardtekpt.crux.live.LiveTimers()
        val vm =
            SessionViewModel(
                SavedStateHandle(mapOf("sessionId" to sessionId)),
                sessions,
                exercises,
                places,
                preferences,
                FIXED_CLOCK,
                live,
            )
        // The session's second exercise is the one a timer runs for.
        running(live, runBlocking { sessions.observeSession(sessionId).first { it != null }!!.items[1].id })
        bothThemes(name, readyText = "Max hangs", dark = dark) {
            SessionScreen(onLeave = {}, onLogClimb = {}, onFinished = {}, viewModel = vm, bigTimer = bigTimer)
        }
    }

    @Test
    fun logClimb() = logClimb("logclimb")

    private fun logClimb(name: String, dark: List<Boolean> = BOTH) {
        val vm = LogClimbViewModel(SavedStateHandle(), climbs, places, FIXED_CLOCK, preferences, FakeImageFiles(), sessions())
        bothThemes(name, readyText = "Log climb", dark = dark) { LogClimbScreen(onDone = {}, viewModel = vm) }
    }

    // The largest text size on top of a large phone font, so clipped or overlapping text shows up.
    // Twice the text size, Android's largest font with display size left alone.
    @Test
    @Config(fontScale = DOUBLE_TEXT)
    fun homeDoubleText() = home("home_text_double", dark = DARK)

    @Test
    @Config(fontScale = DOUBLE_TEXT)
    fun logClimbDoubleText() = logClimb("logclimb_text_double", dark = DARK)

    @Test
    @Config(fontScale = DOUBLE_TEXT)
    fun sessionDoubleText() = session("session_text_double", dark = DARK)

    @Test
    @Config(fontScale = DOUBLE_TEXT)
    fun settingsDoubleText() = settings("settings_text_double", dark = DARK)

    @Test
    @Config(fontScale = DOUBLE_TEXT)
    fun setupDoubleText() = setup("setup_units_text_double", com.hardtekpt.crux.ui.setup.SetupState(step = com.hardtekpt.crux.ui.setup.SetupStep.UNITS))

    @Test
    @Config(fontScale = LARGEST_TEXT)
    fun homeLargestText() = home("home_text_largest", dark = DARK)

    @Test
    @Config(fontScale = LARGEST_TEXT)
    fun logClimbLargestText() = logClimb("logclimb_text_largest", dark = DARK)

    @Test
    @Config(fontScale = LARGEST_TEXT)
    fun sessionLargestText() = session("session_text_largest", dark = DARK)

    @Test
    @Config(fontScale = LARGEST_TEXT)
    fun settingsLargestText() = settings("settings_text_largest", dark = DARK)

    @Test
    fun place() {
        val placeId = runBlocking { db.placeDao().getPlaces().first { it.name == "Block Lab" }.id }
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        val vm = PlaceDetailViewModel(SavedStateHandle(mapOf("placeId" to placeId)), places, AreaImageStore(context), climbs)
        bothThemes("place", readyText = "Block Lab") {
            PlaceDetailScreen(onBack = {}, onEdit = {}, onOpenProblem = {}, onLogHere = { _, _ -> }, viewModel = vm)
        }
    }

    private val exercises by lazy { OfflineExerciseRepository(dbs, FIXED_CLOCK) }

    private fun sessions() = OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK)

    /** The view model's state once it has loaded, so the screenshot never catches a half-filled screen. */
    private fun <T> loaded(state: Flow<T>, isLoaded: (T) -> Boolean): T = runBlocking { withTimeout(10_000) { state.first(isLoaded) } }

    /** Renders [content] dark (the app's default) and light, or only the modes in [dark]. */
    private fun bothThemes(
        name: String,
        readyText: String? = null,
        dark: List<Boolean> = BOTH,
        accent: Accent = Accent.TEAL,
        content: @Composable () -> Unit,
    ) {
        var isDark by mutableStateOf(dark.first())
        compose.setContent {
            // "Today" is the fixed test day everywhere, so the goldens don't change from day to day.
            CompositionLocalProvider(LocalClock provides FIXED_CLOCK) {
                CruxTheme(darkTheme = isDark, accent = accent) {
                    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) { content() }
                }
            }
        }
        dark.forEach { theme ->
            isDark = theme
            // Screens that load their own data: wait until it's on screen.
            readyText?.let { text ->
                compose.waitUntil(10_000) { compose.onAllNodesWithText(text, substring = true).fetchSemanticsNodes().isNotEmpty() }
            }
            compose.waitForIdle()
            // The main screens in dark double as the store screenshots (fastlane/, in this order).
            val path = STORE_ORDER[name]?.takeIf { theme }?.let { "$STORE_SCREENSHOTS/${it}_$name.png" }
                ?: "src/test/screenshots/${name}_${if (theme) "dark" else "light"}.png"
            compose.onRoot().captureRoboImage(path)
        }
        assertEveryTappableIsLabelled(name)
    }

    /**
     * Every screen in these tests doubles as an accessibility check: anything TalkBack can tap
     * must say what it is, through its own text or description or its children's.
     */
    private fun assertEveryTappableIsLabelled(name: String) {
        val unlabelled = compose.onAllNodes(androidx.compose.ui.test.hasClickAction()).fetchSemanticsNodes().filter { node ->
            val c = node.config
            val text = c.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.Text).orEmpty().joinToString("")
            val description = c.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.ContentDescription).orEmpty().joinToString("")
            val edit = c.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.EditableText)?.text.orEmpty()
            // A click label ("Go to it") says what a tap does, not what the thing is, so it doesn't count.
            (text + description + edit).isBlank()
        }
        org.junit.Assert.assertTrue(
            "$name: tappable with no label: " +
                unlabelled.joinToString { it.config.getOrNull(androidx.compose.ui.semantics.SemanticsProperties.TestTag) ?: "untagged at ${it.boundsInRoot}" },
            unlabelled.isEmpty(),
        )
    }

    private companion object {
        val BOTH = listOf(true, false)
        val DARK = listOf(true)

        /** Largest (1.3×) on a phone already set to 1.3×. */
        const val LARGEST_TEXT = 1.69f

        /** Android's 200% font size. */
        const val DOUBLE_TEXT = 2f
        const val STORE_SCREENSHOTS = "../fastlane/metadata/android/en-US/images/phoneScreenshots"
        val STORE_ORDER = mapOf(
            "home" to 1,
            "journal" to 2,
            "progress" to 3,
            "you" to 4,
            "train" to 5,
            "session" to 6,
            "logclimb" to 7,
            "place" to 8,
        )
    }
}
