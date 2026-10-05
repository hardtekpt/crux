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

/** Small app-wide settings backed by Preferences DataStore. */
@Singleton
class UserPreferencesRepository @Inject constructor(
    private val dataStore: DataStore<Preferences>,
) {
    val gradeSystem: Flow<GradeSystem> = dataStore.data.map { prefs ->
        prefs[GRADE_SYSTEM]?.let(GradeSystem::valueOf) ?: GradeSystem.V_SCALE
    }

    suspend fun setGradeSystem(system: GradeSystem) {
        dataStore.edit { it[GRADE_SYSTEM] = system.name }
    }

    private companion object {
        val GRADE_SYSTEM = stringPreferencesKey("grade_system")
    }
}
