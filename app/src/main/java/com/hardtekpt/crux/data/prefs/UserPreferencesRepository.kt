package com.hardtekpt.crux.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("System") }

/** How weights and lengths are shown. Data is always stored in kg and cm. */
enum class UnitSystem(val label: String) { METRIC("Metric"), IMPERIAL("Imperial") }

/** The grade scale the climber picked per discipline. New climbs are logged in it. */
data class GradeScales(val boulder: GradeScale = Discipline.BOULDER.defaultScale, val route: GradeScale = Discipline.ROUTE.defaultScale) {
    fun forDiscipline(discipline: Discipline): GradeScale = when (discipline) {
        Discipline.BOULDER -> boulder
        Discipline.ROUTE -> route
    }
}

/** Small app-wide settings backed by Preferences DataStore. */
@Singleton
class UserPreferencesRepository @Inject constructor(private val dataStore: DataStore<Preferences>) {
    /** Dark is the app default; the climber can switch to light or follow the system. */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    val units: Flow<UnitSystem> = dataStore.data.map { prefs ->
        prefs[UNITS]?.let { name -> UnitSystem.entries.firstOrNull { it.name == name } } ?: UnitSystem.METRIC
    }

    suspend fun setUnits(units: UnitSystem) {
        dataStore.edit { it[UNITS] = units.name }
    }

    /** Demo mode shows the separate demo data set; off means the climber's own data. */
    val demoMode: Flow<Boolean> = dataStore.data.map { it[DEMO_MODE] ?: false }

    suspend fun setDemoMode(enabled: Boolean) {
        dataStore.edit { it[DEMO_MODE] = enabled }
    }

    /** Which version of the sample data the demo database holds; 0 before any. */
    val demoDataVersion: Flow<Int> = dataStore.data.map { it[DEMO_DATA_VERSION] ?: 0 }

    suspend fun setDemoDataVersion(version: Int) {
        dataStore.edit { it[DEMO_DATA_VERSION] = version }
    }

    /** The place last logged at, so the next log starts there. */
    // Beeps for the last seconds of a timer phase and when the phase changes.
    val timerSounds: Flow<Boolean> = dataStore.data.map { it[TIMER_SOUNDS] ?: true }

    suspend fun setTimerSounds(enabled: Boolean) {
        dataStore.edit { it[TIMER_SOUNDS] = enabled }
    }

    /** The live session's timer band shown as one slim line. */
    val timerCompact: Flow<Boolean> = dataStore.data.map { it[TIMER_COMPACT] ?: false }

    suspend fun setTimerCompact(compact: Boolean) {
        dataStore.edit { it[TIMER_COMPACT] = compact }
    }

    val lastPlaceId: Flow<Long?> = dataStore.data.map { it[LAST_PLACE] }

    suspend fun setLastPlaceId(id: Long?) {
        dataStore.edit { if (id == null) it.remove(LAST_PLACE) else it[LAST_PLACE] = id }
    }

    /** Where in the last place the last climb was: its facility and wall. */
    val lastSpot: Flow<Pair<Long?, Long?>> = dataStore.data.map { it[LAST_SECTION] to it[LAST_AREA] }

    /** Remembers the place, facility and wall of the last climb logged. */
    suspend fun setLastSpot(placeId: Long?, sectionId: Long?, areaId: Long?) {
        dataStore.edit { prefs ->
            if (placeId == null) prefs.remove(LAST_PLACE) else prefs[LAST_PLACE] = placeId
            if (sectionId == null) prefs.remove(LAST_SECTION) else prefs[LAST_SECTION] = sectionId
            if (areaId == null) prefs.remove(LAST_AREA) else prefs[LAST_AREA] = areaId
        }
    }

    val gradeScales: Flow<GradeScales> = dataStore.data.map { prefs ->
        GradeScales(
            boulder = prefs[BOULDER_SCALE].toScale(Discipline.BOULDER),
            route = prefs[ROUTE_SCALE].toScale(Discipline.ROUTE),
        )
    }

    suspend fun setGradeScale(scale: GradeScale) {
        dataStore.edit {
            it[if (scale.discipline == Discipline.BOULDER) BOULDER_SCALE else ROUTE_SCALE] = scale.name
        }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val DEMO_MODE = booleanPreferencesKey("demo_mode")
        val DEMO_DATA_VERSION = androidx.datastore.preferences.core.intPreferencesKey("demo_data_version")
        val UNITS = stringPreferencesKey("units")
        val LAST_PLACE = longPreferencesKey("last_place_id")
        val LAST_SECTION = longPreferencesKey("last_section_id")
        val LAST_AREA = longPreferencesKey("last_area_id")
        val TIMER_SOUNDS = booleanPreferencesKey("timer_sounds")
        val TIMER_COMPACT = booleanPreferencesKey("timer_compact")
        val BOULDER_SCALE = stringPreferencesKey("boulder_grade_scale")
        val ROUTE_SCALE = stringPreferencesKey("route_grade_scale")

        fun String?.toScale(discipline: Discipline): GradeScale = GradeScale.entries.firstOrNull { it.name == this && it.discipline == discipline }
            ?: discipline.defaultScale
    }
}
