package com.hardtekpt.crux.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.hardtekpt.crux.data.model.Discipline
import com.hardtekpt.crux.data.model.GradeScale
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode(val label: String) { DARK("Dark"), LIGHT("Light"), SYSTEM("System") }

/** The grade scale the climber picked per discipline. New climbs are logged in it. */
data class GradeScales(
    val boulder: GradeScale = Discipline.BOULDER.defaultScale,
    val route: GradeScale = Discipline.ROUTE.defaultScale,
) {
    fun forDiscipline(discipline: Discipline): GradeScale = when (discipline) {
        Discipline.BOULDER -> boulder
        Discipline.ROUTE -> route
    }
}

/** Small app-wide settings backed by Preferences DataStore. */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    /** Dark is the app default; the climber can switch to light or follow the system. */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
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
        val BOULDER_SCALE = stringPreferencesKey("boulder_grade_scale")
        val ROUTE_SCALE = stringPreferencesKey("route_grade_scale")

        fun String?.toScale(discipline: Discipline): GradeScale =
            GradeScale.entries.firstOrNull { it.name == this && it.discipline == discipline }
                ?: discipline.defaultScale
    }
}
