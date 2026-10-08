package com.hardtekpt.crux.ui.journal

import androidx.lifecycle.SavedStateHandle
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.FakeClimbRepository
import com.hardtekpt.crux.data.FakeImageFiles
import com.hardtekpt.crux.data.FakePlaceRepository
import com.hardtekpt.crux.data.PlaceInput
import com.hardtekpt.crux.data.ProblemInput
import com.hardtekpt.crux.data.SectionInput
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.LocalScale
import com.hardtekpt.crux.data.model.NewClimb
import com.hardtekpt.crux.data.model.PlaceType
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import java.time.LocalDate
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class LogClimbViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private val repository = FakeClimbRepository()
    private val places = FakePlaceRepository(repository)
    private val imageFiles = FakeImageFiles()
    private val sessions = com.hardtekpt.crux.data.FakeSessionRepository()
    private val preferences by lazy {
        UserPreferencesRepository(mainDispatcherRule.preferencesDataStore(java.io.File(tmp.root, "prefs.preferences_pb")))
    }
    private val viewModel by lazy { viewModel() }

    private fun viewModel(vararg args: Pair<String, Long>) =
        LogClimbViewModel(SavedStateHandle(mapOf(*args)), repository, places, FIXED_CLOCK, preferences, imageFiles, sessions)

    private suspend fun boardWithProblem(): Pair<Long, Long> {
        val placeId = places.savePlace(
            PlaceInput(
                name = "Moon board",
                types = listOf(PlaceType.BOARD),
                location = null,
                defaultAngle = 40,
                notes = null,
                sections = listOf(SectionInput(type = PlaceType.BOARD, name = "Moon board", boulderScale = GradeScale.V_SCALE)),
            ),
        )
        val problemId = places.saveProblem(
            ProblemInput(
                placeId = placeId,
                areaId = null,
                name = "Hard moves",
                discipline = Discipline.BOULDER,
                gradeScale = GradeScale.V_SCALE,
                gradeIndex = 6,
                tape = null,
                notes = null,
            ),
        )
        return placeId to problemId
    }

    @Test
    fun `at a gym with two boards, the section or wall picked says where the climb was`() = runBlocking {
        val placeId = places.savePlace(
            PlaceInput(
                name = "Block Lab",
                location = null,
                defaultAngle = 40,
                notes = null,
                sections = listOf(
                    SectionInput(type = PlaceType.GYM, name = "Main gym"),
                    SectionInput(type = PlaceType.BOARD, name = "Spray wall"),
                    SectionInput(type = PlaceType.BOARD, name = "Moonboard"),
                ),
            ),
        )
        val (gym, spray, moon) = places.getPlace(placeId)!!.sections.map { it.id }
        val cave = places.saveArea(placeId, 0, "Cave", null, null, gym)
        val benchmarks = places.saveArea(placeId, 0, "Benchmarks", 40, null, moon)
        viewModel.selectPlace(placeId)
        withTimeout(5_000) { viewModel.placeDetail.first { it?.areas?.size == 2 } }
        assertEquals(gym, viewModel.draft.value.sectionId)
        assertEquals(Venue.GYM, viewModel.draft.value.venue)

        // A wall picks its section; picking another section drops a wall that isn't in it.
        viewModel.selectArea(benchmarks)
        assertEquals(moon, viewModel.draft.value.sectionId)
        assertEquals(Venue.BOARD, viewModel.draft.value.venue)
        viewModel.selectSection(spray)
        assertEquals(null, viewModel.draft.value.areaId)
        viewModel.save()
        val saved = withTimeout(5_000) { repository.climbs.first { it.isNotEmpty() } }.single()
        assertEquals(Venue.BOARD, saved.venue)
        assertEquals(spray, saved.sectionId)
        assertEquals(null, saved.areaId)
        assertTrue(cave > 0)
    }

    @Test
    fun `defaults to a boulder flash today`() {
        val draft = viewModel.draft.value
        assertEquals(Discipline.BOULDER, draft.discipline)
        assertEquals("6A", GradeScale.FONT.label(draft.gradeIndex))
        assertEquals(AscentStyle.FLASH, draft.style)
        assertEquals(LocalDate.now(FIXED_CLOCK), draft.date)
    }

    @Test
    fun `switching discipline resets the grade and drops styles that do not apply`() {
        viewModel.setDiscipline(Discipline.ROUTE)
        viewModel.setStyle(AscentStyle.ONSIGHT)
        viewModel.setDiscipline(Discipline.BOULDER)

        val draft = viewModel.draft.value
        assertEquals(GradeScale.FONT.defaultIndex, draft.gradeIndex)
        assertEquals(AscentStyle.FLASH, draft.style)
    }

    @Test
    fun `flash locks attempts to one and redpoint needs at least two`() {
        viewModel.setStyle(AscentStyle.ATTEMPT)
        viewModel.setAttempts(5)
        viewModel.setStyle(AscentStyle.FLASH)
        assertEquals(1, viewModel.draft.value.attempts)
        assertTrue(viewModel.draft.value.attemptsLocked)

        viewModel.setStyle(AscentStyle.REDPOINT)
        assertEquals(2, viewModel.draft.value.attempts)
        assertFalse(viewModel.draft.value.attemptsLocked)
    }

    @Test
    fun `new climbs use the scale chosen in settings`() = runBlocking {
        preferences.setGradeScale(GradeScale.V_SCALE)
        val draft = withTimeout(5_000) { viewModel.draft.first { it.gradeScale == GradeScale.V_SCALE } }
        assertEquals("V3", draft.gradeScale.label(draft.gradeIndex))

        viewModel.setVenue(Venue.CRAG)
        viewModel.save()

        val logged = repository.logged.single()
        assertEquals(GradeScale.V_SCALE, logged.gradeScale)
        assertEquals(Venue.CRAG, logged.venue)
    }

    @Test
    fun `a future day is rejected with a message`() {
        viewModel.setDate(LocalDate.now(FIXED_CLOCK).plusDays(1))
        viewModel.save()

        assertNotNull(viewModel.draft.value.dateError)
        assertTrue(repository.logged.isEmpty())
    }

    @Test
    fun `saving logs the climb once`() {
        viewModel.setGrade(11)
        viewModel.setName("  Yellow dyno ")
        viewModel.save()
        viewModel.save()

        assertTrue(viewModel.draft.value.saved)
        assertEquals(1, repository.logged.size)
        assertEquals(11, repository.logged.single().gradeIndex)
    }

    @Test
    fun `logging a problem copies its grade and links the place`() = runBlocking {
        val (placeId, problemId) = boardWithProblem()
        val vm = viewModel("problemId" to problemId)
        val draft = withTimeout(5_000) { vm.draft.first { it.problemId == problemId } }

        assertEquals(placeId, draft.placeId)
        assertEquals(GradeScale.V_SCALE, draft.gradeScale)
        assertEquals(6, draft.gradeIndex)
        assertEquals("Hard moves", draft.name)
        assertEquals(40, draft.angle)

        vm.setStyle(AscentStyle.ATTEMPT)
        vm.save()
        withTimeout(5_000) { vm.draft.first { it.saved } }

        val logged = repository.logged.single()
        assertEquals(placeId, logged.placeId)
        assertEquals(problemId, logged.problemId)
        assertEquals(Venue.BOARD, logged.venue)
        assertEquals("Moon board", logged.place)
    }

    @Test
    fun `a new climb can be saved as a problem at the place`() = runBlocking {
        val (placeId, _) = boardWithProblem()
        val vm = viewModel("placeId" to placeId)
        withTimeout(5_000) { vm.draft.first { it.placeId == placeId } }
        vm.setSaveAsProblem(true)
        vm.save()
        assertNotNull(vm.draft.value.nameError)

        vm.setName(" Crimp ladder ")
        vm.save()
        withTimeout(5_000) { vm.draft.first { it.saved } }

        val problem = places.problems.value.single { it.name == "Crimp ladder" }
        assertEquals(problem.id, repository.logged.single().problemId)
    }

    @Test
    fun `editing a climb keeps its id and delete removes it`() = runBlocking {
        val id = repository.logClimb(
            NewClimb(
                Discipline.ROUTE, GradeScale.FRENCH, 14, AscentStyle.REDPOINT, 3, Venue.CRAG,
                LocalDate.now(
                    FIXED_CLOCK,
                ).minusDays(2),
                "Pilastro", "Arco", null,
            ),
        )
        val vm = viewModel("climbId" to id)
        val draft = withTimeout(5_000) { vm.draft.first { it.name == "Pilastro" } }
        assertTrue(draft.isEditing)
        assertEquals(Discipline.ROUTE, draft.discipline)
        assertEquals(14, draft.gradeIndex)

        vm.setAttempts(5)
        vm.save()
        withTimeout(5_000) { vm.draft.first { it.saved } }
        assertEquals(1, repository.climbs.value.size)
        assertEquals(5, repository.climbs.value.single().attempts)

        val again = viewModel("climbId" to id)
        withTimeout(5_000) { again.draft.first { it.name == "Pilastro" } }
        again.requestDelete()
        again.confirmDelete()
        withTimeout(5_000) { again.draft.first { it.saved } }
        assertTrue(repository.climbs.value.isEmpty())
    }

    @Test
    fun `the next climb starts at the last climb's place, facility and wall`() = runBlocking {
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
        val first = viewModel("placeId" to placeId)
        withTimeout(5_000) { first.placeDetail.first { it?.areas?.size == 1 } }
        first.selectArea(benchmarks)
        first.save()
        withTimeout(5_000) { first.draft.first { it.saved } }

        // A new climb with nothing picked yet.
        val next = viewModel()
        val draft = withTimeout(5_000) { next.draft.first { it.placeId == placeId && it.areaId != null } }
        assertEquals(kilter, draft.sectionId)
        assertEquals(benchmarks, draft.areaId)
        assertEquals(Venue.BOARD, draft.venue)
    }

    @Test
    fun `each part of a place grades in its own scale`() = runBlocking {
        val placeId = places.savePlace(
            PlaceInput(
                name = "Block Lab",
                location = null,
                defaultAngle = 40,
                notes = null,
                sections = listOf(
                    SectionInput(type = PlaceType.GYM, name = "Main gym", boulderScale = GradeScale.FONT),
                    SectionInput(type = PlaceType.BOARD, name = "Kilter", boulderScale = GradeScale.V_SCALE),
                ),
            ),
        )
        val kilter = places.getPlace(placeId)!!.sections[1].id
        val vm = viewModel("placeId" to placeId)
        withTimeout(5_000) { vm.draft.first { it.placeId == placeId && it.gradeScale == GradeScale.FONT } }
        withTimeout(5_000) { vm.placeDetail.first { it?.place?.id == placeId } }
        vm.selectSection(kilter)
        assertEquals(GradeScale.V_SCALE, vm.draft.value.gradeScale)
    }

    @Test
    fun `a place with local colour grades logs the colour, not a converted grade`() = runBlocking {
        val placeId = places.savePlace(
            PlaceInput(
                name = "Tape Gym",
                types = listOf(PlaceType.GYM),
                location = null,
                defaultAngle = null,
                notes = null,
                sections = listOf(
                    SectionInput(type = PlaceType.GYM, name = "Gym", boulderScale = GradeScale.LOCAL_BOULDER, localScale = LocalScale.DEFAULT_COLOURS),
                ),
            ),
        )
        val vm = viewModel("placeId" to placeId)
        val draft = withTimeout(5_000) { vm.draft.first { it.placeId == placeId && it.local != null } }
        assertEquals(listOf("Yellow", "Green", "Blue", "Purple", "Red", "Black"), draft.system.labels)

        vm.setGrade(2)
        vm.save()
        withTimeout(5_000) { vm.draft.first { it.saved } }

        val logged = repository.logged.single()
        assertEquals(GradeScale.LOCAL_BOULDER, logged.gradeScale)
        assertEquals(2, logged.gradeIndex)
        assertEquals("Blue", logged.gradeLabel)
        assertEquals("Blue", repository.climbs.value.single().grade)
    }

    @Test
    fun `a climb logged while a session runs joins it, at the session's place`() = runBlocking {
        val placeId = places.savePlace(
            PlaceInput(name = "Block Lab", location = null, defaultAngle = null, notes = null),
        )
        sessions.runningSession = com.hardtekpt.crux.data.RunningSession(id = 7, placeId = placeId, sectionId = null)
        val vm = viewModel()
        withTimeout(5_000) { vm.draft.first { it.placeId == placeId } }
        vm.save()
        val saved = withTimeout(5_000) { repository.climbs.first { it.isNotEmpty() } }.single()
        assertEquals(7L, saved.sessionId)
        assertEquals(placeId, saved.placeId)
    }
}
