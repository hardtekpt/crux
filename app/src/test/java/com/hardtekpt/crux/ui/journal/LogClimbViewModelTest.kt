package com.hardtekpt.crux.ui.journal

import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.FakeClimbRepository
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
    private val preferences by lazy {
        UserPreferencesRepository(PreferenceDataStoreFactory.create { java.io.File(tmp.root, "prefs.preferences_pb") })
    }
    private val viewModel by lazy { LogClimbViewModel(repository, FIXED_CLOCK, preferences) }

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
}
