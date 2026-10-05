package com.hardtekpt.crux.data.prefs

import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

enum class ThemeMode { DARK, LIGHT, SYSTEM }

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

    suspend fun isStarterDataSeeded(): Boolean = dataStore.data.first()[STARTER_SEEDED] == true

    suspend fun markStarterDataSeeded() {
        dataStore.edit { it[STARTER_SEEDED] = true }
    }

    private companion object {
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val STARTER_SEEDED = booleanPreferencesKey("starter_data_seeded")
    }
}
