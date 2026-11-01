package com.megapro.labyrinth.ui

import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.os.Build
import androidx.compose.foundation.Canvas
import androidx.compose.ui.graphics.Canvas as BitmapCanvas
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.BlendMode
import androidx.compose.ui.graphics.Paint
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ShaderBrush
import androidx.compose.ui.graphics.asAndroidBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.scale
import com.megapro.labyrinth.game.Maze
import com.megapro.labyrinth.game.Wall
import com.megapro.labyrinth.game.Trap
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import com.megapro.labyrinth.data.Settings
import com.megapro.labyrinth.game.Medal
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.size
import com.megapro.labyrinth.audio.Music
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.ClipOp
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.width
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.mutableStateOf
import com.megapro.labyrinth.data.Leaderboard
import com.megapro.labyrinth.data.LevelBoards
import com.megapro.labyrinth.data.SaveSlot
import androidx.compose.material3.Button
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.foundation.layout.height
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.rememberScrollState
import kotlin.math.roundToInt
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.layout.layout
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.widthIn
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.material3.ButtonDefaults
import com.megapro.labyrinth.data.RunEntry
import com.megapro.labyrinth.game.Game
import com.megapro.labyrinth.game.Levels
import com.megapro.labyrinth.game.Environment
import com.megapro.labyrinth.game.TrapKind
import com.megapro.labyrinth.game.Phase
import kotlin.math.min
import kotlin.math.sin

private val Felt = Color(0xFF2B1D12)
private val WoodLight = Color(0xFF9C6B3C)
private val WoodMid = Color(0xFF7A5230)
private val WoodDark = Color(0xFF4E3119)
private val WallTop = Color(0xFFA9774A)
private val WallShade = Color(0xFF3A2414)
private val Ink = Color(0xFFE8DCC8)

@Composable
fun GameScreen(game: Game, leaderboard: Leaderboard, levelBoards: LevelBoards, saveSlot: SaveSlot, settings: Settings, music: Music) {
    var frame by remember { mutableIntStateOf(0) }
    val tm = rememberTextMeasurer()
    LaunchedEffect(Unit) {
        var last = 0L
        while (true) {
            withFrameNanos { now ->
                if (last != 0L) game.update(((now - last) / 1_000_000_000f).coerceAtMost(0.05f))
                last = now
                frame++
            }
        }
    }
    // Some tablets ignore the portrait lock. Whatever the system does, undo it here so the game
    // always sits upright relative to the device: when the window is sideways, rotate the whole
    // content back by the display rotation.
    val view = LocalView.current
    @Suppress("DEPRECATION")
    val rotation = (view.context.getSystemService(android.content.Context.WINDOW_SERVICE) as android.view.WindowManager).defaultDisplay.rotation
    BoxWithConstraints(Modifier.fillMaxSize().background(Felt)) {
        // Only a window that came up sideways is turned back. A portrait window is already upright,
        // whatever the display's rotation: on a tablet whose natural way is landscape, portrait is
        // a rotation of 90 or 270, and turning it would lay the game on its side.
        // Judged from the whole window, exactly as the sensor's turning is (MainActivity), so the two always agree
        val root = view.rootView
        val landscapeWindow = if (root.width > 0 && root.height > 0) !(root.height >= root.width) else maxWidth > maxHeight
        val counter = when {
            landscapeWindow && rotation == android.view.Surface.ROTATION_90 -> -90f
            landscapeWindow && rotation == android.view.Surface.ROTATION_270 -> 90f
            rotation == android.view.Surface.ROTATION_180 -> 180f // upside down, either way round: turned back
            else -> 0f
        }
        val sideways = counter == -90f || counter == 90f
        val w = if (sideways) maxHeight else maxWidth
        val h = if (sideways) maxWidth else maxHeight
        Box(
            Modifier.requiredSize(w, h).align(Alignment.Center).graphicsLayer { rotationZ = counter },
        ) {
            GameContent(game, leaderboard, levelBoards, saveSlot, settings, music, frame, tm)
        }
    }
}

@Composable
private fun GameContent(game: Game, leaderboard: Leaderboard, levelBoards: LevelBoards, saveSlot: SaveSlot, settings: Settings, music: Music, frame: Int, tm: TextMeasurer) {
    LaunchedEffect(settings.musicMuted) { music.muted = settings.musicMuted }
    LaunchedEffect(game.level) { music.board(game.level) }
    LaunchedEffect(game.phase) { music.hold(game.phase == Phase.PAUSED) } // quiet while paused
    var showEntry by remember { mutableStateOf(false) }
    var askedFor by remember { mutableStateOf(-1L to -1) }
    val materials = remember { Materials() }
    LaunchedEffect(game.phase, game.level) {
        val reached = if (game.phase == Phase.LOST) game.level - 1 else game.level
        // Only a real run asks for a name: the test board and free play are never ranked
        if ((game.phase == Phase.COMPLETE || game.phase == Phase.LOST) && !game.isTestLevel) {
            val name = leaderboard.nameFor(game.runId)
            if (name != null) {
                // A run already named ended again (loaded from the same save): its best standing
                // stays on the board, and any new board times it set get its name
                leaderboard.improve(game.runId, reached, game.runTime)
                levelBoards.name(game.runId, name)
            } else if (askedFor != (game.runId to game.level) &&
                (leaderboard.qualifies(game.runId, reached, game.runTime) || levelBoards.hasRun(game.runId))
            ) {
                askedFor = game.runId to game.level
                showEntry = true
            }
        }
    }
    Box(
        Modifier.fillMaxSize().background(Felt).safeDrawingPadding()
            .pointerInput(Unit) {
                detectTapGestures(
                    onLongPress = { if (game.phase == Phase.TITLE) game.startTest() },
                    onTap = { game.onTap() },
                )
            },
    ) {
        Column(Modifier.fillMaxSize()) {
            Canvas(
                Modifier.fillMaxWidth().weight(1f).padding(bottom = 4.dp).pointerInput(game) {
                    // The level counter in the bottom rail: a long press on it mutes or unmutes the
                    // music; on the test board a vertical drag on it rolls the board for free play.
                    // Only the counter's own window, bezel included, is touch-sensitive.
                    awaitEachGesture {
                        val down = awaitFirstDown(requireUnconsumed = false)
                        // When the canvas is wider than tall the board is drawn turned a quarter
                        // left, so touches are mapped back into the board's own portrait space
                        val landscape = size.width > size.height
                        val bw = (if (landscape) size.height else size.width).toFloat()
                        val bh = (if (landscape) size.width else size.height).toFloat()
                        fun map(p: Offset) = if (landscape) Offset(size.height - p.y, p.x) else p
                        val l = BoardLayout(game.maze, bw, bh)
                        val digitH = l.reelDigit; val pad = digitH * 0.16f // as drawReelWindow draws it
                        val halfW = (3 * digitH * 0.66f) / 2f + pad; val halfH = digitH / 2f + pad
                        val centreY = l.fieldBottom + (l.by + l.bh - l.fieldBottom) / 2f
                        val dp = map(down.position)
                        val onCounter = kotlin.math.abs(dp.x - (l.bx + l.bw / 2f)) < halfW && kotlin.math.abs(dp.y - centreY) < halfH
                        if (!onCounter) return@awaitEachGesture
                        down.consume()
                        val step = 36.dp.toPx(); val slop = viewConfiguration.touchSlop
                        val startY = dp.y; val t0 = System.currentTimeMillis()
                        var dragging = false; var toggled = false; var acc = 0f
                        while (true) {
                            // A finger held still still reports tiny moves, so the hold is timed on
                            // every event as well as when none comes
                            val event = withTimeoutOrNull(60L) { awaitPointerEvent() }
                            val change = event?.changes?.firstOrNull { it.id == down.id }
                            if (event != null && (change == null || !change.pressed)) break
                            if (change != null) {
                                val cp = map(change.position)
                                if (kotlin.math.abs(cp.y - startY) > slop) dragging = true
                            }
                            if (!dragging && !toggled && System.currentTimeMillis() - t0 > 600) { toggled = true; settings.muteMusic(!settings.musicMuted) }
                            if (change == null) continue
                            val cp = map(change.position); val pp = map(change.previousPosition)
                            if (dragging && game.isTestLevel) {
                                acc -= cp.y - pp.y
                                var target = game.level
                                while (acc >= step) { acc -= step; target = (target + 1).coerceAtMost(Levels.COUNT) }
                                while (acc <= -step) { acc += step; target = (target - 1).coerceAtLeast(0) }
                                if (target != game.level) game.playFree(target) // free play of that board, or back to the playground at 0
                            }
                            change.consume()
                        }
                    }
                },
            ) {
                @Suppress("UNUSED_EXPRESSION")
                frame
                translate(materials.shakeX, materials.shakeY + materials.slideY) { // a hit shakes the whole board; a new board slides up into place
                    if (size.width > size.height) {
                        // Landscape: draw the portrait board turned a quarter left, START at the screen's left
                        rotate(-90f, pivot = Offset(size.width / 2f, size.height / 2f)) {
                            translate(left = (size.width - size.height) / 2f, top = (size.height - size.width) / 2f) {
                                drawBoard(game, tm, materials, size.height, size.width)
                            }
                        }
                    } else {
                        drawBoard(game, tm, materials, size.width, size.height)
                    }
                }
            }
        }
        Overlay(game, leaderboard.entries, levelBoards, saveSlot, settings, Modifier.align(Alignment.Center))
        // All hundred boards done: fireworks all over the screen, over everything, letting taps through
        if (game.phase == Phase.COMPLETE) Fireworks(game, frame)
    }
    if (showEntry) {
        NameDialog(
            level = if (game.phase == Phase.LOST) game.level - 1 else game.level, time = game.runTime, initial = leaderboard.lastName,
            onSave = { leaderboard.record(game.runId, it, if (game.phase == Phase.LOST) game.level - 1 else game.level, game.runTime); levelBoards.name(game.runId, it); showEntry = false },
            onSkip = { levelBoards.name(game.runId, "PLAYER"); showEntry = false },
        )
    }
}

// ------------------------------------------------------------------ overlay styling

private val Gold = Color(0xFFF2C98A)
private val GoldDim = Color(0xFFBFA98A)
private val CardBg = Color(0xF02A1B10)
private val WoodBtn = Color(0xFF8E6240)
private val WoodBtnDark = Color(0xFF5A3C22)
private val WoodBtnEdge = Color(0xFFB98A5A)

/** The still wood of one board, rendered on the GPU: frame and floor, and the wall strips (transparent elsewhere). */
private class GpuLayers(val floor: ImageBitmap, val walls: ImageBitmap)

/** A speck of dust, spark, shard or smoke: position and speed in cells, life in seconds. */
class Particle(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val size: Float, val color: Color, val gravity: Float, val drag: Float) {
    val maxLife = life
}

/** Type grows on tablet-sized screens so the card reads as well there as on a phone. */
@Composable
private fun typeScale(): Float = if (LocalConfiguration.current.smallestScreenWidthDp >= 600) 1.25f else 1f

/**
 * A card taller than the screen (a short phone with everything filled in) is shrunk evenly until it
 * fits, so nothing is ever cut off or has to be scrolled; a card that fits keeps its full size.
 */
private fun Modifier.shrinkToFit(): Modifier = layout { measurable, constraints ->
    val full = measurable.measure(constraints.copy(minHeight = 0, maxHeight = Constraints.Infinity))
    val room = constraints.maxHeight
    val k = if (constraints.hasBoundedHeight && full.height > room) room.toFloat() / full.height else 1f
    val h = (full.height * k).roundToInt()
    layout(full.width, h) {
        full.placeWithLayer(0, (h - full.height) / 2) { scaleX = k; scaleY = k; transformOrigin = TransformOrigin(0.5f, 0.5f) }
    }
}

/** The game's card: dark wood-stained glass with a thin gold edge. */
private fun Modifier.card(): Modifier = this
    .background(CardBg, RoundedCornerShape(18.dp))
    .border(1.5.dp, Gold.copy(alpha = 0.45f), RoundedCornerShape(18.dp))
    .padding(horizontal = 22.dp, vertical = 20.dp)

/** A small gold heading with rules either side. */
@Composable
private fun SectionTitle(text: String) {
    val fs = typeScale()
    Row(Modifier.fillMaxWidth().padding(top = 18.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        HorizontalDivider(Modifier.weight(1f), color = Gold.copy(alpha = 0.3f))
        Text(text, color = Gold, fontSize = (11 * fs).sp, letterSpacing = 2.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 10.dp))
        HorizontalDivider(Modifier.weight(1f), color = Gold.copy(alpha = 0.3f))
    }
}

/** The time column of the lists, in dp before the type scale. */
private const val TIME_COL = 74

/** One line of a list: rank, name, an optional tag, and the time, in fixed columns; the player's own line is lit. */
@Composable
private fun ListRow(index: Int, rank: String, name: String, tag: String?, time: String, mine: Boolean, rankWidth: Int = 26) {
    val fs = typeScale()
    val fg = if (mine) Gold else Ink
    val bg = when { mine -> Gold.copy(alpha = 0.14f); index % 2 == 1 -> Color.White.copy(alpha = 0.05f); else -> Color.Transparent }
    Row(
        Modifier.fillMaxWidth().background(bg, RoundedCornerShape(6.dp)).padding(horizontal = 8.dp, vertical = 3.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(rank, color = if (mine) Gold else GoldDim, fontSize = (13 * fs).sp, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width((rankWidth * fs).dp))
        Text(name, color = fg, fontSize = (14 * fs).sp, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).padding(start = 10.dp))
        if (tag != null) Text(tag, color = Gold, fontSize = (13 * fs).sp, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width((48 * fs).dp))
        // Wide enough for the longest run (100 boards of three minutes: 300:00.0), with a gap after the level
        Text(time, color = fg, fontSize = (13 * fs).sp, fontFamily = FontFamily.Monospace, textAlign = TextAlign.End, maxLines = 1, softWrap = false, modifier = Modifier.padding(start = 6.dp).width((TIME_COL * fs).dp))
    }
}

/** A wooden button, the same everywhere; dimmed when it has nothing left to do. */
@Composable
private fun WoodButton(text: String, enabled: Boolean = true, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        enabled = enabled,
        shape = RoundedCornerShape(10.dp),
        colors = ButtonDefaults.buttonColors(containerColor = WoodBtn, contentColor = Color(0xFFFFF1DC), disabledContainerColor = WoodBtnDark, disabledContentColor = GoldDim),
        border = BorderStroke(1.dp, if (enabled) WoodBtnEdge else WoodBtnDark),
        contentPadding = PaddingValues(horizontal = 22.dp, vertical = 10.dp),
        modifier = Modifier.widthIn(min = (120 * typeScale()).dp),
    ) { Text(text, fontSize = (14 * typeScale()).sp, letterSpacing = 2.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun NameDialog(level: Int, time: Float, initial: String, onSave: (String) -> Unit, onSkip: () -> Unit) {
    var name by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onSkip,
        containerColor = CardBg,
        titleContentColor = Gold,
        textContentColor = Ink,
        shape = RoundedCornerShape(18.dp),
        title = { Text("TOP 10", letterSpacing = 3.sp, fontWeight = FontWeight.Black) },
        text = {
            Column {
                Text("Level $level reached in ${formatTime(time)}.", lineHeight = 22.sp)
                Text("Your name for the boards:", color = GoldDim, modifier = Modifier.padding(top = 4.dp))
                OutlinedTextField(
                    value = name, onValueChange = { if (it.length <= Leaderboard.MAX_NAME) name = it }, singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Gold, unfocusedBorderColor = GoldDim, focusedTextColor = Ink, unfocusedTextColor = Ink, cursorColor = Gold),
                    modifier = Modifier.padding(top = 12.dp).fillMaxWidth(),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name) }) { Text("SAVE", color = Gold, letterSpacing = 2.sp, fontWeight = FontWeight.Bold) } },
        dismissButton = { TextButton(onClick = onSkip) { Text("SKIP", color = GoldDim, letterSpacing = 2.sp) } },
    )
}

/** [c] toned toward the world's wood colour, keeping its brightness, so the grain stays as it was. */
private fun woodTone(env: Environment, c: Color): Color {
    if (env.woodAmount <= 0f) return c
    val t = Color(env.wood)
    val lc = 0.299f * c.red + 0.587f * c.green + 0.114f * c.blue; val lt = (0.299f * t.red + 0.587f * t.green + 0.114f * t.blue).coerceAtLeast(1e-3f)
    val k = lc / lt
    return lerp(c, Color((t.red * k).coerceIn(0f, 1f), (t.green * k).coerceIn(0f, 1f), (t.blue * k).coerceIn(0f, 1f), c.alpha), env.woodAmount)
}

/** One glowing star of a burst, or a rocket on its way up. */
private class Spark(var x: Float, var y: Float, var vx: Float, var vy: Float, var life: Float, val maxLife: Float, val color: Color, val size: Float, val drag: Float, val rocket: Boolean = false, val burstAt: Float = 0f) {
    var px = x; var py = y
}

