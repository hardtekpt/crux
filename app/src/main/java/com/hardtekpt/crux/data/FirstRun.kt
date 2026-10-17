package com.hardtekpt.crux.data

import com.hardtekpt.crux.data.local.CruxDatabases
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext

/**
 * Whether first-run setup shows: only once, on a fresh install. An install that already has
 * the climber's own data (one from before setup existed, or one restored from a backup) or is in
 * demo mode never sees it, and is marked done so it never asks again.
 */
@Singleton
class FirstRun @Inject constructor(private val preferences: UserPreferencesRepository, private val dbs: CruxDatabases) {
    suspend fun needed(): Boolean {
        if (preferences.setupDone.first()) return false
        val needed = !preferences.demoMode.first() && !hasOwnData()
        if (!needed) preferences.setSetupDone()
        return needed
    }

    /** Anything in the climber's own data set: climbs, places, sessions, plans, exercises, body stats or notes. */
    suspend fun hasOwnData(): Boolean = withContext(Dispatchers.IO) {
        val tables = listOf("climbs", "places", "sessions", "workout_templates", "exercises", "body_measurements", "notes", "datasets")
        val sql = tables.joinToString(" + ", prefix = "SELECT ") { "(SELECT COUNT(*) FROM $it)" }
        dbs.real.openHelper.readableDatabase.query(sql).use { it.moveToFirst() && it.getLong(0) > 0 }
    }
}
