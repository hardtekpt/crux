package com.hardtekpt.crux.data.diagnostics

import java.time.Clock
import java.time.Instant
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class CrashReportsTest {

    @get:Rule val tmp = TemporaryFolder()

    private var now = Instant.parse("2026-10-07T10:15:30.123Z")
    private val clock = object : Clock() {
        override fun getZone() = ZoneOffset.UTC
        override fun withZone(zone: java.time.ZoneId?) = this
        override fun instant() = now
    }
    private val reports by lazy { CrashReports(tmp.root.resolve("crashes"), clock, about = "App: Crux 0.1.0 (100, debug)", keep = 3) }

    @Test
    fun `a crash is saved with the build, the thread and the stack trace`() {
        val file = reports.record("main", IllegalStateException("No place picked"))

        val text = file.readText()
        assertTrue(text.startsWith("Crux crash report\nTime: 2026-10-07T10:15:30.123Z\nApp: Crux 0.1.0 (100, debug)\nThread: main\n"))
        assertTrue(text.contains("java.lang.IllegalStateException: No place picked"))
        assertTrue(text.contains("at com.hardtekpt.crux.data.diagnostics.CrashReportsTest"))
        assertEquals(1, reports.count.value)
    }

    @Test
    fun `only the newest reports are kept, newest first`() {
        repeat(5) { second ->
            now = Instant.parse("2026-10-07T10:15:0${second}Z")
            reports.record("main", RuntimeException("crash $second"))
        }

        assertEquals(3, reports.count.value)
        assertTrue(reports.latest()!!.readText().contains("crash 4"))
        assertTrue(reports.list().last().readText().contains("crash 2"))
    }

    @Test
    fun `clearing removes every report`() {
        reports.record("main", RuntimeException())
        reports.clear()

        assertEquals(0, reports.count.value)
        assertNull(reports.latest())
    }

    @Test
    fun `an uncaught exception is recorded and still reaches the previous handler`() {
        val original = Thread.getDefaultUncaughtExceptionHandler()
        var handedOn: Throwable? = null
        Thread.setDefaultUncaughtExceptionHandler { _, error -> handedOn = error }
        try {
            reports.install()
            val error = RuntimeException("boom")
            Thread.getDefaultUncaughtExceptionHandler()!!.uncaughtException(Thread.currentThread(), error)

            assertSame(error, handedOn)
            assertTrue(reports.latest()!!.readText().contains("RuntimeException: boom"))
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(original)
        }
    }
}
