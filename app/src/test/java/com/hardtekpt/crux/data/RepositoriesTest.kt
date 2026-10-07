package com.hardtekpt.crux.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.DataMode
import com.hardtekpt.crux.data.local.DatabaseFactory
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.model.NewClimb
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.data.seed.StarterDataSeeder
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** The Room-backed repositories, on in-memory databases: the climber's own and the demo set. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class RepositoriesTest {

    @get:Rule val tmp = TemporaryFolder()

    private fun newDb() = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CruxDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    private val real = newDb()
    private val demo = newDb()

    @After
    fun close() {
        real.close()
        demo.close()
    }

    private lateinit var preferences: UserPreferencesRepository

    private fun TestScope.databases(): CruxDatabases {
        preferences = UserPreferencesRepository(
            PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "prefs.preferences_pb") },
        )
        return CruxDatabases(preferences, DatabaseFactory { if (it == DataMode.DEMO) demo else real }, backgroundScope)
    }

    private fun climb(gradeIndex: Int, style: AscentStyle = AscentStyle.FLASH, date: LocalDate = TODAY) = NewClimb(
        discipline = Discipline.BOULDER,
        gradeScale = GradeScale.FONT,
        gradeIndex = gradeIndex,
        style = style,
        attempts = 1,
        venue = Venue.GYM,
        date = date,
        name = null,
        place = null,
        notes = null,
    )

    @Test
    fun `a climb is logged, edited and deleted, and bests follow`() = runTest {
        val climbs = OfflineClimbRepository(databases(), FIXED_CLOCK)

        val easy = climbs.logClimb(climb(gradeIndex = 10))
        val hard = climbs.logClimb(climb(gradeIndex = 14, style = AscentStyle.REDPOINT))
        assertEquals(2, climbs.observeClimbCount().first())
        assertEquals(14, climbs.observePersonalBests().first().maxOf { it.gradeIndex })

        climbs.updateClimb(easy, climb(gradeIndex = 11).copy(notes = "Felt easy"))
        val edited = climbs.getClimb(easy)!!
        assertEquals(11, edited.gradeIndex)
        assertEquals("Felt easy", edited.notes)

        climbs.deleteClimb(hard)
        assertNull(climbs.getClimb(hard))
        assertEquals(11, climbs.observePersonalBests().first().maxOf { it.gradeIndex })
    }

    @Test
    fun `weigh-ins come back newest first and body stats keep their latest value`() = runTest {
        val body = OfflineBodyRepository(databases(), FIXED_CLOCK)

        body.logWeight(70.0, TODAY.minusDays(7))
        body.logWeight(68.5, TODAY)
        body.setMeasurement(MeasurementType.HEIGHT, 178.0)
        body.setMeasurement(MeasurementType.HEIGHT, 179.0)

        assertEquals(listOf(68.5, 70.0), body.observeWeights().first().map { it.value })
        assertEquals(179.0, body.observeLatest().first().getValue(MeasurementType.HEIGHT).value, 0.0)
    }

    @Test
    fun `notes are saved, edited in place, pinned first and deleted`() = runTest {
        val notes = OfflineNoteRepository(databases(), FIXED_CLOCK)

        val plain = notes.saveNote(0, "Warm up longer", pinned = false)
        val pinned = notes.saveNote(0, "Left finger tweak\nEasy on crimps", pinned = true, tag = "Injury")
        assertEquals(listOf(pinned, plain), notes.observeNotes().first().map { it.id })

        assertEquals(plain, notes.saveNote(plain, "Warm up much longer", pinned = false))
        assertEquals("Warm up much longer", notes.getNote(plain)!!.text)
        // Tags are kept trimmed and lowercase, so "Injury" and "injury" are one filter.
        assertEquals("injury", notes.getNote(pinned)!!.tag)
        assertEquals("Left finger tweak", notes.getNote(pinned)!!.title)

        notes.deleteNote(plain)
        assertEquals(listOf(pinned), notes.observeNotes().first().map { it.id })
    }

    @Test
    fun `the heavier result is the personal record`() = runTest {
        val dbs = databases()
        val exercises = OfflineExerciseRepository(dbs, FIXED_CLOCK)
        val records = OfflineRecordRepository(dbs, FIXED_CLOCK)
        val hang = exercises.saveExercise(
            ExerciseInput(name = "Max hang", category = ExerciseCategory.FINGERS, metric = MetricType.WEIGHTED_TIME, notes = null),
        )

        records.addRecord(hang, TODAY.minusDays(7), reps = null, seconds = 10, loadKg = 10.0, notes = null)
        val heavier = records.addRecord(hang, TODAY, reps = null, seconds = 10, loadKg = 15.0, notes = "New PR")

        val best = records.observeBests().first().single()
        assertEquals(heavier, best.best.id)
        assertEquals(2, best.results)

        records.deleteRecord(heavier)
        assertEquals(10.0, records.observeBests().first().single().best.loadKg!!, 0.0)
    }

    @Test
    fun `an interval exercise keeps its timer, and plans start from it`() = runTest {
        val dbs = databases()
        val exercises = OfflineExerciseRepository(dbs, FIXED_CLOCK)
        val timer = com.hardtekpt.crux.data.model.IntervalSettings(
            prepSeconds = 5,
            workSeconds = 7,
            restSeconds = 3,
            repeats = 6,
            cycles = 3,
            cycleRestSeconds = 180,
        )
        val id = exercises.saveExercise(
            ExerciseInput(name = "Repeaters", category = ExerciseCategory.FINGERS, metric = MetricType.INTERVALS, notes = null, intervals = timer),
        )
        val saved = exercises.getExercise(id)!!
        assertEquals(timer, saved.intervals)
        val target = com.hardtekpt.crux.data.model.ExerciseTarget.defaultFor(saved)
        assertEquals(3, target.sets)
        assertEquals(6, target.reps)
        assertEquals(7, target.seconds)
        assertEquals(3, target.repRestSeconds)
        assertEquals(180, target.restSeconds)

        // Measured another way, the timer is dropped.
        exercises.saveExercise(
            ExerciseInput(id = id, name = "Repeaters", category = ExerciseCategory.FINGERS, metric = MetricType.TIME, notes = null, intervals = timer),
        )
        assertNull(exercises.getExercise(id)!!.intervals)
    }

    @Test
    fun `deleting an exercise takes it out of the plans that used it`() = runTest {
        val dbs = databases()
        StarterDataSeeder(FIXED_CLOCK).seed(real, includeSampleData = false)
        val exercises = OfflineExerciseRepository(dbs, FIXED_CLOCK)
        val templates = OfflineTemplateRepository(dbs)
        val plan = templates.observeTemplates().first().first()
        val used = plan.blocks.flatMap { it.items }.first().exercise

        assertTrue(exercises.planCount(used.id) > 0)
        exercises.deleteExercise(used.id)

        assertNull(exercises.getExercise(used.id))
        assertFalse(templates.getTemplate(plan.id)!!.blocks.flatMap { it.items }.any { it.exercise.id == used.id })
    }

    @Test
    fun `a plan saves and reads back, and deleting it removes it`() = runTest {
        val dbs = databases()
        StarterDataSeeder(FIXED_CLOCK).seed(real, includeSampleData = false)
        val templates = OfflineTemplateRepository(dbs)
        val plan = templates.observeTemplates().first().first()

        val copy = templates.saveTemplate(plan.copy(id = 0, name = "Copy of ${plan.name}"))
        val saved = templates.getTemplate(copy)!!
        assertEquals("Copy of ${plan.name}", saved.name)
        assertEquals(plan.blocks, saved.blocks)

        templates.deleteTemplate(copy)
        assertNull(templates.getTemplate(copy))
    }

    @Test
    fun `demo mode reads and writes the demo data and never touches the climber's own`() = runTest {
        val dbs = databases()
        val climbs = OfflineClimbRepository(dbs, FIXED_CLOCK)
        climbs.logClimb(climb(gradeIndex = 10).copy(notes = "Mine"))

        preferences.setDemoMode(true)
        dbs.mode.first { it == DataMode.DEMO }
        assertEquals(0, climbs.observeClimbCount().first())
        climbs.logClimb(climb(gradeIndex = 20).copy(notes = "Demo"))

        preferences.setDemoMode(false)
        dbs.mode.first { it == DataMode.REAL }
        assertEquals(listOf("Mine"), climbs.observeClimbs().first().map { it.notes })
        assertEquals(listOf("Demo"), demo.climbDao().getAll().map { it.notes })
    }

    private companion object {
        val TODAY: LocalDate = LocalDate.now(FIXED_CLOCK)
    }

    @Test
    fun `a session runs a plan, logs sets and climbs, and finishes into the journal list`() = runTest {
        val dbs = databases()
        StarterDataSeeder(FIXED_CLOCK).seed(real, includeSampleData = false)
        val templates = OfflineTemplateRepository(dbs)
        val sessions = OfflineSessionRepository(dbs, templates, FIXED_CLOCK)
        val climbs = OfflineClimbRepository(dbs, FIXED_CLOCK)
        val plan = templates.observeTemplates().first().first()

        val id = sessions.start(plan.id, placeId = null, sectionId = null)
        val running = sessions.observeRunning().first()!!
        assertEquals(id, running.id)
        assertEquals(plan.name, running.name)
        assertEquals(plan.blocks.sumOf { it.items.size }, running.items.size)

        val first = running.items.first()
        sessions.logSet(first.id, 0, reps = 5, seconds = 10, loadKg = 12.5)
        sessions.skipSet(first.id, 1)
        climbs.logClimb(climb(gradeIndex = 12).copy(sessionId = id))
        val during = sessions.observeSession(id).first()!!
        assertEquals(1, during.setsDone)
        assertEquals(1, during.setsSkipped)
        assertEquals(1, during.climbs.size)

        sessions.finish(id, effort = 8, notes = " Strong day ")
        assertNull(sessions.running())
        val finished = sessions.observeFinished().first().single()
        assertEquals(8, finished.effort)
        assertEquals("Strong day", finished.notes)

        // A session without a plan starts empty and takes exercises as it goes.
        val free = sessions.start(null, placeId = null, sectionId = null)
        assertEquals(OfflineSessionRepository.CLIMBING_SESSION, sessions.observeSession(free).first()!!.name)
        sessions.discard(free)
        assertNull(sessions.observeSession(free).first())
    }
}
