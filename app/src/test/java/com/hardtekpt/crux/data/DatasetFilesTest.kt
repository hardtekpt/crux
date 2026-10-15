package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.model.BoardHolds
import com.hardtekpt.crux.data.model.BoardModel
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class DatasetFilesTest {
    private val font = { _: Discipline -> GradeScale.FONT }

    @Test
    fun aCsvReadsItsColumnsInAnyOrderAndCase() {
        val parsed = DatasetFiles.parseCsv("Grade,NAME,note,Setter,colour\n6A,Blue slab,warm-up,Rita,blue\n6B+,Cave crimps,,,\n")
        assertEquals(listOf("Blue slab", "Cave crimps"), parsed.climbs.map { it.name })
        assertEquals("6A", parsed.climbs[0].grade)
        assertEquals("Rita", parsed.climbs[0].setter)
        assertEquals("warm-up", parsed.climbs[0].note)
        assertNull(parsed.climbs[1].setter)
        assertEquals(listOf("colour"), parsed.ignoredColumns)
    }

    @Test
    fun semicolonsQuotesAndRoutesAreRead() {
        val parsed = DatasetFiles.parseCsv("name;grade;type;note\n\"Roof; to pinch\";6c;route;\"a \"\"quoted\"\" note\nover two lines\"\r\n")
        val climb = parsed.climbs.single()
        assertEquals("Roof; to pinch", climb.name)
        assertEquals(Discipline.ROUTE, climb.discipline)
        assertEquals("a \"quoted\" note\nover two lines", climb.note)
    }

    @Test
    fun rowsWithoutANameAreLeftOutAndCounted() {
        val parsed = DatasetFiles.parseCsv("name,grade\n,6A\nBlue slab,6A\n  ,7A\n")
        assertEquals(1, parsed.climbs.size)
        assertEquals(2, parsed.skipped)
    }

    @Test(expected = DatasetFiles.Unreadable::class)
    fun aCsvWithoutANameColumnIsRefused() {
        DatasetFiles.parseCsv("grade,note\n6A,x\n")
    }

    @Test
    fun anUnknownGradeKeepsTheClimbUngradedWithTheTextInItsNote() {
        val raw = DatasetFiles.parseCsv("name,grade,note\nPink circuit 7,pink,fun\nBlue slab,6a+,\n").climbs
        val climbs = DatasetFiles.resolve(raw, font)
        assertNull(climbs[0].gradeIndex)
        assertEquals("fun\nGrade in file: pink", climbs[0].note)
        // Case doesn't matter.
        assertEquals("6A+", climbs[1].gradeLabel)
        assertEquals(1, DatasetFiles.unknownGrades(raw, font).size)
    }

    @Test
    fun aCsvExportReadsBackTheSame() {
        val dataset = Dataset(
            name = "Garage board",
            boulderScale = GradeScale.FONT,
            climbs = listOf(
                DatasetClimb(name = "Roof, to pinch", gradeScale = GradeScale.FONT, gradeIndex = 9, setter = "Ana", note = "start \"low\""),
                DatasetClimb(name = "Sit start project"),
            ),
        )
        val back = DatasetFiles.resolve(DatasetFiles.parseCsv(DatasetFiles.writeCsv(dataset)).climbs, font)
        assertEquals(dataset.climbs.map { it.copy(id = 0) }, back)
    }

    @Test
    fun aCruxFileKeepsTheBoardAndScalesAndReadsBack() {
        val dataset = Dataset(
            name = "Home wall",
            board = BoardModel.KILTER_HOMEWALL,
            boardSize = "10x12",
            boardHolds = BoardHolds.MAINLINE,
            boulderScale = GradeScale.V_SCALE,
            routeScale = GradeScale.FRENCH,
            climbs = listOf(DatasetClimb(name = "Roof to pinch", gradeScale = GradeScale.V_SCALE, gradeIndex = 6, setter = "Ana")),
        )
        val text = DatasetFiles.writeCrux(dataset, "2026-10-14T21:55:00Z")
        assertTrue("\"format\": \"crux-dataset\"" in text)
        val parsed = DatasetFiles.parse("home.crux-dataset.json", text)
        assertEquals(dataset.copy(climbs = emptyList()), parsed.dataset)
        val climbs = DatasetFiles.resolve(parsed.climbs) { d -> parsed.dataset!!.scaleFor(d)!! }
        assertEquals(dataset.climbs, climbs)
    }

    @Test
    fun aCustomBoardStaysACustomBoard() {
        val parsed = DatasetFiles.parseCrux(DatasetFiles.writeCrux(Dataset(name = "Spray", customBoard = true), "x"))
        assertTrue(parsed.dataset!!.customBoard)
        assertEquals("Custom board", parsed.dataset.boardLabel)
    }

    @Test(expected = DatasetFiles.Unreadable::class)
    fun aCruxFileFromANewerVersionIsRefused() {
        DatasetFiles.parseCrux("""{"format":"crux-dataset","version":2,"dataset":{"name":"x"}}""")
    }

    @Test(expected = DatasetFiles.Unreadable::class)
    fun aBackupIsNotADataset() {
        DatasetFiles.parseCrux("""{"format":"crux-backup","version":1,"dataset":{"name":"x"}}""")
    }
}
