package com.hardtekpt.crux.data

import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class BoardGeometryTest {
    private val context = ApplicationProvider.getApplicationContext<android.content.Context>()

    @Test
    fun aClimbsHoldsReadWithTheirRoles() {
        val holds = BoardGeometry.holds("p4125r42p4146r45p4212r43p4334r44p1090r12p1200r15")
        assertEquals(listOf("4125", "4146", "4212", "4334", "1090", "1200"), holds.map { it.id })
        assertEquals(
            listOf(HoldRole.START, HoldRole.FOOT, HoldRole.HAND, HoldRole.FINISH, HoldRole.START, HoldRole.FOOT),
            holds.map { it.role },
        )
        assertEquals("6 holds: 2 start, 1 hand, 1 finish, 2 foot", BoardGeometry.describe(holds))
    }

    @Test
    fun theBuiltInPositionsPlaceKilterAndMoonBoardHolds() {
        // Moon Landing's first start hold, on a Kilter Homewall 10×12.
        val (homewall, size) = BoardGeometry.layout(context, "kilter:8:25")!!
        assertEquals("25", size)
        assertEquals(HoldPoint(-40f, 92f), homewall.points["4125"])
        assertEquals(listOf(-56f, 56f, -12f, 144f), homewall.sizes["25"])
        // A Mini MoonBoard 2025 is an 11 by 12 grid of named holds.
        val (mini, none) = BoardGeometry.layout(context, "moonboard:7")!!
        assertNull(none)
        assertTrue(mini.isGrid)
        assertEquals(12, mini.rows)
        assertEquals(11, mini.columns)
        assertTrue(mini.points.values.all { it.name != null })
        // Every board Crux reads a catalogue for has positions.
        listOf("kilter:1", "moonboard:2", "moonboard:3", "moonboard:4", "moonboard:5", "moonboard:6").forEach {
            assertTrue(it, BoardGeometry.layout(context, it)!!.first.points.isNotEmpty())
        }
        assertNull(BoardGeometry.layout(context, "tension:9"))
    }
}
