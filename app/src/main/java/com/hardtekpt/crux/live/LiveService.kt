package com.hardtekpt.crux.live

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.IconCompat
import com.hardtekpt.crux.MainActivity
import com.hardtekpt.crux.R
import com.hardtekpt.crux.data.Session
import com.hardtekpt.crux.data.SessionRepository
import com.hardtekpt.crux.data.prefs.UserPreferencesRepository
import dagger.hilt.android.AndroidEntryPoint
import java.time.Clock
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * Keeps the live notification of a running session, or of the Interval timer page, while you
 * train. It runs in the foreground so the bar keeps moving and a rest can buzz at its end with
 * the screen off, and stops on its own when nothing is running.
 */
@AndroidEntryPoint
class LiveService : Service() {
    @Inject lateinit var sessions: SessionRepository

    @Inject lateinit var live: LiveTimers

    @Inject lateinit var preferences: UserPreferencesRepository

    @Inject lateinit var clock: Clock

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var started = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (!started) {
            started = true
            ensureChannel(this)
            // The system wants a notification at once; the real card follows in a moment.
            startForegroundWith(placeholder())
            scope.launch { follow() }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        super.onDestroy()
    }

    private suspend fun follow() {
        val usualMinutes = sessions.observeFinished().first()
            .filter { it.items.isEmpty() && it.endedAtMillis != null }
            .map { ((it.endedAtMillis!! - it.startedAtMillis) / 60_000).toInt() }
            .takeIf { it.size >= 2 }?.average()?.toInt() ?: LiveCards.USUAL_CLIMBING_MINUTES
        // A tick each second while a clock runs on the bar, else every half minute.
        val ticks = flow {
            while (true) {
                emit(clock.millis())
                delay(if (live.rest.value != null || live.timer.value != null || live.standalone.value != null) 1_000 else 30_000)
            }
        }
        combine(sessions.observeRunning(), live.rest, live.timer, live.standalone, ticks) { session, rest, timer, standalone, _ ->
            // The time now, not the tick's: a rest started between ticks shows at once.
            Snapshot(session, rest, timer, standalone, clock.millis())
        }.flowOn(Dispatchers.Default).collect { snapshot -> show(snapshot, usualMinutes) }
    }

    private data class Snapshot(
        val session: Session?,
        val rest: com.hardtekpt.crux.ui.session.Rest?,
        val timer: com.hardtekpt.crux.ui.session.SessionTimer?,
        val standalone: com.hardtekpt.crux.ui.session.IntervalRun?,
        val now: Long,
    )

    private suspend fun show(s: Snapshot, usualMinutes: Int) {
        // A rest that ran out ends here, with a buzz, whether the screen is on or not.
        val rest = s.rest
        if (rest != null && rest.endsAtMillis <= s.now) {
            live.endRest()
            if (preferences.timerSounds.first()) com.hardtekpt.crux.ui.session.buzz(this)
            return
        }
        val card = when {
            s.session != null -> LiveCards.forSession(s.session, s.rest, s.timer, s.now, usualMinutes)
            s.standalone != null -> LiveCards.forInterval(s.standalone, s.now, "Interval timer", null)
            else -> null
        }
        if (card == null) {
            ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
            stopSelf()
            return
        }
        // Without the permission (Android 13+, if you said no) there's simply no card.
        if (Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) {
            NotificationManagerCompat.from(this).notify(NOTIFICATION_ID, build(this, card))
        }
    }

    private fun startForegroundWith(notification: Notification) {
        val type = if (Build.VERSION.SDK_INT >= 34) ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE else 0
        ServiceCompat.startForeground(this, NOTIFICATION_ID, notification, type)
    }

    private fun placeholder(): Notification = NotificationCompat.Builder(this, CHANNEL)
        .setSmallIcon(R.drawable.ic_stat_crux)
        .setContentTitle("Session")
        .setOngoing(true)
        .setSilent(true)
        .build()

    companion object {
        const val CHANNEL = "live"
        const val NOTIFICATION_ID = 7

        fun start(context: Context) {
            runCatching { ContextCompat.startForegroundService(context, Intent(context, LiveService::class.java)) }
        }

        fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            if (manager.getNotificationChannel(CHANNEL) != null) return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "Live session and timers", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Your session, rests and the interval timer while they run"
                    setSound(null, null)
                    enableVibration(false)
                    setShowBadge(false)
                },
            )
        }

        /** The colours of the bar, the session timer band's. */
        private fun argb(color: LiveColor): Int = when (color) {
            LiveColor.WORK -> 0xFF3FDDE8.toInt()
            LiveColor.REST -> 0xFFFFBD45.toInt()
            LiveColor.CYCLE_REST -> 0xFFB9A2FF.toInt()
            LiveColor.DONE -> 0xFF5EE391.toInt()
            LiveColor.LATER -> 0xFF5D6B6E.toInt()
        }

        /** A live card as a notification: a Live Update with a segmented bar on Android 16, a plain bar before. */
        fun build(context: Context, card: LiveCard): Notification {
            val open = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                putExtra(MainActivity.EXTRA_OPEN, if (card.sessionId != null) MainActivity.OPEN_SESSION else MainActivity.OPEN_TIMER)
                card.sessionId?.let { putExtra(MainActivity.EXTRA_SESSION, it) }
            }
            val content = PendingIntent.getActivity(context, 1, open, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val total = card.segments.sumOf { it.length }.coerceAtLeast(1)
            val style = NotificationCompat.ProgressStyle()
                .setStyledByProgress(true)
                .setProgressSegments(card.segments.map { NotificationCompat.ProgressStyle.Segment(it.length).setColor(argb(it.color)) })
                .setProgressPoints(card.points.map { NotificationCompat.ProgressStyle.Point(it.position.coerceIn(1, total - 1)).setColor(argb(it.color)) })
                .setProgress(card.progress.coerceIn(0, total))
            when (card.tracker) {
                LiveTracker.CLIMBER -> style.setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_live_climber))
                LiveTracker.REST -> style.setProgressTrackerIcon(IconCompat.createWithResource(context, R.drawable.ic_live_rest))
                LiveTracker.NONE -> Unit
            }
            val builder = NotificationCompat.Builder(context, CHANNEL)
                .setSmallIcon(R.drawable.ic_stat_crux)
                .setContentTitle(card.title)
                .setContentText(card.text)
                .setSubText(card.sub)
                .setColor(argb(card.accent))
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .setSilent(true)
                .setCategory(NotificationCompat.CATEGORY_WORKOUT)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(content)
                .setRequestPromotedOngoing(true)
                .setStyle(style)
            card.chip?.let { builder.setShortCriticalText(it) }
            card.clockAt?.let { at ->
                builder.setWhen(at).setShowWhen(true).setUsesChronometer(true).setChronometerCountDown(card.countDown)
            }
            card.actions.forEachIndexed { index, action ->
                val pending = if (action == LiveAction.LOG_CLIMB) {
                    val intent = Intent(context, MainActivity::class.java).apply {
                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                        putExtra(MainActivity.EXTRA_OPEN, MainActivity.OPEN_LOG_CLIMB)
                    }
                    PendingIntent.getActivity(context, 10 + index, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                } else {
                    val intent = Intent(context, LiveActionReceiver::class.java).setAction(action.name)
                    PendingIntent.getBroadcast(context, 20 + index, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
                }
                builder.addAction(NotificationCompat.Action.Builder(null as IconCompat?, action.label, pending).build())
            }
            return builder.build()
        }
    }
}
