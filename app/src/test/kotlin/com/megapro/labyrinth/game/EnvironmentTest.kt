package com.megapro.labyrinth.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EnvironmentTest {
    @Test
    fun everyBoardHasItsOwnWorld() {
        val all = (0..Levels.COUNT).map { Environment.of(it) }
        assertEquals(Theme.WORKSHOP, all[0].theme)
        assertEquals("Workshop", all[0].name)
        assertEquals(Theme.FOREST, all[1].theme); assertEquals(Theme.FOREST, all[10].theme)
        assertEquals(Theme.SEA, all[11].theme); assertEquals(Theme.STARS, all[100].theme)
        assertEquals("Starlight 10", all[100].name)
        // No two boards share music: theme, key, tempo and voice together differ everywhere
        val tunes = all.map { listOf(it.theme, it.root, it.bpm, it.instrument, it.scale.toList(), it.seed) }
        assertEquals(Levels.COUNT + 1, tunes.toSet().size)
        // and no two share a lamp palette
        assertEquals(Levels.COUNT + 1, all.map { it.lamps.toList() }.toSet().size)
        // Ten boards in a row never repeat a key
        for (start in 1..(Levels.COUNT - 9)) assertEquals(10, (start until start + 10).map { Environment.of(it).root }.toSet().size)
        for (e in all) { assertTrue(e.bpm in 55..140); assertTrue(e.root in 150f..420f) }
        assertTrue("the same board gives the same world", Environment.of(42) === Environment.of(42))
    }

    @Test
    fun eachWorldIsLitInColoursThatMatchIt() {
        fun rgb(c: Long) = Triple(((c shr 16) and 0xFF) / 255f, ((c shr 8) and 0xFF) / 255f, (c and 0xFF) / 255f)
        for (level in 1..Levels.COUNT) {
            val e = Environment.of(level)
            // Lamps are bright and coloured, never grey or dim
            for (c in e.lamps) { val (r, g, b) = rgb(c); assertTrue("board $level lamp is bright", maxOf(r, g, b) > 0.7f) }
            // The shade is a deep colour, the high sun nearly white
            val (sr, sg, sb) = rgb(e.shade); assertTrue("board $level shade is deep", maxOf(sr, sg, sb) < 0.25f)
            val (hr, hg, hb) = rgb(e.sunHigh); assertTrue("board $level high sun is pale", minOf(hr, hg, hb) > 0.65f)
            assertTrue(e.woodAmount > 0f)
        }
        // Only the fiery and the sun-baked worlds lean red: the others' lamps are not red on average
        val warm = setOf(Theme.VOLCANO, Theme.DESERT, Theme.TEMPLE)
        for (level in 1..Levels.COUNT) {
            val e = Environment.of(level); if (e.theme in warm) continue
            val avg = e.lamps.map { rgb(it) }.let { l -> Triple(l.sumOf { it.first.toDouble() }, l.sumOf { it.second.toDouble() }, l.sumOf { it.third.toDouble() }) }
            assertTrue("board $level (${e.theme}) is not reddish", avg.first < avg.second || avg.first < avg.third)
        }
        // Every board of a world shares its look, turned a little, and no two boards are lit alike
        assertEquals(Levels.COUNT + 1, (0..Levels.COUNT).map { Environment.of(it).lamps.toList() }.toSet().size)
        assertEquals(0f, Environment.of(0).woodAmount, 0f)
        assertEquals("the practice board keeps plain red, green and blue", listOf(0xFFFF3B3BL, 0xFF3BFF6AL, 0xFF3B8CFFL), Environment.of(0).lamps.toList())
    }
}
