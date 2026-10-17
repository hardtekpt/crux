package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.model.GradeScale
import org.junit.Assert.assertEquals
import org.junit.Test

/** Filters add up, sorts chain, and the labels the chips read. */
class BrowseEngineTest {
    private val climbs = listOf(
        BrowseCandidate("c1", "Heel hook cave", "kim", grade = 9, ascents = 240, rating = 3.0, benchmark = true, goes = 5),
        BrowseCandidate("c2", "Pinch power", "rui", grade = 9, ascents = 215, rating = 2.8),
        BrowseCandidate("c3", "Crimp ladder", "ana", grade = 10, ascents = 270, rating = 3.1, goes = 2, sent = true),
        BrowseCandidate("c4", "Compression", "rui", grade = 11, ascents = 290, rating = 2.4, goes = 3, tonight = 3),
        BrowseCandidate("c5", "Warm-up jugs", "kim", grade = 5, ascents = 900, rating = 2.0),
        BrowseCandidate("c6", "Unrated", null, grade = null, ascents = 3),
    )

    private fun names(q: BrowseQuery) = BrowseEngine.apply(climbs, q).map { it.name }

    @Test
    fun `filters add up`() {
        val q = BrowseQuery(
            filters = BrowseFilters(gradeScale = GradeScale.FONT, gradeFrom = 9, gradeTo = 11, you = setOf(YouState.NEW, YouState.TRIED), minRating = 2.5),
            sorts = listOf(SortLine(SortKey.NAME)),
        )
        assertEquals(listOf("Heel hook cave", "Pinch power"), names(q))
    }

    @Test
    fun `setters, ascents and benchmarks`() {
        assertEquals(listOf("Compression", "Pinch power"), names(BrowseQuery(BrowseFilters(setters = setOf("rui")), listOf(SortLine(SortKey.NAME)))))
        assertEquals(listOf("Warm-up jugs"), names(BrowseQuery(BrowseFilters(minAscents = 500))))
        assertEquals(listOf("Heel hook cave"), names(BrowseQuery(BrowseFilters(benchmarks = true))))
    }

    @Test
    fun `sorts chain, the next line breaking ties`() {
        val gradeThenPopular = listOf(SortLine(SortKey.GRADE), SortLine(SortKey.POPULAR))
        assertEquals(
            listOf("Warm-up jugs", "Heel hook cave", "Pinch power", "Crimp ladder", "Compression", "Unrated"),
            names(BrowseQuery(sorts = gradeThenPopular)),
        )
        val hardestFirst = listOf(SortLine(SortKey.GRADE, descending = true), SortLine(SortKey.NAME))
        assertEquals(
            listOf("Compression", "Crimp ladder", "Heel hook cave", "Pinch power", "Warm-up jugs", "Unrated"),
            names(BrowseQuery(sorts = hardestFirst)),
        )
        assertEquals("Heel hook cave", names(BrowseQuery(sorts = listOf(SortLine(SortKey.GOES)))).first())
    }

    @Test
    fun `chips read their values`() {
        assertEquals("Not sent", setOf(YouState.NEW, YouState.TRIED).label())
        assertEquals(null, YouState.entries.toSet().label())
        assertEquals("Sent", setOf(YouState.SENT).label())
        assertEquals("Grade, Popular", listOf(SortLine(SortKey.GRADE), SortLine(SortKey.POPULAR)).label())
        assertEquals(3, BrowseFilters(gradeFrom = 1, minRating = 2.0, benchmarks = true).count)
    }

    @Test
    fun `grade presets follow your hardest send`() {
        assertEquals(9..11, GradePreset.NEAR_MAX.range(10, 24))
        assertEquals(6..8, GradePreset.WARM_UP.range(10, 24))
        assertEquals(0..9, GradePreset.BELOW_MAX.range(10, 24))
        assertEquals(10, BrowseEngine.maxSent(climbs))
        assertEquals(mapOf(9 to 2, 10 to 1, 11 to 1, 5 to 1), BrowseEngine.gradeCounts(climbs))
        assertEquals(listOf("kim" to 2, "rui" to 2, "ana" to 1), BrowseEngine.setters(climbs))
    }
}
