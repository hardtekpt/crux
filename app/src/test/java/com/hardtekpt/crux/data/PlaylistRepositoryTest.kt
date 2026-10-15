package com.hardtekpt.crux.data

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.DatabaseFactory
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import java.io.File
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Playlists on a real in-memory database: what goes in, in which order, and how a file finds its climbs. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PlaylistRepositoryTest {
    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CruxDatabase::class.java)
        .allowMainThreadQueries()
        .build()

    @get:Rule val tmp = TemporaryFolder()

    @After
    fun close() = db.close()

    private class Setup(val playlists: OfflinePlaylistRepository, val datasets: OfflineDatasetRepository, val dataset: Dataset, val climbs: List<ClimbSource>)

    /** A dataset of three climbs, as sources a playlist can take. */
    private suspend fun TestScope.setup(): Setup {
        val dbs = CruxDatabases(
            UserPreferencesRepository(PreferenceDataStoreFactory.create { File(tmp.root, "prefs.preferences_pb") }),
            DatabaseFactory { db },
            backgroundScope,
        )
        val datasets = OfflineDatasetRepository(dbs, FIXED_CLOCK)
        val datasetId = datasets.saveDataset(Dataset(name = "Block Lab circuit"))
        val dataset = Dataset(id = datasetId, name = "Block Lab circuit")
        val sources = listOf("Blue slab" to 4, "Cave jugs" to 6, "Pinch roof" to 9).map { (name, grade) ->
            val climb = DatasetClimb(name = name, gradeScale = GradeScale.FONT, gradeIndex = grade)
            ClimbSource(climb.copy(id = datasets.saveClimb(datasetId, climb)), dataset)
        }
        return Setup(OfflinePlaylistRepository(dbs, FIXED_CLOCK), datasets, dataset, sources)
    }

    private suspend fun Setup.names(playlistId: Long): List<String> = playlists.observePlaylists().first().single { it.id == playlistId }.items.map { it.name }

    @Test
    fun `adding climbs leaves out the ones already in the list`() = runTest {
        val s = setup()
        val id = s.playlists.savePlaylist(0, "Tonight", null)

        assertEquals(2, s.playlists.addClimbs(id, listOf(s.climbs[0], s.climbs[1])))
        assertEquals(1, s.playlists.addClimbs(id, listOf(s.climbs[1], s.climbs[2])))
        assertEquals(0, s.playlists.addClimbs(id, s.climbs))

        assertEquals(listOf("Blue slab", "Cave jugs", "Pinch roof"), s.names(id))
    }

    @Test
    fun `reordering puts the items in the new order`() = runTest {
        val s = setup()
        val id = s.playlists.savePlaylist(0, "Tonight", null)
        s.playlists.addClimbs(id, s.climbs)
        val items = s.playlists.observePlaylists().first().single().items

        s.playlists.reorder(id, listOf(items[2].id, items[0].id, items[1].id))

        assertEquals(listOf("Pinch roof", "Blue slab", "Cave jugs"), s.names(id))
    }

    @Test
    fun `a removed item comes back where it was`() = runTest {
        val s = setup()
        val id = s.playlists.savePlaylist(0, "Tonight", null)
        s.playlists.addClimbs(id, s.climbs)
        val middle = s.playlists.observePlaylists().first().single().items[1]

        s.playlists.removeItem(middle.id)
        assertEquals(listOf("Blue slab", "Pinch roof"), s.names(id))

        s.playlists.restoreItem(id, middle)
        val items = s.playlists.observePlaylists().first().single().items
        assertEquals(listOf("Blue slab", "Cave jugs", "Pinch roof"), items.map { it.name })
        assertEquals(middle.id, items[1].id)
        assertTrue(items.map { it.position } == items.map { it.position }.sorted())
        assertTrue(items[1].matched)
    }

    @Test
    fun `a copy has the same climbs under its own name`() = runTest {
        val s = setup()
        val id = s.playlists.savePlaylist(0, "Tonight", "Warm up first")
        s.playlists.addClimbs(id, s.climbs)

        val copyId = s.playlists.duplicate(id)

        assertNotEquals(id, copyId)
        val lists = s.playlists.observePlaylists().first()
        val copy = lists.single { it.id == copyId }
        assertEquals("Tonight copy", copy.name)
        assertEquals(listOf("Blue slab", "Cave jugs", "Pinch roof"), copy.items.map { it.name })
        assertTrue(copy.items.map { it.id }.intersect(lists.single { it.id == id }.items.map { it.id }.toSet()).isEmpty())
        // Taking one out of the copy leaves the original whole.
        s.playlists.removeItem(copy.items.first().id)
        assertEquals(3, s.names(id).size)
    }

    @Test
    fun `an imported playlist finds the climbs it names and keeps the rest waiting`() = runTest {
        val s = setup()
        val file = PlaylistFiles.Parsed(
            name = "Tonight",
            note = null,
            items = listOf(
                PlaylistFiles.Item("Block Lab circuit", "Cave jugs", Discipline.BOULDER, "6B"),
                PlaylistFiles.Item("Old list", "Gone climb", Discipline.BOULDER, null),
                PlaylistFiles.Item(null, "Blue slab", Discipline.BOULDER, null),
            ),
        )

        val (id, matched) = s.playlists.import(file)

        assertEquals(2, matched)
        val list = s.playlists.observePlaylists().first().single { it.id == id }
        assertEquals(listOf("Cave jugs", "Gone climb", "Blue slab"), list.items.map { it.name })
        assertEquals(listOf(true, false, true), list.items.map { it.matched })
        assertFalse(list.items[1].matched)
        assertEquals(s.climbs[1].climb.id, list.items[0].climb?.id)
        // A second import of the same file is a playlist of its own.
        val (second, _) = s.playlists.import(file)
        assertEquals("Tonight (2)", s.playlists.observePlaylists().first().single { it.id == second }.name)
    }
}
