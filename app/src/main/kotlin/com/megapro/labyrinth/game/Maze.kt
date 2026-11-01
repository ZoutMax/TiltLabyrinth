package com.megapro.labyrinth.game

import kotlin.random.Random

/** A wall segment on the cell grid, in cell units: from (x0, y0) to (x1, y1), always axis-aligned. */
class Wall(val x0: Float, val y0: Float, val x1: Float, val y1: Float)

data class Cell(val x: Int, val y: Int)

/**
 * A maze carved with a randomised depth-first walk, then [openings] extra gaps so there are loops.
 * The ball starts in the top-centre cell; the exit hole is in the bottom-centre cell.
 * Fully determined by (cols, rows, seed, openings), so the same level is the same maze everywhere.
 */
class Maze(val cols: Int, val rows: Int, val seed: Int, val openings: Int) {
    val walls: List<Wall>
    val start = Cell(cols / 2, 0)
    val finish = Cell(cols / 2, rows - 1)

    /** Length of the shortest route from start to finish, in cells; a measure of difficulty. */
    val solutionLength: Int

    private val right = Array(cols) { BooleanArray(rows) { true } }
    private val down = Array(cols) { BooleanArray(rows) { true } }

    init {
        val rnd = Random(seed)
        val seen = Array(cols) { BooleanArray(rows) }
        val stack = ArrayDeque<Cell>()
        var cur = start
        seen[cur.x][cur.y] = true
        stack.add(cur)
        while (stack.isNotEmpty()) {
            cur = stack.last()
            val options = ArrayList<Cell>(4)
            if (cur.x > 0 && !seen[cur.x - 1][cur.y]) options.add(Cell(cur.x - 1, cur.y))
            if (cur.x < cols - 1 && !seen[cur.x + 1][cur.y]) options.add(Cell(cur.x + 1, cur.y))
            if (cur.y > 0 && !seen[cur.x][cur.y - 1]) options.add(Cell(cur.x, cur.y - 1))
            if (cur.y < rows - 1 && !seen[cur.x][cur.y + 1]) options.add(Cell(cur.x, cur.y + 1))
            if (options.isEmpty()) { stack.removeLast(); continue }
            val next = options[rnd.nextInt(options.size)]
            when {
                next.x == cur.x + 1 -> right[cur.x][cur.y] = false
                next.x == cur.x - 1 -> right[next.x][next.y] = false
                next.y == cur.y + 1 -> down[cur.x][cur.y] = false
                else -> down[next.x][next.y] = false
            }
            seen[next.x][next.y] = true
            stack.add(next)
        }
        repeat(openings) {
            val x = rnd.nextInt(cols); val y = rnd.nextInt(rows)
            if (rnd.nextBoolean()) { if (x < cols - 1) right[x][y] = false } else { if (y < rows - 1) down[x][y] = false }
        }

        val list = ArrayList<Wall>()
        list.add(Wall(0f, 0f, cols.toFloat(), 0f))
        list.add(Wall(0f, rows.toFloat(), cols.toFloat(), rows.toFloat()))
        list.add(Wall(0f, 0f, 0f, rows.toFloat()))
        list.add(Wall(cols.toFloat(), 0f, cols.toFloat(), rows.toFloat()))
        for (y in 0 until rows) for (x in 0 until cols - 1) {
            if (right[x][y]) list.add(Wall(x + 1f, y.toFloat(), x + 1f, y + 1f))
        }
        for (x in 0 until cols) for (y in 0 until rows - 1) {
            if (down[x][y]) list.add(Wall(x.toFloat(), y + 1f, x + 1f, y + 1f))
        }
        walls = list
        solutionLength = shortestPath()
    }

