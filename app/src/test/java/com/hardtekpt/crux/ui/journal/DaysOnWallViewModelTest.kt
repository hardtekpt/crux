package com.hardtekpt.crux.ui.journal

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.OfflineClimbRepository
import com.hardtekpt.crux.data.OfflineNoteRepository
import com.hardtekpt.crux.data.OfflineRecordRepository
import com.hardtekpt.crux.data.OfflineSessionRepository
import com.hardtekpt.crux.data.OfflineTemplateRepository
import com.hardtekpt.crux.data.local.CruxDatabase
import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.local.DatabaseFactory
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.data.seed.StarterDataSeeder
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Days on the wall over the demo data: the figures, paging months and picking a day. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class DaysOnWallViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CruxDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { runBlocking { StarterDataSeeder(FIXED_CLOCK).seed(it, includeSampleData = true) } }
    private val preferences by lazy { UserPreferencesRepository(mainDispatcherRule.preferencesDataStore(File(tmp.root, "prefs.preferences_pb"))) }
    private val dbs by lazy { CruxDatabases(preferences, DatabaseFactory { db }, CoroutineScope(mainDispatcherRule.testDispatcher + SupervisorJob())) }

    private fun viewModel() = DaysOnWallViewModel(
        OfflineClimbRepository(dbs, FIXED_CLOCK),
        OfflineRecordRepository(dbs, FIXED_CLOCK),
        OfflineNoteRepository(dbs, FIXED_CLOCK),
        OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK),
        FIXED_CLOCK,
    )

    @After
    fun close() = db.close()

    @Test
    fun `figures count climbing days, and a picked day shows its journal`() = runBlocking {
        val vm = viewModel()
        val state = withTimeout(10_000) { vm.uiState.first { !it.isLoading } }
        val today = LocalDate.now(FIXED_CLOCK)
        assertEquals(YearMonth.from(today), state.month)
        assertTrue(state.thisYear >= state.thisMonth && state.thisMonth >= state.thisWeek)
        assertEquals(state.climbsByDay.keys.count { it.year == today.year }, state.thisYear)

        val climbed = state.climbsByDay.keys.max()
        vm.pick(climbed)
        val picked = withTimeout(10_000) { vm.uiState.first { it.selected == climbed } }
        assertNotNull(picked.day)
        assertEquals(climbed, picked.day!!.date)

        // A month back picks that month's last day on the wall, if it has one.
        vm.page(-1)
        val back = withTimeout(10_000) { vm.uiState.first { it.month == YearMonth.from(today).minusMonths(1) } }
        val lastThen = back.climbsByDay.keys.filter { YearMonth.from(it) == back.month }.maxOrNull()
        if (lastThen != null) assertEquals(lastThen, back.selected)
    }
}
