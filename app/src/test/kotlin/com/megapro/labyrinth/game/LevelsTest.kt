package com.megapro.labyrinth.game

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LevelsTest {
    @Test
    fun everyLevelIsSolvableAndIdenticalOnRebuild() {
        for (level in 1..Levels.COUNT) {
            val a = Levels.maze(level)
            val b = Maze(a.cols, a.rows, level * 1000 + 17, 0) // same seed family builds deterministically
            assertTrue("level $level unsolvable", a.solutionLength > 0)
            assertEquals("level $level not deterministic", Maze(a.cols, a.rows, level * 1000 + 17, 0).walls.size, b.walls.size)
        }
    }

    @Test
    fun difficultyGrowsOverTheHundredLevels() {
        // Compare averages over bands of ten levels: route length must rise band over band
        val bands = (0 until 10).map { b -> (1..10).map { Levels.maze(b * 10 + it).solutionLength }.average() }
        for (i in 1 until bands.size) {
            assertTrue("band $i (${bands[i]}) not harder than band ${i - 1} (${bands[i - 1]})", bands[i] > bands[i - 1])
        }
        assertTrue(Levels.maze(1).cols < Levels.maze(100).cols)
        // Complexity climbs: routes get far longer and blind alleys multiply from the first ten boards to the last ten
        fun avg(range: IntRange, f: (Maze) -> Int) = range.map { f(Levels.maze(it)) }.average()
        assertTrue(avg(91..100) { it.solution.size } > 4 * avg(1..10) { it.solution.size })
        assertTrue(avg(91..100) { it.deadEnds } > 6 * avg(1..10) { it.deadEnds })
        for (level in 11..100 step 10) assertTrue("level $level route longer than level ${level - 10}", avg(level..minOf(level + 9, 100)) { it.solution.size } > avg(level - 10..level - 1) { it.solution.size })
    }
}

class WallSoundTest {
    @Test
    fun everyWallKnowsHowLongItIs() {
        for (level in listOf(0, 1, 37, 100)) {
            val m = Levels.maze(level)
            for (w in m.walls) {
                val len = m.runLength(w)
                val piece = kotlin.math.abs(w.x1 - w.x0) + kotlin.math.abs(w.y1 - w.y0)
                assertTrue("a wall is at least as long as its piece", len >= piece - 1e-3f)
            }
            assertEquals("the top of the frame is the whole width", m.cols.toFloat(), m.runLength(m.walls[0]), 1e-3f)
            assertEquals("its side is the whole height", m.rows.toFloat(), m.runLength(m.walls[2]), 1e-3f)
            assertTrue("inner walls come in many lengths", m.walls.drop(4).map { m.runLength(it) }.toSet().size >= 3)
        }
    }

    @Test
    fun longerWallsKnockDeeper() {
        val sizes = listOf(1f, 2f, 3f, 5f, 8f, 13f, 22f, 37f)
        val rates = sizes.map { GameSounds.knockRate(0.6f, it) }
        for (i in 1 until rates.size) assertTrue("${sizes[i]} cells knocks lower than ${sizes[i - 1]}", rates[i] < rates[i - 1])
        assertTrue("a stub ticks high", rates.first() > 1.05f)
        assertTrue("the frame thuds deep", rates.last() < 0.7f)
        assertTrue("a soft touch is a shade higher than a hard hit", GameSounds.knockRate(0.2f, 4f) > GameSounds.knockRate(1f, 4f))
    }

    @Test
    fun theGamePassesTheWallLengthWithEachKnock() {
        var sizes = ArrayList<Float>()
        val rec = object : GameSounds by GameSounds.None { override fun knock(strength: Float, size: Float) { sizes.add(size) } }
        val g = Game(sounds = rec)
        for (i in 0 until 16) g.onSensor(0f, 0f, 9.81f)
        g.onTap()
        for (i in 0 until 16) g.onSensor(4f, 0f, 9f) // tipped hard: the ball runs into a wall
        var t = 0f; while (t < 3f) { g.update(1f / 120f); t += 1f / 120f }
        assertTrue("the ball knocked something", sizes.isNotEmpty())
        assertTrue("each knock carries a real wall length", sizes.all { it >= 1f })
    }
}