    /**
     * The walls as whole straight strips (collinear pieces that touch, joined), and for each piece
     * the length of the strip it is part of. One merge serves both the drawing (a long wall is one
     * piece of wood without seams) and the knock (its pitch follows the wall's length).
     */
    private val strips: Pair<List<Wall>, java.util.IdentityHashMap<Wall, Float>> by lazy {
        val out = ArrayList<Wall>(); val lengths = java.util.IdentityHashMap<Wall, Float>()
        fun merge(pieces: List<Wall>, horizontal: Boolean) {
            fun lo(w: Wall) = if (horizontal) minOf(w.x0, w.x1) else minOf(w.y0, w.y1)
            fun hi(w: Wall) = if (horizontal) maxOf(w.x0, w.x1) else maxOf(w.y0, w.y1)
            var run = ArrayList<Wall>(); var start = 0f; var end = Float.NEGATIVE_INFINITY
            fun close() {
                if (run.isEmpty()) return
                val w0 = run[0]
                out.add(if (horizontal) Wall(start, w0.y0, end, w0.y0) else Wall(w0.x0, start, w0.x0, end))
                for (w in run) lengths[w] = end - start
                run = ArrayList()
            }
            for (w in pieces.sortedBy { lo(it) }) {
                if (lo(w) > end + 1e-3f) { close(); start = lo(w); end = hi(w) } else end = maxOf(end, hi(w))
                run.add(w)
            }
            close()
        }
        for ((_, row) in walls.filter { it.y0 == it.y1 }.groupBy { it.y0 }) merge(row, true)
        for ((_, col) in walls.filter { it.x0 == it.x1 && it.y0 != it.y1 }.groupBy { it.x0 }) merge(col, false)
        out to lengths
    }

    /** Collinear wall pieces joined into whole strips. */
    val merged: List<Wall> get() = strips.first

    /** The length in cells of the straight wall that [w] belongs to: what the ball hears when it knocks on it. */
    fun runLength(w: Wall): Float = strips.second[w] ?: 1f

    /** Cells with a single opening: the blind alleys that make a maze confusing. */
    val deadEnds: Int by lazy {
        var n = 0
        for (x in 0 until cols) for (y in 0 until rows) {
            var open = 0
            if (openLeft(x, y)) open++; if (openRight(x, y)) open++; if (openUp(x, y)) open++; if (openDown(x, y)) open++
            if (open == 1) n++
        }
        n
    }

    /** The shortest route from start to finish, cell by cell. */
    val solution: List<Cell> by lazy {
        val parent = HashMap<Cell, Cell>()
        val queue = ArrayDeque<Cell>()
        val seen = Array(cols) { BooleanArray(rows) }
        seen[start.x][start.y] = true; queue.add(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (c == finish) break
            fun visit(n: Cell) { if (!seen[n.x][n.y]) { seen[n.x][n.y] = true; parent[n] = c; queue.add(n) } }
            if (openLeft(c.x, c.y)) visit(Cell(c.x - 1, c.y))
            if (openRight(c.x, c.y)) visit(Cell(c.x + 1, c.y))
            if (openUp(c.x, c.y)) visit(Cell(c.x, c.y - 1))
            if (openDown(c.x, c.y)) visit(Cell(c.x, c.y + 1))
        }
        val route = ArrayList<Cell>()
        var c: Cell? = finish
        while (c != null) { route.add(c); c = parent[c] }
        route.reverse(); route
    }

    /** Is there an open straight run between two cells of the same row or column (so a push or a boosted ball travels from one to the other)? */
    fun openLine(a: Cell, b: Cell): Boolean {
        if (a.x == b.x) { for (y in minOf(a.y, b.y) until maxOf(a.y, b.y)) if (!openDown(a.x, y)) return false; return true }
        if (a.y == b.y) { for (x in minOf(a.x, b.x) until maxOf(a.x, b.x)) if (!openRight(x, a.y)) return false; return true }
        return false
    }

    fun openRight(x: Int, y: Int) = x < cols - 1 && !right[x][y]
    fun openDown(x: Int, y: Int) = y < rows - 1 && !down[x][y]
    fun openLeft(x: Int, y: Int) = x > 0 && !right[x - 1][y]
    fun openUp(x: Int, y: Int) = y > 0 && !down[x][y - 1]

