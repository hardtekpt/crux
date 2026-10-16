package com.hardtekpt.crux.ui.datasets

import com.hardtekpt.crux.data.DatasetClimb
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class GradeFilterTest {
    private fun climb(id: Long, name: String, scale: GradeScale?, grade: String?) = DatasetClimb(
        id = id,
        name = name,
        discipline = Discipline.BOULDER,
        gradeScale = scale,
        gradeIndex = grade?.let { scale!!.grades.indexOf(it) },
    )

    private val climbs = listOf(
        climb(1, "Easy", GradeScale.FONT, "5+"),
        climb(2, "Mid", GradeScale.FONT, "6B"),
        climb(3, "Hard", GradeScale.FONT, "7A"),
        climb(4, "In V", GradeScale.V_SCALE, "V4"),
        climb(5, "Ungraded", null, null),
    )

    private fun index(grade: String) = GradeScale.FONT.grades.indexOf(grade)

    @Test
    fun `the scale is the one most graded climbs are in`() {
        assertEquals(GradeScale.FONT, datasetScale(climbs))
        assertNull(datasetScale(listOf(climb(5, "Ungraded", null, null))))
    }

    @Test
    fun `no range keeps everything, including ungraded climbs`() {
        assertEquals(5, gradeFiltered(climbs, GradeRange(GradeScale.FONT)).size)
    }

    @Test
    fun `a range keeps climbs inside it, ends included, and drops ungraded ones`() {
        val kept = gradeFiltered(climbs, GradeRange(GradeScale.FONT, from = index("6A"), to = index("7A"))).map { it.name }
        // V4 is 6B+ in Font, so it stays; Easy is below the range and Ungraded has no grade.
        assertEquals(listOf("Mid", "Hard", "In V"), kept)
        assertEquals(listOf("Easy"), gradeFiltered(climbs, GradeRange(GradeScale.FONT, to = index("5+"))).map { it.name })
    }

    @Test
    fun `the range reads as a label`() {
        assertEquals("6A to 7A", GradeRange(GradeScale.FONT, index("6A"), index("7A")).label())
        assertEquals("from 6A", GradeRange(GradeScale.FONT, index("6A")).label())
        assertEquals("up to 7A", GradeRange(GradeScale.FONT, to = index("7A")).label())
        assertNull(GradeRange(GradeScale.FONT).label())
    }

    @Test
    fun `the slider spans the grades the dataset has`() {
        assertEquals(index("5+")..index("7A"), gradeBounds(climbs, GradeScale.FONT))
        assertNull(gradeBounds(listOf(climbs[0]), GradeScale.FONT))
    }
}
