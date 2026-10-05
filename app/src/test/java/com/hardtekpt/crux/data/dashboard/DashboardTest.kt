package com.hardtekpt.crux.data.dashboard

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import com.hardtekpt.crux.MainDispatcherRule
import com.hardtekpt.crux.data.FIXED_CLOCK
import com.hardtekpt.crux.data.FakeBodyRepository
import com.hardtekpt.crux.data.FakeClimbRepository
import com.hardtekpt.crux.data.FakePlaceRepository
import com.hardtekpt.crux.data.FakeTemplateRepository
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import com.hardtekpt.crux.ui.home.HomeViewModel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class DashboardTest {

    @get:Rule val mainDispatcherRule = MainDispatcherRule()
    @get:Rule val tmp = TemporaryFolder()

    private val dataStore by lazy { PreferenceDataStoreFactory.create { File(tmp.root, "prefs.preferences_pb") } }
    private val repository by lazy { DashboardRepository(dataStore) }

    private fun viewModel() = HomeViewModel(
        FakeClimbRepository(),
        FakeBodyRepository(),
        FakeTemplateRepository(),
        FakePlaceRepository(),
        UserPreferencesRepository(dataStore),
        repository,
        FIXED_CLOCK,
    )

    @Test
    fun `half-width widgets pair up and full-width ones take a row`() {
        val small = { DashboardWidget(type = WidgetType.WEEK_CLIMBS, size = WidgetSize.SMALL) }
        val wide = DashboardWidget(type = WidgetType.RECENT_CLIMBS, size = WidgetSize.WIDE)
        val rows = listOf(small(), small(), small(), wide, small()).toRows()

        assertEquals(listOf(2, 1, 1, 1), rows.map { it.size })
        assertTrue(rows[2].single().size.fullWidth)
    }

    @Test
    fun `resizing cycles through the sizes a widget supports`() {
        val widget = DashboardWidget(type = WidgetType.BODYWEIGHT)
        assertEquals(WidgetSize.SMALL, widget.size)
        assertEquals(WidgetSize.WIDE, widget.nextSize().size)
        assertEquals(WidgetSize.SMALL, widget.nextSize().nextSize().size)
    }

    @Test
    fun `a fresh install shows the default dashboard`() = runBlocking {
        assertEquals(defaultDashboard().map { it.type }, repository.layout.first().map { it.type })
    }

    @Test
    fun `edits apply to the working copy and are saved on Done`() = runBlocking {
        val vm = viewModel()
        withTimeout(5_000) { vm.dashboard.first { it.widgets.isNotEmpty() } }
        vm.startEditing()

        val ids = vm.dashboard.value.widgets.map { it.id }
        vm.move(ids.last(), ids.first())
        vm.remove(ids[0])
        vm.add(WidgetType.WEIGHT_TREND)
        val weekId = vm.dashboard.value.widgets.first { it.type == WidgetType.WEEK_CLIMBS }.id
        vm.resize(weekId)
        vm.finishEditing()

        val saved = withTimeout(5_000) { repository.layout.first { it.any { w -> w.type == WidgetType.WEIGHT_TREND } } }
        assertEquals(WidgetType.RECENT_CLIMBS, saved.first().type)
        assertFalse(saved.any { it.type == WidgetType.TODAYS_PLAN })
        assertEquals(WidgetType.WEIGHT_TREND, saved.last().type)
        assertEquals(WidgetSize.WIDE, saved.first { it.type == WidgetType.WEEK_CLIMBS }.size)
        assertFalse(vm.dashboard.value.editing)
    }

    @Test
    fun `a saved layout round-trips`() = runBlocking {
        val layout = listOf(DashboardWidget(type = WidgetType.WEIGHT_TREND, size = WidgetSize.LARGE))
        repository.save(layout)
        assertEquals(layout, repository.layout.first())
    }
}