    /** Is finish reachable from start when [blocked] cells cannot be entered? */
    fun solvableAvoiding(blocked: Set<Cell>): Boolean {
        val seen = Array(cols) { BooleanArray(rows) }
        val queue = ArrayDeque<Cell>()
        seen[start.x][start.y] = true; queue.add(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            if (c == finish) return true
            fun visit(n: Cell) { if (!seen[n.x][n.y] && n !in blocked) { seen[n.x][n.y] = true; queue.add(n) } }
            if (openLeft(c.x, c.y)) visit(Cell(c.x - 1, c.y))
            if (openRight(c.x, c.y)) visit(Cell(c.x + 1, c.y))
            if (openUp(c.x, c.y)) visit(Cell(c.x, c.y - 1))
            if (openDown(c.x, c.y)) visit(Cell(c.x, c.y + 1))
        }
        return false
    }

    private fun shortestPath(): Int {
        val dist = Array(cols) { IntArray(rows) { -1 } }
        val queue = ArrayDeque<Cell>()
        dist[start.x][start.y] = 0
        queue.add(start)
        while (queue.isNotEmpty()) {
            val c = queue.removeFirst()
            val d = dist[c.x][c.y]
            if (c == finish) return d
            fun visit(n: Cell) { if (dist[n.x][n.y] < 0) { dist[n.x][n.y] = d + 1; queue.add(n) } }
            if (c.x > 0 && !right[c.x - 1][c.y]) visit(Cell(c.x - 1, c.y))
            if (c.x < cols - 1 && !right[c.x][c.y]) visit(Cell(c.x + 1, c.y))
            if (c.y > 0 && !down[c.x][c.y - 1]) visit(Cell(c.x, c.y - 1))
            if (c.y < rows - 1 && !down[c.x][c.y]) visit(Cell(c.x, c.y + 1))
        }
        return -1
    }
}

/**
 * Killers end the run: HOLE/BLINK (timed hole), TRAPDOOR (drops when the ball comes close), CRUSHER
 * (piston strike after a warning), LASER (beam across the corridor), ICE (slippery patch that cracks
 * open). Buggers only trouble the ball: MAGNET, WIND, RADIOACTIVE, SPEED, SPINNER (vortex current).
 */
/** What a board time earns, from the par time of a clean run. */
enum class Medal(val title: String) { GOLD("Gold"), SILVER("Silver"), BRONZE("Bronze") }

enum class TrapKind { HOLE, BLINK, SLIDE, TRAPDOOR, MAGNET, WIND, RADIOACTIVE, SPEED, CRUSHER, LASER, ICE, SPINNER;
    val killer: Boolean get() = this == HOLE || this == BLINK || this == SLIDE || this == TRAPDOOR || this == CRUSHER || this == LASER || this == ICE
}

/** A hole in the floor. [x]/[y] is the resting centre; sliders move along [axisX] over [range] cells. */
class Trap(val kind: TrapKind, val x: Float, val y: Float, val axisX: Boolean = false, val range: Float = 0f, val period: Float = 2f, val phase: Float = 0f, val duty: Float = 0.5f, val pull: Float = 0f, val hold: Float = 0f) {
    var cx = x
    var cy = y
    var open = kind != TrapKind.BLINK && kind != TrapKind.TRAPDOOR
    var armed = 0f // magnet: seconds of hold remaining while the ball is caught; crusher: warning progress 0..1; trapdoor: arming timer (-1 idle)
    var releaseAt = -1f // magnet: stays off until this game time after releasing the ball
    var beam = 0f // laser: length of the beam to the first wall, found when the level is set up
    var clear = true // magnet: the ball has left the disc since it was last released, so it may be caught again
    // trapdoor: arms when the ball comes within [range] cells, drops open [hold] seconds later, resets
    // [period] seconds after the ball has gone; the delay shrinks with the level
    // crusher: strikes for CRUSHER_STRIKE seconds at the start of each [period], after a warning of [hold] seconds;
    // a ball in the halo (CRUSHER_KICK cells) when it strikes is kicked away at up to [pull] cells/s;
    // the warning and the period shrink and the kick grows with the level
    // laser: fires along direction [range] (0 right, 1 down, 2 left, 3 up) during its on-phase, which grows with the level
    // ice: the patch within [range] cells is slippery, [pull] = how slippery (0 = normal floor, 1 = no grip at all),
    // more with the level; during the on-phase the ice is cracked and the disc is an open hole
    // spinner: during the on-phase a current of strength [pull] swirls the ball around within [range] cells; both grow with the level
    // radioactive: touching the trap while it leaks contaminates the ball until the leak (on-phase) ends;
    // [pull] = thickness of the slime (1 = the level 0 test value), which grows with the level
    // speed: touching the trap while it is charged makes the ball roll faster until the charge (on-phase) ends;
    // [pull] = the extra drive at full charge (1 = twice the gravity pull), which grows with the level
}