class TrapsTest {
    @Test
    fun trapsAreFixedTimedHolesThatArriveGradually() {
        var previous = 0
        for (level in 1..Levels.COUNT) {
            val m = Levels.maze(level)
            val traps = Levels.traps(level)
            assertTrue("level $level: only the ten real kinds", traps.all { it.kind in setOf(TrapKind.BLINK, TrapKind.MAGNET, TrapKind.WIND, TrapKind.RADIOACTIVE, TrapKind.SPEED, TrapKind.TRAPDOOR, TrapKind.CRUSHER, TrapKind.LASER, TrapKind.ICE, TrapKind.SPINNER) })
            assertTrue("level $level: holes present", traps.any { it.kind == TrapKind.BLINK })
            for (t in traps) {
                val c = Cell(t.x.toInt(), t.y.toInt())
                assertTrue("level $level: trap on start or finish", c != m.start && c != m.finish)
            }
            assertTrue("level $level has fewer traps than level ${level - 1}", traps.size >= previous - 1)
            assertTrue("level $level has no trap", traps.isNotEmpty())
            previous = traps.size
            // Same level, same traps, every time
            assertEquals(traps.map { it.x to it.y }, Levels.traps(level).map { it.x to it.y })
        }
        assertTrue(Levels.traps(1).size >= 2)
        assertTrue(Levels.traps(100).size >= 30)
        // Placement: killers on the route, but never so many that the board drags; buggers feed killers
        for (level in 1..Levels.COUNT) {
            val m = Levels.maze(level); val traps = Levels.traps(level)
            val route = m.solution.toSet()
            val onRoute = traps.count { it.kind.killer && Cell(it.x.toInt(), it.y.toInt()) in route }
            assertTrue("level $level: $onRoute killers on the route", onRoute in 1..(1 + level * 8 / Levels.COUNT))
            val buggers = traps.filter { !it.kind.killer }
            if (buggers.size >= 4) {
                val fed = buggers.count { b -> traps.any { k -> k.kind.killer && kotlin.math.abs(k.x - b.x) <= 2.01f && kotlin.math.abs(k.y - b.y) <= 2.01f } }
                assertTrue("level $level: only $fed of ${buggers.size} buggers near a killer", fed * 10 >= buggers.size * 6)
            }
        }
        assertTrue(Levels.traps(1).any { it.kind == TrapKind.MAGNET })
        assertTrue(Levels.traps(10).any { it.kind == TrapKind.WIND })
        assertTrue(Levels.traps(12).any { it.kind == TrapKind.TRAPDOOR })
        assertTrue(Levels.traps(20).any { it.kind == TrapKind.RADIOACTIVE })
        assertTrue(Levels.traps(25).any { it.kind == TrapKind.SPEED })
        assertTrue(Levels.traps(25).any { it.kind == TrapKind.CRUSHER })
        assertTrue(Levels.traps(30).any { it.kind == TrapKind.SPINNER })
        assertTrue(Levels.traps(30).any { it.kind == TrapKind.LASER })
        assertTrue(Levels.traps(35).any { it.kind == TrapKind.ICE })
        assertEquals(10, Levels.traps(100).map { it.kind }.toSet().size)
        // Half killers, half buggers once everything is in play
        val kinds100 = Levels.traps(100).map { it.kind }
        assertTrue(kotlin.math.abs(kinds100.count { it.killer } - kinds100.count { !it.killer }) <= 2)
        // Every kind gets harder: longer active time and a stronger effect, or a shorter fuse
        for (kind in listOf(TrapKind.MAGNET, TrapKind.WIND, TrapKind.RADIOACTIVE, TrapKind.SPEED, TrapKind.SPINNER, TrapKind.ICE, TrapKind.LASER)) {
            val early = Levels.traps(40).first { it.kind == kind }
            val late = Levels.traps(100).first { it.kind == kind }
            assertTrue("$kind active time grows", late.duty * late.period > early.duty * early.period)
            if (kind != TrapKind.LASER) assertTrue("$kind effect grows", late.pull > early.pull)
        }
        run {
            val early = Levels.traps(40).first { it.kind == TrapKind.TRAPDOOR }; val late = Levels.traps(100).first { it.kind == TrapKind.TRAPDOOR }
            assertTrue("trapdoor drops sooner", late.hold < early.hold); assertTrue("trapdoor sees further", late.range > early.range)
        }
        run {
            val early = Levels.traps(40).first { it.kind == TrapKind.CRUSHER }; val late = Levels.traps(100).first { it.kind == TrapKind.CRUSHER }
            assertTrue("crusher warns less", late.hold < early.hold); assertTrue("crusher strikes more often", late.period < early.period); assertTrue("crusher kicks harder", late.pull > early.pull)
        }
        assertTrue(Levels.traps(100).filter { it.kind == TrapKind.BLINK }.map { it.period }.average() < Levels.traps(5).filter { it.kind == TrapKind.BLINK }.map { it.period }.average())
    }
}

