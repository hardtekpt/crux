package com.hardtekpt.crux.data

import android.database.sqlite.SQLiteDatabase
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.DatabaseFactory
import com.hardtekpt.crux.data.model.BoardHolds
import com.hardtekpt.crux.data.model.BoardModel
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import java.io.File
import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Reads a small snapshot shaped like Boardsesh's (board_climbs and board_climb_stats, WITHOUT ROWID). */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class CatalogueImporterTest {
    @get:Rule val tmp = TemporaryFolder()

    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()
    private val main = Room.inMemoryDatabaseBuilder(context, CruxDatabase::class.java).allowMainThreadQueries().build()
    private val catalogueDb = Room.inMemoryDatabaseBuilder(context, CatalogueDatabase::class.java).allowMainThreadQueries().build()
    private val clock = Clock.fixed(Instant.parse("2026-10-15T09:00:00Z"), ZoneOffset.UTC)

    @After
    fun close() {
        main.close()
        catalogueDb.close()
    }

    private fun TestScope.setUp(): Triple<CatalogueImporter, DatasetRepository, CatalogueRepository> {
        val preferences = UserPreferencesRepository(PreferenceDataStoreFactory.create(scope = backgroundScope) { File(tmp.root, "prefs.preferences_pb") })
        val dbs = CruxDatabases(preferences, DatabaseFactory { main }, backgroundScope)
        val datasets = OfflineDatasetRepository(dbs, clock)
        val catalogues = CatalogueDatabases(context).also { it.override = catalogueDb }
        return Triple(CatalogueImporter(context, catalogues, datasets, backgroundScope), datasets, CatalogueRepository(catalogues))
    }

    /** A Kilter Homewall snapshot: three listed climbs for different sizes, one unlisted. */
    private fun snapshot(): File {
        val file = File(tmp.root, "kilter-${System.nanoTime()}.db")
        SQLiteDatabase.openOrCreateDatabase(file, null).use { db ->
            db.execSQL(
                "CREATE TABLE board_climbs (uuid TEXT PRIMARY KEY, board_type TEXT, layout_id INTEGER, setter_username TEXT, name TEXT, description TEXT, " +
                    "angle INTEGER, frames TEXT, is_draft INTEGER, is_listed INTEGER, is_hidden INTEGER, created_at TEXT, compatible_size_ids TEXT) WITHOUT ROWID",
            )
            db.execSQL(
                "CREATE TABLE board_climb_stats (board_type TEXT NOT NULL, climb_uuid TEXT NOT NULL, angle INTEGER NOT NULL, display_difficulty REAL, " +
                    "benchmark_difficulty REAL, ascensionist_count INTEGER, difficulty_average REAL, quality_average REAL, " +
                    "PRIMARY KEY (board_type, climb_uuid, angle)) WITHOUT ROWID",
            )
            fun climb(uuid: String, name: String, sizes: String, listed: Int = 1) = db.execSQL(
                "INSERT INTO board_climbs VALUES (?, 'kilter', 8, 'ana', ?, '', 40, 'p4131r42p4220r43', 0, ?, 0, '2025-01-01', ?)",
                arrayOf<Any>(uuid, name, listed, sizes),
            )
            climb("A", "Roof to pinch", "[20,25,26]")
            climb("B", "Small wall only", "[17,18]")
            climb("C", "Big crimps", "[25,26,29]")
            climb("D", "Draft thing", "[25]", listed = 0)
            db.execSQL("INSERT INTO board_climb_stats VALUES ('kilter', 'A', 40, 20.0, 20.0, 1200, 20.0, 2.9)")
            db.execSQL("INSERT INTO board_climb_stats VALUES ('kilter', 'A', 30, 18.2, NULL, 40, 18.2, 2.5)")
            db.execSQL("INSERT INTO board_climb_stats VALUES ('kilter', 'B', 40, 16.0, NULL, 5, 16.0, 2.0)")
            db.execSQL("INSERT INTO board_climb_stats VALUES ('kilter', 'C', 40, 24.0, NULL, 30, 24.0, 3.0)")
        }
        return file
    }

    @Test
    fun aKilterSnapshotKeepsOnlyClimbsForYourSize() = runTest {
        val (importer, datasets, catalogue) = setUp()
        val inspection = importer.inspect(snapshot(), "kilter.db")
        assertEquals(BoardModel.KILTER_HOMEWALL, inspection.board)
        assertEquals(4, inspection.climbs)

        val (id, kept) = importer.copy(snapshot(), inspection, "Home Kilter", "10x12", BoardHolds.MAINLINE)
        assertEquals(2, kept)
        val dataset = datasets.observeDatasets().first().single { it.id == id }
        assertEquals("boardsesh:kilter:8:26", dataset.catalogueKey)
        assertEquals(2, dataset.size)
        assertTrue(dataset.isCatalogue)

        // Most sent first at 40°; grades are the board's, as Font.
        val rows = catalogue.search(dataset.catalogueKey!!, 40)
        assertEquals(listOf("Roof to pinch", "Big crimps"), rows.map { it.name })
        assertEquals(listOf(30, 40), catalogue.angles(dataset.catalogueKey!!))
        val climb = catalogue.climb(dataset.catalogueKey!!, "A")!!
        assertEquals("6C", GradeScale.FONT.label(climb.fontIndex(40)!!))
        assertEquals("6B", GradeScale.FONT.label(climb.fontIndex(30)!!))
        assertEquals("A", climb.asDatasetClimb(40).catalogueUuid)
        // Benchmarks only, and a search by setter.
        assertEquals(listOf("Roof to pinch"), catalogue.search(dataset.catalogueKey!!, 40, benchmarks = true).map { it.name })
        assertEquals(2, catalogue.search(dataset.catalogueKey!!, 40, query = "ana").size)

        // Importing again replaces it rather than adding a second catalogue.
        importer.copy(snapshot(), inspection, "Home Kilter", "10x12", BoardHolds.MAINLINE)
        assertEquals(1, datasets.observeDatasets().first().count { it.isCatalogue })
    }

    @Test
    fun aFileThatIsNotASnapshotIsRefused() = runTest {
        val (importer) = setUp()
        val file = tmp.newFile("notes.db").also { it.delete() }
        SQLiteDatabase.openOrCreateDatabase(file, null).use { it.execSQL("CREATE TABLE notes (text TEXT)") }
        val error = runCatching { importer.inspect(file, "notes.db") }.exceptionOrNull()
        assertEquals(CatalogueImporter.NOT_A_SNAPSHOT, error?.message)
    }

    @Test
    fun boardDifficultiesReadAsFontGrades() {
        fun font(d: Double) = GradeScale.FONT.label(BoardseshBoards.fontIndex(d))
        assertEquals("6A", font(16.0))
        assertEquals("7A", font(22.0))
        assertEquals("7B+", font(24.6))
        assertEquals("4", font(10.0))
        assertEquals("5+", font(15.0))
        assertTrue(22.0 in BoardseshBoards.difficultyRange(GradeScale.FONT.grades.indexOf("7A")))
        assertEquals(
            listOf(25, 26),
            listOf(
                BoardseshBoards.sizeIds(BoardModel.KILTER_HOMEWALL, "10x12", BoardHolds.FULL_RIDE),
                BoardseshBoards.sizeIds(BoardModel.KILTER_HOMEWALL, "10x12", BoardHolds.MAINLINE),
            ).flatten(),
        )
        assertNull(BoardseshBoards.model("tension", 9))
        assertTrue(CatalogueImporter.fits("[20,25,26]", listOf(26)))
    }
}