/** The fixed set of 100 levels. Same grid, seed and selection rule everywhere, so levels never change. */
object Levels {
    const val COUNT = 100
    private val cache = HashMap<Int, Maze>()

    fun maze(level: Int): Maze = cache.getOrPut(level) { if (level == 0) testMaze() else build(level.coerceIn(1, COUNT)) }

    /** Level 0: a small, open board with one trap of each kind near the ball. Kept in the game as the trap test board. */
    private fun testMaze(): Maze = Maze(7, 11, 4242, openings = 48) // the same 7x11 shape as the first real board

    private val trapCache = HashMap<Int, List<Trap>>()

    fun traps(level: Int): List<Trap> = trapCache.getOrPut(level) { if (level == 0) testTraps() else buildTraps(level.coerceIn(1, COUNT), maze(level)) }

    /** A clean run's time for a board: about 0.37 s a cell of the route, plus a moment at the start. */
    fun parTime(level: Int): Float = 2f + 0.37f * maze(level).solutionLength

    /** The medal a time earns on a board, or null for none: gold within 15% of par, silver within half again, bronze within double. */
    fun medal(level: Int, time: Float): Medal? {
        val par = parTime(level)
        return when {
            time <= par * 1.15f -> Medal.GOLD
            time <= par * 1.5f -> Medal.SILVER
            time <= par * 2f -> Medal.BRONZE
            else -> null
        }
    }

    private fun testTraps(): List<Trap> = listOf(
        Trap(TrapKind.TRAPDOOR, 1.5f, 1.5f, range = 1.2f, hold = 0.6f, period = 2.5f),
        Trap(TrapKind.CRUSHER, 5.5f, 1.5f, period = 4f, hold = 0.9f, pull = 14f),
        Trap(TrapKind.BLINK, 1.5f, 3.5f, period = 3f),
        Trap(TrapKind.MAGNET, 5.5f, 3.5f, period = 4f, duty = 0.5f, pull = 45f, hold = 2f),
        Trap(TrapKind.LASER, 3.5f, 5.5f, range = longestRun(maze(0), 3, 5).toFloat(), period = 4f, duty = 0.35f, phase = 1.5f), // fires down its longest open run
        Trap(TrapKind.SPEED, 1.5f, 7.5f, period = 5f, duty = 0.5f, phase = 3.14f, pull = 1f),
        Trap(TrapKind.WIND, 3.5f, 7.5f, period = 4f, duty = 0.5f, pull = 60f),
        Trap(TrapKind.RADIOACTIVE, 5.5f, 7.5f, period = 5f, duty = 0.5f, pull = 1f),
        Trap(TrapKind.ICE, 1.5f, 9.5f, range = 1.0f, period = 5f, duty = 0.4f, phase = 2f, pull = 0.8f),
        Trap(TrapKind.SPINNER, 5.5f, 9.5f, range = 1.2f, period = 5f, duty = 0.5f, phase = 4f, pull = 55f),
    )

