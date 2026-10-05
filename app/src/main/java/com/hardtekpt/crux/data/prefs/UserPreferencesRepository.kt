package com.hardtekpt.crux.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class GradeSystem { V_SCALE, FONT, YDS, FRENCH }

enum class ThemeMode { DARK, LIGHT, SYSTEM }

/** Small app-wide settings backed by Preferences DataStore. */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val gradeSystem: Flow<GradeSystem> = dataStore.data.map { prefs ->
        prefs[GRADE_SYSTEM]?.let(GradeSystem::valueOf) ?: GradeSystem.V_SCALE
    }

    /** Dark is the app default; the climber can switch to light or follow the system. */
    val themeMode: Flow<ThemeMode> = dataStore.data.map { prefs ->
        prefs[THEME_MODE]?.let(ThemeMode::valueOf) ?: ThemeMode.DARK
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { it[THEME_MODE] = mode.name }
    }

    suspend fun setGradeSystem(system: GradeSystem) {
        dataStore.edit { it[GRADE_SYSTEM] = system.name }
    }

    private companion object {
        val GRADE_SYSTEM = stringPreferencesKey("grade_system")
        val THEME_MODE = stringPreferencesKey("theme_mode")
    }
}