/** The celebration's state: rockets and stars in flight, and when the next rocket goes up. */
private class Show { var w = 0f; var h = 0f; val sparks = ArrayList<Spark>(); val born = ArrayList<Spark>(); var last = 0L; var next = 0f; var clock = 0f; val rng = kotlin.random.Random(100) }

/**
 * Fireworks all over the screen for finishing all hundred boards: rockets climb from the bottom
 * and burst high and low in the colours of the last world, with gold and white: spheres, rings,
 * and golden willows that droop and fade. Each burst is heard where it is.
 */
@Composable
private fun Fireworks(game: Game, frame: Int) {
    val show = remember { Show() }
    val palette = remember { Environment.of(Levels.COUNT).lamps.map { Color(it) } + listOf(Color(0xFFFFD54F), Color.White, Color(0xFFFF8AB3), Color(0xFF8CFF9E)) }
    // The show moves on the frame clock: launches, bursts and their sounds happen once per frame,
    // whatever the drawing does
    LaunchedEffect(show) { while (true) withFrameNanos { now -> stepShow(show, now, game, palette) } }
    Canvas(Modifier.fillMaxSize()) {
        @Suppress("UNUSED_EXPRESSION")
        frame
        show.w = size.width; show.h = size.height
        // Each star a bright head with a short streak behind it and a soft glow while young, fading as it burns out
        for (s in show.sparks) {
            val f = (s.life / s.maxLife).coerceIn(0f, 1f)
            val twinkle = if (!s.rocket && f < 0.35f) (0.5f + 0.5f * sin(s.x * 0.3f + show.clock * 40f)) else 1f
            val a = (if (s.rocket) 1f else f) * twinkle
            drawLine(s.color.copy(alpha = 0.5f * a), Offset(s.px, s.py), Offset(s.x, s.y), s.size * 1.4f, StrokeCap.Round, blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
            if (f > 0.5f || s.rocket) drawCircle(s.color.copy(alpha = 0.22f * a), s.size * 3.2f, Offset(s.x, s.y), blendMode = androidx.compose.ui.graphics.BlendMode.Plus)
            drawCircle(lerp(s.color, Color.White, 0.5f).copy(alpha = a), s.size, Offset(s.x, s.y))
        }
    }
}

/** One frame of the show: rockets go up, reach their height and burst (heard where they are), stars fall and fade. */
private fun stepShow(show: Show, now: Long, game: Game, palette: List<Color>) {
    val w = show.w; val h = show.h
    if (w <= 0f || h <= 0f) return
    val dt = if (show.last == 0L) 0f else ((now - show.last) / 1e9f).coerceIn(0f, 0.05f)
    show.last = now; show.clock += dt
    val unit = kotlin.math.min(w, h)
    val g = h * 0.35f // gravity, in pixels a second squared
    val rng = show.rng
    // Launch: a salvo at first, then a rocket every half second or so, sometimes two
    if (show.clock >= show.next) {
        repeat(if (show.clock < 0.1f) 3 else if (rng.nextFloat() < 0.3f) 2 else 1) {
            val x = w * (0.1f + 0.8f * rng.nextFloat())
            val top = h * (0.12f + 0.4f * rng.nextFloat())
            val v = kotlin.math.sqrt(2f * g * (h - top))
            show.sparks.add(Spark(x, h, (rng.nextFloat() - 0.5f) * w * 0.05f, -v, 3f, 3f, Color(0xFFFFE9B0), unit * 0.006f, 0f, rocket = true, burstAt = top))
        }
        show.next = show.clock + 0.35f + 0.5f * rng.nextFloat()
    }
    val born = show.born; born.clear()
    val it = show.sparks.iterator()
    while (it.hasNext()) {
        val s = it.next()
        s.px = s.x; s.py = s.y
        s.vy += g * dt
        val keep = kotlin.math.exp(-s.drag * dt); s.vx *= keep; s.vy *= keep
        s.x += s.vx * dt; s.y += s.vy * dt
        s.life -= dt
        if (s.rocket && (s.y <= s.burstAt || s.vy >= 0f)) {
            it.remove()
            val kind = rng.nextInt(3) // 0 sphere, 1 ring, 2 golden willow
            val c1 = palette[rng.nextInt(palette.size)]; val c2 = palette[rng.nextInt(palette.size)]
            val count = if (kind == 1) 48 else 72 + rng.nextInt(40)
            val speed = unit * (0.35f + 0.3f * rng.nextFloat())
            for (k in 0 until count) {
                val a = 6.2832f * k / count + rng.nextFloat() * 0.1f
                val sp = if (kind == 1) speed else speed * kotlin.math.sqrt(rng.nextFloat())
                val life = if (kind == 2) 2.6f + rng.nextFloat() else 1.3f + 0.8f * rng.nextFloat()
                val color = if (kind == 2) Color(0xFFFFC94A) else if (k % 2 == 0) c1 else c2
                born.add(Spark(s.x, s.y, kotlin.math.cos(a) * sp, kotlin.math.sin(a) * sp, life, life, color, unit * (0.004f + 0.003f * rng.nextFloat()), if (kind == 2) 2.2f else 1.2f))
            }
            game.celebrate(count / 110f, (s.x / w * 2f - 1f).coerceIn(-1f, 1f))
            continue
        }
        if (s.life <= 0f || s.y > h * 1.1f) it.remove()
    }
    show.sparks.addAll(born)
    if (show.sparks.size > 600) { var extra = show.sparks.size - 600; show.sparks.removeAll { s -> !s.rocket && extra-- > 0 } } // the oldest stars go first; rockets always burst
}

/** The boards finished in the saved run, each with its medal and time; tapping one plays it again, freely. */
@Composable
private fun YourBoards(times: List<Float>, onPlay: (Int) -> Unit) {
    val fs = typeScale()
    SectionTitle("YOUR BOARDS")
    Text("Tap a board to play it again, unscored.", color = GoldDim, fontSize = (11 * fs).sp, textAlign = TextAlign.Center)
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        times.forEachIndexed { i, t ->
            if (t <= 0f) return@forEachIndexed
            val level = i + 1
            val medal = Levels.medal(level, t)
            val ring = medal?.let { medalColour(it) } ?: GoldDim.copy(alpha = 0.5f)
            Column(
                Modifier.background(Color(0xFF3B2614), RoundedCornerShape(8.dp)).border(1.dp, ring, RoundedCornerShape(8.dp)).clickable { onPlay(level) }.padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text("$level", color = Gold, fontSize = (15 * fs).sp, fontWeight = FontWeight.Bold)
                Canvas(Modifier.size((14 * fs).dp).padding(vertical = 2.dp)) {
                    if (medal != null) { drawCircle(medalColour(medal)); drawCircle(Color.Black.copy(alpha = 0.35f), style = Stroke(width = 1.5f)) } else drawCircle(GoldDim.copy(alpha = 0.4f), style = Stroke(width = 1.5f))
                }
                Text(formatTime(t), color = GoldDim, fontSize = (10 * fs).sp, fontFamily = FontFamily.Monospace)
            }
        }
    }
}

private fun medalColour(m: Medal) = when (m) { Medal.GOLD -> Color(0xFFFFD54F); Medal.SILVER -> Color(0xFFCFD8DC); Medal.BRONZE -> Color(0xFFCD7F32) }

/** A thumbnail of a board's maze, for the saved run. */
@Composable
private fun BoardThumbnail(level: Int) {
    val maze = remember(level) { Levels.maze(level) }
    Canvas(Modifier.width(64.dp).height(100.dp).background(Color(0xFF3B2614), RoundedCornerShape(6.dp)).border(1.dp, Gold.copy(alpha = 0.5f), RoundedCornerShape(6.dp)).padding(5.dp)) {
        val u = kotlin.math.min(size.width / maze.cols, size.height / maze.rows)
        val ox = (size.width - maze.cols * u) / 2f; val oy = (size.height - maze.rows * u) / 2f
        for (w in maze.walls) drawLine(Color(0xFFC79560), Offset(ox + w.x0 * u, oy + w.y0 * u), Offset(ox + w.x1 * u, oy + w.y1 * u), 1.2f, StrokeCap.Round)
        drawCircle(Color(0xFFF2EEE6), u * 0.35f, Offset(ox + (maze.start.x + 0.5f) * u, oy + (maze.start.y + 0.5f) * u))
        drawCircle(Color.Black, u * 0.35f, Offset(ox + (maze.finish.x + 0.5f) * u, oy + (maze.finish.y + 0.5f) * u))
    }
}

/** The overall top 10 on the main screen, its rows appearing one after another. */
@Composable
private fun Board(entries: List<RunEntry>) {
    var shown by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { shown = true }
    SectionTitle("TOP 10")
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text("#", color = GoldDim, fontSize = (10 * typeScale()).sp, textAlign = TextAlign.End, maxLines = 1, modifier = Modifier.width((26 * typeScale()).dp))
        Text("PLAYER", color = GoldDim, fontSize = (10 * typeScale()).sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f).padding(start = 10.dp))
        Text("LEVEL", color = GoldDim, fontSize = (10 * typeScale()).sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.width((48 * typeScale()).dp))
        Text("TIME", color = GoldDim, fontSize = (10 * typeScale()).sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.padding(start = 6.dp).width((TIME_COL * typeScale()).dp))
    }
    entries.forEachIndexed { i, e ->
        val a by animateFloatAsState(if (shown) 1f else 0f, tween(350, delayMillis = 60 * i), label = "row")
        Box(Modifier.alpha(a)) { ListRow(i, "${i + 1}", e.name, "L${e.level}", formatTime(e.time), false) }
    }
}

/** The ten boards with the fastest times of all, shortest first, with the player who set each; the current run's own picked out. */
@Composable
private fun TopBoardsPlayers(levelBoards: LevelBoards, runId: Long) {
    val bests = (1..Levels.COUNT).mapNotNull { level -> levelBoards.entries(level).firstOrNull()?.let { level to it } }
        .sortedBy { it.second.time }.take(10)
    if (bests.isEmpty()) return
    SectionTitle("TOP BOARDS PLAYERS")
    Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 2.dp)) {
        Text("BOARD", color = GoldDim, fontSize = (10 * typeScale()).sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.width((52 * typeScale()).dp))
        Text("PLAYER", color = GoldDim, fontSize = (10 * typeScale()).sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false, modifier = Modifier.weight(1f).padding(start = 10.dp))
        Text("TIME", color = GoldDim, fontSize = (10 * typeScale()).sp, letterSpacing = 1.sp, maxLines = 1, softWrap = false, textAlign = TextAlign.End, modifier = Modifier.padding(start = 6.dp).width((TIME_COL * typeScale()).dp))
    }
    bests.forEachIndexed { i, (level, e) -> ListRow(i, "$level", e.name.ifEmpty { if (e.runId == runId) "you" else "…" }, null, formatTime(e.time), e.runId == runId, rankWidth = 52) }
}

@Composable
private fun Overlay(game: Game, entries: List<RunEntry>, levelBoards: LevelBoards, saveSlot: SaveSlot, settings: Settings, modifier: Modifier) {
    val (title, sub) = when (game.phase) {
        Phase.TITLE -> "TILT LABYRINTH" to "Level ${game.level} of ${Levels.COUNT}.\n\nHold the device flat,\nthe way you'll play,\nthen tap to start.\n\nTilt to roll the ball\nfrom the start to the finish hole."
        Phase.PAUSED -> "PAUSED" to "Tap to resume.\nThe current hold becomes level."
        Phase.FINISHED -> {
            // After a RESTART only: how this time compares with the try that was given up
            val compare = if (game.previousTry >= 0f) "\n" + compareText(game.previousTry, game.elapsed) else ""
            // The medal the board time earns against a clean run's par
            val medal = if (game.level > 0) Levels.medal(game.level, game.elapsed) else null
            val medalLine = if (game.level == 0) "" else "\n" + (if (medal != null) "${medal.title} medal." else "No medal: gold is under ${formatTime(Levels.parTime(game.level) * 1.15f)}.")
            if (game.isTestLevel) (if (game.freePlay) "FREE PLAY DONE" else "TEST BOARD DONE") to "Board ${game.level}: ${formatTime(game.elapsed)}.$medalLine$compare\n\nTap around to return to the menu."
            else "LEVEL ${game.level} DONE" to ("Board ${formatTime(game.elapsed)}.$medalLine\nRun ${formatTime(game.runTime)}.$compare\n\n" +
                (if (game.level >= Levels.COUNT) "Tap around to finish the run." else "Tap around for level ${game.level + 1}."))
        }
        Phase.COMPLETE -> "ALL ${Levels.COUNT} LEVELS DONE" to "Total ${formatTime(game.runTime)}.\n\nTap around to start a new run."
        Phase.LOST -> if (game.isTestLevel) "TIME'S UP" to "The board starts over." else (if (game.timedOut) "TIME'S UP" else "TRAPPED") to "Run over at level ${game.level}.\n${game.level - 1} levels completed.\n\nTap around to start again from level 1."
        else -> return
    }
    val fs = typeScale()
    // The full top 10 opens on its own card from the main screen, so the main card stays short and
    // the board stays in sight; a tap anywhere on it (or CLOSE) goes back
    var topTen by remember { mutableStateOf(false) }
    if (game.phase != Phase.TITLE || entries.isEmpty()) topTen = false
    if (topTen) {
        Column(
            modifier.widthIn(max = (420 * fs).dp).fillMaxWidth(0.88f).padding(vertical = 12.dp).shrinkToFit().card()
                .pointerInput(Unit) { detectTapGestures { topTen = false } },
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Board(entries)
            Box(Modifier.padding(top = 16.dp)) { WoodButton("CLOSE") { topTen = false } }
        }
        return
    }
    Column(modifier.widthIn(max = (420 * fs).dp).fillMaxWidth(0.88f).padding(vertical = 12.dp).shrinkToFit().card(), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(title, color = Gold, fontSize = (24 * fs).sp, fontWeight = FontWeight.Black, letterSpacing = 3.sp, textAlign = TextAlign.Center)
        Text(sub, color = Ink, fontSize = (15 * fs).sp, lineHeight = (22 * fs).sp, textAlign = TextAlign.Center, modifier = Modifier.padding(top = 10.dp))
        @Suppress("UNUSED_EXPRESSION")
        saveSlot.version
        // Level done: the run can be saved (one slot, replacing any earlier save), or the board played again
        if (game.phase == Phase.FINISHED) {
            val saved = saveSlot.read()
            // Already saved exactly this state (same run, same next board, same time): a faster retry re-enables SAVE
            val isThis = saved != null && saved.runId == game.runId && saved.level == game.level + 1 && kotlin.math.abs(saved.time - game.runTime) < 0.001f
            Row(Modifier.padding(top = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!game.isTestLevel && game.level < Levels.COUNT) WoodButton(if (isThis) "SAVED" else "SAVE", enabled = !isThis) { saveSlot.write(game.level + 1, game.runTime, game.runId, game.boardTimes.toList()) }
                WoodButton("RESTART") { game.restartLevel() }
            }
        }
        // Main screen: the saved run if there is one, and the overall top 10
        if (game.phase == Phase.TITLE) {
            // How to reach free play: a long press anywhere opens the practice board, whose level counter rolls to any board
            Text("Hold anywhere for free play.", color = GoldDim, fontSize = (11 * fs).sp, textAlign = TextAlign.Center, lineHeight = (15 * fs).sp, modifier = Modifier.padding(top = 12.dp))
            Text("There, roll the level counter to pick any board.", color = GoldDim, fontSize = (11 * fs).sp, textAlign = TextAlign.Center, lineHeight = (15 * fs).sp)
            Text(if (settings.musicMuted) "Music off. Hold the level counter to turn it on." else "Hold the level counter to mute the music.", color = GoldDim, fontSize = (11 * fs).sp, textAlign = TextAlign.Center, lineHeight = (15 * fs).sp, modifier = Modifier.padding(top = 6.dp))
            val saved = saveSlot.read()
            if (saved != null && saved.level in 1..Levels.COUNT && !(saved.runId == game.runId && saved.level == game.level)) {
                Row(Modifier.padding(top = 16.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                    BoardThumbnail(saved.level)
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        WoodButton("LOAD") { game.restore(saved.level, saved.time, saved.runId, saved.boardTimes) }
                        Text("Saved run: level ${saved.level}", color = GoldDim, fontSize = (12 * fs).sp, modifier = Modifier.padding(top = 6.dp))
                        Text(formatTime(saved.time), color = GoldDim, fontSize = (12 * fs).sp, fontFamily = FontFamily.Monospace)
                    }
                }
            }
            if (saved != null && saved.boardTimes.any { it > 0f }) YourBoards(saved.boardTimes) { game.replay(it) }
        }
        if (game.phase == Phase.TITLE && entries.isNotEmpty()) Box(Modifier.padding(top = 16.dp)) { WoodButton("TOP 10") { topTen = true } }
        if (game.phase == Phase.COMPLETE || game.phase == Phase.LOST) {
            @Suppress("UNUSED_EXPRESSION")
            levelBoards.version
            TopBoardsPlayers(levelBoards, game.runId)
        }
    }
}