class SameGameForEveryoneTest {
    /** Everything that defines the 100 boards, as text: size, every wall, every trap with its rhythm and strength. */
    private fun describeAllLevels(): String {
        val sb = StringBuilder()
        for (level in 1..Levels.COUNT) {
            val m = Levels.maze(level)
            sb.append("L").append(level).append(' ').append(m.cols).append('x').append(m.rows).append(" start=").append(m.start).append(" finish=").append(m.finish).append('\n')
            for (w in m.walls) sb.append(w.x0).append(',').append(w.y0).append('-').append(w.x1).append(',').append(w.y1).append(' ')
            sb.append('\n')
            for (t in Levels.traps(level)) sb.append(t.kind).append('@').append(t.x).append(',').append(t.y).append(" p=").append(t.period).append(" ph=").append(t.phase).append(" d=").append(t.duty).append(" pull=").append(t.pull).append(" hold=").append(t.hold).append('\n')
        }
        return sb.toString()
    }

    private fun sha256(text: String): String =
        java.security.MessageDigest.getInstance("SHA-256").digest(text.toByteArray()).joinToString("") { "%02x".format(it) }

    @Test
    fun aMazeRebuiltFromItsSeedIsIdentical() {
        for (level in listOf(1, 7, 42, 100)) {
            val a = Levels.maze(level)
            val b = Maze(a.cols, a.rows, a.seed, a.openings)
            assertEquals(a.walls.map { listOf(it.x0, it.y0, it.x1, it.y1) }, b.walls.map { listOf(it.x0, it.y0, it.x1, it.y1) })
            assertEquals(a.start, b.start); assertEquals(a.finish, b.finish)
        }
    }

    /** The pinned fingerprint of all 100 levels. If this fails, the game a player gets has changed:
     *  either revert, or knowingly update the constant (and tell the players). */
    @Test
    fun theHundredLevelsAreExactlyTheOnesEveryPlayerGets() {
        assertEquals(FINGERPRINT, sha256(describeAllLevels()))
    }

    companion object {
        const val FINGERPRINT = "2b3e1d89169a652298c8dce613489451b5890e35304819de6c575dd85679e24c"
    }
}


/** Each trap does what it says, checked by running the game on the level 0 test board. */
class TrapSimTest {
    private fun game(): Game = Game().also { it.startTest() }
    private fun trap(g: Game, kind: TrapKind): Trap = g.traps.first { it.kind == kind }
    private fun runUntil(g: Game, maxSeconds: Float = 20f, cond: () -> Boolean) {
        var t = 0f
        while (!cond() && t < maxSeconds) { g.update(1f / 120f); t += 1f / 120f }
        assertTrue("condition not reached within $maxSeconds s", cond())
    }
    private fun run(g: Game, seconds: Float) { var t = 0f; while (t < seconds) { g.update(1f / 120f); t += 1f / 120f } }
    private fun startX(g: Game) = g.maze.start.x + 0.5f
    /** A unit direction from the trap's cell into an open neighbour, so a placed ball has room to move. */
    private fun openDir(g: Game, t: Trap): Pair<Float, Float> {
        val x = t.x.toInt(); val y = t.y.toInt(); val m = g.maze
        return when {
            m.openRight(x, y) -> 1f to 0f
            m.openLeft(x, y) -> -1f to 0f
            m.openDown(x, y) -> 0f to 1f
            else -> 0f to -1f
        }
    }

    @Test
    fun holeSwallowsTheBallAndTheTestBoardRestarts() {
        val g = game(); val t = trap(g, TrapKind.BLINK)
        runUntil(g) { t.open }
        g.placeBall(t.cx, t.cy); g.update(1f / 120f)
        assertEquals(startX(g), g.ballX, 1e-3f); assertEquals(0.5f, g.ballY, 1e-3f)
    }

