package com.hardtekpt.crux.data.diagnostics

import java.io.File
import java.time.Clock
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * When Crux crashes, the stack trace is written to `files/crashes/` and stays on the phone.
 * The climber can share the latest one from Settings to attach to a bug report; the app never
 * sends anything by itself. Only the newest [keep] reports are kept.
 *
 * [about] describes the build and device; it heads every report.
 */
class CrashReports(private val dir: File, private val clock: Clock, private val about: String, private val keep: Int = 10) {
    private val _count = MutableStateFlow(list().size)

    /** How many reports are saved. */
    val count: StateFlow<Int> = _count.asStateFlow()

    /** Records uncaught exceptions, then hands them on so the app still closes as usual. */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(threadName: String, error: Throwable): File {
        dir.mkdirs()
        val now = clock.instant()
        val file = File(dir, "$PREFIX${FILE_TIME.format(now)}.txt")
        file.writeText(
            buildString {
                appendLine("Crux crash report")
                appendLine("Time: $now")
                appendLine(about)
                appendLine("Thread: $threadName")
                appendLine()
                append(error.stackTraceToString())
            },
        )
        list().drop(keep).forEach(File::delete)
        _count.value = list().size
        return file
    }

    /** Saved reports, newest first. */
    fun list(): List<File> = dir.listFiles { file -> file.name.startsWith(PREFIX) && file.name.endsWith(".txt") }
        .orEmpty()
        .sortedByDescending { it.name }

    fun latest(): File? = list().firstOrNull()

    fun clear() {
        list().forEach(File::delete)
        _count.value = 0
    }

    companion object {
        const val DIR = "crashes"
        private const val PREFIX = "crash-"

        // UTC and zero-padded, so file names sort in time order.
        private val FILE_TIME = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss-SSS").withZone(ZoneOffset.UTC)
    }
}
