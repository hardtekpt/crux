package com.hardtekpt.crux.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.DatabaseFactory
import com.hardtekpt.crux.data.local.ExerciseEntity
import com.hardtekpt.crux.data.local.SessionStatus
import com.hardtekpt.crux.data.model.Exercise
import com.hardtekpt.crux.data.model.ExerciseCategory
import com.hardtekpt.crux.data.model.MetricType
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import java.io.File
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Sets done in sessions count toward personal records, next to results logged by hand. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class SessionRecordsTest {

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CruxDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @get:Rule val tmp = TemporaryFolder()

    @After
    fun close() = db.close()

    /** Real mode (the default) reads and writes this one in-memory database. */
    private fun TestScope.databases() = CruxDatabases(
        UserPreferencesRepository(PreferenceDataStoreFactory.create { File(tmp.root, "prefs.preferences_pb") }),
        DatabaseFactory { db },
        backgroundScope,
    )

    private class Setup(val records: OfflineRecordRepository, val sessions: OfflineSessionRepository, val exercise: Exercise)

    private suspend fun TestScope.setup(): Setup {
        val dbs = databases()
        val id = db.exerciseDao().insert(
            ExerciseEntity(name = "Weighted pull-ups", category = ExerciseCategory.PULLING, metric = MetricType.WEIGHTED_REPS, createdAtMillis = 0),
        )
        return Setup(
            OfflineRecordRepository(dbs, FIXED_CLOCK),
            OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK),
            Exercise(id, "Weighted pull-ups", ExerciseCategory.PULLING, MetricType.WEIGHTED_REPS, notes = null),
        )
    }

    @Test
    fun `a heavier set in a session beats a result logged by hand`() = runTest {
        val s = setup()
        s.records.addRecord(s.exercise.id, LocalDate.now(FIXED_CLOCK), reps = 5, seconds = null, loadKg = 5.0, notes = null)
        val session = s.sessions.start(templateId = null, placeId = null, sectionId = null)
        val item = s.sessions.addExercise(session, s.exercise)
        s.sessions.logSet(item, setIndex = 0, reps = 5, seconds = null, loadKg = 10.0)

        val best = s.records.observeBests().first().single()

        assertEquals(10.0, best.best.loadKg!!, 0.0)
        assertTrue(best.best.fromSession)
        assertEquals(2, best.results)
        assertEquals(2, s.records.observeRecords(s.exercise.id).first().size)
    }

    @Test
    fun `skipped sets and sets in a discarded session don't count`() = runTest {
        val s = setup()
        s.records.addRecord(s.exercise.id, LocalDate.now(FIXED_CLOCK), reps = 5, seconds = null, loadKg = 5.0, notes = null)

        val first = s.sessions.start(templateId = null, placeId = null, sectionId = null)
        val firstItem = s.sessions.addExercise(first, s.exercise)
        s.sessions.skipSet(firstItem, setIndex = 0)
        s.sessions.logSet(firstItem, setIndex = 1, reps = 5, seconds = null, loadKg = 20.0)
        // Marked discarded rather than deleted, as the query's own filter sees it.
        val entity = db.sessionDao().getSession(first)!!
        db.sessionDao().updateSession(entity.copy(status = SessionStatus.DISCARDED))

        val second = s.sessions.start(templateId = null, placeId = null, sectionId = null)
        val secondItem = s.sessions.addExercise(second, s.exercise)
        s.sessions.skipSet(secondItem, setIndex = 0)

        val best = s.records.observeBests().first().single()

        assertEquals(5.0, best.best.loadKg!!, 0.0)
        assertEquals(false, best.best.fromSession)
        assertEquals(1, best.results)
    }

    @Test
    fun `a set from a session can't be deleted as a result`() = runTest {
        val s = setup()
        val session = s.sessions.start(templateId = null, placeId = null, sectionId = null)
        val item = s.sessions.addExercise(session, s.exercise)
        s.sessions.logSet(item, setIndex = 0, reps = 5, seconds = null, loadKg = 10.0)

        val record = s.records.observeRecords(s.exercise.id).first().single()
        assertTrue(record.fromSession)
        assertTrue(record.id < 0)
        assertEquals("In a session", record.notes)

        s.records.deleteRecord(record.id)

        assertEquals(1, s.records.observeRecords(s.exercise.id).first().size)
        assertEquals(1, s.sessions.observeSession(session).first()!!.items.single().sets.size)
    }
}