    /**
     * Traps for a board. More of them as the levels go up (2 to 40), every kind quicker and
     * stronger, the kinds arriving one by one along [ROTATION]. Where they go is decided by the
     * board's shortest route: killers sit on the route itself (more of them with the level, but
     * spaced out so the board stays quick to play) or at its side; each bugger is placed so that
     * its effect feeds a killer: wind with a straight open line onto one, a magnet or a spinner
     * beside one, a speed pad a few cells before one down a straight corridor, slime right before
     * a blinking hole or cracking ice.
     */
    private fun buildTraps(level: Int, m: Maze): List<Trap> {
        val t = (level - 1) / (COUNT - 1).toFloat()
        val count = minOf(2 + (level - 1) * 38 / (COUNT - 1), m.cols * m.rows / 5)
        val rnd = Random(level * 31337 + 5)
        val path = m.solution
        val onPath = HashMap<Cell, Int>(); path.forEachIndexed { i, c -> onPath[c] = i }
        val cells = ArrayList<Cell>(); val kinds = ArrayList<TrapKind>()
        val routeKillers = ArrayList<Int>()
        val routeCap = 1 + level * 8 / COUNT // killers allowed on the route: 1 at level 1, 9 at level 100

        fun inside(c: Cell) = c.x in 0 until m.cols && c.y in 0 until m.rows
        fun free(c: Cell): Boolean = inside(c) && c != m.start && c != m.finish &&
            kotlin.math.abs(c.x - m.start.x) + c.y >= 3 && // not right at the start
            kotlin.math.abs(c.x - m.finish.x) + (m.rows - 1 - c.y) >= 2 && // not on the finish doorstep
            cells.none { kotlin.math.abs(it.x - c.x) + kotlin.math.abs(it.y - c.y) < 2 } // a cell of space between traps
        fun pick(list: List<Cell>): Cell? = if (list.isEmpty()) null else list[rnd.nextInt(list.size)]
        fun randomFree(): Cell? { repeat(400) { val c = Cell(rnd.nextInt(m.cols), rnd.nextInt(m.rows)); if (free(c)) return c }; return null }
        fun besideRoute(): Cell? = pick(path.flatMap { p -> listOf(Cell(p.x + 1, p.y), Cell(p.x - 1, p.y), Cell(p.x, p.y + 1), Cell(p.x, p.y - 1)) }.distinct().filter { it !in onPath && free(it) })
        fun killerSpot(): Cell? {
            if (routeKillers.size < routeCap) {
                val lo = (path.size * 0.15f).toInt(); val hi = (path.size * 0.95f).toInt()
                val spots = (lo until hi).filter { i -> routeKillers.none { kotlin.math.abs(it - i) < 4 } && free(path[i]) }.map { path[it] }
                pick(spots)?.let { return it }
            }
            return besideRoute() ?: randomFree()
        }
        fun comboSpot(kind: TrapKind): Cell? {
            // Latest killers first, so the pairs stay close in the dealing order
            for (i in cells.indices.reversed()) {
                if (!kinds[i].killer) continue
                val k = cells[i]
                val spots: List<Cell> = when (kind) {
                    TrapKind.WIND -> listOf(Cell(k.x + 2, k.y), Cell(k.x - 2, k.y), Cell(k.x, k.y + 2), Cell(k.x, k.y - 2)).filter { inside(it) && free(it) && m.openLine(it, k) }
                    TrapKind.MAGNET -> listOf(Cell(k.x + 1, k.y + 1), Cell(k.x - 1, k.y + 1), Cell(k.x + 1, k.y - 1), Cell(k.x - 1, k.y - 1), Cell(k.x + 2, k.y), Cell(k.x - 2, k.y), Cell(k.x, k.y + 2), Cell(k.x, k.y - 2)).filter { inside(it) && free(it) }
                    TrapKind.SPINNER -> listOf(Cell(k.x + 2, k.y), Cell(k.x - 2, k.y), Cell(k.x, k.y + 2), Cell(k.x, k.y - 2), Cell(k.x + 1, k.y + 1), Cell(k.x - 1, k.y + 1), Cell(k.x + 1, k.y - 1), Cell(k.x - 1, k.y - 1)).filter { inside(it) && free(it) && (m.openLine(it, k) || kotlin.math.abs(it.x - k.x) == 1) }
                    TrapKind.SPEED -> { val idx = onPath[k]; (if (idx == null) emptyList() else (2..4).mapNotNull { d -> path.getOrNull(idx - d) }).filter { free(it) && m.openLine(it, k) }.ifEmpty { listOf(Cell(k.x + 2, k.y), Cell(k.x - 2, k.y), Cell(k.x, k.y + 2), Cell(k.x, k.y - 2), Cell(k.x + 3, k.y), Cell(k.x - 3, k.y), Cell(k.x, k.y + 3), Cell(k.x, k.y - 3)).filter { inside(it) && free(it) && m.openLine(it, k) } } }
                    TrapKind.RADIOACTIVE -> { val idx = onPath[k]; if (idx == null) emptyList() else (1..3).mapNotNull { d -> path.getOrNull(idx - d) }.filter { free(it) } } // slime right before a route killer
                    else -> emptyList()
                }
                pick(spots)?.let { return it }
            }
            // Otherwise anywhere within two cells of a killer, so the bugger still works on it
            val near = ArrayList<Cell>()
            for (i in cells.indices) if (kinds[i].killer) for (dx in -2..2) for (dy in -2..2) {
                val c = Cell(cells[i].x + dx, cells[i].y + dy)
                if (inside(c) && free(c)) near.add(c)
            }
            return pick(near.distinct())
        }

        val traps = ArrayList<Trap>()
        for (i in 0 until count) {
            val kind = ROTATION[i % ROTATION.size]
            val c = (if (kind.killer) killerSpot() else comboSpot(kind) ?: besideRoute() ?: randomFree()) ?: break
            cells.add(c); kinds.add(kind)
            if (kind.killer) onPath[c]?.let { routeKillers.add(it) }
            val cx = c.x + 0.5f; val cy = c.y + 0.5f
            val phase = rnd.nextFloat() * 6.28f
            traps.add(when (kind) {
                TrapKind.MAGNET -> {
                    // On-time grows and off-time shrinks with the level, pull gets stronger, hold longer
                    val on = 1.0f + 2.0f * t; val off = 2.6f - 1.4f * t
                    Trap(TrapKind.MAGNET, cx, cy, period = on + off, phase = phase, duty = on / (on + off), pull = 30f + 30f * t, hold = 1f + 3f * t)
                }
                TrapKind.WIND -> {
                    // Gusts get longer and stronger with the level
                    val on = 1.5f + 1.5f * t; val off = 2.5f - 1.0f * t
                    Trap(TrapKind.WIND, cx, cy, period = on + off, phase = phase, duty = on / (on + off), pull = 45f + 45f * t)
                }
                TrapKind.RADIOACTIVE -> {
                    // Leaks last longer (so does the slime), pauses shrink, and the slime gets thicker
                    val on = 1.5f + 2.0f * t; val off = 2.5f - 1.3f * t
                    Trap(TrapKind.RADIOACTIVE, cx, cy, period = on + off, phase = phase, duty = on / (on + off), pull = 0.6f + 0.9f * t)
                }
                TrapKind.SPEED -> {
                    // Charges last longer, pauses shrink, and the drive gets stronger
                    val on = 1.5f + 2.0f * t; val off = 2.5f - 1.3f * t
                    Trap(TrapKind.SPEED, cx, cy, period = on + off, phase = phase, duty = on / (on + off), pull = 0.5f + 1.5f * t)
                }
                TrapKind.TRAPDOOR -> Trap(TrapKind.TRAPDOOR, cx, cy, range = 1.0f + 0.4f * t, hold = 0.9f - 0.6f * t, period = 2.5f - 1.0f * t) // sees further, drops sooner, resets quicker
                TrapKind.CRUSHER -> Trap(TrapKind.CRUSHER, cx, cy, period = 4.5f - 2.3f * t, phase = phase, hold = 1.0f - 0.65f * t, pull = 10f + 14f * t) // strikes more often, warns less, kicks harder
                TrapKind.LASER -> {
                    // Aimed down the longest open run from its cell; fires longer and rests less with the level
                    val on = 0.8f + 1.4f * t; val off = 3.0f - 1.6f * t
                    Trap(TrapKind.LASER, cx, cy, range = longestRun(m, c.x, c.y).toFloat(), period = on + off, phase = phase, duty = on / (on + off))
                }
                TrapKind.ICE -> {
                    // A larger, more slippery patch, cracked open longer and more often
                    val on = 1.0f + 1.5f * t; val off = 4.0f - 2.0f * t
                    Trap(TrapKind.ICE, cx, cy, range = 0.8f + 0.6f * t, period = on + off, phase = phase, duty = on / (on + off), pull = 0.5f + 0.5f * t)
                }
                TrapKind.SPINNER -> {
                    // A wider, stronger current, on longer and off less
                    val on = 1.5f + 2.0f * t; val off = 2.5f - 1.3f * t
                    Trap(TrapKind.SPINNER, cx, cy, range = 1.0f + 0.6f * t, period = on + off, phase = phase, duty = on / (on + off), pull = 35f + 55f * t)
                }
                else -> {
                    // Hole: blinks quicker with the level (and stays open longer, see Game.updateTraps)
                    val period = (3.2f - 1.4f * t) + rnd.nextFloat() * (1.2f - 0.6f * t)
                    Trap(TrapKind.BLINK, cx, cy, period = period, phase = phase)
                }
            })
        }
        return traps
    }