// ------------------------------------------------------------------ drawing

/**
 * A chrome pinball. With the mirror shader, the ball is a convex mirror of the board around it:
 * the floor, walls and labels wrap around its rim, the dim room reflects in the middle and the
 * lamps put hard hot spots on it. Without the shader (Android 12 and older), a painted chrome look.
 */
private fun DrawScope.drawChromeBall(c: Offset, r: Float, chrome: RuntimeShader?) {
    if (chrome != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        chrome.setFloatUniform("center", c.x, c.y)
        chrome.setFloatUniform("radius", r)
        // The player's eye: above the middle of the screen, a few screen heights up
        chrome.setFloatUniform("eye", size.width / 2f, size.height / 2f, 2.2f * kotlin.math.max(size.width, size.height))
        drawCircle(ShaderBrush(chrome), r, c)
        drawCircle(Color.Black.copy(alpha = 0.35f), r - r * 0.03f, c, style = Stroke(width = r * 0.06f)) // inside the physical radius
        return
    }
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
    drawCircle(
        Brush.radialGradient(
            listOf(Color(0xFFF7F9FB), Color(0xFFC9CFD6), Color(0xFF7D858E), Color(0xFF2B3036)),
            center = Offset(c.x - r * 0.3f, c.y - r * 0.35f), radius = r * 1.55f,
        ),
        r, c,
    )
    clipPath(disc) {
        // Ground reflection below the horizon, warm from the wood, sky reflection above it
        drawRect(
            Brush.verticalGradient(
                0f to Color.Transparent, 0.48f to Color.Transparent, 0.52f to Color(0xFF3A2A1C),
                0.75f to Color(0xFF8A7760), 1f to Color(0xFFB9C0C8),
                startY = c.y - r, endY = c.y + r,
            ),
            Offset(c.x - r, c.y - r), Size(2 * r, 2 * r),
        )
        drawRect(
            Brush.verticalGradient(
                0f to Color(0xFFE9F1FF).copy(alpha = 0.55f), 0.45f to Color.Transparent,
                startY = c.y - r, endY = c.y + r,
            ),
            Offset(c.x - r, c.y - r), Size(2 * r, 2 * r),
        )
    }
    drawCircle(Color.White.copy(alpha = 0.35f), r * 0.32f, Offset(c.x - r * 0.38f, c.y - r * 0.42f))
    drawCircle(Color.White, r * 0.14f, Offset(c.x - r * 0.38f, c.y - r * 0.42f))
    drawCircle(Color.White.copy(alpha = 0.25f), r * 0.08f, Offset(c.x + r * 0.45f, c.y + r * 0.35f))
    drawCircle(Color.Black.copy(alpha = 0.4f), r - r * 0.045f, c, style = Stroke(width = r * 0.09f))
}

/** Checkered disc like a racing flag. */
private fun DrawScope.drawChecker(c: Offset, sr: Float, u: Float) {
    val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - sr, c.y - sr, c.x + sr, c.y + sr)) }
    clipPath(disc) {
        val n = 6
        val cellSize = 2 * sr / n
        for (iy in 0 until n) for (ix in 0 until n) {
            val col = if ((ix + iy) % 2 == 0) Color(0xFFF2EEE6) else Color(0xFF111111)
            drawRect(col, Offset(c.x - sr + ix * cellSize, c.y - sr + iy * cellSize), Size(cellSize + 0.5f, cellSize + 0.5f))
        }
    }
    drawCircle(Color.Black.copy(alpha = 0.5f), sr, c, style = Stroke(width = u * 0.05f))
}

private fun DrawScope.drawHole(c: Offset, u: Float) {
    drawCircle(Color(0xFF0E0804), u * 0.32f, c)
    drawCircle(Color.Black.copy(alpha = 0.5f), u * 0.32f, c, style = Stroke(width = u * 0.06f))
}

/** Where the board sits on the canvas: a thin wooden frame around the recessed maze, with room for the labels. */
private class BoardLayout(val cols: Int, val rows: Int, width: Float, height: Float, minRail: Float = 0f) {
    constructor(maze: Maze, width: Float, height: Float) : this(maze.cols, maze.rows, width, height, counterRail(width, height))
    val u: Float; val bw: Float; val bh: Float; val bx: Float; val by: Float; val ox: Float; val oy: Float
    /** Limited by the width (a tall screen): the side rails are kept thin and the rails above and below have room. */
    val widthLimited: Boolean
    init {
        val frameTop = 1.15f; val frameBottom = 1.15f
        // One board for every level: the outer frame fills the whole space the screen gives it, so
        // it is the same on every level and screen and wastes nothing. Each level's maze is carved
        // centred inside it, and whatever room is left over widens the frame's rails.
        bw = width; bh = height
        bx = 0f; by = 0f
        // On a tall screen the maze is limited by the width, so the side rails are kept thin there
        // to give the cells all the width they can have; on wider screens the height is the limit
        // and the rails keep their normal width
        widthLimited = bw / (cols + 2 * 0.45f) < bh / (rows + frameTop + frameBottom)
        val frameX = if (widthLimited) 0.15f else 0.45f
        var cell = min(bw / (cols + 2 * frameX), bh / (rows + frameTop + frameBottom))
        // The rails above and below always have room for the counters, even round a large maze
        if (frameTop * cell < minRail) cell = min(bw / (cols + 2 * frameX), (bh - 2 * minRail) / rows)
        u = cell
        ox = bx + (bw - cols * u) / 2f // maze origin (cell 0,0)
        oy = by + (bh - rows * u) / 2f // the maze centred, the rails above and below alike
    }
    val fieldTop: Float get() = oy
    val fieldBottom: Float get() = oy + rows * u
    /** The height of the counters' digits: one size on a screen for all the boards, 0 to 100. */
    val reelDigit: Float get() = counterRail(bw, bh) * 0.54f

    companion object {
        private val digits = HashMap<Pair<Int, Int>, Float>()

        /**
         * The counters' digit height for a screen, the same on every board. Where every board is
         * limited by its width (a tall phone) the rails have room to spare, and the counters take
         * what the tightest board's rails allow. Elsewhere (a tablet) the rails are made a little
         * taller where needed, but never so much that a maze becomes more than 6% smaller. In both,
         * the four-digit time reel never takes more than half the board's width.
         */
        fun counterDigit(w: Float, h: Float): Float = digits.getOrPut((w.toInt() to h.toInt()).also { if (digits.size > 8 && it !in digits) digits.clear() }) {
            val plain = (0..Levels.COUNT).map { Levels.size(it) }.distinct().map { (c, r) -> BoardLayout(c, r, w, h) }
            fun rail(l: BoardLayout) = min(l.fieldTop - l.by, l.by + l.bh - l.fieldBottom)
            val room = if (plain.all { it.widthLimited }) plain.minOf { rail(it) }
                       else plain.minOf { maxOf(rail(it), (h - 0.94f * it.u * it.rows) / 2f) }
            min(room * 0.54f, w * 0.5f / 3.42f) // the time reel is 3.42 digit heights wide: four drums, two gaps, the bezel
        }

        /** The rail height the counters need on this screen: their window with a little wood around it. */
        private var railW = -1f; private var railH = -1f; private var rail = 0f
        fun counterRail(w: Float, h: Float): Float { // asked every frame: the last screen's answer is kept at hand
            if (w != railW || h != railH) { rail = counterDigit(w, h) / 0.54f; railW = w; railH = h }
            return rail
        }
    }
}

/** The light over the board: a new place for every level, so the walls' shading and shadows differ. */
/** A coloured lamp hovering over the board: position in board units (0..1 of its width and height). */
private class ColourLamp(val x: Float, val y: Float, val color: Color)

/**
 * The lighting of a board, live: a sun that circles the board and casts every shadow, and three
 * coloured lamps, red, green and blue, each drifting on its own slow round. Every one of the 101
 * boards has its own starting places, speeds and directions, so no two are lit alike.
 */
private class Lighting(level: Int, u: Float, time: Float) {
    /** The board's world: its lamp, sun and shade colours. */
    val env: Environment = Environment.of(level)
    /** Unit vector along the board pointing toward the sun. */
    val dir: Offset
    /** Where a raised edge drops its shadow: away from the sun, shorter when it is high. */
    val shadow: Offset
    /** Direction to the sun for the ball's mirror (x, y, z up). */
    val lamp: FloatArray
    /** How high the sun stands, 0 low and raking, 1 overhead. */
    val high: Float
    val lamps: List<ColourLamp>

    /**
     * The shadow a point at [px],[py] (canvas px) of height [h] (px) throws from lamp [lp] hovering
     * over a board of [bx],[by],[bw],[bh]: offset in px, strength 0..1 by the lamp's reach, and the
     * tint, which is the dark of the colour that lamp no longer delivers there.
     */
    fun lampShadow(lp: ColourLamp, px: Float, py: Float, h: Float, bx: Float, by: Float, bw: Float, bh: Float): Triple<Offset, Float, Color> {
        val lx = bx + lp.x * bw; val ly = by + lp.y * bh
        val span = kotlin.math.max(bw, bh)
        val hover = span * 0.55f // the lamps hang about half a board above it
        val dx = px - lx; val dy = py - ly
        val d = kotlin.math.sqrt(dx * dx + dy * dy)
        val reach = (1f - d / (span * 0.5f)).coerceIn(0f, 1f) // the lamp's pool of light
        val len = h * d / hover // a point light: shadows lengthen away from the lamp
        val off = if (d < 1f) Offset(0f, 0f) else Offset(dx / d * len, dy / d * len)
        val tint = Color((1f - lp.color.red) * 0.35f, (1f - lp.color.green) * 0.35f, (1f - lp.color.blue) * 0.35f)
        return Triple(off, reach, tint)
    }
    init {
        // The sun: a golden-angle start per level, circling in one or two minutes, clockwise on
        // even levels and the other way on odd ones, rising and sinking as it goes
        // Everything moves faster as the levels go up: level 100 runs at three times level 1's pace
        val pace = 1f + 2f * (level.coerceIn(0, Levels.COUNT) / Levels.COUNT.toFloat())
        val turn = (0.045f + 0.06f * ((level * 0.381966f) % 1f)) * (if (level % 2 == 0) 1f else -1f) * pace
        val a = level * 2.39996f + 0.8f + time * turn
        val baseHigh = 0.25f + 0.5f * ((level * 0.618034f + 0.3f) % 1f)
        high = (baseHigh + 0.22f * sin(time * (0.07f + 0.03f * (level % 3)) * pace + level)).coerceIn(0.08f, 0.95f)
        dir = Offset(kotlin.math.cos(a), sin(a))
        shadow = Offset(-dir.x, -dir.y) * (u * (0.06f + 0.11f * (1f - high)))
        val flat = 0.45f + 0.45f * (1f - high)
        lamp = floatArrayOf(dir.x * flat, dir.y * flat, 1f)
        // Three coloured lamps on their own rounds, in the colours of the board's environment
        val colours = env.lamps.map { Color(it) }
        lamps = (0 until 3).map { i ->
            val phase = level * 1.7f + i * 2.0944f + ((level * 0.29f + i * 0.37f) % 1f) * 3f
            val speed = (0.08f + 0.05f * ((level * 0.173f + i * 0.41f) % 1f)) * (if ((level + i) % 2 == 0) 1f else -1f) * pace
            val ang = phase + time * speed
            val r = 0.30f + 0.22f * ((level * 0.53f + i * 0.61f) % 1f)
            ColourLamp(0.5f + r * kotlin.math.cos(ang), 0.5f + r * sin(ang), colours[i])
        }
    }
}

/** Shaders for the board's materials (Android 13 and up): photoreal wood, and the chrome mirror,
 *  which needs the still parts of the board rendered once per level at half resolution. */
private class Materials {
    private var key: List<Int>? = null
    private var shader: RuntimeShader? = null
    val wood: RuntimeShader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        RuntimeShader(WOOD_AGSL).also {
            val tex = BitmapShader(noiseTexture(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
            tex.filterMode = BitmapShader.FILTER_MODE_LINEAR
            it.setInputShader("noise", tex)
        }
    } else null

    /** A 256x256 tile of noise, the same on every device: red smooth and slow (for bending the
     *  rings), green finer (for the streaks), blue pure grain (for the pores). Seamless on repeat. */
    private fun noiseTexture(): android.graphics.Bitmap {
        val n = 256
        val rnd = kotlin.random.Random(7)
        fun lattice(size: Int): Array<FloatArray> = Array(size) { FloatArray(size) { rnd.nextFloat() } }
        fun value(l: Array<FloatArray>, x: Float, y: Float): Float {
            val size = l.size
            val x0 = kotlin.math.floor(x).toInt(); val y0 = kotlin.math.floor(y).toInt()
            val fx = x - x0; val fy = y - y0
            val sx = fx * fx * (3 - 2 * fx); val sy = fy * fy * (3 - 2 * fy)
            fun at(i: Int, j: Int) = l[((i % size) + size) % size][((j % size) + size) % size]
            val top = at(x0, y0) + (at(x0 + 1, y0) - at(x0, y0)) * sx
            val bottom = at(x0, y0 + 1) + (at(x0 + 1, y0 + 1) - at(x0, y0 + 1)) * sx
            return top + (bottom - top) * sy
        }
        val r1 = lattice(8); val r2 = lattice(16); val r3 = lattice(32)
        val g1 = lattice(32); val g2 = lattice(64)
        val pixels = IntArray(n * n)
        for (y in 0 until n) for (x in 0 until n) {
            val fx = x / n.toFloat(); val fy = y / n.toFloat()
            val r = (value(r1, fx * 8, fy * 8) * 0.55f + value(r2, fx * 16, fy * 16) * 0.3f + value(r3, fx * 32, fy * 32) * 0.15f)
            val g = (value(g1, fx * 32, fy * 32) * 0.65f + value(g2, fx * 64, fy * 64) * 0.35f)
            val b = rnd.nextFloat()
            pixels[y * n + x] = (0xFF shl 24) or ((r * 255).toInt() shl 16) or ((g * 255).toInt() shl 8) or (b * 255).toInt()
        }
        return android.graphics.Bitmap.createBitmap(pixels, n, n, android.graphics.Bitmap.Config.ARGB_8888)
    }

    /** Set while drawing into a software bitmap, which cannot run shaders: wood falls back to flat colour. */
    private var software = false

    /** The level shown on the bottom reels, rolling toward the real level when it changes. */
    var shownLevel = -1f
    private var wallShapesKey: List<Int>? = null
    private var wallShapesCache: WallShapes? = null

    /** The two passes of light on the walls, run on the GPU: the shadows they throw and their lit
     *  and shaded faces. Each reads the wall silhouette directly, so the moving light costs two
     *  passes a frame. Null where shaders cannot run. */
    private val wallShadowShader: RuntimeShader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(WALL_SHADOW_AGSL) else null
    private val wallShadeShader: RuntimeShader? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) RuntimeShader(WALL_SHADE_AGSL) else null
    private var boundShapes: WallShapes? = null

