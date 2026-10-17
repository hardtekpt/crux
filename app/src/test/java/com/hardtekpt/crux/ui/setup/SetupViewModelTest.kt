package com.hardtekpt.crux.ui.setup

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.FirstRun
import com.hardtekpt.crux.data.OfflineBodyRepository
import com.hardtekpt.crux.data.OfflinePlaceRepository
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.DatabaseFactory
import com.hardtekpt.crux.data.model.BoardModel
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** First-run setup: when it shows, and what Start saves. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SetupViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CruxDatabase::class.java)
        .allowMainThreadQueries()
        .build()
    private val preferences by lazy { UserPreferencesRepository(mainDispatcherRule.preferencesDataStore(File(tmp.root, "prefs.preferences_pb"))) }
    private val dbs by lazy { CruxDatabases(preferences, DatabaseFactory { db }, CoroutineScope(mainDispatcherRule.testDispatcher + SupervisorJob())) }
    private val places by lazy { OfflinePlaceRepository(dbs, FIXED_CLOCK) }
    private val body by lazy { OfflineBodyRepository(dbs, FIXED_CLOCK) }
    private val firstRun by lazy { FirstRun(preferences, dbs) }

    @After
    fun close() = db.close()

    private fun viewModel() = SetupViewModel(preferences, places, body, FIXED_CLOCK, firstRun)

    @Test
    fun `a fresh install needs setup, once it's done it never shows again`() = runBlocking {
        assertTrue(firstRun.needed())
        viewModel().skip {}.join()
        assertFalse(firstRun.needed())
    }

    @Test
    fun `an install with its own data never sees setup`() = runBlocking {
        body.logWeight(70.0, java.time.LocalDate.now(FIXED_CLOCK))
        assertFalse(firstRun.needed())
        assertTrue(preferences.setupDone.first())
    }

    @Test
    fun `demo mode skips setup`() = runBlocking {
        preferences.setDemoMode(true)
        assertFalse(firstRun.needed())
    }

    @Test
    fun `start saves units, grades, the place and body stats into the climber's own data`() = runBlocking {
        val vm = viewModel()
        var done = false
        vm.start()
        vm.setUnits(UnitSystem.IMPERIAL)
        vm.setScale(GradeScale.V_SCALE)
        vm.unitsDone(skip = false)
        vm.setPlaceName("Arco Boulder Gym")
        vm.toggleKind(PlaceType.BOARD)
        vm.setBoard(SetupBoard.MOON)
        vm.placeDone(skip = false)
        vm.bodyDone(72.4, 178.0)
        assertEquals(SetupStep.START, vm.state.value.step)
        vm.finish { done = true }?.join()

        assertTrue(done)
        assertEquals(UnitSystem.IMPERIAL, preferences.units.first())
        assertEquals(GradeScale.V_SCALE, preferences.gradeScales.first().boulder)
        assertEquals(GradeScale.FRENCH, preferences.gradeScales.first().route)
        val place = places.observePlaces().first().single().place
        assertEquals("Arco Boulder Gym", place.name)
        assertEquals(place.id, preferences.lastPlaceId.first())
        val sections = db.placeDao().observeAllSections().first()
        assertEquals(listOf(PlaceType.GYM, PlaceType.BOARD), sections.map { it.type })
        assertEquals(BoardModel.MOON_2016.key, sections.last().boardModel)
        val latest = body.observeLatest().first()
        assertEquals(72.4, latest.getValue(MeasurementType.WEIGHT).value, 0.001)
        assertEquals(178.0, latest.getValue(MeasurementType.HEIGHT).value, 0.001)
        assertFalse(preferences.demoMode.first())
        assertTrue(preferences.setupDone.first())
    }

    @Test
    fun `skipped steps keep the defaults and save nothing`() = runBlocking {
        val vm = viewModel()
        vm.start()
        vm.setUnits(UnitSystem.IMPERIAL)
        vm.unitsDone(skip = true)
        vm.setPlaceName("Somewhere")
        vm.placeDone(skip = true)
        vm.bodyDone(null, null)
        vm.setDemo(true)
        vm.finish {}?.join()

        assertEquals(UnitSystem.METRIC, preferences.units.first())
        assertTrue(db.placeDao().observePlaces().first().isEmpty())
        assertTrue(db.bodyMeasurementDao().observeAll().first().isEmpty())
        assertTrue(preferences.demoMode.first())
    }

    @Test
    fun `back walks the steps and stops at Welcome`() {
        val vm = viewModel()
        vm.start()
        vm.unitsDone(skip = false)
        assertTrue(vm.back())
        assertEquals(SetupStep.UNITS, vm.state.value.step)
        assertTrue(vm.back())
        assertEquals(SetupStep.WELCOME, vm.state.value.step)
        assertFalse(vm.back())
    }
}