    /** Order in which trap kinds are dealt out on a level: killers and buggers alternate, ten of each per cycle. */
    private val ROTATION = listOf(
        TrapKind.BLINK, TrapKind.MAGNET, TrapKind.BLINK, TrapKind.WIND, TrapKind.TRAPDOOR, TrapKind.RADIOACTIVE, TrapKind.BLINK, TrapKind.SPEED, TrapKind.CRUSHER, TrapKind.SPINNER,
        TrapKind.LASER, TrapKind.MAGNET, TrapKind.ICE, TrapKind.WIND, TrapKind.BLINK, TrapKind.RADIOACTIVE, TrapKind.TRAPDOOR, TrapKind.SPEED, TrapKind.CRUSHER, TrapKind.SPINNER,
    )

    /** The direction (0 right, 1 down, 2 left, 3 up) with the longest run of open cells from (x, y): where a laser can see furthest. */
    private fun longestRun(m: Maze, x: Int, y: Int): Int {
        var best = 0; var bestLen = -1
        for (dir in 0 until 4) {
            var cx = x; var cy = y; var len = 0
            while (true) {
                val open = when (dir) { 0 -> m.openRight(cx, cy); 1 -> m.openDown(cx, cy); 2 -> m.openLeft(cx, cy); else -> m.openUp(cx, cy) }
                if (!open) break
                when (dir) { 0 -> cx++; 1 -> cy++; 2 -> cx--; else -> cy-- }
                len++
            }
            if (len > bestLen) { bestLen = len; best = dir }
        }
        return best
    }

    /** A board's size in cells, columns by rows, without building it: 7x11 for the test board and the first, growing to 22x37. */
    fun size(level: Int): Pair<Int, Int> {
        if (level <= 0) return 7 to 11
        val t = (level.coerceAtMost(COUNT) - 1) / (COUNT - 1).toFloat()
        val cols = (7 + 15 * t).toInt()
        return cols to (cols * 1.7f).toInt()
    }

    private fun build(level: Int): Maze {
        val t = (level - 1) / (COUNT - 1).toFloat()
        val (cols, rows) = size(level)
        // Fewer loops as levels go up: from one opening per 9 cells to one per 80 (nearly a perfect maze)
        val openings = (cols * rows / (9 + 71 * t)).toInt()
        // Of many candidates (more on higher levels), keep the most confusing: the longest route
        // with the most blind alleys, so complexity climbs steadily with the level
        var best: Maze? = null; var bestScore = -1
        for (k in 0 until 10 + (20 * t).toInt()) {
            val m = Maze(cols, rows, level * 1000 + k * 7919 + 17, openings)
            val score = m.solutionLength * 2 + m.deadEnds
            if (score > bestScore) { best = m; bestScore = score }
        }
        return best!!
    }
}
