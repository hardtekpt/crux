package com.hardtekpt.crux.data

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class BoardLightsTest {
    private fun bytes(vararg values: Int) = values.map { it.toByte() }.toByteArray()

    @Test
    fun anEmptyMessageClearsTheBoard() {
        // The well-known Aurora v3 clear frame.
        assertArrayEquals(bytes(0x01, 0x01, 0xAB, 0x02, 0x54, 0x03), BoardLights.aurora(emptyList(), 3).single())
        assertArrayEquals(bytes(0x01, 0x01, 0xAF, 0x02, 0x50, 0x03), BoardLights.aurora(emptyList(), 2).single())
    }

    @Test
    fun version3SendsPositionAndAColourByte() {
        val message = BoardLights.aurora(listOf(Light(300, 0x00FF00), Light(5, 0xFF00FF)), 3).single()
        // 01, length 7, checksum, 02, T, 300 low and high, green, 5, 0, magenta, 03.
        val body = bytes(0x54, 0x2C, 0x01, 0x1C, 0x05, 0x00, 0xE3)
        val checksum = (body.sumOf { it.toInt() and 0xFF } and 0xFF) xor 0xFF
        assertArrayEquals(bytes(0x01, 7, checksum, 0x02) + body + bytes(0x03), message)
    }

    @Test
    fun version2PacksTheColourWithThePositionsTopBits() {
        val message = BoardLights.aurora(listOf(Light(300, 0x00FFFF)), 2).single()
        // P, 300 & 0xFF = 0x2C, then cyan 00 11 11 and position bits 01.
        assertEquals(listOf(0x50, 0x2C, 0b00111101), message.drop(4).dropLast(1).map { it.toInt() and 0xFF })
    }

    @Test
    fun manyHoldsSplitIntoFirstMiddleAndLast() {
        val lights = (0 until 200).map { Light(it, 0x00FFFF) }
        val messages = BoardLights.aurora(lights, 3)
        assertEquals(listOf('R', 'Q', 'S'), messages.map { (it[4].toInt() and 0xFF).toChar() })
        assertEquals(listOf(84, 84, 32), messages.map { (it.size - 6) / 3 })
        assertEquals(listOf(20, 20, 20, 6), BoardLights.chunks(ByteArray(66)).map { it.size })
    }

    @Test
    fun aKilterClimbLightsItsHoldsOnTheSizesLeds() {
        val holds = BoardGeometry.holds("p4125r42p4334r44p9999r43")
        val lights = BoardLights.kilterLights(holds, mapOf("4125" to 86, "4334" to 12))
        assertEquals(listOf(Light(86, 0x00FF00), Light(12, 0xFF00FF)), lights)
        assertEquals(3, BoardLights.auroraVersion("Kilter Board#1234@3"))
        assertEquals(2, BoardLights.auroraVersion("Kilter Board#1234"))
    }

    @Test
    fun moonBoardLedsSnakeUpAndDownTheColumns() {
        // A1 is the first LED, A18 the 18th, then B18 and down to B1.
        assertEquals(0, BoardLights.moonLed(1, 1, 18, 11))
        assertEquals(17, BoardLights.moonLed(1, 18, 18, 11))
        assertEquals(18, BoardLights.moonLed(2, 18, 18, 11))
        assertEquals(35, BoardLights.moonLed(2, 1, 18, 11))
        assertEquals(0, BoardLights.moonLed(11, 1, 18, 11, BoardLights.MoonOrder.FROM_K1))
        assertEquals(0, BoardLights.moonLed(1, 12, 12, 11, BoardLights.MoonOrder.FROM_A_TOP))
        assertEquals(
            "l#S0,P35,E17#",
            BoardLights.moonCommand(listOf(0 to HoldRole.START, 35 to HoldRole.HAND, 17 to HoldRole.FINISH)).decodeToString(),
        )
    }
}
