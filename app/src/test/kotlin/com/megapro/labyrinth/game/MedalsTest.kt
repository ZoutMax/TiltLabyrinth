package com.megapro.labyrinth.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MedalsTest {
    @Test
    fun parGrowsWithTheBoardsAndMedalsFollowIt() {
        assertTrue("par rises from the first board to the last", Levels.parTime(Levels.COUNT) > Levels.parTime(1) * 3f)
        assertTrue("the hardest clean run fits the three-minute limit", Levels.parTime(Levels.COUNT) < Game.TIME_LIMIT * 0.8f)
        for (level in listOf(1, 25, 50, 100)) {
            val par = Levels.parTime(level)
            assertEquals(Medal.GOLD, Levels.medal(level, par))
            assertEquals(Medal.GOLD, Levels.medal(level, par * 1.14f))
            assertEquals(Medal.SILVER, Levels.medal(level, par * 1.3f))
            assertEquals(Medal.BRONZE, Levels.medal(level, par * 1.9f))
            assertNull(Levels.medal(level, par * 2.1f))
        }
    }
}
