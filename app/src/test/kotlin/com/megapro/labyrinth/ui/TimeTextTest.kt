package com.megapro.labyrinth.ui

import org.junit.Assert.assertEquals
import org.junit.Test

class TimeTextTest {
    @Test
    fun timesAreCutToTenths() {
        assertEquals("0:00.0", formatTime(0f))
        assertEquals("1:05.3", formatTime(65.39f))
        assertEquals("3:00.0", formatTime(180f))
    }

    @Test
    fun comparisonUsesBoardTimesAndAgreesWithTheDisplayedNumbers() {
        assertEquals("You are 0:10.0 faster than your previous try (0:55.2).", compareText(55.2f, 45.2f))
        assertEquals("You are 0:03.5 slower than your previous try (0:30.0).", compareText(30.0f, 33.5f))
        assertEquals("Same time as your previous try (0:20.0).", compareText(20.04f, 20.01f))
        // Each time truncates on its own; the difference must match previous minus board as printed
        for (p in listOf(45.27f, 100.99f, 12.05f, 59.96f)) for (b in listOf(30.19f, 100.9f, 12.01f, 61.04f)) {
            val shownP = tenths(p); val shownB = tenths(b)
            val expected = kotlin.math.abs(shownP - shownB)
            val text = compareText(p, b)
            val shownD = Regex("You are (\\d+):(\\d+)\\.(\\d)").find(text)?.let { it.groupValues[1].toLong() * 600 + it.groupValues[2].toLong() * 10 + it.groupValues[3].toLong() } ?: 0L
            assertEquals("$p vs $b: $text", expected, shownD)
        }
    }
}
