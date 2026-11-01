package com.megapro.labyrinth.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LeaderboardTest {
    @Test
    fun ranksByLevelThenTimeThenEarlierRun() {
        val b = Leaderboard(MemoryStore())
        b.record(1, "slow-high", 5, 300f)
        b.record(2, "fast-high", 5, 120f)
        b.record(3, "low", 7, 50f)
        b.record(4, "tie-later", 5, 120f) // same level and time as fast-high, arrived later
        assertEquals(listOf("low", "fast-high", "tie-later", "slow-high"), b.entries.map { it.name })
    }

    @Test
    fun keepsTenAndTellsWhoQualifies() {
        val b = Leaderboard(MemoryStore())
        for (i in 1..10) b.record(i.toLong(), "p$i", 3, 100f + i)
        assertEquals(10, b.entries.size)
        assertTrue(b.qualifies(99, 3, 100.5f)) // faster than the slowest
        assertFalse(b.qualifies(99, 3, 200f)) // slower than all ten
        assertTrue(b.qualifies(99, 4, 999f)) // more levels beats any time
        assertFalse(b.qualifies(99, 0, 1f)) // nothing finished: not ranked
        assertEquals(1, b.record(99, "new", 4, 999f))
        assertEquals(10, b.entries.size)
        assertFalse(b.entries.any { it.name == "p10" }) // the slowest fell off
    }

    @Test
    fun aNamedRunPlayedAgainKeepsItsBestStanding() {
        val b = Leaderboard(MemoryStore())
        b.record(1L, "Max", 5, 200f)
        b.improve(1L, 3, 100f) // a worse second attempt changes nothing
        assertEquals(5, b.entries.single().level); assertEquals(200f, b.entries.single().time, 1e-3f)
        b.improve(1L, 5, 150f) // same distance, faster: the better time stays
        assertEquals(150f, b.entries.single().time, 1e-3f)
        b.improve(1L, 8, 400f) // further beats faster
        assertEquals(8, b.entries.single().level); assertEquals("Max", b.entries.single().name)
        b.improve(2L, 9, 100f) // an unknown run is not invented
        assertEquals(1, b.entries.size)
    }

    @Test
    fun aRunHasOneEntryAndSurvivesReload() {
        val store = MemoryStore()
        val b = Leaderboard(store)
        b.record(7, "first", 2, 40f)
        b.record(7, "first", 3, 70f) // the same run improves: replaces, never duplicates
        assertEquals(1, b.entries.size); assertEquals(3, b.entries[0].level)
        val again = Leaderboard(store)
        assertEquals(b.entries, again.entries)
        assertEquals("first", again.nameFor(7))
    }
}

class LevelBoardsTest {
    @Test
    fun eachBoardKeepsItsTenFastest() {
        val lb = LevelBoards(MemoryStore())
        for (i in 1..12) lb.record(5, i.toLong(), 100f - i) // run 12 is the fastest
        assertEquals(10, lb.entries(5).size)
        assertEquals(12L, lb.entries(5)[0].runId)
        assertEquals(listOf(88f, 89f, 90f), lb.entries(5).take(3).map { it.time })
        assertTrue(lb.entries(6).isEmpty())
    }

    @Test
    fun namesArriveAtTheEndAndLandOnEveryBoardOfTheRun() {
        val store = MemoryStore()
        val lb = LevelBoards(store)
        lb.record(1, 42, 12f); lb.record(2, 42, 20f); lb.record(2, 43, 15f)
        assertEquals("", lb.entries(1)[0].name)
        assertTrue(lb.hasRun(42)); assertFalse(lb.hasRun(7))
        lb.name(42, " Dan ")
        assertEquals("Dan", lb.entries(1)[0].name)
        assertEquals(listOf("", "Dan"), lb.entries(2).map { it.name }) // run 43 faster, still unnamed
        lb.name(43, "")
        assertEquals("PLAYER", lb.entries(2)[0].name)
        assertEquals(1, lb.rank(2, 43)); assertEquals(2, lb.rank(2, 42)); assertEquals(0, lb.rank(2, 99))
        val again = LevelBoards(store)
        assertEquals(lb.entries(2), again.entries(2))
    }

    @Test
    fun aRunHoldsOnePlacePerBoardAndClearedStorageMeansEmptyBoards() {
        val lb = LevelBoards(MemoryStore())
        lb.record(3, 1, 30f); lb.record(3, 1, 25f)
        assertEquals(1, lb.entries(3).size); assertEquals(25f, lb.entries(3)[0].time)
        lb.record(3, 1, 40f) // a slower replay does not take the better time away
        assertEquals(25f, lb.entries(3)[0].time)
        assertTrue(LevelBoards(MemoryStore()).entries(3).isEmpty())
    }
}

class SaveSlotTest {
    @Test
    fun theSaveCarriesTheRunAndEveryBoardTime() {
        val store = MemoryStore()
        val slot = SaveSlot(store)
        assertEquals(null, slot.read())
        slot.write(4, 150.5f, 42L, listOf(40f, 52.25f, 58.25f))
        val saved = SaveSlot(store).read()!!
        assertEquals(4, saved.level); assertEquals(150.5f, saved.time, 1e-6f); assertEquals(42L, saved.runId)
        assertEquals(listOf(40f, 52.25f, 58.25f), saved.boardTimes)
        slot.write(2, 30f, 7L, listOf(30f)) // one slot: replaced
        assertEquals(listOf(30f), SaveSlot(store).read()!!.boardTimes)
    }
}
