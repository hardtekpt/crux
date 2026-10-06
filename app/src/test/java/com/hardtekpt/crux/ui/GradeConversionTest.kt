package com.hardtekpt.crux.ui

import com.hardtekpt.crux.data.model.GradeConversion
import com.hardtekpt.crux.data.model.GradeConversion.System
import org.junit.Assert.assertEquals
import org.junit.Test

class GradeConversionTest {
    @Test
    fun `the chart anchors line up`() {
        assertEquals("5.10a", GradeConversion.convert(System.FRENCH, "6a", System.YDS))
        assertEquals("7a+", GradeConversion.convert(System.YDS, "5.12a", System.FRENCH))
        assertEquals("8b+", GradeConversion.convert(System.YDS, "5.14a", System.FRENCH))
        assertEquals("V6", GradeConversion.convert(System.FONT, "7A", System.V))
        assertEquals("7C+", GradeConversion.convert(System.V, "V10", System.FONT))
    }

    @Test
    fun `a grade spanning several steps converts to a range`() {
        assertEquals("5.11b–5.11c", GradeConversion.convert(System.FRENCH, "6c+", System.YDS))
        assertEquals("6A–6A+", GradeConversion.convert(System.V, "V3", System.FONT))
    }

    @Test
    fun `every system lists each grade once, easiest first`() {
        System.entries.forEach { system ->
            val grades = GradeConversion.grades(system)
            assertEquals(grades.distinct(), grades)
        }
        assertEquals("4a", GradeConversion.grades(System.FRENCH).first())
        assertEquals("V17", GradeConversion.grades(System.V).last())
    }
}
