package com.hardtekpt.crux.data.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BoardAngleNoteTest {
    @Test
    fun theAngleIsTheFirstLineThenTheNote() {
        assertEquals("40°\nHeel hook", BoardAngleNote.write(40, "Heel hook"))
        assertEquals("40°", BoardAngleNote.write(40, ""))
        assertEquals("40°", BoardAngleNote.write(40, "  "))
        assertEquals("Heel hook", BoardAngleNote.write(null, "Heel hook"))
        assertEquals("", BoardAngleNote.write(null, ""))
    }

    @Test
    fun readingLiftsTheFirstLineIntoTheAngle() {
        assertEquals(BoardAngleNote.Parsed(40, "Heel hook"), BoardAngleNote.read("40°\nHeel hook"))
        assertEquals(BoardAngleNote.Parsed(45, ""), BoardAngleNote.read("45°"))
        assertEquals(BoardAngleNote.Parsed(25, "two\nlines"), BoardAngleNote.read(" 25 ° \ntwo\nlines"))
    }

    @Test
    fun aNoteWithoutAnAngleComesBackWhole() {
        assertEquals(BoardAngleNote.Parsed(null, "Pumped at 40 moves"), BoardAngleNote.read("Pumped at 40 moves"))
        assertEquals(BoardAngleNote.Parsed(null, ""), BoardAngleNote.read(null))
        // A degree sign later in the note is just text.
        assertNull(BoardAngleNote.read("Tried it\n40°").angle)
        // Three digits is no board angle.
        assertNull(BoardAngleNote.read("140°").angle)
    }

    @Test
    fun theMigrationsOldLastLineIsReadToo() {
        val parsed = BoardAngleNote.read("Heel hook\nBoard angle 40°")
        assertEquals(40, parsed.angle)
        assertEquals("Heel hook", parsed.body)
        assertTrue(parsed.legacy)
        assertEquals(BoardAngleNote.Parsed(25, "", legacy = true), BoardAngleNote.read("Board angle 25°"))
    }

    @Test
    fun anOldFormLogKeepsItsFormUntilTheAngleChanges() {
        val loaded = BoardAngleNote.read("Heel hook\nBoard angle 40°")
        assertEquals("Heel hook\nBoard angle 40°", BoardAngleNote.write(40, "Heel hook", loaded))
        assertEquals("45°\nHeel hook", BoardAngleNote.write(45, "Heel hook", loaded))
        assertEquals("Heel hook", BoardAngleNote.write(null, "Heel hook", loaded))
        val fresh = BoardAngleNote.read("40°\nHeel hook")
        assertFalse(fresh.legacy)
        assertEquals("40°\nHeel hook", BoardAngleNote.write(40, "Heel hook", fresh))
    }

    @Test
    fun aBoardsLabelNamesItsSizeAndHolds() {
        assertEquals("Kilter Original 12×12", boardLabel(BoardModel.KILTER_ORIGINAL, "12x12", null))
        assertEquals("Kilter Homewall 10×12 · Full ride", boardLabel(BoardModel.KILTER_HOMEWALL, "10x12", BoardHolds.FULL_RIDE))
        assertEquals("MoonBoard 2016", boardLabel(BoardModel.MOON_2016, "12x12", BoardHolds.MAINLINE))
        assertNull(boardLabel(null, null, null))
        // Keys never change: the database and backups keep them.
        assertEquals(BoardModel.MOON_MINI_2025, BoardModel.fromKey("moon_mini_2025"))
        assertNull(BoardModel.fromKey("tension_2"))
    }
}
