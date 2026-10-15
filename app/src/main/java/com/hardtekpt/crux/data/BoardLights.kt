package com.hardtekpt.crux.data

import java.util.UUID

/** A light to send: which LED and its colour (0xRRGGBB). */
data class Light(val position: Int, val color: Int)

/**
 * What a board's lights need over Bluetooth: the bytes for a climb, split the way the board wants.
 * Pure Kotlin, so it is unit-tested without a phone or a board.
 */
object BoardLights {
    /** Aurora boards (Kilter) advertise this service; commands go to Nordic UART. */
    val AURORA_SERVICE: UUID = UUID.fromString("4488b571-7806-4df6-bcff-a2897e4953ff")
    val UART_SERVICE: UUID = UUID.fromString("6e400001-b5a3-f393-e0a9-e50e24dcca9e")
    val UART_WRITE: UUID = UUID.fromString("6e400002-b5a3-f393-e0a9-e50e24dcca9e")

    /** First-generation MoonBoard LED boxes use a RedBearLab module instead of Nordic UART. */
    val REDBEAR_SERVICE: UUID = UUID.fromString("713d0000-503e-4c75-ba94-3148f18d941e")
    val REDBEAR_WRITE: UUID = UUID.fromString("713d0003-503e-4c75-ba94-3148f18d941e")

    /** Bluetooth writes go in pieces of this many bytes. */
    const val CHUNK = 20

    /** Protocols a facility's paired board speaks; kept on the section. */
    const val AURORA_V2 = 2
    const val AURORA_V3 = 3
    const val MOONBOARD = 1

    /** The colour each role lights in, as the boards' own apps use them. */
    fun color(role: HoldRole): Int = when (role) {
        HoldRole.START -> 0x00FF00
        HoldRole.HAND -> 0x00FFFF
        HoldRole.FINISH -> 0xFF00FF
        HoldRole.FOOT -> 0xFFA500
    }

    /** The Aurora protocol version a Kilter board's name says ("Kilter Board#1234@3"); 2 when it doesn't. */
    fun auroraVersion(name: String?): Int = name?.substringAfterLast('@', "")?.toIntOrNull()?.takeIf { it == AURORA_V3 } ?: AURORA_V2

    // ---- Aurora (Kilter) -------------------------------------------------------------------

    private const val V2_MIDDLE = 'M'.code
    private const val V2_FIRST = 'N'.code
    private const val V2_LAST = 'O'.code
    private const val V2_ONLY = 'P'.code
    private const val V3_MIDDLE = 'Q'.code
    private const val V3_FIRST = 'R'.code
    private const val V3_LAST = 'S'.code
    private const val V3_ONLY = 'T'.code

    /** A message's body can be at most this long. */
    private const val MAX_BODY = 255

    /**
     * The messages that set a Kilter's lights to exactly [lights] (none clears the board): each
     * hold's LED position and colour, in as many messages as it takes, first, middle and last
     * marked so the board shows them together.
     */
    fun aurora(lights: List<Light>, version: Int): List<ByteArray> {
        val v3 = version >= AURORA_V3
        val per = if (v3) 3 else 2
        val holdsPerMessage = (MAX_BODY - 1) / per
        val groups = lights.chunked(holdsPerMessage).ifEmpty { listOf(emptyList()) }
        return groups.mapIndexed { index, group ->
            val command = when {
                groups.size == 1 -> if (v3) V3_ONLY else V2_ONLY
                index == 0 -> if (v3) V3_FIRST else V2_FIRST
                index == groups.lastIndex -> if (v3) V3_LAST else V2_LAST
                else -> if (v3) V3_MIDDLE else V2_MIDDLE
            }
            val body = ArrayList<Byte>(1 + group.size * per)
            body += command.toByte()
            group.forEach { light ->
                val r = (light.color shr 16) and 0xFF
                val g = (light.color shr 8) and 0xFF
                val b = light.color and 0xFF
                if (v3) {
                    body += (light.position and 0xFF).toByte()
                    body += ((light.position shr 8) and 0xFF).toByte()
                    // RRRGGGBB.
                    body += (((r / 32) shl 5) or ((g / 32) shl 2) or (b / 64)).toByte()
                } else {
                    body += (light.position and 0xFF).toByte()
                    // RRGGBB then the position's top two bits.
                    body += (((r / 64) shl 6) or ((g / 64) shl 4) or ((b / 64) shl 2) or ((light.position shr 8) and 0x03)).toByte()
                }
            }
            frame(body.toByteArray())
        }
    }

    /** 01, length, checksum, 02, body, 03: the checksum is the body's byte sum, inverted. */
    private fun frame(body: ByteArray): ByteArray {
        val checksum = body.fold(0) { sum, b -> (sum + (b.toInt() and 0xFF)) and 0xFF } xor 0xFF
        return byteArrayOf(0x01, body.size.toByte(), checksum.toByte(), 0x02) + body + byteArrayOf(0x03)
    }

    /** A Kilter climb's lights on a size: each hold's LED for that size, in its role's colour. Holds the size lacks are left out. */
    fun kilterLights(holds: List<Hold>, leds: Map<String, Int>): List<Light> = holds.mapNotNull { hold -> leds[hold.id]?.let { Light(it, color(hold.role)) } }

    // ---- MoonBoard ---------------------------------------------------------------------------

    /**
     * Where a MoonBoard's LED string starts, which the first-use check finds out: A1 (bottom left)
     * up column A then down B, and so on; or the mirror of that.
     */
    enum class MoonOrder { FROM_A1, FROM_K1, FROM_A_TOP }

    /** The LED number of grid hold [column] (1 for A) and [row] (1 at the bottom) on a board of [rows] by [columns]. */
    fun moonLed(column: Int, row: Int, rows: Int, columns: Int, order: MoonOrder = MoonOrder.FROM_A1): Int {
        val c = if (order == MoonOrder.FROM_K1) columns - column else column - 1
        val upward = (c % 2 == 0) == (order != MoonOrder.FROM_A_TOP)
        return c * rows + if (upward) row - 1 else rows - row
    }

    /** The MoonBoard command lighting a climb: "l#S12,P45,E99#", start, hand and finish by LED number. */
    fun moonCommand(holds: List<Pair<Int, HoldRole>>): ByteArray {
        val parts = holds.map { (led, role) ->
            val letter = when (role) {
                HoldRole.START -> "S"
                HoldRole.FINISH -> "E"
                else -> "P"
            }
            "$letter$led"
        }
        return "l#${parts.joinToString(",")}#".toByteArray(Charsets.US_ASCII)
    }

    /** Bytes in Bluetooth-sized pieces. */
    fun chunks(message: ByteArray): List<ByteArray> = message.toList().chunked(CHUNK).map { it.toByteArray() }
}
