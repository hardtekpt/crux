package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.Venue
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** "+1 go" logs an attempt on the problem where it is, and Undo takes it back out. */
class QuickGoTest {
    private val climbs = FakeClimbRepository()
    private val places = FakePlaceRepository(climbs)
    private val quickGo = QuickGo(climbs, places, FIXED_CLOCK)

    @Test
    fun `a go is an attempt today on the problem, at its facility and wall`() = runTest {
        val placeId = places.savePlace(
            PlaceInput(
                name = "Block Lab",
                location = null,
                defaultAngle = 40,
                notes = null,
                sections = listOf(SectionInput(type = PlaceType.GYM, name = "Main gym"), SectionInput(type = PlaceType.BOARD, name = "Kilter")),
            ),
        )
        val kilter = places.getPlace(placeId)!!.sections[1].id
        val benchmarks = places.saveArea(placeId, 0, "Benchmarks", 40, null, kilter)
        val problemId = places.saveProblem(
            ProblemInput(
                placeId = placeId,
                areaId = benchmarks,
                name = "Yellow dyno",
                discipline = Discipline.BOULDER,
                gradeScale = GradeScale.FONT,
                gradeIndex = 10,
                tape = null,
                notes = null,
            ),
        )

        val id = quickGo.log(problemId)!!
        val go = climbs.climbs.first().single()
        assertEquals(id, go.id)
        assertEquals(AscentStyle.ATTEMPT, go.style)
        assertEquals(1, go.attempts)
        assertEquals(problemId, go.problemId)
        assertEquals(benchmarks, go.areaId)
        assertEquals(kilter, go.sectionId)
        assertEquals(Venue.BOARD, go.venue)
        assertEquals(LocalDate.now(FIXED_CLOCK), go.date)
        assertEquals("Yellow dyno", go.name)

        quickGo.undo(id)
        assertTrue(climbs.climbs.first().isEmpty())
    }
}