    @Test
    fun magnetCatchesHoldsReleasesAndDoesNotRecatchUntilTheBallLeaves() {
        val g = game(); val t = trap(g, TrapKind.MAGNET)
        runUntil(g) { t.open }
        g.placeBall(t.cx + 0.2f, t.cy); g.update(1f / 120f)
        assertTrue("caught", t.armed > 0f); assertEquals(t.cx, g.ballX, 1e-4f)
        runUntil(g, 10f) { t.armed <= 0f }
        run(g, 3f) // sits on the disc through the magnet's rest and its next on-phase
        assertTrue("not caught again while still on the disc", t.armed <= 0f)
    }

    @Test
    fun windPushesTheBallAway() {
        val g = game(); val t = trap(g, TrapKind.WIND)
        runUntil(g) { t.open }
        val (dx, dy) = openDir(g, t)
        g.placeBall(t.cx + dx * 0.45f, t.cy + dy * 0.45f); run(g, 0.3f)
        assertTrue("blown away", (g.ballX - t.cx) * dx + (g.ballY - t.cy) * dy > 0.6f)
    }

    @Test
    fun radioactiveSlimesTheBallWhileLeaking() {
        val g = game(); val t = trap(g, TrapKind.RADIOACTIVE)
        runUntil(g) { t.open }
        g.placeBall(t.cx + 0.5f, t.cy); g.update(1f / 120f)
        assertTrue(g.toxicLevel > 0.9f)
    }

    @Test
    fun speedTrapChargesTheBall() {
        val g = game(); val t = trap(g, TrapKind.SPEED)
        runUntil(g) { t.open }
        g.placeBall(t.cx + 0.5f, t.cy); g.update(1f / 120f)
        assertTrue(g.boostLevel > 0.9f)
    }

    @Test
    fun trapdoorArmsWhenTheBallIsNearAndDropsAfterItsDelay() {
        val g = game(); val t = trap(g, TrapKind.TRAPDOOR)
        assertTrue(t.armed < 0f)
        g.placeBall(t.cx + 0.6f, t.cy); g.update(1f / 120f)
        assertTrue("armed", t.armed >= 0f); assertTrue(!t.open)
        g.placeBall(t.cx + 0.6f, t.cy); run(g, t.hold + 0.1f)
        assertTrue("dropped open", t.open)
    }

    @Test
    fun crusherKicksABallInItsHaloWhenItStrikes() {
        val g = game(); val t = trap(g, TrapKind.CRUSHER)
        runUntil(g) { t.armed > 0.8f && !t.open } // late in the warning
        val (dx, dy) = openDir(g, t)
        g.placeBall(t.cx + dx * 0.5f, t.cy + dy * 0.5f)
        runUntil(g, 5f) { t.open }
        run(g, 0.1f)
        assertTrue("kicked away", (g.ballX - t.cx) * dx + (g.ballY - t.cy) * dy > 0.6f)
    }

    @Test
    fun crusherCrushesABallOnThePlateWhenItStrikes() {
        val g = game(); val t = trap(g, TrapKind.CRUSHER)
        runUntil(g) { t.armed > 0.8f && !t.open } // late in the warning
        g.placeBall(t.cx, t.cy) // sitting on the plate
        runUntil(g, 5f) { t.open }
        g.update(1f / 120f)
        assertEquals("crushed: the test board restarted", startX(g), g.ballX, 1e-3f)
    }

    @Test
    fun laserDestroysABallOnItsBeam() {
        val g = game(); val t = trap(g, TrapKind.LASER)
        runUntil(g) { t.open }
        assertTrue("beam reaches down the row", t.beam > 0.5f)
        val (dx, dy) = g.laserDir(t)
        g.placeBall(t.cx + dx * 0.5f, t.cy + dy * 0.5f); g.update(1f / 120f)
        assertEquals("destroyed: test board restarted", startX(g), g.ballX, 1e-3f)
    }

    @Test
    fun iceIsSlipperyAndOpensAsAHoleWhenCracked() {
        val g = game(); val t = trap(g, TrapKind.ICE)
        runUntil(g) { !t.open }
        g.placeBall(t.cx + 0.5f, t.cy); g.update(1f / 120f)
        assertTrue("on ice", g.onIce)
        runUntil(g) { t.open }
        g.placeBall(t.cx, t.cy); g.update(1f / 120f)
        assertEquals("fell through the cracked ice", startX(g), g.ballX, 1e-3f)
    }

