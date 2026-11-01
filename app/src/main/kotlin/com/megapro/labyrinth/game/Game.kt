package com.megapro.labyrinth.game

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

enum class Phase { TITLE, PLAYING, PAUSED, FINISHED, COMPLETE, LOST }

/**
 * Rolling-ball physics on a tilted board. Lengths are in cells (one cell is a corridor width,
 * taken as 1.5 cm of a real board), so gravity is scaled accordingly.
 */
class Game(
    private val haptics: GameHaptics = GameHaptics.None,
    private val sounds: GameSounds = GameSounds.None,
) {
    var level by mutableIntStateOf(1); private set

    /** Cumulative time of the current run, and an id that ties it to its leaderboard entry. */
    var runTime by mutableFloatStateOf(0f); private set
    var runId: Long = System.currentTimeMillis(); private set

    /** Set by the host: called after each finished level with (level, runTime) to persist and rank. */
    var onLevelDone: (Int, Float) -> Unit = { _, _ -> }
    /** The time of every board finished in this run, in order: board 1 first. Saved with the run. */
    val boardTimes: MutableList<Float> = ArrayList()

    /** Set by the host: called after each finished level with (level, that level's own time) for the per-board best list. */
    var onBoardDone: (Int, Float) -> Unit = { _, _ -> }
    var phase by mutableStateOf(Phase.TITLE); private set
    var elapsed by mutableFloatStateOf(0f); private set

    var maze: Maze = Levels.maze(1); private set
    var traps: List<Trap> = Levels.traps(1); private set

    /** Set by the host when a trap ends the run: (levels completed, total time). */
    var onRunOver: (Int, Float) -> Unit = { _, _ -> }
    var lostTrap: Trap? = null; private set
    /** Where the ball ends when the run is lost: the trap's centre, or where the beam caught it. */
    var lostX = 0f; private set
    var lostY = 0f; private set
    /** The run ended because the board's time limit ran out. */
    var timedOut = false; private set
    var ballX = 0f; private set
    var ballY = 0f; private set
    var vx = 0f; private set
    var vy = 0f; private set
    val radius = 0.29f

    /** Board tilt as the gravity component along the board, in cells/s^2 (screen x right, y down). */
    var ax = 0f; private set
    var ay = 0f; private set

    /** Progress of the ball dropping into the finish hole, 0..1. */
    var sink = 0f; private set
    var phaseTime = 0f; private set

    var time = 0f; private set

    /** Clock for trap timing: starts at zero on every level and only runs while playing, so
     *  every player meets the traps in exactly the same sequence. */
    var levelTime = 0f; private set

    /** The finish hole opens and closes like a trap hole; the ball can only drop in while open. */
    var finishOpen = false; private set

    /** Seconds of contamination left after touching a leaking radioactive trap: the ball is coated
     *  in sticky slime that drags on it until that leak cycle ends (drying off over its last second). */
    var toxic = 0f; private set
    /** Thickness of the current slime, from the trap that gave it (grows with the level). */
    private var toxicStrength = 0f
    /** Strength of the contamination right now, 0..1. */
    val toxicLevel: Float get() = toxic.coerceIn(0f, TOXIC_FADE) / TOXIC_FADE

    /** Seconds of speed charge left after touching a charged speed trap: the ball is driven harder
     *  and rolls with less drag until that charge cycle ends (fading over its last moment). */
    var boost = 0f; private set
    /** Extra drive of the current charge, from the trap that gave it (grows with the level). */
    private var boostGain = 0f
    /** Strength of the speed charge right now, 0..1. */
    val boostLevel: Float get() = boost.coerceIn(0f, BOOST_FADE) / BOOST_FADE
    private var rollSound = 0f
    /** How hard the wind is pushing the ball right now, 0..1, for the haptics. */
    private var windFelt = 0f
    /** How strongly a spinner's current has the ball, and whether it is on ice, for the haptics. */
    private var spinFelt = 0f
    private var iceFelt = 0f
    private var accumulator = 0f

    /** Normal force fraction (cos of the tilt), for rolling resistance. */
    private var normal = 1f


    // Gravity readings; the neutral grip is the "board lying flat" reference
    private val recent = Array(12) { FloatArray(3) }
    private var recentCount = 0
    private var recentNext = 0
    private var gx = 0f
    private var gy = 0f
    private var gz = 1f
    private var nx = 0f
    private var ny = 0f
    private var nz = 1f

    init { newLevel(1) }

    // ------------------------------------------------------------- input

    fun onSensor(x: Float, y: Float, z: Float) {
        gx = x; gy = y; gz = z
        val s = recent[recentNext]; s[0] = x; s[1] = y; s[2] = z
        recentNext = (recentNext + 1) % recent.size
        if (recentCount < recent.size) recentCount++
    }

    fun calibrate() {
        if (recentCount == 0) return
        var x = 0f; var y = 0f; var z = 0f
        for (i in 0 until recentCount) { x += recent[i][0]; y += recent[i][1]; z += recent[i][2] }
        val len = sqrt(x * x + y * y + z * z)
        if (len < 1e-3f) return
        nx = x / len; ny = y / len; nz = z / len
    }

    /**
     * Gravity in the board's frame: rotate the reading so the neutral grip maps to straight down,
     * then the x/y components are the pull along the board. Left edge lowered -> rolls left.
     */
    private fun readTilt() {
        if (recentCount == 0) return
        val len = sqrt(gx * gx + gy * gy + gz * gz)
        if (len < 1e-3f) return
        val ux = gx / len; val uy = gy / len; val uz = gz / len
        // Rotation taking n to (0,0,1): Rodrigues with axis n x z and angle acos(n.z)
        val axx = ny; val axy = -nx // n x z = (ny, -nx, 0)
        val s = sqrt(axx * axx + axy * axy)
        val c = nz
        val rx: Float; val ry: Float
        if (s < 1e-4f) {
            rx = ux; ry = uy
        } else {
            val kx = axx / s; val ky = axy / s
            // v' = v c + (k x v) s + k (k.v)(1 - c), with k = (kx, ky, 0)
            val kdotv = kx * ux + ky * uy
            val cxX = ky * uz; val cxY = -kx * uz
            rx = ux * c + cxX * s + kx * kdotv * (1 - c)
            ry = uy * c + cxY * s + ky * kdotv * (1 - c)
        }
        // Sensor x reads positive when the +x side points up, so the ball rolls the other way
        val g = G_CELLS
        ax = -rx * g
        ay = ry * g
        normal = sqrt((1f - rx * rx - ry * ry).coerceIn(0f, 1f))
    }

    fun onTap() {
        when (phase) {
            Phase.TITLE -> { calibrate(); start() }
            Phase.PLAYING -> enter(Phase.PAUSED)
            Phase.PAUSED -> { calibrate(); enter(Phase.PLAYING) }
            Phase.FINISHED -> {
                if (isTestLevel) { toMenu(); return } // test board or free play done: back to the menu
                if (level >= Levels.COUNT) { enter(Phase.COMPLETE); return }
                level++; newLevel(level); calibrate(); start()
            }
            Phase.COMPLETE -> resetRun()
            Phase.LOST -> if (isTestLevel) { newLevel(level); start() } else resetRun() // the test board just starts over
        }
    }

    fun pause() { if (phase == Phase.PLAYING) enter(Phase.PAUSED) }

    /**
     * The Back button. The test board and free play go back to the menu; a board being played
     * pauses. True when the game took it; false when the host should step out of the game, which
     * keeps everything exactly as it is (a run waits paused, as after the Home button).
     */
    fun onBack(): Boolean = when {
        isTestLevel && phase != Phase.TITLE -> { toMenu(); true }
        phase == Phase.PLAYING -> { enter(Phase.PAUSED); true }
        else -> false
    }

    private fun toMenu() { freePlay = false; level = 1; newLevel(1); enter(Phase.TITLE) }

    /** The celebration's fireworks are heard through the game's sounds: one burst, [strength] by its size, [pan] where it is. */
    fun celebrate(strength: Float, pan: Float) { if (phase == Phase.COMPLETE) sounds.firework(strength, pan) }

    /** Level 0 is the trap test board: no timer, no effect on the run; finishing it leads to level 1. */
    val isTestLevel: Boolean get() = level == 0 || freePlay

    /** Free play: any board opened from the test board's reel, played without scoring. */
    var freePlay = false; private set

    /** From the test board: play board [n] freely (0 = the playground). Nothing is scored or saved. */
    fun playFree(n: Int) {
        if (!isTestLevel) return
        freePlay = n != 0
        level = n.coerceIn(0, Levels.COUNT); runTime = 0f; boardTimes.clear()
        newLevel(level)
        calibrate()
        start()
    }

    /** From the main screen: play a board finished in the saved run again, freely. Nothing is scored or saved. */
    fun replay(n: Int) {
        if (phase != Phase.TITLE || n !in 1..Levels.COUNT) return
        freePlay = true
        level = n; runTime = 0f; boardTimes.clear()
        newLevel(level)
        calibrate()
        start()
    }

    /** For tests: put the ball somewhere, at rest. */
    internal fun placeBall(x: Float, y: Float) { ballX = x; ballY = y; vx = 0f; vy = 0f }
    /** Whether the ball is on an ice patch right now. */
    val onIce: Boolean get() = iceFelt > 0f

    /** Long press on the title: play the test board first. The real run still starts at level 1. */
    fun startTest() {
        if (phase != Phase.TITLE) return
        freePlay = false
        level = 0; runTime = 0f; runId = System.currentTimeMillis(); boardTimes.clear()
        newLevel(0)
        calibrate()
        start()
    }

    private fun enter(p: Phase) {
        phase = p
        phaseTime = 0f
        if (p != Phase.PLAYING) { sounds.roll(0f); rollSound = 0f; silenceTraps(); haptics.stop() }
    }

    private fun start() {
        elapsed = 0f
        levelTime = 0f
        moved = false
        enter(Phase.PLAYING)
    }

    /** Set once the ball has started rolling on this board: the clock waits for that first move. */
    private var moved = false

    /** Restore a saved run: continue at [savedLevel] with [savedTime] already on the clock. */
    fun restore(savedLevel: Int, savedTime: Float, savedRunId: Long, savedBoardTimes: List<Float> = emptyList()) {
        freePlay = false
        level = savedLevel.coerceIn(1, Levels.COUNT)
        runTime = savedTime
        runId = savedRunId
        boardTimes.clear(); boardTimes.addAll(savedBoardTimes)
        newLevel(level)
    }

    /** From the level-done screen: play the same board again, as if it had not been finished;
     *  its time is taken back out of the run. */
    fun restartLevel() {
        if (phase != Phase.FINISHED) return
        if (!isTestLevel) { runTime = (runTime - elapsed).coerceAtLeast(0f); if (boardTimes.size >= level) boardTimes.removeAt(boardTimes.size - 1) }
        val last = elapsed
        newLevel(level)
        previousTry = last // so the next finish can be compared with this one
        calibrate()
        start()
    }


    /** Which side of the board a trap is on, -1 left to 1 right, for stereo placement. */
    private fun pan(t: Trap): Float = (t.cx / maze.cols * 2f - 1f).coerceIn(-1f, 1f)

    /** Time of the try of this board that was given up with RESTART, or below zero if there was none. */
    var previousTry = -1f; private set

    /** Start over from level 1 as a new run. */
    fun resetRun() {
        freePlay = false
        level = 1; runTime = 0f; runId = System.currentTimeMillis(); boardTimes.clear()
        newLevel(1)
        enter(Phase.TITLE)
    }

    private fun newLevel(n: Int) {
        maze = Levels.maze(n)
        traps = Levels.traps(n)
        lostTrap = null; timedOut = false
        previousTry = -1f
        for (t in traps) {
            t.cx = t.x; t.cy = t.y; t.releaseAt = -1f; t.open = false
            t.armed = if (t.kind == TrapKind.TRAPDOOR) -1f else 0f
            if (t.kind == TrapKind.LASER) t.beam = beamLength(t)
        }
        ballX = maze.start.x + 0.5f
        ballY = 0.5f
        vx = 0f; vy = 0f; sink = 0f
        toxic = 0f; toxicStrength = 0f
        boost = 0f; boostGain = 0f
    }

    // ------------------------------------------------------------- simulation

    fun update(dt: Float) {
        time += dt
        phaseTime += dt
        if (phase == Phase.FINISHED || phase == Phase.LOST) {
            // Roll onto the hole and drop in; an explosion takes longer to play out
            val t = (phaseTime / (if (timedOut) EXPLOSION else 0.5f)).coerceIn(0f, 1f)
            sink = t * t * (3 - 2 * t)
            if (timedOut && isTestLevel && phaseTime > EXPLOSION + 0.6f) { newLevel(level); start(); return } // test board: blow up, then start over
            val tx = if (lostTrap != null || timedOut) lostX else (maze.finish.x + 0.5f)
            val ty = if (lostTrap != null || timedOut) lostY else (maze.finish.y + 0.5f)
            ballX += (tx - ballX) * (dt * 14f).coerceAtMost(1f)
            ballY += (ty - ballY) * (dt * 14f).coerceAtMost(1f)
            return
        }
        if (phase != Phase.PLAYING) return
        readTilt()
        if (!moved && phaseTime < GRACE) { ax = 0f; ay = 0f } // a moment of grace at the start before gravity bites
        // The clock starts with the ball's first move and runs on every board, level 0 included; only its scoring is skipped there
        if (!moved && (vx * vx + vy * vy > 0.04f || kotlin.math.abs(ballX - (maze.start.x + 0.5f)) > 0.05f || kotlin.math.abs(ballY - 0.5f) > 0.05f)) moved = true
        if (moved) {
            val before = elapsed.toInt()
            elapsed += dt
            if (elapsed.toInt() != before) sounds.reelTick() // the seconds drum steps
            if (elapsed >= TIME_LIMIT) { timeOut(); return }
        }
        levelTime += dt
        updateTraps(dt)
        accumulator += dt
        while (accumulator >= STEP && phase == Phase.PLAYING) { step(STEP); accumulator -= STEP }
        if (phase != Phase.PLAYING) return

        // Rolling noise follows the speed closely: loudness on a perceptual curve of the speed,
        // settling within about 50 ms so it starts and stops with the ball
        val speed = sqrt(vx * vx + vy * vy)
        val target = Math.pow((speed / 12f).coerceIn(0f, 1f).toDouble(), 0.7).toFloat()
        rollSound += (target - rollSound) * (dt / 0.05f).coerceAtMost(1f)
        sounds.roll(rollSound)
        haptics.roll(rollSound)
        haptics.wind(windFelt)
        haptics.spin(spinFelt)
        haptics.slide(iceFelt)
        haptics.magnetHold(traps.any { it.kind == TrapKind.MAGNET && it.armed > 0f })

        // Each kind of active trap is heard only when the ball is near: full on top of it,
        // fading with distance, silent beyond HEAR_RANGE
        for (kind in HEARD) {
            var best = 0f; var bestPan = 0f
            for (t in traps) {
                if (t.kind != kind) continue
                val level = when {
                    t.open -> 1f
                    kind == TrapKind.CRUSHER && t.armed > 0f -> 1f // the piston hisses as it rises
                    kind == TrapKind.ICE && t.armed > 0f -> 0.35f // intact ice creaks under the ball
                    else -> 0f
                }
                val h = hearing(t) * level
                if (h > best) { best = h; bestPan = pan(t) }
            }
            sounds.trap(kind, best, bestPan)
        }
    }

    /** How loudly the ball hears a trap: 1 on top of it, falling steeply so that beyond two cells
     *  there is hardly anything, and nothing at HEAR_RANGE cells and beyond. */
    private fun hearing(t: Trap): Float {
        var dx = ballX - t.cx; var dy = ballY - t.cy
        if (t.kind == TrapKind.LASER && t.open) {
            // The beam hums along its whole length: measure to its nearest point
            val (dxn, dyn) = laserDir(t)
            val along = (dx * dxn + dy * dyn).coerceIn(0f, t.beam)
            dx -= dxn * along; dy -= dyn * along
        }
        val k = (1f - sqrt(dx * dx + dy * dy) / HEAR_RANGE).coerceIn(0f, 1f)
        // The magnet and the hammer carry across the board; everything else falls off steeply
        return if (t.kind == TrapKind.MAGNET || t.kind == TrapKind.CRUSHER) k * sqrt(k) else k * k * k
    }

    private fun silenceTraps() { for (kind in HEARD) sounds.trap(kind, 0f, 0f) }

    /** The board's time is up: the ball is lost where it stands and the run is over. */
    private fun timeOut() {
        sounds.roll(0f); rollSound = 0f
        sounds.explode()
        haptics.explode()
        timedOut = true
        lostTrap = null; lostX = ballX; lostY = ballY
        sink = 0f
        enter(Phase.LOST)
        if (!isTestLevel) onRunOver(level - 1, runTime + elapsed)
    }

    /** Unit direction of a laser's beam: [Trap.range] 0 right, 1 down, 2 left, 3 up. */
    fun laserDir(t: Trap): Pair<Float, Float> = when (t.range.toInt() and 3) { 0 -> 1f to 0f; 1 -> 0f to 1f; 2 -> -1f to 0f; else -> 0f to -1f }

    /** How far a laser's beam reaches before the first wall or the edge of the board. */
    private fun beamLength(t: Trap): Float {
        val (dx, dy) = laserDir(t)
        var len = 0f
        while (len < maze.cols + maze.rows) {
            val nx = t.cx + dx * (len + 0.05f); val ny = t.cy + dy * (len + 0.05f)
            if (nx < 0f || ny < 0f || nx > maze.cols || ny > maze.rows || wallBetween(t.cx, t.cy, nx, ny)) break
            len += 0.05f
        }
        return len - WALL_R
    }

    private fun step(h: Float) {
        for (t in traps) {
            if (t.kind != TrapKind.MAGNET) continue
            if (t.armed > 0f) {
                // Caught: held on the magnet until the hold runs out
                ballX = t.cx; ballY = t.cy; vx = 0f; vy = 0f
                t.armed -= h
                if (t.armed <= 0f) { t.releaseAt = levelTime + 1.5f; t.open = false; t.clear = false; haptics.magnetRelease() }
                return
            }
            val dx = ballX - t.cx; val dy = ballY - t.cy
            val d2 = dx * dx + dy * dy
            // A released ball must roll off the disc before the magnet can take it again
            if (!t.clear && d2 > (HOLE_R + radius) * (HOLE_R + radius)) t.clear = true
            if (t.open && t.clear && d2 < HOLE_R * HOLE_R) { t.armed = t.hold; sounds.knock(0.6f); haptics.magnetCatch() }
        }

        // Radioactive: touching the disc while it leaks coats the ball in slime for a while
        for (t in traps) {
            if (t.kind != TrapKind.RADIOACTIVE || !t.open) continue
            val dx = ballX - t.cx; val dy = ballY - t.cy
            val touch = HOLE_R + radius
            if (dx * dx + dy * dy < touch * touch) {
                if (toxic <= 0f) haptics.toxic()
                // Contaminated for the rest of this leak: the slime lasts until the trap switches off
                val frac = ((levelTime / t.period + t.phase / 6.2832f) % 1f)
                toxic = max(toxic, (t.duty - frac) * t.period)
                toxicStrength = max(toxicStrength, t.pull)
            }
        }
        if (toxic > 0f) toxic -= h
        val slime = toxicLevel * toxicStrength

        // Speed: touching the disc while it is charged drives the ball harder for the rest of the charge
        for (t in traps) {
            if (t.kind != TrapKind.SPEED || !t.open) continue
            val dx = ballX - t.cx; val dy = ballY - t.cy
            val touch = HOLE_R + radius
            if (dx * dx + dy * dy < touch * touch) {
                if (boost <= 0f) { haptics.boost(); sounds.boost() }
                val frac = ((levelTime / t.period + t.phase / 6.2832f) % 1f)
                boost = max(boost, (t.duty - frac) * t.period)
                boostGain = max(boostGain, t.pull)
            }
        }
        if (boost > 0f) boost -= h
        val charge = boostLevel

        // Ice: on the glassy patch the ball slides instead of rolling; how much grip it loses is the
        // trap's slipperiness (grows with the level): full gravity, next to no resistance at 1
        var ice = 0f
        for (t in traps) {
            if (t.kind != TrapKind.ICE) continue
            val dx = ballX - t.cx; val dy = ballY - t.cy
            val reach = t.range + radius
            val on = dx * dx + dy * dy < reach * reach && !wallBetween(t.cx, t.cy, ballX, ballY)
            t.armed = if (on) 1f else 0f // the ball is on this patch (it creaks)
            if (on) ice = max(ice, t.pull.coerceIn(0f, 1f))
        }
        iceFelt = if (ice > 0f) 1f else 0f

        // Rolling solid sphere: 5/7 of the gravity component, plus rolling resistance.
        // Slime adds a thick viscous drag (slow) and far more rolling resistance (sticky).
        val speed = sqrt(vx * vx + vy * vy)
        // Charged: gravity bites harder and the viscous drag drops, so the ball gets quicker and reaches a higher top speed
        val damp = (DAMP + TOXIC_DAMP * slime) * (1f - 0.7f * charge * boostGain.coerceAtMost(1f)) * (1f - 0.8f * ice)
        val drive = (ROLL + (1f - ROLL) * ice) * (1f + boostGain * charge)
        var fx = ax * drive - vx * damp
        var fy = ay * drive - vy * damp
        windFelt = 0f
        for (t in traps) {
            if (t.kind != TrapKind.WIND || !t.open) continue
            val dx = ballX - t.cx; val dy = ballY - t.cy
            val d2 = dx * dx + dy * dy
            if (d2 < WIND_RANGE * WIND_RANGE && d2 > 1e-4f && !wallBetween(t.cx, t.cy, ballX, ballY)) {
                val d = sqrt(d2)
                // Strongest at the mouth, fading with distance, like a fan's outflow
                val push = t.pull * (1f - d / WIND_RANGE)
                fx += dx / d * push; fy += dy / d * push
                windFelt = max(windFelt, 1f - d / WIND_RANGE)
            }
        }
        spinFelt = 0f
        for (t in traps) {
            if (t.kind != TrapKind.SPINNER || !t.open) continue
            val dx = ballX - t.cx; val dy = ballY - t.cy
            val d2 = dx * dx + dy * dy
            val reach = t.range + radius
            if (d2 < reach * reach && d2 > 1e-4f && !wallBetween(t.cx, t.cy, ballX, ballY)) {
                val d = sqrt(d2)
                val k = 1f - d / reach
                // A circular current: mostly sideways, with a gentle pull inward so the ball orbits; tilting hard breaks it free
                val side = t.pull * (0.5f + 0.5f * k)
                fx += -dy / d * side - dx / d * t.pull * 0.35f * k
                fy += dx / d * side - dy / d * t.pull * 0.35f * k
                spinFelt = max(spinFelt, k)
            }
        }
        for (t in traps) {
            if (t.kind != TrapKind.MAGNET || !t.open) continue
            val dx = t.cx - ballX; val dy = t.cy - ballY
            val d2 = dx * dx + dy * dy
            // Attraction begins the moment the ball's edge touches the red field and grows toward the centre
            val reach = MAGNET_RANGE + radius
            if (d2 < reach * reach && d2 > 1e-4f) {
                val d = sqrt(d2)
                val k = 1f - d / reach // 0 at the rim, 1 at the centre
                val pull = t.pull * (0.3f + 2.5f * k * k)
                fx += dx / d * pull; fy += dy / d * pull
            }
        }
        if (speed > 1e-4f) {
            val res = min(RESIST * normal * (1f + TOXIC_STICK * slime) * (1f - 0.5f * charge * boostGain.coerceAtMost(1f)) * (1f - 0.97f * ice), speed / h)
            fx -= vx / speed * res
            fy -= vy / speed * res
        }
        vx += fx * h
        vy += fy * h
        ballX += vx * h
        ballY += vy * h

        // Laser: a ball touching the live beam is destroyed where it stands
        for (t in traps) {
            if (t.kind != TrapKind.LASER || !t.open) continue
            val (dxn, dyn) = laserDir(t)
            val px = ballX - t.cx; val py = ballY - t.cy
            val along = (px * dxn + py * dyn).coerceIn(0f, t.beam)
            val ox = px - dxn * along; val oy = py - dyn * along
            if (ox * ox + oy * oy < radius * radius) {
                sounds.roll(0f); rollSound = 0f
                sounds.disintegrate()
                haptics.zap()
                if (isTestLevel) { newLevel(level); start(); return }
                lostTrap = t; lostX = ballX; lostY = ballY
                sink = 0f
                enter(Phase.LOST)
                onRunOver(level - 1, runTime + elapsed)
                return
            }
        }

        for (t in traps) {
            if (!t.open || !t.kind.killer || t.kind == TrapKind.LASER) continue // only killers swallow the ball; the laser is handled above
            val dx = ballX - t.cx; val dy = ballY - t.cy
            if (dx * dx + dy * dy < HOLE_R * HOLE_R) {
                sounds.roll(0f); rollSound = 0f
                sounds.fall()
                haptics.fall()
                if (isTestLevel) { newLevel(level); start(); return }
                lostTrap = t; lostX = t.cx; lostY = t.cy
                sink = 0f
                enter(Phase.LOST)
                onRunOver(level - 1, runTime + elapsed)
                return
            }
        }

        // Finish hole: once the centre is over it while it is open, the ball drops in
        val fx0 = maze.finish.x + 0.5f
        val fy0 = maze.finish.y + 0.5f
        val dxh = ballX - fx0; val dyh = ballY - fy0
        if (finishOpen && dxh * dxh + dyh * dyh < HOLE_R * HOLE_R) {
            sink = 0f
            sounds.roll(0f); rollSound = 0f
            sounds.finish()
            haptics.finish()
            if (!isTestLevel) {
                onBoardDone(level, elapsed); runTime += elapsed
                while (boardTimes.size < level - 1) boardTimes.add(0f) // a loaded run may lack early entries
                if (boardTimes.size >= level) boardTimes[level - 1] = elapsed else boardTimes.add(elapsed)
            }
            enter(Phase.FINISHED)
            if (!isTestLevel) onLevelDone(level, runTime)
            return
        }

        collideWalls()
    }

    private fun updateTraps(dt: Float) {
        val t = (level - 1) / (Levels.COUNT - 1).toFloat()
        val finishPeriod = 3.2f - 1.4f * t
        finishOpen = sin(levelTime * 6.2832f / finishPeriod) > 0.15f - 0.35f * t
        for (t in traps) {
            when (t.kind) {
                TrapKind.BLINK -> {
                    val was = t.open
                    t.open = sin(levelTime * 6.2832f / t.period + t.phase) > 0.15f - 0.35f * (level - 1) / (Levels.COUNT - 1)
                    if (was != t.open) { val h = hearing(t); sounds.lid(h, pan(t)); haptics.tick(h * 0.6f) } // the lid clacks, heard and felt only nearby
                }
                TrapKind.MAGNET, TrapKind.WIND, TrapKind.RADIOACTIVE, TrapKind.SPEED -> {
                    // on = pulling / blowing / leaking / charged; a magnet also stays off for a while after releasing the ball
                    val was = t.open
                    t.open = (t.kind != TrapKind.MAGNET || levelTime > t.releaseAt) && ((levelTime / t.period + t.phase / 6.2832f) % 1f) < t.duty
                    if (t.open && !was) { val h = hearing(t); sounds.activate(t.kind, h, pan(t)); haptics.tick(h * 0.8f) } // heard and felt switching on, only nearby
                }
                TrapKind.SLIDE -> {
                    val s = sin(time * 6.2832f / t.period + t.phase) * t.range
                    if (t.axisX) t.cx = t.x + s else t.cy = t.y + s
                }
                TrapKind.TRAPDOOR -> {
                    // Arms when the ball comes close, drops after the delay, resets once the ball has been gone a while
                    val dx = ballX - t.cx; val dy = ballY - t.cy
                    val near = dx * dx + dy * dy < t.range * t.range && !wallBetween(t.cx, t.cy, ballX, ballY)
                    if (t.armed < 0f) {
                        if (near) { t.armed = 0f; val h = hearing(t); sounds.activate(t.kind, h, pan(t)); haptics.tick(h * 0.5f) }
                    } else {
                        t.armed += dt
                        val was = t.open
                        t.open = t.armed > t.hold
                        if (t.open && !was) { val h = hearing(t); sounds.slam(h, pan(t)); haptics.tick(h) }
                        if (!near && t.armed > t.hold + t.period) { t.armed = -1f; t.open = false }
                    }
                }
                TrapKind.CRUSHER -> {
                    // The piston strikes at the start of every period; its shadow grows during the warning before it
                    val frac = ((levelTime / t.period + t.phase / 6.2832f) % 1f)
                    val was = t.open
                    t.open = frac < CRUSHER_STRIKE / t.period
                    val warn = t.hold / t.period
                    t.armed = when {
                        t.open -> 1f
                        frac > 1f - warn -> (frac - (1f - warn)) / warn
                        else -> 0f
                    }
                    if (t.open && !was) {
                        val h = hearing(t); sounds.activate(t.kind, h, pan(t)); haptics.tick(h)
                        // The blow: a ball standing in the halo is kicked away from the plate, hardest close in
                        val dx = ballX - t.cx; val dy = ballY - t.cy
                        val d = sqrt(dx * dx + dy * dy)
                        val reach = CRUSHER_KICK + radius
                        if (d >= HOLE_R && d < reach) {
                            val k = 1f - (d - HOLE_R) / (reach - HOLE_R)
                            val kick = t.pull * (0.5f + 0.5f * k)
                            vx += dx / d * kick; vy += dy / d * kick
                            sounds.knock(1f); haptics.knock(1f)
                        }
                    }
                }
                TrapKind.LASER, TrapKind.ICE, TrapKind.SPINNER -> {
                    // on = firing / cracked open / swirling
                    val was = t.open
                    t.open = ((levelTime / t.period + t.phase / 6.2832f) % 1f) < t.duty
                    if (t.open && !was) { val h = hearing(t); sounds.activate(t.kind, h, pan(t)); haptics.tick(h * 0.8f) }
                }
                else -> {}
            }
        }
    }

    /** Is a point in the open, within reach of this wind trap's blow and not sheltered by a wall? */
    fun windReaches(t: Trap, x: Float, y: Float): Boolean {
        val dx = x - t.cx; val dy = y - t.cy
        return dx * dx + dy * dy < WIND_RANGE * WIND_RANGE && !wallBetween(t.cx, t.cy, x, y)
    }

    /** Does any wall segment cross the straight line between the two points? */
    private fun wallBetween(x0: Float, y0: Float, x1: Float, y1: Float): Boolean {
        for (w in maze.walls) {
            if (segmentsCross(x0, y0, x1, y1, w.x0, w.y0, w.x1, w.y1)) return true
        }
        return false
    }

    private fun segmentsCross(ax: Float, ay: Float, bx: Float, by: Float, cx: Float, cy: Float, dx: Float, dy: Float): Boolean {
        fun orient(px: Float, py: Float, qx: Float, qy: Float, rx: Float, ry: Float) = (qx - px) * (ry - py) - (qy - py) * (rx - px)
        val o1 = orient(ax, ay, bx, by, cx, cy); val o2 = orient(ax, ay, bx, by, dx, dy)
        val o3 = orient(cx, cy, dx, dy, ax, ay); val o4 = orient(cx, cy, dx, dy, bx, by)
        return (o1 > 0) != (o2 > 0) && (o3 > 0) != (o4 > 0)
    }

    private fun collideWalls() {
        var hardest = 0f; var hardestWall: Wall? = null
        for (w in maze.walls) {
            // Quick reject by bounding box
            if (ballX + radius + WALL_R < min(w.x0, w.x1) || ballX - radius - WALL_R > max(w.x0, w.x1)) continue
            if (ballY + radius + WALL_R < min(w.y0, w.y1) || ballY - radius - WALL_R > max(w.y0, w.y1)) continue
            // Closest point on the segment to the ball centre
            val dx = w.x1 - w.x0; val dy = w.y1 - w.y0
            val len2 = dx * dx + dy * dy
            val t = if (len2 < 1e-6f) 0f else ((ballX - w.x0) * dx + (ballY - w.y0) * dy) / len2
            val tc = t.coerceIn(0f, 1f)
            val cx = w.x0 + dx * tc; val cy = w.y0 + dy * tc
            var nx = ballX - cx; var ny = ballY - cy
            val dist = sqrt(nx * nx + ny * ny)
            val minDist = radius + WALL_R
            if (dist >= minDist) continue
            if (dist < 1e-5f) { nx = 0f; ny = -1f } else { nx /= dist; ny /= dist }
            // Push out, then bounce the normal component and rub off some tangential speed
            val push = minDist - (if (dist < 1e-5f) 0f else dist)
            ballX += nx * push; ballY += ny * push
            val vn = vx * nx + vy * ny
            if (vn < 0f) {
                val vtx = vx - vn * nx; val vty = vy - vn * ny
                // Below a small approach speed the contact is inelastic: the ball rests on the wall.
                // Sliding along the wall rubs off speed at a fixed rate per second of contact.
                val e = if (-vn < SETTLE) 0f else BOUNCE
                val rub = 1f - WALL_FRICTION * STEP
                vx = vtx * rub - vn * e * nx
                vy = vty * rub - vn * e * ny
                if (-vn > hardest) { hardest = -vn; hardestWall = w }
            }
        }
        if (hardest > 0.6f) {
            val k = ((hardest - 0.6f) / 8f).coerceIn(0f, 1f)
            sounds.knock(0.25f + 0.75f * k, hardestWall?.let { maze.runLength(it) } ?: 1f) // the wall's length sets the knock's pitch
            haptics.knock(k)
        }
    }

    companion object {

        /** One cell is taken as 6 cm: a real 1.5 cm board needs 1-2 degree tilts nobody can hold on a phone. */
        const val G_CELLS = 9.81f / 0.06f
        const val ROLL = 5f / 7f
        const val RESIST = 0.015f * G_CELLS
        /** Mild viscous damping (per second) keeps top speed controllable. */
        const val DAMP = 0.7f
        const val BOUNCE = 0.4f
        const val SETTLE = 0.6f
        const val STEP = 1f / 600f
        /** Tangential speed lost per second while sliding along a wall. */
        const val WALL_FRICTION = 2.5f
        const val WALL_R = 0.1f
        /** Hole radius, the same 0.33 cells the holes are drawn with: the ball tips in once its centre crosses the rim. */
        const val HOLE_R = 0.33f
        /** Pull zone: the red halo drawn around an active magnet, 1 cell around it; walls do not block it. */
        const val MAGNET_RANGE = 1.0f
        const val WIND_RANGE = 2.0f
        /** A board must be finished within this many seconds, or the ball is lost. */
        const val TIME_LIMIT = 180f
        /** Seconds at a board's start during which the ball stays put, whatever the tilt. */
        const val GRACE = 0.5f
        /** How long the ball's explosion plays. */
        const val EXPLOSION = 1.4f
        /** Trap sounds fade out over this many cells from the ball. */
        const val HEAR_RANGE = 3.0f
        private val HEARD = listOf(TrapKind.MAGNET, TrapKind.WIND, TrapKind.RADIOACTIVE, TrapKind.SPEED, TrapKind.CRUSHER, TrapKind.LASER, TrapKind.ICE, TrapKind.SPINNER)
        /** How long the crusher's piston is down on each strike, and how far its blow reaches (the halo). */
        const val CRUSHER_STRIKE = 0.35f
        const val CRUSHER_KICK = 0.45f
        /** How long a speed charge takes to fade at its end. */
        const val BOOST_FADE = 0.3f
        /** Slime: extra viscous drag (per second) and rolling-resistance multiplier at full contamination. */
        const val TOXIC_DAMP = 5f
        const val TOXIC_STICK = 5f
        /** The slime dries off over the last part of the contamination. */
        const val TOXIC_FADE = 0.5f
    }
}
