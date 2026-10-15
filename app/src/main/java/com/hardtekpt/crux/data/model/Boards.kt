package com.hardtekpt.crux.data.model

/**
 * Which board a Board section is. [key] is what the database and backups keep, so it never
 * changes when a name does; a section with no model is a custom board (spray wall, home board).
 * [sizes] are the Kilter sizes in width×height feet ("12x12"); [angles] the only angles the board
 * is built at, and [fixedAngle] the one a board can only be at.
 */
enum class BoardModel(
    val key: String,
    val label: String,
    val maker: String,
    val sizes: List<String> = emptyList(),
    val hasHolds: Boolean = false,
    val angles: List<Int>? = null,
    val fixedAngle: Int? = null,
    val hint: String? = null,
) {
    KILTER_ORIGINAL("kilter_original", "Kilter Original", "Kilter", sizes = listOf("7x10", "8x12", "12x12", "16x12"), hint = "7×10 to 16×12"),
    KILTER_HOMEWALL(
        "kilter_homewall",
        "Kilter Homewall",
        "Kilter",
        sizes = listOf("7x10", "8x12", "10x10", "10x12"),
        hasHolds = true,
        hint = "7×10 to 10×12 · Full ride or Mainline",
    ),
    MOON_2016("moon_2016", "MoonBoard 2016", "MoonBoard", angles = listOf(25, 40), hint = "25° or 40°"),
    MOON_2017("moon_2017", "MoonBoard 2017", "MoonBoard", angles = listOf(25, 40), hint = "25° or 40°"),
    MOON_2019("moon_2019", "MoonBoard 2019", "MoonBoard", angles = listOf(25, 40), hint = "25° or 40°"),
    MOON_2024("moon_2024", "MoonBoard 2024", "MoonBoard", angles = listOf(25, 40), hint = "25° or 40°"),
    MOON_MINI_2020("moon_mini_2020", "Mini MoonBoard 2020", "MoonBoard", angles = listOf(40), fixedAngle = 40, hint = "40°"),
    MOON_MINI_2025("moon_mini_2025", "Mini MoonBoard 2025", "MoonBoard", angles = listOf(40), fixedAngle = 40, hint = "40°"),
    ;

    val needsSize: Boolean get() = sizes.isNotEmpty()

    companion object {
        fun fromKey(key: String?): BoardModel? = entries.firstOrNull { it.key == key }
    }
}

/** A Kilter Homewall's holds: the full set, or the mainline set alone. */
enum class BoardHolds(val key: String, val label: String, val hint: String) {
    FULL_RIDE("full_ride", "Full ride", "Mainline and Auxiliary holds"),
    MAINLINE("mainline", "Mainline only", "The smaller set"),
    ;

    companion object {
        fun fromKey(key: String?): BoardHolds? = entries.firstOrNull { it.key == key }
    }
}

/** "12x12" as people write it: "12×12". */
fun boardSizeLabel(size: String): String = size.replace('x', '×')

/** What a board section is: "Kilter Original 12×12", "Kilter Homewall 10×12 · Full ride", or null for a custom board. */
fun boardLabel(model: BoardModel?, size: String?, holds: BoardHolds?): String? {
    model ?: return null
    return listOfNotNull(
        listOfNotNull(model.label, size?.takeIf { it in model.sizes }?.let(::boardSizeLabel)).joinToString(" "),
        holds?.takeIf { model.hasHolds }?.label,
    ).joinToString(" · ")
}
