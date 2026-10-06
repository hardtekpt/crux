package com.hardtekpt.crux.data.model

/**
 * Approximate equivalences between common grading systems, for the converter tool only.
 * Logged climbs are never converted: they keep the scale and grade they were logged in.
 *
 * Rows follow the widely used comparison charts (Mountain Project's international grade
 * chart, https://www.mountainproject.com/international-climbing-grades, and the tables in
 * Wikipedia's "Grade (climbing)", https://en.wikipedia.org/wiki/Grade_(climbing)), anchored
 * on 5.10a = 6a, 5.11a = 6c, 5.12a = 7a+, 5.13a = 7c+, 5.14a = 8b+, 5.15a = 9a+ and
 * V3 = 6A, V6 = 7A, V10 = 7C+. British grades describe trad climbs (adjectival plus
 * technical) and are the roughest fit of all.
 */
object GradeConversion {
    enum class System(val label: String, val region: String, val discipline: Discipline) {
        FRENCH("French", "Europe, sport", Discipline.ROUTE),
        YDS("YDS", "North America", Discipline.ROUTE),
        UIAA("UIAA", "Central Europe", Discipline.ROUTE),
        BRITISH("British", "UK trad", Discipline.ROUTE),
        EWBANK("Ewbank", "Australia, NZ", Discipline.ROUTE),
        FONT("Font", "Europe", Discipline.BOULDER),
        V("V scale", "North America", Discipline.BOULDER),
    }

    /** One step of difficulty, as each system writes it. */
    data class Row(val values: Map<System, String>)

    private fun route(french: String, yds: String, uiaa: String, british: String, ewbank: String) = Row(
        mapOf(System.FRENCH to french, System.YDS to yds, System.UIAA to uiaa, System.BRITISH to british, System.EWBANK to ewbank),
    )

    private fun boulder(font: String, v: String) = Row(mapOf(System.FONT to font, System.V to v))

    val routes: List<Row> = listOf(
        route("4a", "5.4", "IV", "S 4a", "12"),
        route("4b", "5.5", "IV+", "HS 4b", "13"),
        route("4c", "5.6", "V-", "VS 4b", "14"),
        route("5a", "5.7", "V", "VS 4c", "15"),
        route("5b", "5.8", "V+", "HVS 5a", "16"),
        route("5c", "5.9", "VI", "HVS 5a", "17"),
        route("6a", "5.10a", "VI+", "E1 5a", "18"),
        route("6a+", "5.10b", "VII-", "E1 5b", "19"),
        route("6b", "5.10c", "VII", "E2 5b", "20"),
        route("6b+", "5.10d", "VII+", "E2 5c", "21"),
        route("6c", "5.11a", "VII+", "E3 5c", "22"),
        route("6c+", "5.11b", "VIII-", "E3 6a", "23"),
        route("6c+", "5.11c", "VIII-", "E4 6a", "23"),
        route("7a", "5.11d", "VIII", "E4 6a", "24"),
        route("7a+", "5.12a", "VIII+", "E5 6a", "25"),
        route("7b", "5.12b", "VIII+", "E5 6b", "26"),
        route("7b+", "5.12c", "IX-", "E6 6b", "27"),
        route("7c", "5.12d", "IX", "E6 6b", "28"),
        route("7c+", "5.13a", "IX+", "E6 6c", "29"),
        route("8a", "5.13b", "IX+", "E7 6c", "29"),
        route("8a+", "5.13c", "X-", "E7 7a", "30"),
        route("8b", "5.13d", "X", "E8 6c", "31"),
        route("8b+", "5.14a", "X+", "E8 7a", "32"),
        route("8c", "5.14b", "X+", "E9 7a", "33"),
        route("8c+", "5.14c", "XI-", "E10 7a", "34"),
        route("9a", "5.14d", "XI", "E10 7b", "35"),
        route("9a+", "5.15a", "XI+", "E11 7c", "36"),
        route("9b", "5.15b", "XI+", "E11 7c", "37"),
        route("9b+", "5.15c", "XII-", "E12 7c", "38"),
        route("9c", "5.15d", "XII", "E12 8a", "39"),
    )

    val boulders: List<Row> = listOf(
        boulder("3", "VB"),
        boulder("4", "V0"),
        boulder("4+", "V0"),
        boulder("5", "V1"),
        boulder("5+", "V2"),
        boulder("6A", "V3"),
        boulder("6A+", "V3"),
        boulder("6B", "V4"),
        boulder("6B+", "V4"),
        boulder("6C", "V5"),
        boulder("6C+", "V5"),
        boulder("7A", "V6"),
        boulder("7A+", "V7"),
        boulder("7B", "V8"),
        boulder("7B+", "V8"),
        boulder("7C", "V9"),
        boulder("7C+", "V10"),
        boulder("8A", "V11"),
        boulder("8A+", "V12"),
        boulder("8B", "V13"),
        boulder("8B+", "V14"),
        boulder("8C", "V15"),
        boulder("8C+", "V16"),
        boulder("9A", "V17"),
    )

    fun rows(discipline: Discipline): List<Row> = if (discipline == Discipline.BOULDER) boulders else routes

    fun systems(discipline: Discipline): List<System> = System.entries.filter { it.discipline == discipline }

    /** A system's grades, easiest first, each once. */
    fun grades(system: System): List<String> = rows(system.discipline).map { it.values.getValue(system) }.distinct()

    /** The rows a grade covers (a grade can span more than one step of another system). */
    fun rowsFor(system: System, grade: String): List<Row> = rows(system.discipline).filter { it.values[system] == grade }

    /**
     * [grade] in [system] written in [target]: one grade, or a range like `5b–5c` when the
     * source grade spans several of the target's.
     */
    fun convert(system: System, grade: String, target: System): String? {
        val found = rowsFor(system, grade).mapNotNull { it.values[target] }.distinct()
        return when (found.size) {
            0 -> null
            1 -> found.single()
            else -> "${found.first()}–${found.last()}"
        }
    }
}
