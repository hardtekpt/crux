package com.hardtekpt.crux.data

import android.content.Context
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.float
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/** What a hold is for on a board climb. */
enum class HoldRole(val label: String) { START("start"), HAND("hand"), FINISH("finish"), FOOT("foot") }

/** A hold a climb names: its id on the board and its role. */
data class Hold(val id: String, val role: HoldRole)

/** Where a hold sits on a board; a MoonBoard's carries its grid name, like F4. */
data class HoldPoint(val x: Float, val y: Float, val name: String? = null)

/**
 * One board layout's hold positions: every hold a climb can name, and for Kilter the edges of
 * each size (left, right, bottom, top). A MoonBoard is a grid of [columns] by [rows].
 */
data class BoardLayout(
    val key: String,
    val points: Map<String, HoldPoint>,
    val sizes: Map<String, List<Float>> = emptyMap(),
    val rows: Int? = null,
    val columns: Int? = null,
    /** Kilter: for each size, the LED that lights each hold. */
    val leds: Map<String, Map<String, Int>> = emptyMap(),
) {
    val isGrid: Boolean get() = rows != null
}

/**
 * Hold positions for the boards Crux knows, built into the app (assets/board_holes.json, made by
 * scripts/board_holes.py from Boardsesh's hardware catalogue): numbers only, no climbs or images.
 */
object BoardGeometry {
    @Volatile private var layouts: Map<String, BoardLayout>? = null

    /** The layout for a climb's [board] ("kilter:8:25" or "moonboard:7"), and the size id when it names one. */
    fun layout(context: Context, board: String): Pair<BoardLayout, String?>? {
        val parts = board.split(':')
        if (parts.size < 2) return null
        val layout = all(context)["${parts[0]}:${parts[1]}"] ?: return null
        return layout to parts.getOrNull(2)
    }

    private fun all(context: Context): Map<String, BoardLayout> = layouts ?: synchronized(this) {
        layouts ?: context.applicationContext.assets.open(ASSET).use { parse(it.readBytes().decodeToString()) }.also { layouts = it }
    }

    internal fun parse(text: String): Map<String, BoardLayout> = Json.parseToJsonElement(text).jsonObject.mapValues { (key, value) ->
        val obj = value.jsonObject
        BoardLayout(
            key = key,
            points = obj.getValue("points").jsonObject.mapValues { (_, p) ->
                val a = p.jsonArray
                HoldPoint(a[0].jsonPrimitive.float, a[1].jsonPrimitive.float, a.getOrNull(2)?.jsonPrimitive?.contentOrNull)
            },
            sizes = (obj["sizes"] as? JsonObject)?.mapValues { (_, s) -> (s as JsonArray).map { it.jsonPrimitive.float } }.orEmpty(),
            leds = (obj["leds"] as? JsonObject)?.mapValues { (_, size) -> size.jsonObject.mapValues { it.value.jsonPrimitive.int } }.orEmpty(),
            rows = obj["rows"]?.jsonPrimitive?.int,
            columns = obj["columns"]?.jsonPrimitive?.int,
        )
    }

    /** A board's hold list ("p4125r42p4146r45…") as holds and their roles. */
    fun holds(frames: String): List<Hold> = Regex("p(\\d+)r(\\d+)").findAll(frames).map { m ->
        Hold(m.groupValues[1], role(m.groupValues[2].toInt()))
    }.toList()

    /** Kilter Original's roles are 12 to 15, a Homewall's and a MoonBoard's 42 to 45: start, hand, finish, foot. */
    private fun role(id: Int): HoldRole = when (id % 30) {
        12 -> HoldRole.START
        14 -> HoldRole.FINISH
        15 -> HoldRole.FOOT
        else -> HoldRole.HAND
    }

    /** "13 holds: 2 start, 4 hand, 2 finish, 5 foot". */
    fun describe(holds: List<Hold>): String {
        val counts = HoldRole.entries.mapNotNull { role -> holds.count { it.role == role }.takeIf { it > 0 }?.let { "$it ${role.label}" } }
        return "${holds.size} ${if (holds.size == 1) "hold" else "holds"}: ${counts.joinToString(", ")}"
    }

    private const val ASSET = "board_holes.json"
}