    fun wallLight(sh: WallShapes): Pair<RuntimeShader, RuntimeShader>? {
        val a = wallShadowShader ?: return null; val b = wallShadeShader ?: return null
        if (software || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        if (boundShapes !== sh) {
            fun tex(img: ImageBitmap) = BitmapShader(img.asAndroidBitmap(), Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).also { it.filterMode = BitmapShader.FILTER_MODE_LINEAR }
            a.setInputShader("body", tex(sh.bodyMask)); a.setInputShader("wide", tex(sh.wideMask))
            b.setInputShader("body", tex(sh.bodyMask))
            boundShapes = sh
        }
        return a to b
    }

    /** The walls as shapes and as silhouette images, rebuilt only when the board or its size changes. */
    fun wallShapes(l: BoardLayout, walls: List<Wall>): WallShapes {
        val k = listOf(walls.size, (l.u * 100).toInt(), l.ox.toInt(), l.oy.toInt(), walls.hashCode(), l.bw.toInt(), l.bh.toInt())
        if (k != wallShapesKey) { wallShapesCache = WallShapes(l, walls); wallShapesKey = k }
        return wallShapesCache!!
    }
    private var titleStart = -1f; private var titleLevel = -1

    /** On the main screen the ball shows the way, rolling the board's route by itself: x, y, vx, vy in cells. */
    fun titleBall(game: Game): FloatArray {
        val route = game.maze.solution
        if (titleStart < 0f || titleLevel != game.level) { titleStart = game.time; titleLevel = game.level }
        if (route.size < 2) return floatArrayOf(game.ballX, game.ballY, 0f, 0f)
        val speed = 2.2f; val rest = 1.5f
        val cycle = (route.size - 1) / speed + rest
        val p = ((game.time - titleStart) % cycle) * speed
        val moving = p < route.size - 1
        val i = p.toInt().coerceIn(0, route.size - 2); val f = if (moving) p - i else 1f
        val a = route[i]; val b = route[i + 1]
        return floatArrayOf(a.x + 0.5f + (b.x - a.x) * f, a.y + 0.5f + (b.y - a.y) * f, if (moving) (b.x - a.x) * speed else 0f, if (moving) (b.y - a.y) * speed else 0f)
    }
    var lastTime = 0f

    // ---- live effects: particles, shake, flash, rolling, dust; all driven from game.time
    var fxTime = -1f
    val particles = ArrayList<Particle>()
    val lastOpen = HashMap<Trap, Boolean>()
    var rollAngle = 0f
    var rollDirX = 0f; var rollDirY = 1f
    var shake = 0f; var shakeX = 0f; var shakeY = 0f
    var flash = 0f
    var lastSink = 0f; var lastPhase: Phase? = null; var lastTimedOut = false
    var levelStart = -10f
    var lastLevel = -1
    /** Test board: drag distance on the bottom reel, which rolls the free-play board. */
    var slideStart = -10f
    var slideY = 0f
    /** Dust motes drifting over the board: fixed base spots in board units, each with its own wander. */
    val motes: FloatArray = FloatArray(36 * 3).also { a -> val r = kotlin.random.Random(11); for (i in 0 until 36) { a[i * 3] = r.nextFloat(); a[i * 3 + 1] = r.nextFloat(); a[i * 3 + 2] = r.nextFloat() * 6.28f } }

    fun burst(x: Float, y: Float, n: Int, speed: Float, life: Float, size: Float, color: Color, gravity: Float = 0f, drag: Float = 2f, spread: Float = 6.2832f, dirX: Float = 0f, dirY: Float = 0f, seed: Int = 0) {
        val r = kotlin.random.Random(seed + (fxTime * 1000).toInt())
        for (i in 0 until n) {
            val a = kotlin.math.atan2(dirY, dirX) + (r.nextFloat() - 0.5f) * spread
            val sp = speed * (0.4f + 0.6f * r.nextFloat())
            particles.add(Particle(x, y, kotlin.math.cos(a) * sp, sin(a) * sp, life * (0.6f + 0.4f * r.nextFloat()), size * (0.6f + 0.4f * r.nextFloat()), color, gravity, drag))
        }
    }

    /** Draws through a hardware RenderNode into an off-screen buffer and reads it back as a bitmap. */
    private fun renderOnGpu(w: Int, h: Int, draw: (android.graphics.Canvas) -> Unit): android.graphics.Bitmap? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return null
        return try {
            val reader = android.media.ImageReader.newInstance(
                w, h, android.graphics.PixelFormat.RGBA_8888, 1,
                android.hardware.HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or android.hardware.HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
            )
            val renderer = android.graphics.HardwareRenderer()
            renderer.setOpaque(false)
            val node = android.graphics.RenderNode("layer")
            node.setPosition(0, 0, w, h)
            val canvas = node.beginRecording(w, h)
            draw(canvas)
            node.endRecording()
            renderer.setSurface(reader.surface)
            renderer.setContentRoot(node)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()
            val image = reader.acquireNextImage()
            val buffer = image?.hardwareBuffer
            // The bitmap keeps its own reference to the buffer, so the image and reader can go
            val hardware = if (buffer != null) android.graphics.Bitmap.wrapHardwareBuffer(buffer, null) else null
            buffer?.close(); image?.close()
            renderer.destroy(); reader.close()
            hardware
        } catch (e: Exception) {
            null
        }
    }

    /** A brush of wood: grain running along [angle], rings every few cells, a different tree per [seed]. */
    fun woodBrush(origin: Offset, cell: Float, angle: Float, seed: Float, dark: Color, light: Color): Brush? {
        val w = wood ?: return null
        if (software || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        w.setFloatUniform("origin", origin.x, origin.y)
        w.setFloatUniform("cell", cell)
        w.setFloatUniform("angle", angle)
        w.setFloatUniform("off", (seed * 37f) % 256f, (seed * 91f) % 256f) // a different tree per piece, kept small
        w.setFloatUniform("dark", dark.red, dark.green, dark.blue)
        w.setFloatUniform("light", light.red, light.green, light.blue)
        return ShaderBrush(w)
    }

    /** The still wood of a board, drawn once on the GPU: the frame and floor in one layer, the wall strips in another. */
    private var layersKey: List<Int>? = null
    private var layers: GpuLayers? = null

    fun layersFor(game: Game, scope: DrawScope, width: Float, height: Float): GpuLayers? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val k = listOf(game.level, width.toInt(), height.toInt())
        if (k != layersKey) {
            val w = width.toInt().coerceAtLeast(1); val h = height.toInt().coerceAtLeast(1)
            val l = BoardLayout(game.maze, width, height)
            val floor = renderOnGpu(w, h) { canvas ->
                CanvasDrawScope().draw(scope, scope.layoutDirection, BitmapCanvas(canvas), Size(width, height)) { drawWoodBase(l, game.maze, this@Materials, Environment.of(game.level)) }
            }
            val walls = renderOnGpu(w, h) { canvas ->
                CanvasDrawScope().draw(scope, scope.layoutDirection, BitmapCanvas(canvas), Size(width, height)) { drawWallBodies(l, game.maze, this@Materials, Environment.of(game.level)) }
            }
            layers = if (floor != null && walls != null) GpuLayers(floor.asImageBitmap(), walls.asImageBitmap()) else null
            layersKey = k
            key = null // the mirror is rebuilt from the new layers
        }
        return layers
    }

    fun shaderFor(game: Game, tm: TextMeasurer, scope: DrawScope, width: Float, height: Float): RuntimeShader? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        val k = listOf(game.level, width.toInt(), height.toInt())
        if (k != key) {
            val s = 0.5f
            val w = (width * s).toInt().coerceAtLeast(1); val h = (height * s).toInt().coerceAtLeast(1)
            val ready = layers
            // Preferably on the GPU from the board's own wood layers; otherwise on a software canvas, where the wood is a flat colour
            val android = (if (ready != null) renderOnGpu(w, h) { canvas ->
                CanvasDrawScope().draw(scope, scope.layoutDirection, BitmapCanvas(canvas), Size(width * s, height * s)) {
                    scale(s, s, Offset.Zero) {
                        drawImage(ready.floor)
                        val l = BoardLayout(game.maze, width, height)
                        drawChecker(Offset(l.ox + (game.maze.start.x + 0.5f) * l.u, l.oy + (game.maze.start.y + 0.5f) * l.u), l.u * 0.33f, l.u)
                        drawChecker(Offset(l.ox + (game.maze.finish.x + 0.5f) * l.u, l.oy + (game.maze.finish.y + 0.5f) * l.u), l.u * 0.33f, l.u)
                        drawImage(ready.walls)
                    }
                }
            } else null) ?: run {
                val lightLevel = game.level
                val bmp = ImageBitmap(w, h)
                software = true
                CanvasDrawScope().draw(scope, scope.layoutDirection, BitmapCanvas(bmp), Size(width * s, height * s)) {
                    scale(s, s, Offset.Zero) { drawReflectionScene(game.maze, lightLevel, game.time, tm, this@Materials, width, height) }
                }
                software = false
                bmp.asAndroidBitmap()
            }
            val sh = RuntimeShader(CHROME_AGSL)
            sh.setInputShader("board", BitmapShader(android, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP))
            sh.setFloatUniform("scale", s)
            shader = sh
            key = k
        }
        return shader
    }
}

/**
 * The ball as a convex mirror seen from above. For each pixel the surface normal gives the
 * reflected ray: rays heading down hit the board (the ball rests on it, so a surface point sits
 * radius*(1+z) above the floor) and pick up its colour there, so the surroundings wrap around the
 * rim, compressed, with the far room at the horizon ring; rays heading up see the dim ceiling.
 * Two lamps add the hot spots, and the floor right under the ball sits in its own shadow.
 */
private const val CHROME_AGSL = """
uniform shader board;
uniform float scale;
uniform float2 center;
uniform float radius;
uniform float3 eye;
uniform float3 lamp;
// The three coloured lamps as seen from the ball: direction (x, y, z) and colour
uniform float4 lampDirs[3];
uniform float4 lampCols[3];
// The traps nearest the ball, which the still board image does not hold: lid discs (x, y, radius)
// with their colour, and the glow around an active one (x, y, radius, strength) with its colour
uniform float4 discs[8];
uniform float4 discCol[8];
uniform float4 halos[8];
uniform float4 haloCol[8];
uniform int count;
// Live laser beams (x0, y0, x1, y1), whose red light also shows in the mirror
uniform float4 beams[2];
uniform float beamWidth;
uniform int beamCount;

// The room above the board: the viewer's dark shape straight overhead, lighter walls and
// ceiling toward the horizon
float3 room(float3 d) {
    float up = clamp(d.z, 0.0, 1.0);
    float3 c = mix(float3(0.70, 0.72, 0.76), float3(0.46, 0.48, 0.52), smoothstep(0.0, 0.6, up));
    c = mix(c, float3(0.10, 0.10, 0.12), smoothstep(0.80, 0.97, up));
    // A ceiling tube light reflected as a soft bright bar, up and to the left of the viewer
    float2 o = d.xy - float2(-0.22, -0.42);
    c += float3(0.95, 0.96, 1.0) * 0.55 * exp(-(o.x * o.x / 0.030 + o.y * o.y / 0.004));
    return c;
}

half4 main(float2 p) {
    float2 q = (p - center) / radius;
    float r2 = dot(q, q);
    if (r2 >= 1.0) return half4(0.0);
    float z = sqrt(1.0 - r2);
    float3 n = float3(q, z);
    float3 v = normalize(float3(eye.xy - (center + q * radius), eye.z));
    float3 d = 2.0 * dot(n, v) * n - v;
    float3 col;
    if (d.z < -0.005) {
        float t = radius * (1.0 + z) / -d.z;
        float2 hit = (center + q * radius + d.xy * t) * scale;
        col = 0.25 * (float3(board.eval(hit + float2(0.5, 0.5)).rgb) + float3(board.eval(hit + float2(-0.5, 0.5)).rgb)
                    + float3(board.eval(hit + float2(0.5, -0.5)).rgb) + float3(board.eval(hit + float2(-0.5, -0.5)).rgb));
        float2 hs = hit / scale;
        for (int i = 0; i < 8; i++) {
            if (i >= count) break;
            float dd = length(hs - discs[i].xy);
            if (halos[i].z > 0.0) col = mix(col, haloCol[i].rgb, halos[i].w * (1.0 - smoothstep(0.0, halos[i].z, dd)));
            col = mix(col, discCol[i].rgb, 1.0 - smoothstep(discs[i].z - 1.0, discs[i].z + 1.0, dd));
        }
        for (int b = 0; b < 2; b++) {
            if (b >= beamCount) break;
            float2 a = beams[b].xy; float2 e = beams[b].zw;
            float2 ab = e - a;
            float tt = clamp(dot(hs - a, ab) / max(dot(ab, ab), 1e-3), 0.0, 1.0);
            float dist = length(hs - (a + ab * tt));
            col = mix(col, float3(1.0, 0.25, 0.3), 0.85 * (1.0 - smoothstep(0.0, beamWidth, dist)));
        }
        float far = length(hs - center) / radius;
        col *= mix(1.0, 0.65, clamp((far - 3.0) / 14.0, 0.0, 1.0));
        col *= 1.0 - 0.45 * smoothstep(0.88, 1.0, sqrt(r2));
    } else {
        col = room(d);
    }
    col *= float3(0.92, 0.93, 0.95);
    float3 l = normalize(lamp);
    float s1 = max(dot(d, l), 0.0);
    col += pow(s1, 220.0) * 1.0 + pow(s1, 14.0) * 0.40;
    float3 l2 = normalize(float3(0.55, 0.35, 1.0));
    float s2 = max(dot(d, l2), 0.0);
    col += pow(s2, 90.0) * 0.35;
    for (int c = 0; c < 3; c++) {
        float sc = max(dot(d, normalize(lampDirs[c].xyz)), 0.0);
        col += lampCols[c].rgb * (pow(sc, 70.0) * 0.9 + pow(sc, 6.0) * 0.10);
    }
    return half4(clamp(col, 0.0, 1.0), 1.0);
}
"""

/**
 * Wood, grown on the fly: growth rings as bands across the grain, bent by slow noise so they
 * wander like real rings, with fine streaks and pores along the grain. Colour runs from the dark
 * latewood to the light earlywood.
 */
private const val WOOD_AGSL = """
uniform shader noise;
uniform float2 origin;
uniform float cell;
uniform float angle;
uniform float2 off;
uniform float3 dark;
uniform float3 light;

half4 main(float2 p) {
    // Everything here stays small: cells from the piece's own centre, and a repeating noise
    // texture instead of arithmetic hashes, so GPUs that run shaders in 16-bit draw it cleanly
    float2 q = (p - origin) / cell;
    float c = cos(angle); float s = sin(angle);
    q = float2(c * q.x + s * q.y, -s * q.x + c * q.y);
    float warp = noise.eval(q * float2(9.0, 24.0) + off).r * 2.4;
    float rings = q.y * 1.7 + warp;
    float ring = pow(abs(sin(rings * 3.14159)), 0.45);
    float streak = noise.eval(float2(q.x * 10.0, q.y * 110.0) + off * 1.7).g;
    float pores = noise.eval(float2(q.x * 30.0, q.y * 140.0) + off * 0.6).b;
    float v = ring * 0.55 + streak * 0.30 + pores * 0.15;
    float3 col = mix(dark, light, clamp(v, 0.0, 1.0));
    return half4(col, 1.0);
}
"""

/** The still parts of the board, without traps, shadow or ball: what the chrome ball mirrors. */
private fun DrawScope.drawReflectionScene(maze: Maze, level: Int, time: Float, tm: TextMeasurer, m: Materials, width: Float, height: Float) {
    val l = BoardLayout(maze, width, height)
    val light = Lighting(level, l.u, time)
    drawFrameAndFloor(l, maze, m, light)
    drawChecker(Offset(l.ox + (maze.start.x + 0.5f) * l.u, l.oy + (maze.start.y + 0.5f) * l.u), l.u * 0.33f, l.u)
    drawChecker(Offset(l.ox + (maze.finish.x + 0.5f) * l.u, l.oy + (maze.finish.y + 0.5f) * l.u), l.u * 0.33f, l.u)
    drawWalls(l, maze, m, light)
}


/**
 * Walls: strips of lighter wood glued on the floor. Each one drops a soft shadow away from the
 * light, its face is lit on the side toward the light and shaded on the far side, with a bright
 * edge where the light catches the top corner.
 */
private fun DrawScope.drawWalls(l: BoardLayout, maze: Maze, m: Materials, light: Lighting) {
    val walls = maze.merged
    drawWallShadows(l, walls, m, light)
    drawWallBodies(l, maze, m, light.env)
    drawWallShading(l, walls, m, light)
}

/** Drawn exactly as thick as the physics' wall (its dark edge ends at WALL_R), so the ball meets the wall's edge on contact. */
private fun wallWidth(u: Float) = u * Game.WALL_R * 2f / 1.18f

/**
 * The walls of a board as single shapes: every strip is a round-ended body and all of them are one
 * path, so wherever strips meet there is no cap, no overlap and no seam. The shapes are also kept as
 * silhouette images, made once per board, so the moving light can be drawn every frame from shifted
 * and scaled copies of them: cheap for the graphics chip, and still without a joint anywhere.
 */
private class WallShapes(val l: BoardLayout, walls: List<Wall>) {
    val wallW = wallWidth(l.u)
    val edge: Path = bodies(l, walls, wallW * 1.18f)
    val body: Path = bodies(l, walls, wallW * 1.05f)
    /** The wall tops, white where wood is. */
    val bodyMask: ImageBitmap = mask(body)
    /** A touch wider, for the soft outer shadows. */
    val wideMask: ImageBitmap = mask(bodies(l, walls, wallW * 1.25f))

    private fun mask(p: Path): ImageBitmap {
        val img = ImageBitmap(l.bw.toInt().coerceAtLeast(1), l.bh.toInt().coerceAtLeast(1))
        androidx.compose.ui.graphics.Canvas(img).drawPath(p, Paint().apply { color = Color.White; isAntiAlias = true })
        return img
    }

    companion object {
        /** All strips as one path of rounded rectangles of [width]. */
        fun bodies(l: BoardLayout, walls: List<Wall>, width: Float): Path {
            val p = Path(); val r = width / 2f
            for (w in walls) {
                val x0 = l.ox + min(w.x0, w.x1) * l.u; val x1 = l.ox + kotlin.math.max(w.x0, w.x1) * l.u
                val y0 = l.oy + min(w.y0, w.y1) * l.u; val y1 = l.oy + kotlin.math.max(w.y0, w.y1) * l.u
                p.addRoundRect(RoundRect(x0 - r, y0 - r, x1 + r, y1 + r, CornerRadius(r)))
            }
            return p
        }
    }
}

/** The silhouette tinted [color]. */
private fun DrawScope.silhouette(mask: ImageBitmap, color: Color, at: Offset = Offset.Zero) =
    drawImage(mask, topLeft = at, colorFilter = ColorFilter.tint(color))

