package com.hardtekpt.crux

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hardtekpt.crux.data.diagnostics.CrashReports
import com.hardtekpt.crux.data.seed.StarterData
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

@HiltAndroidApp
class CruxApplication :
    Application(),
    Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var starterData: StarterData

    @Inject lateinit var crashReports: CrashReports

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Crashes are saved on the phone (Settings → Crash reports); nothing is sent anywhere.
        crashReports.install()
        // Only the demo data set is pre-filled; the climber's own starts empty.
        appScope.launch { starterData.seedDemoIfEmpty() }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