    @Test
    fun spinnerSwirlsTheBallSideways() {
        val g = game(); val t = trap(g, TrapKind.SPINNER)
        runUntil(g) { t.open }
        g.placeBall(t.cx + 0.5f, t.cy); run(g, 0.25f)
        val dx = g.ballX - t.cx; val dy = g.ballY - t.cy
        assertTrue("moved sideways around the disc", kotlin.math.abs(dy) > 0.1f)
        assertTrue("still orbiting, not thrown out", dx * dx + dy * dy < (t.range + 1f) * (t.range + 1f))
    }

    @Test
    fun threeMinutesOnABoardLosesTheBall() {
        val g = game()
        run(g, 2f)
        assertEquals("the clock waits for the first move", 0f, g.elapsed, 1e-6f)
        g.placeBall(6.5f, 4.5f) // a quiet cell on the test board: no trap reaches it, the laser fires the other way
        run(g, Game.TIME_LIMIT - 1f)
        assertTrue(g.elapsed > Game.TIME_LIMIT - 2f)
        run(g, 1.5f) // the limit passes: the ball explodes, the test board is lost for the moment
        assertTrue("exploded", g.timedOut)
        run(g, 3f) // then the test board starts over by itself
        assertTrue(g.elapsed < 3f)
        assertFalse(g.timedOut)
        assertEquals(startX(g), g.ballX, 1e-3f)
    }

    @Test
    fun printBoardForAHumanLook() {
        for (level in listOf(12, 40)) {
            val m = Levels.maze(level); val traps = Levels.traps(level); val route = m.solution.toSet()
            val mark = HashMap<Cell, Char>()
            for (t in traps) mark[Cell(t.x.toInt(), t.y.toInt())] = when (t.kind) {
                TrapKind.BLINK -> 'O'; TrapKind.TRAPDOOR -> 'T'; TrapKind.CRUSHER -> 'C'; TrapKind.LASER -> 'L'; TrapKind.ICE -> 'I'
                TrapKind.MAGNET -> 'm'; TrapKind.WIND -> 'w'; TrapKind.RADIOACTIVE -> 'r'; TrapKind.SPEED -> 's'; TrapKind.SPINNER -> 'p'; else -> '?'
            }
            val sb = StringBuilder("level $level ${m.cols}x${m.rows} route=${m.solution.size} traps=${traps.size}\n")
            for (y in 0 until m.rows) {
                for (x in 0 until m.cols) {
                    val c = Cell(x, y)
                    sb.append(mark[c] ?: if (c == m.start) 'S' else if (c == m.finish) 'F' else if (c in route) '.' else ' ')
                    sb.append(if (m.openRight(x, y)) ' ' else '|')
                }
                sb.append('\n')
                for (x in 0 until m.cols) sb.append(if (m.openDown(x, y)) "  " else "--")
                sb.append('\n')
            }
            println(sb)
        }
    }
}

/** The save, load and restart flows, run on the real game. */
class RunFlowTest {
    private fun tick(g: Game, seconds: Float) { var t = 0f; while (t < seconds) { g.update(1f / 120f); t += 1f / 120f } }
    /** Plays the current board to its end: waits for the finish hole to open, then drops the ball in. */
    private fun finishBoard(g: Game) {
        var t = 0f
        while (!g.finishOpen && t < 20f) { g.update(1f / 120f); t += 1f / 120f }
        assertTrue("finish opens", g.finishOpen)
        g.placeBall(g.maze.finish.x + 0.5f, g.maze.finish.y + 0.5f)
        g.update(1f / 120f)
        assertEquals(Phase.FINISHED, g.phase)
    }

    @Test
    fun finishingBoardsBuildsTheRunHistoryAndRestartTakesTheLastOneBack() {
        val g = Game()
        g.onTap() // start level 1
        tick(g, 1f); g.placeBall(2.5f, 2.5f); tick(g, 1f) // the clock runs once the ball has moved
        finishBoard(g)
        val first = g.elapsed
        assertEquals(1, g.boardTimes.size); assertEquals(first, g.boardTimes[0], 1e-4f); assertEquals(first, g.runTime, 1e-4f)
        assertTrue(g.previousTry < 0f)
        g.restartLevel()
        assertEquals(Phase.PLAYING, g.phase); assertEquals(1, g.level)
        assertEquals(0, g.boardTimes.size); assertEquals(0f, g.runTime, 1e-6f)
        assertEquals("the given-up try is kept for comparison", first, g.previousTry, 1e-4f)
        tick(g, 0.5f); g.placeBall(2.5f, 2.5f); tick(g, 0.5f)
        finishBoard(g)
        assertEquals(1, g.boardTimes.size); assertEquals(g.elapsed, g.runTime, 1e-4f)
        assertTrue("comparison still available on the second finish", g.previousTry > 0f)
        g.onTap() // next board
        assertEquals(2, g.level); assertEquals(Phase.PLAYING, g.phase)
        assertTrue("comparison cleared on a new board", g.previousTry < 0f)
    }

