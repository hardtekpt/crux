package com.hardtekpt.crux

import android.app.Application
import android.os.StrictMode
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.hardtekpt.crux.data.diagnostics.CrashReports
import com.hardtekpt.crux.data.seed.StarterData
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

@HiltAndroidApp
class CruxApplication :
    Application(),
    Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory

    @Inject lateinit var starterData: StarterData

    @Inject lateinit var crashReports: CrashReports

    @Inject lateinit var sessions: com.hardtekpt.crux.data.SessionRepository

    @Inject lateinit var liveTimers: com.hardtekpt.crux.live.LiveTimers

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        // Crashes are saved on the phone (Settings → Crash reports); nothing is sent anywhere.
        crashReports.install()
        // Debug builds log disk or network work on the main thread, and files left open.
        if (BuildConfig.DEBUG) {
            StrictMode.setThreadPolicy(StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork().penaltyLog().build())
            StrictMode.setVmPolicy(StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects().penaltyLog().build())
        }
        // Only the demo data set is pre-filled; the climber's own starts empty.
        appScope.launch { starterData.seedDemoIfEmpty() }
        // A running session, or the Interval timer page's run, gets the live notification.
        appScope.launch {
            combine(sessions.observeRunning(), liveTimers.standalone) { session, standalone -> session != null || standalone != null }
                .distinctUntilChanged()
                .collect { live -> if (live) com.hardtekpt.crux.live.LiveService.start(this@CruxApplication) }
        }
    }

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()
}