/** Where each coloured lamp hangs over the board, in board pixels. */
private fun lampSpots(l: BoardLayout, light: Lighting) = light.lamps.map { Offset(l.bx + it.x * l.bw, l.by + it.y * l.bh) }

/**
 * The shadows the walls throw on the floor: the sun's in three softening steps, and each coloured
 * lamp's as a point light, every point moved away from the lamp in proportion to its distance (the
 * silhouette scaled up about the lamp), fading with the lamp's reach. All from the one silhouette,
 * so where strips meet a shadow is never darker.
 */
private fun DrawScope.drawWallShadows(l: BoardLayout, walls: List<Wall>, m: Materials, light: Lighting) {
    val sh = m.wallShapes(l, walls)
    val shaders = m.wallLight(sh)
    if (shaders == null || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) { // no shaders here: the sun's shadow alone, from the silhouette
        for (k in 0 until 3) {
            val reach = 0.55f + 0.225f * k
            silhouette(if (k == 0) sh.bodyMask else sh.wideMask, Color.Black.copy(alpha = floatArrayOf(0.22f, 0.14f, 0.09f)[k]), Offset(light.shadow.x * reach, light.shadow.y * reach))
        }
        return
    }
    val sd = shaders.first
    val span = kotlin.math.max(l.bw, l.bh)
    val spots = lampSpots(l, light)
    sd.setFloatUniform("sun", light.shadow.x, light.shadow.y)
    sd.setFloatUniform("grow", 1f + (l.u * 0.2f) / (span * 0.55f)) // wall height over the lamps' hover, as lampShadow has it
    sd.setFloatUniform("reach", span * 0.5f)
    for (i in 0 until 3) {
        val tint = light.lampShadow(light.lamps[i], spots[i].x, spots[i].y, 0f, l.bx, l.by, l.bw, l.bh).third
        sd.setFloatUniform("lamp$i", spots[i].x, spots[i].y)
        sd.setFloatUniform("tint$i", tint.red, tint.green, tint.blue)
    }
    drawRect(ShaderBrush(sd), Offset(l.bx, l.by), Size(l.bw, l.bh))
}

/** The still wood of the walls: one dark edge and one sheet of grain over the whole lattice, no joints. */
private fun DrawScope.drawWallBodies(l: BoardLayout, maze: Maze, m: Materials, env: Environment) {
    val walls = maze.merged
    val sh = m.wallShapes(l, walls)
    drawPath(sh.edge, woodTone(env, WoodDark))
    val centre = Offset(l.bx + l.bw / 2f, l.by + l.bh / 2f)
    val grain = m.woodBrush(centre, l.u, 0.22f, 3f, woodTone(env, Color(0xFF6E4624)), woodTone(env, Color(0xFFC79560)))
    if (grain != null) drawPath(sh.body, grain) else drawPath(sh.body, woodTone(env, WallTop))
}

/**
 * What the lights do to the walls every frame: the face toward the sun is lit, the face away from
 * it shaded, the top corner catches the sun, and each coloured lamp lights the side facing it. Each
 * is the wall silhouette less a shifted copy of itself, worked out per pixel on the GPU, so it
 * follows every corner and junction without a seam.
 */
private fun DrawScope.drawWallShading(l: BoardLayout, walls: List<Wall>, m: Materials, light: Lighting) {
    val sh = m.wallShapes(l, walls)
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return // the lit faces need shaders
    val sd = m.wallLight(sh)?.second ?: return
    val span = kotlin.math.max(l.bw, l.bh)
    val spots = lampSpots(l, light)
    // The rims catch the world's sun, in the world's rim colours
    val sun = lerp(Color(light.env.rimLow), Color(light.env.rimHigh), light.high)
    sd.setFloatUniform("lit", light.dir.x * sh.wallW, light.dir.y * sh.wallW)
    sd.setFloatUniform("sunColour", sun.red, sun.green, sun.blue)
    sd.setFloatUniform("away", 1f + (sh.wallW * 0.28f) / (span * 0.4f))
    sd.setFloatUniform("reach", span * 0.75f)
    for (i in 0 until 3) {
        val c = light.lamps[i].color
        sd.setFloatUniform("lamp$i", spots[i].x, spots[i].y)
        sd.setFloatUniform("colour$i", c.red, c.green, c.blue)
    }
    drawRect(ShaderBrush(sd), Offset(l.bx, l.by), Size(l.bw, l.bh))
}

/** The walls' shadows, per pixel: three steps of the sun's, then each lamp's, laid over one another. */
private const val WALL_SHADOW_AGSL = """
uniform shader body;
uniform shader wide;
uniform float2 sun;
uniform float grow;
uniform float reach;
uniform float2 lamp0; uniform float2 lamp1; uniform float2 lamp2;
uniform float3 tint0; uniform float3 tint1; uniform float3 tint2;

half4 over(half4 dst, half3 rgb, float a) { return half4(rgb * a, a) + dst * (1.0 - a); }

half4 lampShadow(half4 dst, float2 p, float2 at, float3 tint) {
    float fade = clamp(1.0 - length(p - at) / reach, 0.0, 1.0);
    if (fade <= 0.0) return dst;
    float a = wide.eval((p - at) / grow + at).a * 0.28 * fade;
    return over(dst, half3(tint), a);
}

half4 main(float2 p) {
    half4 c = half4(0.0);
    c = over(c, half3(0.0), body.eval(p - sun * 0.55).a * 0.22);
    c = over(c, half3(0.0), wide.eval(p - sun * 0.775).a * 0.14);
    c = over(c, half3(0.0), wide.eval(p - sun * 1.0).a * 0.09);
    c = lampShadow(c, p, lamp0, tint0);
    c = lampShadow(c, p, lamp1, tint1);
    c = lampShadow(c, p, lamp2, tint2);
    return c;
}
"""

/** The light on the walls, per pixel: bands on the sun's side, shade on the far side, the lamps' rims. */
private const val WALL_SHADE_AGSL = """
uniform shader body;
uniform float2 lit;
uniform float3 sunColour;
uniform float away;
uniform float reach;
uniform float2 lamp0; uniform float2 lamp1; uniform float2 lamp2;
uniform float3 colour0; uniform float3 colour1; uniform float3 colour2;

half4 over(half4 dst, half3 rgb, float a) { return half4(rgb * a, a) + dst * (1.0 - a); }

half4 rim(half4 dst, float2 p, float m, float2 at, float3 colour) {
    float fade = clamp(1.0 - length(p - at) / reach, 0.0, 1.0);
    if (fade <= 0.0) return dst;
    float a = m * (1.0 - body.eval((p - at) / away + at).a) * 0.6 * fade;
    return over(dst, half3(colour), a);
}

half4 main(float2 p) {
    float m = body.eval(p).a;
    if (m <= 0.0) return half4(0.0);
    half4 c = half4(0.0);
    c = over(c, half3(1.0), m * (1.0 - body.eval(p + lit * 0.30).a) * 0.18);
    c = over(c, half3(sunColour), m * (1.0 - body.eval(p + lit * 0.12).a) * 0.50);
    c = over(c, half3(0.0), m * (1.0 - body.eval(p - lit * 0.34).a) * 0.30);
    c = rim(c, p, m, lamp0, colour0);
    c = rim(c, p, m, lamp1, colour1);
    c = rim(c, p, m, lamp2, colour2);
    return c;
}
"""

/** Where a reel drum stands: [digit] 0..9 plus the fraction rolled toward the next digit. */
private fun reelPosition(x: Float, period: Float, digits: Int): Float {
    if (period <= REEL_ROLL) return ((x % digits) + digits) % digits // the fastest drum simply spins; at rest it shows its digit squarely
    val whole = kotlin.math.floor(x)
    val left = (1f - (x - whole)) * period // seconds until this drum steps
    val roll = if (left < REEL_ROLL) 1f - left / REEL_ROLL else 0f
    return ((whole + roll) % digits + digits) % digits
}
/** Seconds a drum takes to roll from one digit to the next. */
private const val REEL_ROLL = 0.14f

/**
 * Electromechanical pinball score reels: a dark metal window with cream drums that carry black
 * digits and roll over at the carry, like the counters in the back glass of an old machine.
 * [positions] gives each drum's digit with its roll fraction; [groups] marks gaps between drums.
 */
private fun DrawScope.drawReelWindow(tm: TextMeasurer, centre: Offset, digitH: Float, positions: FloatArray, digitsPer: IntArray, gapsAfter: Set<Int>) {
    val digitW = digitH * 0.66f
    val gap = digitW * 0.35f
    val pad = digitH * 0.16f
    val total = positions.size * digitW + gapsAfter.size * gap
    val left = centre.x - total / 2f
    val top = centre.y - digitH / 2f
    // Bezel: dark steel with a worn highlight along the top edge
    drawRoundRect(Color(0xFF2A2D31), Offset(left - pad, top - pad), Size(total + 2 * pad, digitH + 2 * pad), CornerRadius(pad * 0.8f))
    drawRoundRect(Color(0xFF6E7378), Offset(left - pad, top - pad), Size(total + 2 * pad, digitH + 2 * pad), CornerRadius(pad * 0.8f), style = Stroke(width = pad * 0.22f))
    drawLine(Color.White.copy(alpha = 0.25f), Offset(left - pad * 0.5f, top - pad * 0.55f), Offset(left + total + pad * 0.5f, top - pad * 0.55f), pad * 0.18f, StrokeCap.Round)
    val style = TextStyle(color = Color(0xFF141414), fontSize = (digitH * 0.74f).toSp(), fontWeight = FontWeight.Black, fontFamily = FontFamily.SansSerif)
    var x = left
    for (i in positions.indices) {
        val p = positions[i]
        val d0 = kotlin.math.floor(p).toInt()
        val f = p - d0
        clipRect(x, top, x + digitW, top + digitH) {
            // The drum: cream, darkening toward the top and bottom where it curves away
            drawRect(Color(0xFFF1E9D2), Offset(x, top), Size(digitW, digitH))
            for (k in -1..1) {
                val d = ((d0 + k) % digitsPer[i] + digitsPer[i]) % digitsPer[i]
                val t = tm.measure(d.toString(), style)
                val cy = top + digitH / 2f + (k - f) * digitH
                drawText(t, topLeft = Offset(x + digitW / 2f - t.size.width / 2f, cy - t.size.height / 2f))
            }
            drawRect(Brush.verticalGradient(0f to Color.Black.copy(alpha = 0.45f), 0.22f to Color.Transparent, 0.78f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f), startY = top, endY = top + digitH), Offset(x, top), Size(digitW, digitH))
        }
        drawRect(Color(0xFF2A2D31), Offset(x + digitW - digitW * 0.04f, top), Size(digitW * 0.08f, digitH)) // divider between drums
        x += digitW
        if (i in gapsAfter) {
            // A raised dot marks the group boundary (minutes, seconds, tenths)
            drawCircle(Color(0xFFD8DCE0), digitW * 0.07f, Offset(x + gap / 2f, top + digitH * 0.8f))
            x += gap
        }
    }
}

/** The two reel windows on the board: the running time on the top rail, the level on the bottom rail. */
private fun DrawScope.drawReels(tm: TextMeasurer, l: BoardLayout, elapsed: Float, shownLevel: Float, spin: Float = 0f) {
    val u = l.u
    val digitH = l.reelDigit
    // Time as M SS T: minutes, tens and units of seconds, tenths; each drum rolls just before it steps
    val t = elapsed.coerceAtLeast(0f)
    // On a new board the drums whirr back to zero
    val time = floatArrayOf(
        (reelPosition(t / 60f, 60f, 10) + spin * 1.3f) % 10f,
        (reelPosition((t % 60f) / 10f, 10f, 6) + spin * 0.9f) % 6f,
        (reelPosition(t % 60f, 1f, 10) + spin * 1.7f) % 10f,
        (reelPosition(t * 10f, 0.1f, 10) + spin * 2.4f) % 10f,
    )
    val topCentre = Offset(l.bx + l.bw / 2f, l.oy - (l.oy - l.by) / 2f)
    drawReelWindow(tm, topCentre, digitH, time, intArrayOf(10, 6, 10, 10), setOf(0, 2))
    // Level as three drums; a change rolls the units drum, which carries into the tens and hundreds
    val lv = shownLevel.coerceAtLeast(0f)
    val units = lv % 10f
    val tens = kotlin.math.floor(lv / 10f) % 10f + (units - 9f).coerceIn(0f, 1f)
    val hundreds = kotlin.math.floor(lv / 100f) % 10f + ((lv % 100f) - 99f).coerceIn(0f, 1f)
    val level = floatArrayOf(hundreds % 10f, tens % 10f, units)
    val bottomCentre = Offset(l.bx + l.bw / 2f, l.fieldBottom + (l.by + l.bh - l.fieldBottom) / 2f)
    drawReelWindow(tm, bottomCentre, digitH, level, intArrayOf(10, 10, 10), emptySet())
}

/**
 * The board: a frame of wood around a recessed floor of darker wood, lit from the level's light
 * so the far side falls off into shade and the recess's near edge throws a shadow onto the floor.
 */
private fun DrawScope.drawFrameAndFloor(l: BoardLayout, maze: Maze, m: Materials, light: Lighting) {
    drawWoodBase(l, maze, m, light.env)
    drawLightOverlays(l, light)
}