    @Test
    fun theBallWaitsHalfASecondAtTheStartBeforeRolling() {
        fun rolled(seconds: Float): Float {
            val g = Game()
            for (i in 0 until 16) g.onSensor(0f, 0f, 9.81f) // held flat
            g.onTap() // level 1 starts, level taken from the flat hold
            val x0 = g.ballX
            for (i in 0 until 16) g.onSensor(-1f, 0f, 9.7f) // then tipped a little so the ball rolls right
            tick(g, seconds)
            return g.ballX - x0
        }
        assertEquals("no roll during the grace", 0f, rolled(Game.GRACE - 0.05f), 1e-4f)
        assertTrue("rolls after the grace", rolled(Game.GRACE + 0.08f) > 0.002f) // a short roll, well before any wall
    }

    @Test
    fun resumingFromPauseTakesTheCurrentHoldAsLevel() {
        val g = Game()
        for (i in 0 until 16) g.onSensor(0f, 0f, 9.81f)
        g.onTap(); g.pause()
        assertEquals(Phase.PAUSED, g.phase)
        for (i in 0 until 16) g.onSensor(-2.5f, 0f, 9.5f) // a new, tipped hold
        g.onTap() // resume: the tipped hold is now level, so the ball should not roll
        assertEquals(Phase.PLAYING, g.phase)
        val x0 = g.ballX
        tick(g, 1f)
        assertEquals("the re-levelled hold is neutral", x0, g.ballX, 0.02f)
    }

    @Test
    fun aFinishedBoardCanBeReplayedFromTheMainScreenUnscored() {
        val g = Game()
        var scored = 0; g.onBoardDone = { _, _ -> scored++ }; g.onLevelDone = { _, _ -> scored++ }; g.onRunOver = { _, _ -> scored++ }
        g.replay(7)
        assertEquals(Phase.PLAYING, g.phase); assertEquals(7, g.level); assertTrue(g.freePlay && g.isTestLevel)
        tick(g, 0.6f); g.placeBall(2.5f, 2.5f); tick(g, 0.5f)
        finishBoard(g)
        assertEquals(Phase.FINISHED, g.phase); assertEquals("free play is not scored", 0, scored); assertEquals(0, g.boardTimes.size)
        g.onTap()
        assertEquals("back to the menu at level 1", Phase.TITLE, g.phase); assertEquals(1, g.level); assertTrue(!g.freePlay)
        g.replay(3) // and losing the ball in free play ranks nothing either: the board just starts over
        tick(g, 0.6f); g.placeBall(g.maze.cols - 0.5f, 0.5f); tick(g, Game.TIME_LIMIT + 2f)
        assertEquals(Phase.LOST, g.phase); assertTrue(g.isTestLevel)
        tick(g, 3f)
        assertEquals("the board started over", Phase.PLAYING, g.phase); assertEquals(3, g.level)
        assertEquals("nothing of free play reaches the boards", 0, scored)
        tick(g, 0.6f); g.placeBall(2.5f, 2.5f); tick(g, 0.5f); finishBoard(g); g.onTap() // finish it: back to the menu
        assertEquals(Phase.TITLE, g.phase); assertEquals(1, g.level); assertTrue(!g.freePlay)
        g.onTap() // a real run starts
        g.replay(5)
        assertEquals("replay only from the menu", 1, g.level); assertTrue(!g.freePlay)
    }

    @Test
    fun backPausesARunAndNeverThrowsItAway() {
        val g = Game()
        assertFalse("on the menu Back leaves the game to the host", g.onBack())
        g.onTap() // level 1 starts
        assertTrue(g.onBack()); assertEquals("Back pauses the board", Phase.PAUSED, g.phase)
        assertFalse("paused, Back steps out of the game, keeping the run", g.onBack())
        assertEquals(Phase.PAUSED, g.phase); assertEquals(1, g.level)
        g.onTap(); tick(g, 0.6f); g.placeBall(2.5f, 2.5f); tick(g, 0.5f); finishBoard(g)
        assertFalse("on the level-done card Back keeps the card", g.onBack()); assertEquals(Phase.FINISHED, g.phase)
    }

