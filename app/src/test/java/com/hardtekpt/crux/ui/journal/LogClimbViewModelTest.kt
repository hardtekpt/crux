package com.hardtekpt.crux.ui.journal

import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.FakeClimbRepository
import com.hardtekpt.crux.data.FakeImageFiles
import com.hardtekpt.crux.data.FakePlaceRepository
import com.hardtekpt.crux.data.PlaceInput
import com.hardtekpt.crux.data.ProblemInput
import com.hardtekpt.crux.data.model.NewClimb
import com.hardtekpt.crux.data.model.LocalScale
import com.hardtekpt.crux.data.model.PlaceType
import androidx.lifecycle.SavedStateHandle
import com.hardtekpt.crux.data.model.AscentStyle
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import com.hardtekpt.crux.data.model.Venue
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.rules.TemporaryFolder
import org.junit.Test
import java.time.LocalDate

class LogClimbViewModelTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private val repository = FakeClimbRepository()
    private val places = FakePlaceRepository(repository)
    private val imageFiles = FakeImageFiles()
    private val preferences by lazy {
        UserPreferencesRepository(PreferenceDataStoreFactory.create { java.io.File(tmp.root, "prefs.preferences_pb") })
    }
    private val viewModel by lazy { viewModel() }

    private fun viewModel(vararg args: Pair<String, Long>) =
        LogClimbViewModel(SavedStateHandle(mapOf(*args)), repository, places, FIXED_CLOCK, preferences, imageFiles)

    private suspend fun boardWithProblem(): Pair<Long, Long> {
        val placeId = places.savePlace(
            PlaceInput(name = "Moon board", types = listOf(PlaceType.BOARD), location = null, boulderScale = GradeScale.V_SCALE, routeScale = null, defaultAngle = 40, notes = null),
        )
        val problemId = places.saveProblem(
            ProblemInput(placeId = placeId, areaId = null, name = "Hard moves", discipline = Discipline.BOULDER, gradeScale = GradeScale.V_SCALE, gradeIndex = 6, tape = null, notes = null),
        )
        return placeId to problemId
    }

    @Test
    fun `at a gym with a board, the wall picked says which kind of climb it was`() = runBlocking {
        val placeId = places.savePlace(
            PlaceInput(name = "Block Lab", types = listOf(PlaceType.GYM, PlaceType.BOARD), location = null, boulderScale = null, routeScale = null, defaultAngle = 40, notes = null),
        )
        val cave = places.saveArea(placeId, 0, "Cave", null, null, PlaceType.GYM)
        val kilter = places.saveArea(placeId, 0, "Kilter", 40, null, PlaceType.BOARD)
        viewModel.selectPlace(placeId)
        withTimeout(5_000) { viewModel.placeDetail.first { it?.areas?.size == 2 } }
        assertEquals(Venue.GYM, viewModel.draft.value.venue)

        viewModel.selectArea(kilter)
        assertEquals(Venue.BOARD, viewModel.draft.value.venue)
        // Switching back to the gym drops the board's wall.
        viewModel.selectPlaceType(PlaceType.GYM)
        assertEquals(Venue.GYM, viewModel.draft.value.venue)
        assertEquals(null, viewModel.draft.value.areaId)
        viewModel.selectArea(cave)
        viewModel.save()
        val saved = withTimeout(5_000) { repository.climbs.first { it.isNotEmpty() } }.single()
        assertEquals(Venue.GYM, saved.venue)
        assertEquals(cave, saved.areaId)
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
            NewClimb(Discipline.ROUTE, GradeScale.FRENCH, 14, AscentStyle.REDPOINT, 3, Venue.CRAG, LocalDate.now(FIXED_CLOCK).minusDays(2), "Pilastro", "Arco", null),
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
    fun `a place with local colour grades logs the colour, not a converted grade`() = runBlocking {
        val placeId = places.savePlace(
            PlaceInput(
                name = "Tape Gym", types = listOf(PlaceType.GYM), location = null,
                boulderScale = GradeScale.LOCAL_BOULDER, routeScale = null, defaultAngle = null, notes = null,
                localScale = LocalScale.DEFAULT_COLOURS,
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

}