/** The still wood: the frame plank, the recessed floor sheet and the polished route. */
private fun DrawScope.drawWoodBase(l: BoardLayout, maze: Maze, m: Materials, env: Environment) {
    val u = l.u; val bx = l.bx; val by = l.by; val bw = l.bw; val bh = l.bh; val ox = l.ox; val oy = l.oy; val cols = l.cols
    val fieldTop = l.fieldTop; val fieldBottom = l.fieldBottom
    // Frame: one wide plank, grain running the long way
    val frame = m.woodBrush(Offset(bx + bw / 2f, by + bh / 2f), u, 1.5708f, 1f, woodTone(env, Color(0xFF55361C)), woodTone(env, Color(0xFFA87A4C)))
    if (frame != null) drawRoundRect(frame, Offset(bx, by), Size(bw, bh), CornerRadius(u * 0.5f))
    else drawRoundRect(woodTone(env, WoodMid), Offset(bx, by), Size(bw, bh), CornerRadius(u * 0.5f))
    drawRoundRect(woodTone(env, WoodDark), Offset(bx, by), Size(bw, bh), CornerRadius(u * 0.5f), style = Stroke(width = u * 0.08f))

    // Recessed playing field: a darker sheet, grain across
    val fieldPos = Offset(ox - u * 0.12f, fieldTop)
    val fieldSize = Size(cols * u + u * 0.24f, fieldBottom - fieldTop + u * 0.12f)
    val floor = m.woodBrush(Offset(ox + cols * u / 2f, (fieldTop + fieldBottom) / 2f), u, 0.08f, 2f, woodTone(env, Color(0xFF2A170A)), woodTone(env, Color(0xFF5C3B1E)))
    if (floor != null) drawRoundRect(floor, fieldPos, fieldSize, CornerRadius(u * 0.2f))
    else drawRoundRect(woodTone(env, Color(0xFF3B2614)), fieldPos, fieldSize, CornerRadius(u * 0.2f))
    // Wood wear: the route the balls take is polished a shade lighter
    val path = Path()
    maze.solution.forEachIndexed { i, cell -> val px = ox + (cell.x + 0.5f) * u; val py = oy + (cell.y + 0.5f) * u; if (i == 0) path.moveTo(px, py) else path.lineTo(px, py) }
    drawPath(path, Color(0xFFE0B080).copy(alpha = 0.07f), style = Stroke(width = u * 0.55f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
    drawPath(path, Color(0xFFE0B080).copy(alpha = 0.05f), style = Stroke(width = u * 0.3f, cap = StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
}

/** What the lights do to the board every frame: the sun's fall-off, the coloured pools, the recess's edge shadow. */
private fun DrawScope.drawLightOverlays(l: BoardLayout, light: Lighting) {
    val u = l.u; val bx = l.bx; val by = l.by; val bw = l.bw; val bh = l.bh; val ox = l.ox; val cols = l.cols
    val fieldTop = l.fieldTop; val fieldBottom = l.fieldBottom
    val fieldPos = Offset(ox - u * 0.12f, fieldTop)
    val fieldSize = Size(cols * u + u * 0.24f, fieldBottom - fieldTop + u * 0.12f)
    // Light falling across the whole board: brightest toward the sun, darker and cooler on the far
    // side; the sun's colour warms toward amber as it sinks and whitens as it climbs
    val centre = Offset(bx + bw / 2f, by + bh / 2f)
    val lampAt = centre + light.dir * (kotlin.math.max(bw, bh) * 0.7f)
    val sun = lerp(Color(light.env.sunLow), Color(light.env.sunHigh), light.high)
    drawRoundRect(
        Brush.radialGradient(0f to sun.copy(alpha = 0.12f + 0.06f * (1f - light.high)), 0.35f to Color.Transparent, 1f to Color(light.env.shade).copy(alpha = 0.36f), center = lampAt, radius = kotlin.math.max(bw, bh) * 1.7f),
        Offset(bx, by), Size(bw, bh), CornerRadius(u * 0.5f),
    )
    // The three coloured lamps: pools of coloured light added over the wood, drifting as they move
    for (lp in light.lamps) {
        val at = Offset(bx + lp.x * bw, by + lp.y * bh)
        drawRoundRect(
            Brush.radialGradient(0f to lp.color.copy(alpha = 0.30f), 0.5f to lp.color.copy(alpha = 0.10f), 1f to Color.Transparent, center = at, radius = kotlin.math.max(bw, bh) * 0.5f),
            Offset(bx, by), Size(bw, bh), CornerRadius(u * 0.5f), blendMode = androidx.compose.ui.graphics.BlendMode.Plus,
        )
    }

    // The recess's wall on the lit side shades the floor along that edge
    clipRect(fieldPos.x, fieldPos.y, fieldPos.x + fieldSize.width, fieldPos.y + fieldSize.height) {
        for (k in 0 until 2) {
            val off = light.shadow * (1.6f + 0.6f * k)
            drawRoundRect(Color.Black.copy(alpha = 0.22f - 0.06f * k), fieldPos + off, fieldSize, CornerRadius(u * 0.2f), style = Stroke(width = u * (0.14f + 0.14f * k)))
        }
    }
}

private fun DrawScope.drawBoard(game: Game, tm: TextMeasurer, m: Materials, width: Float, height: Float) {
    val maze = game.maze
    val l = BoardLayout(maze, width, height)
    val u = l.u; val ox = l.ox; val oy = l.oy; val cols = l.cols; val rows = l.rows
    // The still wood comes from GPU layers drawn once per board; only the light is painted every frame.
    // They are made first, so the ball's mirror is built from this board's wood, not the last one's.
    val layers = m.layersFor(game, this, width, height)
    val chrome = m.shaderFor(game, tm, this, width, height)
    val light = Lighting(game.level, u, game.time)
    // On the main screen the ball rolls the route by itself to show the way; in play it is the real ball
    val tb = if (game.phase == Phase.TITLE) m.titleBall(game) else null
    val ballX = tb?.get(0) ?: game.ballX; val ballY = tb?.get(1) ?: game.ballY
    val vx = tb?.get(2) ?: game.vx; val vy = tb?.get(3) ?: game.vy
    if (layers != null) drawImage(layers.floor) else drawWoodBase(l, maze, m, light.env)
    drawLightOverlays(l, light)
    updateEffects(game, m, u)
    // Score reels on the rails: the time on the top rail, the level on the bottom one
    run {
        val dt = (game.time - m.lastTime).coerceIn(0f, 0.1f); m.lastTime = game.time
        val target = game.level.toFloat()
        if (m.shownLevel < 0f || kotlin.math.abs(m.shownLevel - target) > 20f) m.shownLevel = target
        else m.shownLevel += (target - m.shownLevel).coerceIn(-4f * dt, 4f * dt)
        val since = game.time - m.levelStart
        val spin = if (game.elapsed == 0f && since in 0f..0.8f) (1f - since / 0.8f).let { it * it * 7f } else 0f
        drawReels(tm, l, game.elapsed, m.shownLevel, spin)
    }

    // Start marker (decorative) and finish hole
    val startC = Offset(ox + (maze.start.x + 0.5f) * u, oy + (maze.start.y + 0.5f) * u)
    drawChecker(startC, u * 0.33f, u)
    val hole = Offset(ox + (maze.finish.x + 0.5f) * u, oy + (maze.finish.y + 0.5f) * u)
    if (game.finishOpen || game.phase == Phase.FINISHED) {
        drawCircle(Color(0xFF0E0804), u * 0.33f, hole)
        drawCircle(Color.Black.copy(alpha = 0.5f), u * 0.33f, hole, style = Stroke(width = u * 0.06f))
    } else {
        drawChecker(hole, u * 0.33f, u)
    }

    // Traps: open holes look just like the finish hole; shut ones wear a wooden lid.
    // Magnet traps always show a lid with a horseshoe magnet on it; when on, it glows and pulls.
    for (t in game.traps) {
        val c = Offset(ox + t.cx * u, oy + t.cy * u)
        drawCircle(Color(0xFF0E0804), u * 0.33f, c)
        drawCircle(Color.Black.copy(alpha = 0.5f), u * 0.33f, c, style = Stroke(width = u * 0.06f))
        if (t.kind == TrapKind.MAGNET) {
            if (t.open) {
                val pulse = 0.5f + 0.5f * sin(game.time * 8f)
                drawCircle(Color(0xFFFF5252).copy(alpha = 0.12f + 0.12f * pulse), u * 1.0f, c)
                drawCircle(Color(0xFFFF8A80).copy(alpha = 0.6f), u * (0.45f + 0.25f * pulse), c, style = Stroke(width = u * 0.04f))
                // Field lines: dashed rings turning in alternate directions, drawn in toward the core
                for (ring in 0 until 3) {
                    val rr = u * (0.55f + 0.2f * ring) - u * 0.2f * ((game.time * 0.6f + ring * 0.33f) % 1f)
                    val spin = game.time * (if (ring % 2 == 0) 90f else -70f) + ring * 40f
                    for (seg in 0 until 4) drawArc(Color(0xFFFF8A80).copy(alpha = 0.35f), startAngle = spin + seg * 90f, sweepAngle = 42f, useCenter = false, topLeft = Offset(c.x - rr, c.y - rr), size = Size(2 * rr, 2 * rr), style = Stroke(width = u * 0.025f, cap = StrokeCap.Round))
                }
            }
            drawCircle(Color(0xFF8E6240), u * 0.32f, c)
            drawCircle(Color(0xFF5A3C22), u * 0.32f, c, style = Stroke(width = u * 0.04f))
            // Horseshoe: red arc with two steel tips
            val r = u * 0.15f
            val red = if (t.open) Color(0xFFFF3B30) else Color(0xFFB3261E)
            drawArc(red, startAngle = 180f, sweepAngle = 180f, useCenter = false, topLeft = Offset(c.x - r, c.y - r * 1.1f), size = Size(2 * r, 2 * r), style = Stroke(width = u * 0.09f))
            drawLine(red, Offset(c.x - r, c.y - r * 0.1f), Offset(c.x - r, c.y + r * 0.5f), u * 0.09f)
            drawLine(red, Offset(c.x + r, c.y - r * 0.1f), Offset(c.x + r, c.y + r * 0.5f), u * 0.09f)
            drawLine(Color(0xFFD0D4D8), Offset(c.x - r, c.y + r * 0.5f), Offset(c.x - r, c.y + r * 0.8f), u * 0.09f)
            drawLine(Color(0xFFD0D4D8), Offset(c.x + r, c.y + r * 0.5f), Offset(c.x + r, c.y + r * 0.8f), u * 0.09f)
        } else if (t.kind == TrapKind.WIND) {
            // Blue hole with a tornado sign; the sign spins while it blows
            drawCircle(Color(0xFF1E5AA8), u * 0.33f, c)
            drawCircle(Color(0xFF123A70), u * 0.33f, c, style = Stroke(width = u * 0.05f))
            // Fan: seven curved blades sweeping out from a hub, inside a ring
            val w = Color(0xFFE8F1FF)
            val rim = u * 0.25f
            val hub = u * 0.06f
            drawCircle(w, rim, c, style = Stroke(width = u * 0.03f))
            val blades = 7
            for (i in 0 until blades) {
                val a0 = i * (6.2832f / blades)
                val a1 = a0 + 0.95f // the blade curves ahead of its root as it reaches the rim
                val rootX = c.x + hub * 1.3f * kotlin.math.cos(a0); val rootY = c.y + hub * 1.3f * sin(a0)
                val tipX = c.x + rim * 0.92f * kotlin.math.cos(a1); val tipY = c.y + rim * 0.92f * sin(a1)
                val cx1 = c.x + rim * 0.55f * kotlin.math.cos(a0 + 0.15f); val cy1 = c.y + rim * 0.55f * sin(a0 + 0.15f)
                val cx2 = c.x + rim * 0.6f * kotlin.math.cos(a1 + 0.35f); val cy2 = c.y + rim * 0.6f * sin(a1 + 0.35f)
                val blade = Path().apply {
                    moveTo(rootX, rootY)
                    quadraticTo(cx1, cy1, tipX, tipY)
                    quadraticTo(cx2, cy2, c.x + hub * 1.3f * kotlin.math.cos(a0 + 0.5f), c.y + hub * 1.3f * sin(a0 + 0.5f))
                    close()
                }
                drawPath(blade, w)
            }
            drawCircle(w, hub, c)
            drawCircle(Color(0xFF1E5AA8), hub * 0.45f, c)
        } else if (t.kind == TrapKind.RADIOACTIVE) {
            // Gray disc with a green radiation trefoil; the sign glows while the trap leaks
            val gray = Color(0xFF6F7478)
            if (t.open) {
                val pulse = 0.5f + 0.5f * sin(game.time * 6f)
                drawCircle(Color(0xFF7CFF3A).copy(alpha = 0.10f + 0.10f * pulse), u * 0.6f, c)
            }
            drawCircle(gray, u * 0.33f, c)
            drawCircle(Color(0xFF43484C), u * 0.33f, c, style = Stroke(width = u * 0.05f))
            val green = if (t.open) Color(0xFF8CFF3F) else Color(0xFF4CAF50)
            val ro = u * 0.25f
            drawCircle(green, ro, c, style = Stroke(width = u * 0.03f))
            for (k in 0 until 3) {
                val mid = 90f + 120f * k // one blade points down, two reach up
                drawArc(green, startAngle = mid - 30f, sweepAngle = 60f, useCenter = true, topLeft = Offset(c.x - ro * 0.9f, c.y - ro * 0.9f), size = Size(2 * ro * 0.9f, 2 * ro * 0.9f))
            }
            drawCircle(gray, ro * 0.32f, c)
            drawCircle(green, ro * 0.15f, c)
        } else if (t.kind == TrapKind.SPEED) {
            // Fluorescent yellow disc with a lightning bolt through a broken ring; glows while charged
            if (t.open) {
                val pulse = 0.5f + 0.5f * sin(game.time * 9f)
                drawCircle(Color(0xFFFFF176).copy(alpha = 0.12f + 0.12f * pulse), u * 0.6f, c)
            }
            val fluo = if (t.open) Color(0xFFF4FF4A) else Color(0xFFD8E63A)
            drawCircle(fluo, u * 0.33f, c)
            drawCircle(Color(0xFF8E9A12), u * 0.33f, c, style = Stroke(width = u * 0.05f))
            val ro = u * 0.24f
            val ink = Color(0xFF26231E)
            // Ring open where the bolt crosses it, top right and bottom left
            drawArc(ink, startAngle = -40f, sweepAngle = 150f, useCenter = false, topLeft = Offset(c.x - ro, c.y - ro), size = Size(2 * ro, 2 * ro), style = Stroke(width = u * 0.035f, cap = StrokeCap.Round))
            drawArc(ink, startAngle = 140f, sweepAngle = 150f, useCenter = false, topLeft = Offset(c.x - ro, c.y - ro), size = Size(2 * ro, 2 * ro), style = Stroke(width = u * 0.035f, cap = StrokeCap.Round))
            val bolt = Path().apply {
                moveTo(c.x + ro * 0.22f, c.y - ro * 1.02f)
                lineTo(c.x - ro * 0.62f, c.y + ro * 0.12f)
                lineTo(c.x - ro * 0.05f, c.y + ro * 0.12f)
                lineTo(c.x - ro * 0.22f, c.y + ro * 1.02f)
                lineTo(c.x + ro * 0.62f, c.y - ro * 0.12f)
                lineTo(c.x + ro * 0.05f, c.y - ro * 0.12f)
                close()
            }
            drawPath(bolt, Brush.linearGradient(listOf(Color(0xFFF7D13D), Color(0xFFF7A23D)), start = Offset(c.x - ro, c.y + ro), end = Offset(c.x + ro, c.y - ro)))
        } else if (t.kind == TrapKind.TRAPDOOR) {
            // A hinged wooden lid. It looks shut; while armed it trembles, then it drops open (black hole)
            if (!t.open) {
                val shake = if (t.armed >= 0f) sin(game.time * 60f) * u * 0.012f else 0f
                val lc = Offset(c.x + shake, c.y)
                drawCircle(Color(0xFF8E6240), u * 0.32f, lc)
                drawCircle(Color(0xFF5A3C22), u * 0.32f, lc, style = Stroke(width = u * 0.04f))
                // An eye watching the ball: it follows it and blinks now and then; wide open once armed
                val blinkPhase = (game.time * 0.37f + t.x * 0.7f + t.y * 1.3f) % 1f
                val lidClosed = if (t.armed >= 0f) 0f else (1f - kotlin.math.abs(blinkPhase - 0.5f) / 0.03f).coerceIn(0f, 1f)
                val openH = u * 0.19f * (1f - 0.95f * lidClosed)
                val eye = Path().apply {
                    moveTo(lc.x - u * 0.21f, lc.y)
                    quadraticTo(lc.x, lc.y - openH, lc.x + u * 0.21f, lc.y)
                    quadraticTo(lc.x, lc.y + openH, lc.x - u * 0.21f, lc.y)
                    close()
                }
                drawPath(eye, Color(0xFFF4EFE6))
                drawPath(eye, Color(0xFF3A2414), style = Stroke(width = u * 0.03f))
                if (lidClosed < 0.8f) {
                    val gx = ballX - t.cx; val gy = ballY - t.cy
                    val gd = kotlin.math.sqrt(gx * gx + gy * gy).coerceAtLeast(0.01f)
                    val look = (gd * 0.25f).coerceAtMost(1f) * u * 0.07f
                    val pc = Offset(lc.x + gx / gd * look, lc.y + gy / gd * look * 0.6f)
                    clipPath(eye) {
                        drawCircle(if (t.armed >= 0f) Color(0xFFB71C1C) else Color(0xFF5D4037), u * 0.085f, pc)
                        drawCircle(Color(0xFF111111), u * 0.045f, pc)
                        drawCircle(Color.White.copy(alpha = 0.85f), u * 0.018f, Offset(pc.x - u * 0.03f, pc.y - u * 0.03f))
                    }
                }
            }
        } else if (t.kind == TrapKind.CRUSHER) {
            // Dark steel plate with a hammer. The piston's shadow spreads over it as a warning, then it strikes
            drawCircle(Color(0xFF4A4F55), u * 0.33f, c)
            drawCircle(Color(0xFF24272B), u * 0.33f, c, style = Stroke(width = u * 0.05f))
            // Sledgehammer, upright and symmetric: a handle up the middle and a wide head on top
            val steel = Color(0xFFC9CED4)
            drawLine(Color(0xFF9A7B5A), Offset(c.x, c.y + u * 0.23f), Offset(c.x, c.y - u * 0.08f), u * 0.055f, StrokeCap.Round) // handle
            drawRoundRect(steel, Offset(c.x - u * 0.18f, c.y - u * 0.21f), Size(u * 0.36f, u * 0.14f), CornerRadius(u * 0.03f)) // head
            drawRoundRect(Color(0xFF7D858E), Offset(c.x - u * 0.18f, c.y - u * 0.21f), Size(u * 0.36f, u * 0.14f), CornerRadius(u * 0.03f), style = Stroke(width = u * 0.02f))
            if (t.open) {
                // Struck: the piston is down, a flash around the plate
                drawCircle(Color(0xFF2B2E33), u * 0.31f, c)
                drawCircle(Color(0xFFFFE0B2).copy(alpha = 0.35f), u * 0.45f, c, style = Stroke(width = u * 0.08f))
            } else if (t.armed > 0f) {
                // The piston descends: its shadow spreads on the plate while the head grows smaller and sharper as it nears
                val a = t.armed
                drawCircle(Color.Black.copy(alpha = 0.25f + 0.45f * a), u * 0.33f * (0.3f + 0.7f * a), c)
                val head = u * (0.70f - 0.36f * a)
                drawCircle(Color(0xFF3A3F45).copy(alpha = 0.35f + 0.6f * a), head, c)
                drawCircle(Color(0xFF6E7378).copy(alpha = 0.3f + 0.6f * a), head, c, style = Stroke(width = u * 0.03f))
                drawCircle(Color.White.copy(alpha = 0.12f + 0.25f * a), head * 0.35f, Offset(c.x - head * 0.3f, c.y - head * 0.3f))
            }
        } else if (t.kind == TrapKind.LASER) {
            // Red disc with a crosshair; the beam itself is drawn above the walls
            drawCircle(if (t.open) Color(0xFFD32F2F) else Color(0xFF9E2A2A), u * 0.33f, c)
            drawCircle(Color(0xFF5C1212), u * 0.33f, c, style = Stroke(width = u * 0.05f))
            val w = Color(0xFFFFEBEE)
            drawCircle(w, u * 0.15f, c, style = Stroke(width = u * 0.03f))
            for ((ddx, ddy) in listOf(1f to 0f, -1f to 0f, 0f to 1f, 0f to -1f)) drawLine(w, Offset(c.x + ddx * u * 0.08f, c.y + ddy * u * 0.08f), Offset(c.x + ddx * u * 0.24f, c.y + ddy * u * 0.24f), u * 0.03f, StrokeCap.Round)
            drawCircle(w, u * 0.03f, c)
        } else if (t.kind == TrapKind.ICE) {
            // A glassy patch of ice around a disc with a snowflake; cracked open it is a hole
            val pr = u * t.range
            clipRect(ox, oy, ox + cols * u, oy + rows * u) { // the ice stays inside the playing field
                drawCircle(Brush.radialGradient(listOf(Color(0xFFE1F5FE).copy(alpha = 0.55f), Color(0xFFB3E5FC).copy(alpha = 0.45f), Color(0xFF81D4FA).copy(alpha = 0.15f)), center = c, radius = pr), pr, c)
                drawCircle(Color(0xFFE1F5FE).copy(alpha = 0.5f), pr, c, style = Stroke(width = u * 0.025f))
                drawLine(Color.White.copy(alpha = 0.45f), Offset(c.x - pr * 0.6f, c.y - pr * 0.45f), Offset(c.x - pr * 0.2f, c.y - pr * 0.7f), u * 0.04f, StrokeCap.Round) // glint
            }
            if (t.open) {
                drawCircle(Color(0xFF0E0804), u * 0.33f, c)
                drawCircle(Color.Black.copy(alpha = 0.5f), u * 0.33f, c, style = Stroke(width = u * 0.06f))
                clipRect(ox, oy, ox + cols * u, oy + rows * u) {
                    for (k in 0 until 7) { // cracks running out over the ice
                        val a0 = k * 0.9f + 0.3f
                        val a1 = a0 + 0.35f
                        val p1 = Offset(c.x + kotlin.math.cos(a0) * u * 0.33f, c.y + sin(a0) * u * 0.33f)
                        val p2 = Offset(c.x + kotlin.math.cos(a1) * pr * 0.75f, c.y + sin(a1) * pr * 0.75f)
                        val p3 = Offset(c.x + kotlin.math.cos(a1 - 0.2f) * pr * 0.98f, c.y + sin(a1 - 0.2f) * pr * 0.98f)
                        drawLine(Color(0xFF37474F).copy(alpha = 0.8f), p1, p2, u * 0.03f, StrokeCap.Round)
                        drawLine(Color(0xFF37474F).copy(alpha = 0.6f), p2, p3, u * 0.02f, StrokeCap.Round)
                    }
                }
            } else {
                drawCircle(Color(0xFFBBDEFB), u * 0.33f, c)
                drawCircle(Color(0xFF64B5F6), u * 0.33f, c, style = Stroke(width = u * 0.04f))
                val ink = Color(0xFF1565C0)
                for (k in 0 until 6) { // snowflake: six spokes with little branches
                    val a = k * 1.0472f
                    val dxs = kotlin.math.cos(a); val dys = sin(a)
                    drawLine(ink, c, Offset(c.x + dxs * u * 0.22f, c.y + dys * u * 0.22f), u * 0.028f, StrokeCap.Round)
                    val bx = c.x + dxs * u * 0.14f; val by = c.y + dys * u * 0.14f
                    drawLine(ink, Offset(bx, by), Offset(bx + kotlin.math.cos(a + 0.9f) * u * 0.07f, by + sin(a + 0.9f) * u * 0.07f), u * 0.022f, StrokeCap.Round)
                    drawLine(ink, Offset(bx, by), Offset(bx + kotlin.math.cos(a - 0.9f) * u * 0.07f, by + sin(a - 0.9f) * u * 0.07f), u * 0.022f, StrokeCap.Round)
                }
            }
        } else if (t.kind == TrapKind.SPINNER) {
            // Purple disc with a swirl; the vortex current is drawn above the walls
            drawCircle(if (t.open) Color(0xFF8E24AA) else Color(0xFF6A1B9A), u * 0.33f, c)
            drawCircle(Color(0xFF3E1156), u * 0.33f, c, style = Stroke(width = u * 0.05f))
            val w = Color(0xFFF3E5F5)
            val spin = if (t.open) game.time * 5f else 0f
            for (k in 0 until 3) {
                val a0 = k * 2.0944f + spin
                val sw = Path()
                for (i in 0..12) {
                    val f = i / 12f
                    val ang = a0 + f * 2.2f
                    val rr = u * (0.05f + 0.19f * f)
                    val px = c.x + kotlin.math.cos(ang) * rr; val py = c.y + sin(ang) * rr
                    if (i == 0) sw.moveTo(px, py) else sw.lineTo(px, py)
                }
                drawPath(sw, w, style = Stroke(width = u * 0.04f, cap = StrokeCap.Round))
            }
            drawCircle(w, u * 0.04f, c)
        } else if (!t.open) {
            drawCircle(Color(0xFF8E6240), u * 0.32f, c)
            drawCircle(Color(0xFF5A3C22), u * 0.32f, c, style = Stroke(width = u * 0.04f))
            if (t.kind == TrapKind.BLINK) {
                // About to open: the lid glows amber and pulses faster as the moment nears
                val ph = game.levelTime * 6.2832f / t.period + t.phase
                val thr = 0.15f - 0.35f * (game.level - 1).coerceAtLeast(0) / (Levels.COUNT - 1)
                val sv = sin(ph)
                if (kotlin.math.cos(ph) > 0f && sv > thr - 0.4f && sv < thr) {
                    val k = (sv - (thr - 0.4f)) / 0.4f
                    val pulse = 0.5f + 0.5f * sin(game.time * (6f + 18f * k))
                    drawCircle(Color(0xFFFFB300).copy(alpha = (0.15f + 0.45f * k) * (0.6f + 0.4f * pulse)), u * 0.36f, c, style = Stroke(width = u * 0.05f))
                }
            }
        }
    }

    // Ball shadow on the floor, under the walls so they hide it like a real board
    run {
        val sr = game.radius * u * (1f - 0.75f * game.sink)
        val sc = Offset(ox + ballX * u, oy + ballY * u)
        if (game.sink < 0.3f) {
            val lean = Offset(game.ax / Game.G_CELLS * u * 0.2f + light.shadow.x * 1.3f, game.ay / Game.G_CELLS * u * 0.2f + light.shadow.y * 1.3f)
            for (lp in light.lamps) { // the lamps each cast the ball's shadow their own way, tinted by what they do not light
                val (off, reach, tint) = light.lampShadow(lp, sc.x, sc.y, sr * 2f, l.bx, l.by, l.bw, l.bh)
                if (reach > 0.03f) drawCircle(tint.copy(alpha = 0.30f * reach), sr * 1.0f, sc + off)
            }
            drawCircle(Color.Black.copy(alpha = 0.45f), sr * 1.02f, sc + lean)
            drawCircle(Color.Black.copy(alpha = 0.35f), sr * 0.55f, sc + lean * 0.4f) // where it touches the wood
        }
    }

    run {
        val walls = maze.merged
        drawWallShadows(l, walls, m, light)
        if (layers != null) drawImage(layers.walls) else drawWallBodies(l, maze, m, light.env)
        drawWallShading(l, walls, m, light)
    }

    // Wind: streaks streaming outward from an active wind trap, only where the air actually reaches
    // (walls shelter), fading toward the edge of its range
    for (t in game.traps) {
        if (t.kind != TrapKind.WIND || !t.open) continue
        val rays = 14
        val range = Game.WIND_RANGE
        for (i in 0 until rays) {
            val ang = i * (6.2832f / rays) + 0.2f
            val dx = kotlin.math.cos(ang); val dy = sin(ang)
            for (k in 0 until 3) {
                val d = ((game.levelTime * 2.6f + k * (range / 3f) + i * 0.17f) % range)
                val d1 = (d + 0.25f).coerceAtMost(range)
                if (!game.windReaches(t, t.cx + dx * d1, t.cy + dy * d1)) continue
                val a = (1f - d / range) * 0.75f
                drawLine(
                    Color(0xFFDDEBFF).copy(alpha = a),
                    Offset(ox + (t.cx + dx * d) * u, oy + (t.cy + dy * d) * u),
                    Offset(ox + (t.cx + dx * d1) * u, oy + (t.cy + dy * d1) * u),
                    u * 0.045f, StrokeCap.Round,
                )
            }
        }
    }

    // Laser: the live beam from the disc to the first wall, with a hot spot where it lands
    for (t in game.traps) {
        if (t.kind != TrapKind.LASER || !t.open) continue
        val (ddx, ddy) = game.laserDir(t)
        val a = Offset(ox + t.cx * u, oy + t.cy * u)
        val b = Offset(ox + (t.cx + ddx * t.beam) * u, oy + (t.cy + ddy * t.beam) * u)
        val flick = 0.8f + 0.2f * sin(game.time * 40f)
        drawLine(Color(0xFFFF1744).copy(alpha = 0.35f * flick), a, b, u * 0.16f, StrokeCap.Round)
        drawLine(Color(0xFFFF5252).copy(alpha = 0.9f), a, b, u * 0.05f, StrokeCap.Round)
        drawLine(Color.White.copy(alpha = 0.8f * flick), a, b, u * 0.018f, StrokeCap.Round)
        drawCircle(Color(0xFFFF8A80).copy(alpha = 0.7f * flick), u * 0.12f, b)
        drawCircle(Color.White.copy(alpha = 0.9f), u * 0.05f, b)
    }

    // Spinner: the vortex current swirling around an active spinner, kept inside the playing field
    for (t in game.traps) {
        if (t.kind != TrapKind.SPINNER || !t.open) continue
        val c = Offset(ox + t.cx * u, oy + t.cy * u)
        val reach = u * (t.range + game.radius)
        clipRect(ox, oy, ox + cols * u, oy + rows * u) {
        drawCircle(Color(0xFFCE93D8).copy(alpha = 0.10f), reach, c)
        for (k in 0 until 4) {
            val a0 = k * 1.5708f - game.time * 4f
            val sw = Path()
            for (i in 0..16) {
                val f = i / 16f
                val ang = a0 + f * 2.6f
                val rr = u * 0.36f + (reach - u * 0.36f) * f
                val px = c.x + kotlin.math.cos(ang) * rr; val py = c.y + sin(ang) * rr
                if (i == 0) sw.moveTo(px, py) else sw.lineTo(px, py)
            }
            drawPath(sw, Color(0xFFE1BEE7).copy(alpha = 0.55f), style = Stroke(width = u * 0.035f, cap = StrokeCap.Round))
        }
        }
    }

    // Radioactive: toxic clouds puffing out of a leaking trap, drifting away and thinning out
    for (t in game.traps) {
        if (t.kind != TrapKind.RADIOACTIVE || !t.open) continue
        val puffs = 10
        for (i in 0 until puffs) {
            val k = ((game.levelTime * 0.45f + i * 0.37f + t.phase) % 1f)
            val ang = i * (6.2832f / puffs) + 0.6f * sin(game.levelTime * 0.9f + i)
            val d = 0.25f + 0.75f * k
            val pr = u * (0.12f + 0.2f * k)
            val a = (1f - k) * 0.5f
            val p = Offset(ox + (t.cx + kotlin.math.cos(ang) * d) * u, oy + (t.cy + sin(ang) * d) * u)
            drawCircle(Color(0xFF6FE24A).copy(alpha = a), pr, p)
            drawCircle(Color(0xFFB9FF8A).copy(alpha = a * 0.5f), pr * 0.55f, p + Offset(-pr * 0.25f, -pr * 0.25f))
        }
    }

    // Dust motes drifting in the lamp light
    for (i in 0 until 36) {
        val bx0 = m.motes[i * 3]; val by0 = m.motes[i * 3 + 1]; val ph = m.motes[i * 3 + 2]
        val mx = l.bx + (bx0 + 0.03f * sin(game.time * 0.23f + ph)) * l.bw
        val my = l.by + (by0 + 0.02f * sin(game.time * 0.31f + ph * 1.7f) - ((game.time * 0.004f + ph * 0.1f) % 1f) * 0.0f) * l.bh
        var lit = 0f
        for (lp in light.lamps) { val dx = (lp.x - bx0) * l.bw; val dy = (lp.y - by0) * l.bh; val d = kotlin.math.sqrt(dx * dx + dy * dy) / kotlin.math.max(l.bw, l.bh); lit = kotlin.math.max(lit, 1f - d / 0.45f) }
        val a = 0.05f + 0.30f * lit.coerceIn(0f, 1f) * (0.7f + 0.3f * sin(game.time * 1.3f + ph))
        drawCircle(Color(0xFFFFF3DC).copy(alpha = a), u * (0.018f + 0.014f * ((i * 7) % 3) / 2f), Offset(mx, my))
    }
    // Sparks, dust, shards, smoke and splashes
    for (pt in m.particles) {
        val k = (pt.life / pt.maxLife).coerceIn(0f, 1f)
        drawCircle(pt.color.copy(alpha = pt.color.alpha * k), u * pt.size * (0.5f + 0.5f * k), Offset(ox + pt.x * u, oy + pt.y * u))
    }

    // Ball with a shadow that leans with the tilt, sinking into the hole at the finish
    val scale = when {
        game.timedOut -> (1f - game.sink * 1.6f).coerceAtLeast(0f) // blown to pieces
        else -> 1f - 0.75f * game.sink // sinking away into the hole
    }
    // Start of a board: the ball drops in from above onto the start disc
    val sinceStart = game.time - m.levelStart
    val drop = if (game.phase == Phase.PLAYING && game.elapsed == 0f && sinceStart in 0f..0.45f) (1f - sinceStart / 0.45f) else 0f
    val r = game.radius * u * scale * (1f + 0.5f * drop * drop)
    val c = Offset(ox + ballX * u, oy + ballY * u + game.sink * u * 0.08f - drop * drop * u * 0.9f)
    // Speed: a fading trail of ghosts behind a fast ball
    run {
        val speed = kotlin.math.sqrt(vx * vx + vy * vy)
        if (speed > 2.5f && game.sink == 0f && r > 0.5f) {
            val k = ((speed - 2.5f) / 8f).coerceIn(0f, 1f)
            val dx = -vx / speed; val dy = -vy / speed
            for (i in 1..4) {
                val f = i / 4f
                drawCircle(Color(0xFFB8C0C8).copy(alpha = 0.22f * k * (1f - f)), r * (1f - 0.18f * f), Offset(c.x + dx * r * 1.1f * f * (0.5f + k), c.y + dy * r * 1.1f * f * (0.5f + k)))
            }
        }
    }
    // Contaminated: a green smoke halo trailing behind the ball, under it, and a green tint over the chrome
    val slime = game.toxicLevel
    if (r > 0.5f && slime > 0f) {
        val speed = kotlin.math.sqrt(vx * vx + vy * vy)
        val bx0 = if (speed > 0.05f) -vx / speed else 0f
        val by0 = if (speed > 0.05f) -vy / speed else 0f
        val trail = (speed / 6f).coerceIn(0f, 1f) // the smoke streams out behind a moving ball
        for (i in 0 until 7) {
            val k = i / 6f
            val wob = sin(game.time * 7f + i * 1.9f) * 0.25f
            val px = c.x + (bx0 * k * 2.2f * trail - by0 * wob) * r
            val py = c.y + (by0 * k * 2.2f * trail + bx0 * wob) * r
            val pr = r * (1.25f - 0.45f * k)
            val a = (1f - k * 0.8f) * 0.28f * slime
            drawCircle(Color(0xFF6FE24A).copy(alpha = a), pr, Offset(px, py))
        }
        drawCircle(Color(0xFF8CFF3F).copy(alpha = 0.18f * slime), r * 1.6f, c)
    }
    if (chrome != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
        // The eight traps nearest the ball, with their lids and any glow they give off right now
        val near = game.traps.sortedBy { (it.cx - ballX) * (it.cx - ballX) + (it.cy - ballY) * (it.cy - ballY) }.take(8)
        val discs = FloatArray(32); val discCol = FloatArray(32); val halos = FloatArray(32); val haloCol = FloatArray(32)
        near.forEachIndexed { i, t ->
            val tx = ox + t.cx * u; val ty = oy + t.cy * u
            discs[i * 4] = tx; discs[i * 4 + 1] = ty; discs[i * 4 + 2] = u * 0.33f
            val lid = when (t.kind) {
                TrapKind.MAGNET -> Color(0xFF8E6240)
                TrapKind.WIND -> Color(0xFF1E5AA8)
                TrapKind.RADIOACTIVE -> Color(0xFF6F7478)
                TrapKind.SPEED -> Color(0xFFE6F040)
                TrapKind.CRUSHER -> Color(0xFF4A4F55)
                TrapKind.LASER -> Color(0xFFB71C1C)
                TrapKind.ICE -> if (t.open) Color(0xFF0E0804) else Color(0xFFBBDEFB)
                TrapKind.SPINNER -> Color(0xFF7B1FA2)
                else -> if (t.open) Color(0xFF0E0804) else Color(0xFF8E6240)
            }
            discCol[i * 4] = lid.red; discCol[i * 4 + 1] = lid.green; discCol[i * 4 + 2] = lid.blue; discCol[i * 4 + 3] = 1f
            val (glow, gr, ga) = when {
                t.kind == TrapKind.MAGNET && t.open -> Triple(Color(0xFFFF5252), u * 1.0f, 0.35f + 0.25f * (0.5f + 0.5f * sin(game.time * 8f)))
                t.kind == TrapKind.RADIOACTIVE && t.open -> Triple(Color(0xFF7CFF3A), u * 1.0f, 0.35f + 0.2f * (0.5f + 0.5f * sin(game.time * 6f)))
                t.kind == TrapKind.SPEED && t.open -> Triple(Color(0xFFFFF176), u * 0.6f, 0.3f + 0.2f * (0.5f + 0.5f * sin(game.time * 9f)))
                t.kind == TrapKind.SPINNER && t.open -> Triple(Color(0xFFCE93D8), u * t.range, 0.25f)
                t.kind == TrapKind.ICE -> Triple(Color(0xFFB3E5FC), u * t.range, 0.35f)
                else -> Triple(Color.Transparent, 0f, 0f)
            }
            halos[i * 4] = tx; halos[i * 4 + 1] = ty; halos[i * 4 + 2] = gr; halos[i * 4 + 3] = ga
            haloCol[i * 4] = glow.red; haloCol[i * 4 + 1] = glow.green; haloCol[i * 4 + 2] = glow.blue; haloCol[i * 4 + 3] = 1f
        }
        chrome.setFloatUniform("discs", discs); chrome.setFloatUniform("discCol", discCol)
        chrome.setFloatUniform("halos", halos); chrome.setFloatUniform("haloCol", haloCol)
        chrome.setIntUniform("count", near.size)
        val lasers = game.traps.filter { it.kind == TrapKind.LASER && it.open }.sortedBy { (it.cx - ballX) * (it.cx - ballX) + (it.cy - ballY) * (it.cy - ballY) }.take(2)
        val beams = FloatArray(8)
        lasers.forEachIndexed { i, t ->
            val (ddx, ddy) = game.laserDir(t)
            beams[i * 4] = ox + t.cx * u; beams[i * 4 + 1] = oy + t.cy * u
            beams[i * 4 + 2] = ox + (t.cx + ddx * t.beam) * u; beams[i * 4 + 3] = oy + (t.cy + ddy * t.beam) * u
        }
        chrome.setFloatUniform("beams", beams); chrome.setFloatUniform("beamWidth", u * 0.1f); chrome.setIntUniform("beamCount", lasers.size)
        chrome.setFloatUniform("lamp", light.lamp[0], light.lamp[1], light.lamp[2])
        val dirs = FloatArray(12); val cols = FloatArray(12)
        val hover = kotlin.math.max(l.bw, l.bh) * 0.55f
        light.lamps.forEachIndexed { i, lp ->
            val lx = l.bx + lp.x * l.bw - c.x; val ly = l.by + lp.y * l.bh - c.y
            val len = kotlin.math.sqrt(lx * lx + ly * ly + hover * hover)
            dirs[i * 4] = lx / len; dirs[i * 4 + 1] = ly / len; dirs[i * 4 + 2] = hover / len
            cols[i * 4] = lp.color.red; cols[i * 4 + 1] = lp.color.green; cols[i * 4 + 2] = lp.color.blue; cols[i * 4 + 3] = 1f
        }
        chrome.setFloatUniform("lampDirs", dirs); chrome.setFloatUniform("lampCols", cols)
    }
    // Charged: a yellow halo around the ball, flaring with the charge
    val charge = game.boostLevel
    if (r > 0.5f && charge > 0f) {
        val flick = 0.85f + 0.15f * sin(game.time * 21f)
        drawCircle(Brush.radialGradient(listOf(Color(0xFFFFF59D).copy(alpha = 0.55f * charge * flick), Color(0xFFFFEB3B).copy(alpha = 0.25f * charge), Color.Transparent), center = c, radius = r * 2.1f), r * 2.1f, c)
        drawCircle(Color(0xFFFFF176).copy(alpha = 0.6f * charge), r * 1.22f, c, style = Stroke(width = r * 0.14f))
    }
    if (game.timedOut && game.sink > 0f) {
        // The explosion: a hard white flash that blooms orange, a shock ring, smoke, and steel
        // shards thrown far out that slow down and fade
        val k = game.sink
        val br = game.radius * u
        val flash = (1f - k * 1.6f).coerceAtLeast(0f)
        drawCircle(Brush.radialGradient(listOf(Color.White.copy(alpha = flash), Color(0xFFFFC14D).copy(alpha = flash * 0.8f), Color(0xFFFF5722).copy(alpha = flash * 0.4f), Color.Transparent), center = c, radius = br * (2f + 8f * k)), br * (2f + 8f * k), c)
        drawCircle(Color(0xFFFFE0B2).copy(alpha = (1f - k) * 0.8f), br * (1f + 9f * k), c, style = Stroke(width = br * 0.35f * (1f - k) + 1f))
        for (i in 0 until 9) { // smoke
            val ang = i * 0.698f + 0.3f
            val d = br * (0.5f + 3.5f * k)
            val p = Offset(c.x + kotlin.math.cos(ang) * d, c.y + sin(ang) * d - k * br * 1.2f)
            drawCircle(Color(0xFF3A3A3A).copy(alpha = (1f - k) * 0.45f), br * (0.5f + 1.6f * k), p)
        }
        for (i in 0 until 22) { // shards
            val ang = i * (6.2832f / 22f) + 0.5f * sin(i * 3.1f)
            val reach = 1f - (1f - k) * (1f - k) // fast at first, then slowing
            val dist = br * (0.6f + 9f * reach * (0.6f + 0.4f * ((i * 7) % 5) / 4f))
            val sr = br * 0.26f * (1f - 0.6f * k) * (0.5f + 0.5f * ((i * 3) % 4) / 3f)
            val p = Offset(c.x + kotlin.math.cos(ang) * dist, c.y + sin(ang) * dist + k * k * br * 2.5f)
            drawCircle(Color(0xFFC9CED4).copy(alpha = 1f - k), sr, p)
            drawCircle(Color.White.copy(alpha = (1f - k) * 0.8f), sr * 0.4f, Offset(p.x - sr * 0.3f, p.y - sr * 0.3f))
        }
    }
    if (r > 0.5f) drawChromeBall(c, r, chrome)
    if (r > 0.5f && game.sink == 0f) {
        // Rolling texture: faint scratches that travel over the chrome as the ball turns
        val disc = Path().apply { addOval(androidx.compose.ui.geometry.Rect(c.x - r, c.y - r, c.x + r, c.y + r)) }
        clipPath(disc) {
            val nx = -m.rollDirY; val ny = m.rollDirX
            for (k in 0 until 7) {
                val phi = m.rollAngle + k * 0.8976f
                val depth = kotlin.math.cos(phi)
                if (depth <= 0.05f) continue
                val along = sin(phi) * r * 0.82f
                val side = sin(k * 2.3f) * r * 0.45f
                val px = c.x + m.rollDirX * along + nx * side; val py = c.y + m.rollDirY * along + ny * side
                drawLine(Color(0xFF2B3036).copy(alpha = 0.22f * depth), Offset(px - nx * r * 0.08f, py - ny * r * 0.08f), Offset(px + nx * r * 0.08f, py + ny * r * 0.08f), r * 0.025f, StrokeCap.Round)
            }
        }
    }
    if (r > 0.5f && slime > 0f) {
        drawCircle(Color(0xFF3FD63F).copy(alpha = 0.45f * slime), r, c)
        drawCircle(Color(0xFF1E7A1E).copy(alpha = 0.3f * slime), r, c, style = Stroke(width = r * 0.09f))
    }
    if (game.sink > 0f) {
        val lost = game.lostTrap
        if (lost?.kind != TrapKind.LASER && !game.timedOut) {
            // The hole the ball drops into: a trap's own, the plate under the hammer, or the finish
            val h = if (lost != null) Offset(ox + lost.cx * u, oy + lost.cy * u) else hole
            drawCircle(Color(0xFF0E0804).copy(alpha = game.sink * 0.9f), u * 0.33f, h)
            if (lost?.kind == TrapKind.ICE) {
                // The whole patch gives way: cracks race out to its edge and the ice whitens as it shatters
                val pr = u * lost.range; val grow = (game.sink * 1.6f).coerceAtMost(1f)
                clipRect(ox, oy, ox + cols * u, oy + rows * u) {
                    drawCircle(Color.White.copy(alpha = 0.35f * grow), pr, h)
                    for (k in 0 until 14) {
                        val a0 = k * 0.449f + 0.15f; val a1 = a0 + 0.25f * (if (k % 2 == 0) 1f else -1f)
                        val r1 = u * 0.33f + (pr * 0.6f - u * 0.33f) * grow; val r2 = pr * (0.6f + 0.4f * grow)
                        val p1 = Offset(h.x + kotlin.math.cos(a0) * u * 0.33f, h.y + sin(a0) * u * 0.33f)
                        val p2 = Offset(h.x + kotlin.math.cos(a0 + 0.1f) * r1, h.y + sin(a0 + 0.1f) * r1)
                        val p3 = Offset(h.x + kotlin.math.cos(a1) * r2, h.y + sin(a1) * r2)
                        drawLine(Color(0xFF263238).copy(alpha = 0.85f), p1, p2, u * 0.035f, StrokeCap.Round)
                        if (grow > 0.5f) drawLine(Color(0xFF263238).copy(alpha = 0.7f * (grow - 0.5f) * 2f), p2, p3, u * 0.025f, StrokeCap.Round)
                    }
                }
            }
            if (lost?.kind == TrapKind.TRAPDOOR) {
                // Once the ball is down the lid swings shut over it, hinged at the top
                val shut = ((game.sink - 0.55f) / 0.3f).coerceIn(0f, 1f)
                if (shut > 0f) {
                    val lr = u * 0.32f; val sweep = shut * shut * (3 - 2 * shut)
                    drawOval(Color(0xFF8E6240), Offset(h.x - lr, h.y - lr), Size(lr * 2, lr * 2 * sweep))
                    drawOval(Color(0xFF5A3C22), Offset(h.x - lr, h.y - lr), Size(lr * 2, lr * 2 * sweep), style = Stroke(width = u * 0.04f))
                }
            }
        }
    }
    // A white flash on the hardest hits
    if (m.flash > 0.01f) drawRect(Color.White.copy(alpha = (m.flash * 0.85f).coerceAtMost(0.85f)), Offset(0f, 0f), Size(width, height))
}

/** Advances the live effects by the time since the last frame and fires the emitters off the game's state changes. */
private fun updateEffects(game: Game, m: Materials, u: Float) {
    if (m.fxTime < 0f) m.fxTime = game.time
    val dt = (game.time - m.fxTime).coerceIn(0f, 0.1f)
    m.fxTime = game.time
    val rnd = kotlin.random.Random((game.time * 997f).toInt())

    // The ball's rolling: angle advances with distance, the texture travels along the direction of motion
    val speed = kotlin.math.sqrt(game.vx * game.vx + game.vy * game.vy)
    if (speed > 0.3f) { m.rollDirX = game.vx / speed; m.rollDirY = game.vy / speed }
    m.rollAngle = (m.rollAngle + speed * dt / game.radius) % 6.2832f

    // A new board begins
    if (game.phase == Phase.PLAYING && m.lastPhase != Phase.PLAYING && game.elapsed == 0f) m.levelStart = game.time
    // A different board arrives: it slides up into place
    if (m.lastLevel >= 0 && game.level != m.lastLevel) m.slideStart = game.time
    m.lastLevel = game.level
    val sk = ((game.time - m.slideStart) / 0.5f).coerceIn(0f, 1f)
    m.slideY = (1f - sk) * (1f - sk) * u * 14f

    // Trap emitters
    for (t in game.traps) {
        val was = m.lastOpen[t] ?: false
        val rising = t.open && !was
        when (t.kind) {
            TrapKind.LASER -> if (t.open && rnd.nextFloat() < 0.8f) {
                val (dx, dy) = game.laserDir(t)
                val ex = t.cx + dx * t.beam; val ey = t.cy + dy * t.beam
                m.burst(ex, ey, 2, 3.5f, 0.35f, 0.045f, Color(0xFFFFB74D), gravity = 6f, drag = 3f, spread = 2.4f, dirX = -dx, dirY = -dy, seed = 1)
                if (rnd.nextFloat() < 0.3f) m.burst(ex, ey, 1, 2f, 0.25f, 0.035f, Color.White, gravity = 4f, drag = 3f, spread = 3f, dirX = -dx, dirY = -dy, seed = 2)
            }
            TrapKind.CRUSHER -> if (rising) {
                m.burst(t.cx, t.cy, 14, 2.2f, 0.7f, 0.09f, Color(0xFF8D6E63).copy(alpha = 0.7f), gravity = 0f, drag = 4f, seed = 3)
                val d = kotlin.math.hypot(game.ballX - t.cx, game.ballY - t.cy)
                m.shake = kotlin.math.max(m.shake, 0.6f / (1f + d * 0.4f))
            }
            TrapKind.ICE -> if (rising) m.burst(t.cx, t.cy, 16, 3f, 0.7f, 0.06f, Color(0xFFE1F5FE).copy(alpha = 0.9f), gravity = 5f, drag = 2.5f, seed = 4)
            TrapKind.SPINNER -> if (t.open && rnd.nextFloat() < 0.5f) {
                val a = rnd.nextFloat() * 6.2832f
                val rr = t.range + 0.2f
                val px = t.cx + kotlin.math.cos(a) * rr; val py = t.cy + sin(a) * rr
                m.particles.add(Particle(px, py, -sin(a) * 1.6f - kotlin.math.cos(a) * 0.9f, kotlin.math.cos(a) * 1.6f - sin(a) * 0.9f, 1.1f, 0.12f, Color(0xFFD1C4E9).copy(alpha = 0.35f), 0f, 0.6f))
            }
            else -> {}
        }
        m.lastOpen[t] = t.open
    }

    // Deaths and the finish
    if (game.sink > 0f && m.lastSink == 0f) {
        val lost = game.lostTrap
        when {
            game.timedOut -> { m.shake = kotlin.math.max(m.shake, 1.3f); m.flash = kotlin.math.max(m.flash, 0.7f) }
            lost?.kind == TrapKind.LASER -> { m.flash = kotlin.math.max(m.flash, 1f); m.burst(game.ballX, game.ballY, 20, 4f, 0.5f, 0.05f, Color(0xFFFF8A65), gravity = 3f, drag = 2f, seed = 5) }
            lost?.kind == TrapKind.CRUSHER -> { m.shake = kotlin.math.max(m.shake, 1f); m.burst(game.ballX, game.ballY, 10, 2.5f, 0.6f, 0.08f, Color(0xFF8D6E63).copy(alpha = 0.7f), drag = 4f, seed = 6) }
            lost != null -> m.burst(lost.cx, lost.cy, 10, 2.5f, 0.5f, 0.05f, Color(0xFF3B2614), gravity = 12f, drag = 1.5f, spread = 2.6f, dirX = 0f, dirY = -1f, seed = 7) // dust kicked up from the hole
            else -> m.burst(game.maze.finish.x + 0.5f, game.maze.finish.y + 0.5f, 26, 3.5f, 0.9f, 0.05f, Color(0xFFFFD54F), gravity = 7f, drag = 1.5f, spread = 3.2f, dirX = 0f, dirY = -1f, seed = 8) // sparkle at the finish
        }
    }
    // The trapdoor's lid hitting home: a thud through the board and dust from the seam
    val lid = game.lostTrap
    if (lid?.kind == TrapKind.TRAPDOOR && game.sink >= 0.85f && m.lastSink < 0.85f) { m.shake = kotlin.math.max(m.shake, 0.6f); m.burst(lid.cx, lid.cy, 8, 2f, 0.4f, 0.05f, Color(0xFF3B2614), gravity = 10f, drag = 2f, spread = 3f, dirX = 0f, dirY = -1f, seed = 9) }
    m.lastSink = game.sink
    m.lastPhase = game.phase

    // Advance particles
    val it = m.particles.iterator()
    while (it.hasNext()) {
        val pt = it.next()
        pt.life -= dt
        if (pt.life <= 0f) { it.remove(); continue }
        pt.vy += pt.gravity * dt
        val keep = kotlin.math.exp(-pt.drag * dt)
        pt.vx *= keep; pt.vy *= keep
        pt.x += pt.vx * dt; pt.y += pt.vy * dt
    }
    if (m.particles.size > 400) m.particles.subList(0, m.particles.size - 400).clear()

    // Shake and flash settle
    m.shake *= kotlin.math.exp(-6f * dt)
    if (m.shake < 0.01f) m.shake = 0f
    m.shakeX = sin(game.time * 63f) * m.shake * u * 0.08f
    m.shakeY = kotlin.math.cos(game.time * 71f) * m.shake * u * 0.08f
    m.flash *= kotlin.math.exp(-5f * dt)
    if (m.flash < 0.01f) m.flash = 0f
}