    @Test
    fun backFromTheTestBoardOrFreePlayReturnsToTheMenu() {
        val g = Game(); g.startTest()
        assertTrue(g.onBack()); assertEquals(Phase.TITLE, g.phase); assertEquals(1, g.level)
        g.startTest(); g.playFree(37)
        assertTrue(g.onBack()); assertEquals(Phase.TITLE, g.phase); assertEquals(1, g.level); assertFalse(g.freePlay)
        g.replay(4)
        assertTrue(g.onBack()); assertEquals(Phase.TITLE, g.phase); assertFalse(g.freePlay)
    }

    @Test
    fun theTestBoardReelOpensAnyBoardForFreePlay() {
        val g = Game(); g.startTest()
        g.playFree(37)
        assertEquals(37, g.level); assertTrue(g.isTestLevel); assertTrue(g.freePlay)
        assertEquals(Levels.maze(37).solution.size, g.maze.solution.size); assertEquals(Levels.traps(37).size, g.traps.size)
        finishBoard(g) // nothing is scored
        assertEquals(0, g.boardTimes.size); assertEquals(0f, g.runTime, 1e-6f)
        g.onTap() // back to the menu, as after the test board
        assertEquals(Phase.TITLE, g.phase); assertEquals(1, g.level); assertFalse(g.freePlay)
        g.startTest(); g.playFree(12); g.playFree(0)
        assertEquals(0, g.level); assertFalse(g.freePlay); assertTrue(g.isTestLevel)
    }

    @Test
    fun saveThenLoadContinuesTheSameRunWithItsHistory() {
        val store = com.megapro.labyrinth.data.MemoryStore()
        val slot = com.megapro.labyrinth.data.SaveSlot(store)
        val g = Game()
        g.onTap(); tick(g, 0.5f); g.placeBall(2.5f, 2.5f); tick(g, 0.5f); finishBoard(g)
        g.onTap(); tick(g, 0.5f); g.placeBall(2.5f, 2.5f); tick(g, 0.5f); finishBoard(g) // two boards done
        slot.write(g.level + 1, g.runTime, g.runId, g.boardTimes.toList())
        val runId = g.runId; val runTime = g.runTime; val times = g.boardTimes.toList()
        // A fresh start later: new run, level 1, then LOAD
        val fresh = Game()
        assertEquals(1, fresh.level); assertTrue(fresh.runId != runId || fresh.runId == runId)
        val saved = com.megapro.labyrinth.data.SaveSlot(store).read()!!
        fresh.restore(saved.level, saved.time, saved.runId, saved.boardTimes)
        assertEquals(3, fresh.level); assertEquals(runTime, fresh.runTime, 1e-5f); assertEquals(runId, fresh.runId); assertEquals(times, fresh.boardTimes)
        assertEquals(Phase.TITLE, fresh.phase)
        fresh.onTap(); tick(fresh, 0.5f); fresh.placeBall(2.5f, 2.5f); tick(fresh, 0.5f); finishBoard(fresh)
        assertEquals(3, fresh.boardTimes.size); assertEquals(runTime + fresh.elapsed, fresh.runTime, 1e-4f)
    }

    @Test
    fun losingAfterLoadStartsANewRunButTheSaveStays() {
        val store = com.megapro.labyrinth.data.MemoryStore()
        val slot = com.megapro.labyrinth.data.SaveSlot(store)
        slot.write(5, 200f, 99L, listOf(50f, 50f, 50f, 50f))
        val g = Game()
        val saved = slot.read()!!
        g.restore(saved.level, saved.time, saved.runId, saved.boardTimes)
        g.onTap()
        // lose by running the clock out
        tick(g, 0.5f); g.placeBall(g.maze.cols - 0.5f, 0.5f); tick(g, Game.TIME_LIMIT + 2f)
        assertEquals(Phase.LOST, g.phase)
        g.onTap() // start again from level 1 as a new run
        assertEquals(1, g.level); assertTrue(g.runId != 99L); assertEquals(0, g.boardTimes.size)
        assertEquals("the save is untouched", 5, slot.read()!!.level)
    }
}
