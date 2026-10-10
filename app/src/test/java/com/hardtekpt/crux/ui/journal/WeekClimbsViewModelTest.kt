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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/** Climbs this week over the demo data: the figures, the days, and paging weeks. */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class WeekClimbsViewModelTest {
    @get:Rule val mainDispatcherRule = MainDispatcherRule()

    @get:Rule val tmp = TemporaryFolder()

    private val db = Room.inMemoryDatabaseBuilder(ApplicationProvider.getApplicationContext(), CruxDatabase::class.java)
        .allowMainThreadQueries()
        .build()
        .also { runBlocking { StarterDataSeeder(FIXED_CLOCK).seed(it, includeSampleData = true) } }
    private val preferences by lazy { UserPreferencesRepository(mainDispatcherRule.preferencesDataStore(File(tmp.root, "prefs.preferences_pb"))) }
    private val dbs by lazy { CruxDatabases(preferences, DatabaseFactory { db }, CoroutineScope(mainDispatcherRule.testDispatcher + SupervisorJob())) }

    private fun viewModel() = WeekClimbsViewModel(
        OfflineClimbRepository(dbs, FIXED_CLOCK),
        OfflineRecordRepository(dbs, FIXED_CLOCK),
        OfflineNoteRepository(dbs, FIXED_CLOCK),
        OfflineSessionRepository(dbs, OfflineTemplateRepository(dbs), FIXED_CLOCK),
        FIXED_CLOCK,
    )

    @After
    fun close() = db.close()

    @Test
    fun `the week's figures and days match its climbs, and the arrows page weeks`() = runBlocking {
        val vm = viewModel()
        val state = withTimeout(10_000) { vm.uiState.first { !it.isLoading } }
        val monday = LocalDate.now(FIXED_CLOCK).with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY))
        assertEquals(monday, state.week)
        assertTrue(state.entries.all { !it.date.isBefore(monday) && !it.date.isAfter(monday.plusDays(6)) })
        assertEquals(state.days, state.entries.size)

        vm.page(-1)
        val last = withTimeout(10_000) { vm.uiState.first { it.week == monday.minusWeeks(1) } }
        assertTrue(last.climbs >= last.sent)
        // Never past this week.
        vm.page(5)
        assertEquals(monday, withTimeout(10_000) { vm.uiState.first { it.week == monday } }.week)
    }
}
