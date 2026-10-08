package com.hardtekpt.crux.data.dashboard

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/**
 * How much room a widget takes on the two-column dashboard. Half-width widgets sit side
 * by side; the others take a full row.
 */
enum class WidgetSize(val label: String, val fullWidth: Boolean) {
    SMALL("Small", fullWidth = false),
    WIDE("Wide", fullWidth = true),
    LARGE("Large", fullWidth = true),
}

/** Everything the dashboard can show. The first size listed is the default when added. */
enum class WidgetType(val title: String, val description: String, val sizes: List<WidgetSize>) {
    TODAYS_PLAN("Today's plan", "The session planned for today", listOf(WidgetSize.WIDE, WidgetSize.LARGE)),
    WEEK_CLIMBS("This week", "Climbs and sends since Monday", listOf(WidgetSize.SMALL, WidgetSize.WIDE)),
    DAYS_ON_WALL("Days on the wall", "Days you climbed this week", listOf(WidgetSize.SMALL, WidgetSize.WIDE)),
    LATEST_BEST("Latest best", "Your newest personal best", listOf(WidgetSize.SMALL, WidgetSize.WIDE)),
    BODYWEIGHT("Bodyweight", "Latest weigh-in and its 30-day change", listOf(WidgetSize.SMALL, WidgetSize.WIDE)),
    WEIGHT_TREND("Weight trend", "Bodyweight over the last 3 months", listOf(WidgetSize.WIDE, WidgetSize.LARGE)),
    WEEKLY_SENDS("Sends per week", "Sends over the last weeks", listOf(WidgetSize.WIDE, WidgetSize.LARGE)),
    SENDS_BY_STYLE("Sends by style", "Flash, onsight and redpoint split", listOf(WidgetSize.LARGE, WidgetSize.WIDE)),
    RECENT_CLIMBS("Recent climbs", "Your latest journal entries", listOf(WidgetSize.WIDE, WidgetSize.LARGE)),
    PROJECTS("Projects", "Problems you're still working", listOf(WidgetSize.WIDE, WidgetSize.LARGE)),
    CONSISTENCY("Consistency", "Your days on the wall over the last weeks, and your week streak", listOf(WidgetSize.WIDE, WidgetSize.LARGE)),
    ;

    val defaultSize: WidgetSize get() = sizes.first()
}

@Serializable
data class DashboardWidget(val id: String = UUID.randomUUID().toString(), val type: WidgetType, val size: WidgetSize = type.defaultSize) {
    /** The next size this widget supports, wrapping around. */
    fun nextSize(): DashboardWidget {
        val sizes = type.sizes
        return copy(size = sizes[(sizes.indexOf(size) + 1) % sizes.size])
    }
}

/** What a fresh install shows: the MVP dashboard. */
fun defaultDashboard(): List<DashboardWidget> = listOf(
    DashboardWidget(type = WidgetType.TODAYS_PLAN),
    DashboardWidget(type = WidgetType.WEEK_CLIMBS),
    DashboardWidget(type = WidgetType.DAYS_ON_WALL),
    DashboardWidget(type = WidgetType.LATEST_BEST),
    DashboardWidget(type = WidgetType.BODYWEIGHT),
    DashboardWidget(type = WidgetType.PROJECTS),
    DashboardWidget(type = WidgetType.RECENT_CLIMBS),
)

/**
 * The dashboard layout, stored in preferences rather than the database so it survives the
 * MVP's destructive schema changes.
 */
@Singleton
class DashboardRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    private val json = Json { ignoreUnknownKeys = true }

    val layout: Flow<List<DashboardWidget>> = dataStore.data.map { prefs ->
        prefs[LAYOUT]?.let { decode(it) } ?: defaultDashboard()
    }

    suspend fun save(widgets: List<DashboardWidget>) {
        dataStore.edit { it[LAYOUT] = json.encodeToString(widgets) }
    }

    suspend fun reset() {
        dataStore.edit { it.remove(LAYOUT) }
    }

    /** A layout that no longer parses (say, a widget type was removed) falls back to the default. */
    private fun decode(raw: String): List<DashboardWidget>? = runCatching {
        json.decodeFromString<List<DashboardWidget>>(raw)
            .filter { it.size in it.type.sizes }
    }.getOrNull()

    private companion object {
        val LAYOUT = stringPreferencesKey("dashboard_layout")
    }
}

/** Two-column flow: half-width widgets pair up, a lone one sits on its own row. */
fun List<DashboardWidget>.toRows(): List<List<DashboardWidget>> {
    val rows = mutableListOf<List<DashboardWidget>>()
    var pending: DashboardWidget? = null
    for (widget in this) {
        if (widget.size.fullWidth) {
            pending?.let { rows += listOf(it) }
            pending = null
            rows += listOf(widget)
        } else if (pending == null) {
            pending = widget
        } else {
            rows += listOf(pending, widget)
            pending = null
        }
    }
    pending?.let { rows += listOf(it) }
    return rows
}
