package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.model.BoardHolds
import com.hardtekpt.crux.data.model.BoardModel
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.Section
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFilesTest {
    private val garage = DatasetEntity(id = 1, name = "Garage board", createdAtMillis = 0, updatedAtMillis = 0)
    private val circuit = DatasetEntity(id = 2, name = "Block Lab circuit", createdAtMillis = 0, updatedAtMillis = 0)
    private val climbs = listOf(
        DatasetClimbEntity(id = 10, datasetId = 1, name = "Roof to pinch", gradeScale = GradeScale.V_SCALE, gradeIndex = 6, createdAtMillis = 0),
        DatasetClimbEntity(id = 20, datasetId = 2, name = "Roof to pinch", gradeScale = GradeScale.FONT, gradeIndex = 9, createdAtMillis = 0),
        DatasetClimbEntity(id = 21, datasetId = 2, name = "Blue slab", gradeScale = GradeScale.FONT, gradeIndex = 4, createdAtMillis = 0),
    )

    @Test
    fun aPlaylistFileReadsBack() {
        val playlist = Playlist(
            id = 1,
            name = "Tonight",
            note = "Warm up first",
            items = listOf(
                PlaylistItem(1, "Blue slab", Discipline.BOULDER, GradeScale.FONT, 4, "Block Lab circuit"),
                PlaylistItem(2, "Roof to pinch", Discipline.BOULDER, GradeScale.V_SCALE, 6, "Garage board"),
            ),
        )
        val text = PlaylistFiles.write(playlist, "2026-10-15T09:00:00Z")
        assertTrue(PlaylistFiles.isPlaylist(text))
        val parsed = PlaylistFiles.parse(text)
        assertEquals("Tonight", parsed.name)
        assertEquals("Warm up first", parsed.note)
        assertEquals(
            listOf(
                PlaylistFiles.Item("Block Lab circuit", "Blue slab", Discipline.BOULDER, "5+"),
                PlaylistFiles.Item("Garage board", "Roof to pinch", Discipline.BOULDER, "V5"),
            ),
            parsed.items,
        )
    }

    @Test
    fun itemsMatchByDatasetThenByNameThenByGrade() {
        val datasets = listOf(garage, circuit)
        // Its dataset decides between two of the same name.
        assertEquals(10L, PlaylistFiles.match(PlaylistFiles.Item("garage board", "roof to pinch", Discipline.BOULDER, null), datasets, climbs)?.id)
        // A dataset that isn't here: the only climb of that name.
        assertEquals(21L, PlaylistFiles.match(PlaylistFiles.Item("Old list", "Blue slab", Discipline.BOULDER, null), datasets, climbs)?.id)
        // Two of that name and no dataset: the grade decides.
        assertEquals(20L, PlaylistFiles.match(PlaylistFiles.Item(null, "Roof to pinch", Discipline.BOULDER, "6C"), datasets, climbs)?.id)
        assertNull(PlaylistFiles.match(PlaylistFiles.Item(null, "Roof to pinch", Discipline.BOULDER, null), datasets, climbs))
        assertNull(PlaylistFiles.match(PlaylistFiles.Item(null, "Blue slab", Discipline.ROUTE, null), datasets, climbs))
    }

    @Test(expected = DatasetFiles.Unreadable::class)
    fun aDatasetFileIsNotAPlaylist() {
        PlaylistFiles.parse(DatasetFiles.writeCrux(Dataset(name = "Spray"), "x"))
    }

    @Test
    fun gradesConvertToTheSectionsScale() {
        // V5 is 6C in Font (the easier end of 6C to 6C+), and back.
        assertEquals(GradeScale.FONT.grades.indexOf("6C"), convertGrade(GradeScale.V_SCALE, GradeScale.V_SCALE.grades.indexOf("V5"), GradeScale.FONT))
        assertEquals(GradeScale.V_SCALE.grades.indexOf("V5"), convertGrade(GradeScale.FONT, GradeScale.FONT.grades.indexOf("6C+"), GradeScale.V_SCALE))
        assertEquals(3, convertGrade(GradeScale.FONT, 3, GradeScale.FONT))
        // Routes and boulders don't mix; local grades have nothing to convert to.
        assertNull(convertGrade(GradeScale.FONT, 5, GradeScale.FRENCH))
        assertNull(convertGrade(GradeScale.FONT, 5, GradeScale.LOCAL_BOULDER))
    }

    @Test
    fun aSectionUsesDatasetsForItsBoardAndThoseLinkedToIt() {
        val kilter12 = Dataset(id = 1, name = "Kilter warm-ups", board = BoardModel.KILTER_ORIGINAL, boardSize = "12x12")
        val kilter8 = Dataset(id = 2, name = "Small Kilter", board = BoardModel.KILTER_ORIGINAL, boardSize = "8x12")
        val homewall = Dataset(id = 3, name = "Homewall", board = BoardModel.KILTER_HOMEWALL, boardSize = "10x12", boardHolds = BoardHolds.MAINLINE)
        val spray = Dataset(id = 4, name = "Spray", customBoard = true)
        val circuit = Dataset(id = 5, name = "Circuit")
        val all = listOf(kilter12, kilter8, homewall, spray, circuit)
        val board = Section(1, 1, PlaceType.BOARD, "Kilter", board = BoardModel.KILTER_ORIGINAL, boardSize = "12x12")
        assertEquals(listOf(kilter12), datasetsFor(board, all, emptySet()))
        assertEquals(listOf(kilter12, circuit), datasetsFor(board, all, setOf(5L, 1L)))
        val wall = Section(2, 1, PlaceType.BOARD, "Home", board = BoardModel.KILTER_HOMEWALL, boardSize = "10x12", boardHolds = BoardHolds.FULL_RIDE)
        assertEquals(emptyList<Dataset>(), datasetsFor(wall, all, emptySet()))
        // A custom board matches nothing by itself; a gym only what's linked.
        val custom = Section(3, 1, PlaceType.BOARD, "Spray wall")
        assertEquals(listOf(spray), datasetsFor(custom, all, setOf(4L)))
        val gym = Section(4, 1, PlaceType.GYM, "Main gym", board = BoardModel.KILTER_ORIGINAL, boardSize = "12x12")
        assertEquals(emptyList<Dataset>(), datasetsFor(gym, all, emptySet()))
    }
}
