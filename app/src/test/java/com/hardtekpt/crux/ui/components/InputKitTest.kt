package com.hardtekpt.crux.ui.components

import com.hardtekpt.crux.data.model.MeasurementType
import com.hardtekpt.crux.data.model.formatKg
import com.hardtekpt.crux.data.prefs.UnitSystem
import com.hardtekpt.crux.ui.components.input.LoadParts
import com.hardtekpt.crux.ui.components.input.RulerScale
import com.hardtekpt.crux.ui.components.input.effortWord
import com.hardtekpt.crux.ui.components.input.formatDuration
import com.hardtekpt.crux.ui.components.input.parseDuration
import com.hardtekpt.crux.ui.feetInches
import com.hardtekpt.crux.ui.lengthDifference
import com.hardtekpt.crux.ui.measureInput
import com.hardtekpt.crux.ui.measurement
import com.hardtekpt.crux.ui.parseFeetInches
import com.hardtekpt.crux.ui.weight
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** The value lists, rounding and conversions behind the touch inputs. */
class InputKitTest {

    @Test
    fun `load splits into sign, whole kilos and quarter plates`() {
        assertEquals(LoadParts(false, 7, 2), LoadParts.of(7.5))
        assertEquals(LoadParts(true, 1, 1), LoadParts.of(-1.25))
        assertEquals(-1.25, LoadParts(true, 1, 1).kg, 0.0)
        // Values off the quarter grid land on the nearest plate.
        assertEquals(LoadParts(false, 3, 0), LoadParts.of(2.9))
    }

    @Test
    fun `kilos show up to two decimals without trailing zeros`() {
        assertEquals("10", formatKg(10.0))
        assertEquals("7.5", formatKg(7.5))
        assertEquals("1.25", formatKg(1.25))
    }

    @Test
    fun `durations read and print as seconds or minutes`() {
        assertEquals("45 s", formatDuration(45))
        assertEquals("3:00", formatDuration(180))
        assertEquals("1:05", formatDuration(65))
        assertEquals(90, parseDuration("90"))
        assertEquals(90, parseDuration("1:30"))
        assertEquals(90, parseDuration("1m30"))
        assertNull(parseDuration("1:75"))
        assertNull(parseDuration("soon"))
    }

    @Test
    fun `ruler steps round cleanly`() {
        val scale = RulerScale(30.0, 200.0, 0.1, midEvery = 5, majorEvery = 10, labelEvery = 10)
        assertEquals(1700, scale.steps)
        assertEquals(72.4, scale.valueAt(scale.indexOf(72.4)), 0.0)
        assertEquals(30.0, scale.valueAt(scale.indexOf(10.0)), 0.0)
    }

    @Test
    fun `imperial shows pounds and feet and inches but stores metric`() {
        val imperial = UnitSystem.IMPERIAL
        assertEquals("159.6 lb", imperial.weight(72.4).toString())
        assertEquals("5′10″", imperial.measurement(MeasurementType.HEIGHT, 177.8).toString())
        assertEquals("+2.4 in", imperial.lengthDifference(6.0).toString())
        assertEquals("+6 cm", UnitSystem.METRIC.lengthDifference(6.0).toString())

        val input = imperial.measureInput(MeasurementType.WEIGHT)
        assertEquals(72.39, input.toStored(159.6), 0.0)
        val height = imperial.measureInput(MeasurementType.HEIGHT)
        assertEquals(177.8, height.toStored(70.0), 0.0)
    }

    @Test
    fun `feet and inches read the ways people type them`() {
        assertEquals("5′10.5″", feetInches(70.5))
        assertEquals(70.0, parseFeetInches("5'10")!!, 0.0)
        assertEquals(70.5, parseFeetInches("5′10.5″")!!, 0.0)
        assertEquals(70.0, parseFeetInches("5 10")!!, 0.0)
        assertEquals(70.0, parseFeetInches("70")!!, 0.0)
        assertEquals(60.0, parseFeetInches("5'")!!, 0.0)
    }

    @Test
    fun `effort reads in words`() {
        assertEquals("Easy", effortWord(1))
        assertEquals("Moderate", effortWord(6))
        assertEquals("Very hard", effortWord(9))
        assertEquals("Limit", effortWord(10))
    }
}
