package com.hardtekpt.crux.data.backup

import com.hardtekpt.crux.data.dashboard.DashboardRepository
import com.hardtekpt.crux.data.dashboard.DashboardWidget
import com.hardtekpt.crux.data.dashboard.WidgetSize
import com.hardtekpt.crux.data.dashboard.WidgetType
import com.hardtekpt.crux.data.prefs.Accent
import com.hardtekpt.crux.data.prefs.TextSize
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.first

/**
 * The app settings a backup carries. Places last logged at stay out: they are database ids that
 * mean nothing on another install. So do demo mode and the backup's own photo and video switches.
 */
@Singleton
class BackupSettings @Inject constructor(private val preferences: UserPreferencesRepository, private val dashboard: DashboardRepository) {
    suspend fun read(): SettingsDto {
        val scales = preferences.gradeScales.first()
        return SettingsDto(
            theme = preferences.themeMode.first(),
            accent = preferences.accent.first().name,
            textSize = preferences.textSize.first().name,
            units = preferences.units.first(),
            boulderScale = scales.boulder,
            routeScale = scales.route,
            timerSounds = preferences.timerSounds.first(),
            timerCompact = preferences.timerCompact.first(),
            dashboard = dashboard.layout.first().map { WidgetDto(it.type.name, it.size.name) },
        )
    }

    /** Applies what the backup has; anything it leaves out stays as it is. */
    suspend fun write(settings: SettingsDto) {
        settings.theme?.let { preferences.setThemeMode(it) }
        // An accent this version doesn't know leaves the current one.
        settings.accent?.let { name -> Accent.entries.firstOrNull { it.name == name } }?.let { preferences.setAccent(it) }
        settings.textSize?.let { name -> TextSize.entries.firstOrNull { it.name == name } }?.let { preferences.setTextSize(it) }
        settings.units?.let { preferences.setUnits(it) }
        settings.boulderScale?.let { preferences.setGradeScale(it) }
        settings.routeScale?.let { preferences.setGradeScale(it) }
        settings.timerSounds?.let { preferences.setTimerSounds(it) }
        settings.timerCompact?.let { preferences.setTimerCompact(it) }
        // Widgets this version doesn't know are dropped; an empty layout keeps the current one.
        settings.dashboard
            ?.mapNotNull { widget ->
                val type = WidgetType.entries.firstOrNull { it.name == widget.type } ?: return@mapNotNull null
                val size = WidgetSize.entries.firstOrNull { it.name == widget.size }?.takeIf { it in type.sizes } ?: type.defaultSize
                DashboardWidget(type = type, size = size)
            }
            ?.takeIf { it.isNotEmpty() }
            ?.let { dashboard.save(it) }
    }
}
