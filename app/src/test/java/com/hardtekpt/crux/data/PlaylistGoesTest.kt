package com.hardtekpt.crux.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.DatabaseFactory
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** + Fell and + Sent on a session's playlist card: a go joins the climb's log in the session, or starts one. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlaylistGoesTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CruxDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @get:Rule val tmp = TemporaryFolder()

    @After
    fun close() = db.close()

    private class Setup(
        val goes: PlaylistGoes,
        val climbs: OfflineClimbRepository,
        val places: OfflinePlaceRepository,
        val session: Session,
        val source: ClimbSource,
        val placeId: Long,
        val playlists: OfflinePlaylistRepository,
    )

    /** A session at a gym, and a dataset climb "Blue slab" set by Rita. */
    private suspend fun TestScope.setup(): Setup {
        val dbs = CruxDatabases(
            UserPreferencesRepository(PreferenceDataStoreFactory.create { File(tmp.root, "prefs.preferences_pb") }),
            DatabaseFactory { db },
            backgroundScope,
        )
        val preferences = UserPreferencesRepository(PreferenceDataStoreFactory.create { File(tmp.root, "goes.preferences_pb") })
        val climbs = OfflineClimbRepository(dbs, FIXED_CLOCK)
        val places = OfflinePlaceRepository(dbs, FIXED_CLOCK)
        val placeId = places.savePlace(
            PlaceInput(name = "Block Lab", location = null, notes = null, sections = listOf(SectionInput(type = PlaceType.GYM, name = "Main gym"))),
        )
        val sectionId = places.getPlace(placeId)!!.sections.single().id
        val sessions = OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK)
        val session = sessions.observeSession(sessions.start(null, placeId, sectionId)).first()!!
        val datasets = OfflineDatasetRepository(dbs, FIXED_CLOCK)
        val dataset = Dataset(id = datasets.saveDataset(Dataset(name = "Block Lab circuit")), name = "Block Lab circuit")
        val climb = DatasetClimb(name = "Blue slab", gradeScale = GradeScale.FONT, gradeIndex = 4, setter = "Rita")
        val source = ClimbSource(climb.copy(id = datasets.saveClimb(dataset.id, climb)), dataset)
        return Setup(
            PlaylistGoes(climbs, places, preferences, FIXED_CLOCK),
            climbs,
            places,
            session,
            source,
            placeId,
            OfflinePlaylistRepository(dbs, FIXED_CLOCK),
        )
    }

    @Test
    fun `a first go that sends is a flash on a new log, tied to the dataset climb and its setter`() = runTest {
        val s = setup()

        s.goes.add(s.session, s.source, sent = true)

        val log = s.climbs.observeClimbs().first().single()
        assertEquals(AscentStyle.FLASH, log.style)
        assertEquals(1, log.attempts)
        assertEquals(1, log.sends)
        assertEquals("Blue slab", log.name)
        assertEquals(s.session.id, log.sessionId)
        assertEquals(s.placeId, log.placeId)
        val problem = s.places.climbsAt(s.placeId).single()
        assertEquals(s.source.climb.id, problem.sourceDatasetClimbId)
        assertEquals("Rita", problem.setter)
        assertEquals(log.problemId, problem.id)
    }

    @Test
    fun `only a send makes a climb sent`() = runTest {
        val s = setup()
        s.goes.add(s.session, s.source, sent = false)
        assertFalse(s.source.climb.isSentIn(s.playlists.observeSent().first()))

        s.goes.add(s.session, s.source, sent = true)
        val sent = s.playlists.observeSent().first()
        assertTrue(s.source.climb.isSentIn(sent))
        // A same-named climb of that type counts too, one of another name does not.
        assertTrue(s.source.climb.copy(id = 99).isSentIn(sent))
        assertFalse(s.source.climb.copy(id = 99, name = "Other").isSentIn(sent))
    }

    @Test
    fun `a first go that falls is an attempt`() = runTest {
        val s = setup()

        s.goes.add(s.session, s.source, sent = false)

        val log = s.climbs.observeClimbs().first().single()
        assertEquals(AscentStyle.ATTEMPT, log.style)
        assertEquals(1, log.attempts)
        assertEquals(0, log.sends)
    }

    @Test
    fun `a second go joins the same log, and a send after a fall is a redpoint`() = runTest {
        val s = setup()

        s.goes.add(s.session, s.source, sent = false)
        val second = s.goes.add(s.session, s.source, sent = true)

        val log = s.climbs.observeClimbs().first().single()
        assertEquals(AscentStyle.REDPOINT, log.style)
        assertEquals(2, log.attempts)
        assertEquals(1, log.sends)
        assertTrue(second is PlaylistGoes.Undo.Restore)
    }

    @Test
    fun `undo deletes the log a go started and puts a joined log back as it was`() = runTest {
        val s = setup()

        val first = s.goes.add(s.session, s.source, sent = false)
        val second = s.goes.add(s.session, s.source, sent = true)

        s.goes.undo(second)
        val restored = s.climbs.observeClimbs().first().single()
        assertEquals(AscentStyle.ATTEMPT, restored.style)
        assertEquals(1, restored.attempts)
        assertEquals(0, restored.sends)

        assertTrue(first is PlaylistGoes.Undo.Delete)
        s.goes.undo(first)
        assertTrue(s.climbs.observeClimbs().first().isEmpty())
    }

    @Test
    fun `a board climb's go lands on the board, not the gym the session is at`() = runTest {
        val dbs = CruxDatabases(
            UserPreferencesRepository(PreferenceDataStoreFactory.create { File(tmp.root, "board.preferences_pb") }),
            DatabaseFactory { db },
            backgroundScope,
        )
        val preferences = UserPreferencesRepository(PreferenceDataStoreFactory.create { File(tmp.root, "board-goes.preferences_pb") })
        val climbs = OfflineClimbRepository(dbs, FIXED_CLOCK)
        val places = OfflinePlaceRepository(dbs, FIXED_CLOCK)
        val datasets = OfflineDatasetRepository(dbs, FIXED_CLOCK)
        val kilter = com.hardtekpt.crux.data.model.BoardModel.KILTER_ORIGINAL
        val placeId = places.savePlace(
            PlaceInput(
                name = "Block Lab",
                location = null,
                notes = null,
                sections = listOf(
                    SectionInput(type = PlaceType.GYM, name = "Main gym"),
                    SectionInput(type = PlaceType.BOARD, name = "Kilter board", board = kilter, boardSize = "12x12"),
                ),
            ),
        )
        val (gym, board) = places.getPlace(placeId)!!.sections
        val sessions = OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK)
        val session = sessions.observeSession(sessions.start(null, placeId, gym.id)).first()!!
        val dataset = Dataset(name = "Kilter Original", board = kilter, boardSize = "12x12")
        val saved = dataset.copy(id = datasets.saveDataset(dataset))
        val climb = DatasetClimb(name = "Heel hook cave", gradeScale = GradeScale.FONT, gradeIndex = 9, catalogueUuid = "u1")

        val goes = PlaylistGoes(climbs, places, preferences, FIXED_CLOCK, datasets)
        goes.add(session, ClimbSource(climb, saved), sent = false, angle = 40)

        val log = climbs.observeClimbs().first().single()
        assertEquals(board.id, log.sectionId)
        assertEquals("40°", log.notes)
    }
}
