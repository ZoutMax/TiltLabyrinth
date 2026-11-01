package com.megapro.labyrinth.audio

import com.megapro.labyrinth.game.Environment
import com.megapro.labyrinth.game.Instrument
import com.megapro.labyrinth.game.Levels
import com.megapro.labyrinth.game.Theme
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin
import kotlin.random.Random

/**
 * Renders a board's music and ambience from its [Environment], on the device, so every one of the
 * 101 boards has its own tune and soundscape without shipping a hundred recordings. Both come back
 * as seamless 16-bit stereo loops; the tail of the last sounds is folded onto the start of the loop
 * so the join is silent-free.
 *
 * Nothing is the same twice. The tune of a board takes its key, tempo, scale and voice from the
 * environment and draws from the board's own seed the chord progression, the rhythm, the arpeggio
 * style, the octaves, a second voice and the percussion. The ambience is a mix of two to four
 * elements chosen from the theme's palette, each with its own parameters for that board: how dark
 * the wind, how often the drips, which birds, how low the rumble. Pure Kotlin, unit-tested on the JVM.
 */
object Synth {
    private const val TWO_PI = (2 * PI).toFloat()
    /** Seconds in a soundscape loop: long enough that its pattern does not come round too often. */
    private const val AMB = 20f

    // ================================================================== music

    /**
     * Eight bars of the board's tune at its tempo, in stereo (left and right interleaved).
     *
     * The harmony follows a real progression that comes home: the second half of the eight bars
     * ends on the chord before the home chord, then the home chord. The melody grows from a
     * one-bar motif: stated, answered with its second half turned over, contrasted, then closed
     * on the home note. Under it an arpeggio, a soft pad holding the chord, a bass on the root and
     * fifth, and on later boards a light percussion. All of it sits in a small reverberant room.
     */
    /** The last tune's melody as (start in eighths, scale degree) pairs, for tests: what a board's tune is, note by note. */
    internal var lastScore: List<Pair<Int, Int>> = emptyList(); private set

    fun music(env: Environment, sr: Int): ShortArray {
        if (env.level == 1) return opening(sr) // the first board, and the main screen: written out by hand
        if (env.level == Levels.COUNT) return victory(sr) // the last board: a victory theme, also written out
        val beat = 60f / env.bpm; val bars = 8
        val total = (bars * 4 * beat * sr).toInt()
        val size = total + (4f * sr).toInt()
        val rng = Random(env.seed)
        val scale = env.scale; val n = scale.size
        fun pitch(degree: Int): Float {
            val oct = Math.floorDiv(degree, n); val d = Math.floorMod(degree, n)
            return env.root * scale[d] * 2f.pow(oct)
        }
        // --- harmony: two four-bar progressions, the second ending dominant then home
        val progs = if (n >= 7) listOf(intArrayOf(0, 5, 3, 4), intArrayOf(0, 3, 4, 0), intArrayOf(5, 3, 0, 4), intArrayOf(0, 4, 5, 3), intArrayOf(1, 4, 0, 5), intArrayOf(0, 2, 3, 4), intArrayOf(3, 4, 2, 5))
                    else listOf(intArrayOf(0, 3, 1, 4), intArrayOf(0, 2, 3, 0), intArrayOf(4, 2, 3, 0), intArrayOf(0, 1, 3, 4), intArrayOf(3, 1, 4, 0))
        val p1 = progs[rng.nextInt(progs.size)]; val p2 = progs[rng.nextInt(progs.size)]
        val roots = IntArray(bars) { if (it < 4) p1[it] else p2[it - 4] }
        roots[6] = if (n >= 7) 4 else 3; roots[7] = 0
        fun chord(b: Int) = intArrayOf(roots[b], roots[b] + 2, roots[b] + 4)

        // --- voices, each on its own track, mixed to stereo at the end
        val melody = FloatArray(size); val arp = FloatArray(size); val padL = FloatArray(size); val padR = FloatArray(size)
        val bass = FloatArray(size); val perc = FloatArray(size); val answer = FloatArray(size)

        // Melody from a motif: rhythm and steps drawn for the board
        fun rhythm(): BooleanArray = BooleanArray(8) { it == 0 || (it == 4 && rng.nextFloat() < 0.8f) || rng.nextFloat() < 0.35f + 0.3f * env.density }
        val rhyA = rhythm(); val rhyB = rhythm()
        val stepPool = intArrayOf(-2, -1, -1, 1, 1, 2, 0)
        val stepsA = IntArray(8) { stepPool[rng.nextInt(stepPool.size)] }; val stepsB = IntArray(8) { stepPool[rng.nextInt(stepPool.size)] }
        val sustained = env.instrument == Instrument.FLUTE || env.instrument == Instrument.ORGAN
        var cur = n + roots[0]
        val score = ArrayList<Pair<Int, Int>>()
        fun nearestChordTone(b: Int, from: Int): Int {
            var best = from; var bestD = Int.MAX_VALUE
            for (o in 0..3) for (c in chord(b)) { val d0 = c + o * n; val d = abs(d0 - from); if (d < bestD && d0 in n..3 * n) { bestD = d; best = d0 } }
            return best
        }
        for (b in 0 until bars) {
            val section = b / 2 // 0 statement, 1 answer, 2 contrast, 3 close
            if (b == bars - 1) { // the close: the home note, held
                val home = if (abs(cur - 2 * n) < abs(cur - n)) 2 * n else n
                score.add(b * 8 to home); note(melody, ((b * 4) * beat * sr).toInt(), pitch(home), if (sustained) beat * 3f else 2.4f, 0.55f, env.instrument, sr)
                continue
            }
            val rhy = if (section == 2) rhyB else rhyA
            val steps = if (section == 2) stepsB else stepsA
            val active = (0 until 8).filter { rhy[it] }
            for ((idx, k) in active.withIndex()) {
                var step = steps[k]
                if (section == 1 && k >= 4) step = -step // the answer turns the motif's second half over
                cur = if (k == 0 || k == 4) nearestChordTone(b, cur + step) else (cur + step).coerceIn(n, 3 * n)
                val next = if (idx + 1 < active.size) active[idx + 1] else 8
                val dur = if (sustained) (next - k) * 0.5f * beat * 0.95f else 1.1f + 0.4f * rng.nextFloat()
                val vel = (if (k == 0) 0.55f else if (k % 2 == 0) 0.47f else 0.38f) * (0.9f + 0.2f * rng.nextFloat())
                score.add(b * 8 + k to cur)
                note(melody, ((b * 4 + k * 0.5f) * beat * sr).toInt(), pitch(cur), dur, vel, env.instrument, sr)
            }
        }

        lastScore = score.map { (t, d) -> t to (d + (env.root.let { Math.round(12 * kotlin.math.ln(it / 196.0) / kotlin.math.ln(2.0)).toInt() }) * 100) } + roots.mapIndexed { b, r -> -1 - b to r }
        // Arpeggio on a softer, plucked voice, in the octave below the melody
        val arpVoice = listOf(Instrument.KALIMBA, Instrument.MARIMBA, Instrument.PLUCK, Instrument.MUSICBOX).filter { it != env.instrument }[rng.nextInt(3)]
        val style = rng.nextInt(3) // rising, falling, up and down
        val arpEvery = if (env.density < 0.3f && rng.nextBoolean()) 2 else 1 // quarters on the calmer boards, eighths otherwise
        for (b in 0 until bars) {
            val c = chord(b) + intArrayOf(roots[b] + n)
            for (k in 0 until 8 step arpEvery) {
                val i = k / arpEvery
                val idx = when (style) { 0 -> i % c.size; 1 -> (c.size - 1 - i % c.size); else -> { val q = i % (2 * c.size - 2); if (q < c.size) q else 2 * c.size - 2 - q } }
                note(arp, ((b * 4 + k * 0.5f) * beat * sr).toInt(), pitch(c[idx]), 0.9f, 0.22f * (if (k == 0) 1.15f else 1f), arpVoice, sr)
            }
        }

        // Pad: the chord held through the bar, slow to swell, slightly different on each side
        for (b in 0 until bars) for (c in chord(b)) {
            val at = ((b * 4) * beat * sr).toInt(); val dur = 4 * beat * 1.15f
            pad(padL, at, pitch(c), dur, 0.07f, 1.0f, sr); pad(padR, at, pitch(c), dur, 0.07f, 1.0035f, sr)
        }

        // Bass: root on one, fifth on three, two octaves down
        if (env.bass || env.density > 0.15f) for (b in 0 until bars) {
            note(bass, ((b * 4) * beat * sr).toInt(), pitch(roots[b] - 2 * n), beat * 1.9f, 0.42f, Instrument.PLUCK, sr)
            note(bass, ((b * 4 + 2) * beat * sr).toInt(), pitch(roots[b] + (if (n >= 7) 4 else 3) - 2 * n), beat * 1.9f, 0.32f, Instrument.PLUCK, sr)
        }

        // A second voice answers now and then, high and long, on the later boards and in some worlds
        val second = if (env.variation >= 4 || env.density > 0.35f) Instrument.values()[(env.instrument.ordinal + 3 + rng.nextInt(3)) % Instrument.values().size] else null
        if (second != null) for (b in 1 until bars - 1 step 2) {
            val c = chord(b)
            note(answer, ((b * 4 + 2.5f) * beat * sr).toInt(), pitch(c[rng.nextInt(c.size)] + 2 * n), beat * 2.5f, 0.2f, second, sr)
        }

        // Percussion on the later boards: a soft low drum, a mid one, a shaker in between
        if (env.density > 0.3f && env.theme != Theme.STARS) {
            val percMask = IntArray(8) { k -> if (k == 0) 1 else if (k == 4) 2 else if (rng.nextFloat() < 0.35f) 3 else 0 }
            for (b in 0 until bars) for (k in 0 until 8) {
                val at = ((b * 4 + k * 0.5f) * beat * sr).toInt()
                when (percMask[k]) { 1 -> thump(perc, at, 70f, 0.26f, sr); 2 -> thump(perc, at, 110f, 0.15f, sr); 3 -> shaker(perc, at, rng, 0.06f + 0.06f * env.density, sr) }
            }
        }

        // The arrangement: which voices play in which bars, drawn for the board, so every tune has
        // its own shape over the eight bars. 1 plays, 0 rests, between them a short fade.
        val all = FloatArray(bars) { 1f }
        fun bars(vararg on: Int) = FloatArray(bars) { if (it in on) 1f else 0f }
        val plan = rng.nextInt(6)
        val gMelody: FloatArray; val gArp: FloatArray; val gPad: FloatArray; val gBass: FloatArray; val gPerc: FloatArray; val gAnswer: FloatArray
        when (plan) {
            0 -> { gMelody = all; gArp = all; gPad = all; gBass = all; gPerc = all; gAnswer = all } // steady
            1 -> { gMelody = bars(2, 3, 4, 5, 6, 7); gArp = all; gPad = all; gBass = bars(2, 3, 4, 5, 6, 7); gPerc = bars(4, 5, 6, 7); gAnswer = all } // a sparse intro, then in
            2 -> { gMelody = all; gArp = bars(0, 1, 2, 3, 6, 7); gPad = all; gBass = bars(0, 1, 2, 3, 6, 7); gPerc = bars(0, 1, 2, 3, 7); gAnswer = all } // a breakdown in the middle
            3 -> { gMelody = all; gArp = bars(2, 3, 4, 5, 6, 7); gPad = all; gBass = bars(4, 5, 6, 7); gPerc = bars(6, 7); gAnswer = bars(4, 5, 6, 7) } // a slow build
            4 -> { gMelody = bars(0, 1, 4, 5, 7); gArp = all; gPad = all; gBass = all; gPerc = all; gAnswer = all } // call and response: the melody rests while the answer speaks
            else -> { gMelody = all; gArp = all; gPad = bars(0, 1, 2, 3); gBass = bars(4, 5, 6, 7); gPerc = bars(4, 5, 6, 7); gAnswer = all } // two halves: airy, then grounded
        }
        if (plan == 4 && second == null) for (b in listOf(2, 3, 6)) note(answer, ((b * 4 + 0.5f) * beat * sr).toInt(), pitch(chord(b)[0] + 2 * n), beat * 3f, 0.24f, Instrument.VIBES, sr)
        val barLen = 4 * beat * sr; val ramp = 0.08f * sr
        fun barGain(g: FloatArray, i: Int): Float {
            val pos = Math.floorMod(i, total).toFloat(); val b = (pos / barLen).toInt().coerceIn(0, bars - 1)
            val into = pos - b * barLen
            val prev = g[(b - 1 + bars) % bars]
            return if (into < ramp) prev + (g[b] - prev) * (into / ramp) else g[b]
        }

        // Mix to stereo: melody near the middle, arpeggio left, the answer right, pad wide
        val l = FloatArray(size); val r = FloatArray(size)
        fun mix(src: FloatArray, pan: Float, g: FloatArray) { val (gl, gr) = panGains(pan); for (i in 0 until size) { val v = src[i] * barGain(g, i); l[i] += v * gl; r[i] += v * gr } }
        mix(melody, 0.08f, gMelody); mix(arp, -0.4f, gArp); mix(answer, 0.45f, gAnswer); mix(bass, 0f, gBass); mix(perc, 0.2f, gPerc)
        for (i in 0 until size) { val g = barGain(gPad, i); l[i] += padL[i] * g; r[i] += padR[i] * g }
        reverb(l, r, sr, wet = 0.42f, room = 0.80f)
        return loopStereo(l, r, total, 0.8f, rms = 0.13f)
    }

    /**
     * Bumped whenever either written piece (the opening or the victory theme) or the orchestra's
     * sound (its instruments, mix or hall) changes. It is part of the pieces' fingerprints, so the
     * unit tests then fail until tools/render_written.sh has made the shipped recordings again.
     */
    const val WRITTEN_VERSION = 6

    // One cycle of each orchestral timbre, read round and round: far cheaper than adding sines.
    private const val WT = 2048
    private fun table(harmonics: Int, amp: (Int) -> Float): FloatArray {
        val t = FloatArray(WT)
        for (h in 1..harmonics) { val a = amp(h); for (i in 0 until WT) t[i] += a * sin(TWO_PI * h * i / WT) }
        val m = t.maxOf { abs(it) }; for (i in 0 until WT) t[i] /= m
        return t
    }
    private val SINE by lazy { table(1) { 1f } }
    private val BRASS_BRIGHT by lazy { table(12) { h -> 1f / h } }
    private val BRASS_SOFT by lazy { table(6) { h -> 1f / (h * h) } }
    private val STRING by lazy { table(8) { h -> 1f / h.toFloat().pow(1.4f) } }
    private val PLUCK_BRIGHT by lazy { table(6) { h -> 1f / h } }
    private val PLUCK_SOFT by lazy { table(2) { h -> if (h == 1) 1f else 0.15f } }

    /** A plucked string (harp, bass): bright at the pluck, its overtones dying first, then the note. */
    private fun pluck(buf: FloatArray, at: Int, f: Float, dur: Float, vol: Float, sr: Int) {
        if (at < 0) return
        val len = min((dur * sr).toInt(), buf.size - at)
        var ph = 0f; val inc = f * WT / sr
        var env = 1f; val decay = exp(-2.5f / sr)          // the note: about 0.4 s to fall to a third
        var bright = 1f; val fade = exp(-12f / sr)         // the overtones: gone in a tenth of a second
        val attackLen = (0.002f * sr).coerceAtLeast(1f)
        for (i in 0 until len) {
            ph += inc; if (ph >= WT * 64f) ph -= WT * 64f
            val v = read(PLUCK_SOFT, ph) * (1 - bright) + read(PLUCK_BRIGHT, ph) * bright
            buf[at + i] += v * env * min(1f, i / attackLen) * vol
            env *= decay; bright *= fade
        }
    }
    private fun read(t: FloatArray, ph: Float): Float { val i = ph.toInt(); val f = ph - i; return t[i and (WT - 1)] * (1 - f) + t[(i + 1) and (WT - 1)] * f }

    /** A brass note: bright as it speaks, mellowing as it holds, with a little vibrato on long notes. */
    private fun brass(buf: FloatArray, at: Int, f: Float, dur: Float, vol: Float, sr: Int, swell: Float = 0.035f) {
        if (at < 0) return
        val len = min(((dur + 0.12f) * sr).toInt(), buf.size - at)
        var ph = 0f; var vph = 0f; val vinc = 5.2f * WT / sr
        var fall = 1f; val fallK = exp(-1.4f / sr)
        for (i in 0 until len) {
            val t = i / sr.toFloat()
            vph += vinc; if (vph >= WT) vph -= WT
            val vib = if (t > 0.25f) 1f + 0.006f * read(SINE, vph) * min(1f, (t - 0.25f) * 3f) else 1f
            ph += f * vib * WT / sr
            if (ph >= WT * 64f) ph -= WT * 64f
            fall *= fallK
            val bright = (0.35f + 0.65f * min(1f, t / 0.05f) * fall).coerceIn(0f, 1f)
            val v = read(BRASS_SOFT, ph) * (1 - bright) + read(BRASS_BRIGHT, ph) * bright
            val env = min(1f, t / swell) * (if (t > dur) max(0f, 1f - (t - dur) / 0.12f) else 1f)
            buf[at + i] += v * env * vol
        }
    }

    /** A section of strings on one note: three bows a hair apart, slow to swell. */
    private fun strings(buf: FloatArray, at: Int, f: Float, dur: Float, vol: Float, sr: Int, spread: Float) {
        if (at < 0) return
        val len = min(((dur + 0.4f) * sr).toInt(), buf.size - at)
        val det = floatArrayOf(1f - spread, 1f, 1f + spread)
        val ph = FloatArray(3) { it * 300f }
        for (i in 0 until len) {
            val t = i / sr.toFloat()
            var v = 0f
            for (k in 0 until 3) { ph[k] += f * det[k] * WT / sr; if (ph[k] >= WT * 64f) ph[k] -= WT * 64f; v += read(STRING, ph[k]) }
            val env = min(1f, t / 0.3f) * (if (t > dur) max(0f, 1f - (t - dur) / 0.4f) else 1f)
            buf[at + i] += v * env * vol / 3f
        }
    }

    /** A timpani stroke: a low drum note that drops a touch in pitch as it sounds. */
    private fun timpani(buf: FloatArray, at: Int, f: Float, vol: Float, sr: Int) {
        if (at < 0) return
        var ph = 0f
        for (i in 0 until min((1.2f * sr).toInt(), buf.size - at)) {
            val t = i / sr.toFloat(); ph += TWO_PI * f * (1f + 0.04f * exp(-t * 20f)) / sr
            buf[at + i] += (sin(ph) + 0.35f * sin(ph * 1.5f) * exp(-t * 6f)) * exp(-t * 3.2f) * min(1f, t / 0.004f) * vol
        }
    }

    /** A snare stroke: the drum's body and its rattling wires, kept off the very top. */
    private fun snare(buf: FloatArray, at: Int, vol: Float, rng: Random, sr: Int) {
        if (at < 0) return
        var lo = 0f; var lo2 = 0f; var hp = 0f
        for (i in 0 until min((0.18f * sr).toInt(), buf.size - at)) {
            val t = i / sr.toFloat(); val x = rng.nextFloat() * 2 - 1
            lo += 0.5f * (x - lo); lo2 += 0.5f * (lo - lo2); hp += 0.12f * (lo2 - hp)
            buf[at + i] += ((lo2 - hp) * 1.6f * exp(-t * 22f) + sin(TWO_PI * 185f * t) * 0.5f * exp(-t * 35f)) * vol
        }
    }

    // ------------------------------------------------------------------ written scores

    /** A written note: start in beats from its section's start, length in beats, MIDI number. */
    private class N(val b: Float, val len: Float, val m: Int)
    private fun n(vararg x: Number): List<N> = x.toList().chunked(3).map { N(it[0].toFloat(), it[1].toFloat(), it[2].toInt()) }
    private const val T3 = 2f / 3f

    /** A chord: three notes in the middle register, and its bass note. */
    private class Ch(val notes: IntArray, val bass: Int)

    /**
     * A section of a written piece: its chords bar by bar, its melody, who leads (0 brass, 1 flute,
     * 2 both), what the drums do (0 rest, 1 march, 2 march building to a roll, 3 fanfare timpani),
     * whether the harp plays, whether the brass stabs the beat, whether the melody is doubled up an
     * octave with bells, which bars hold swelling brass chords, whether a snare roll leads out, and
     * how many semitones the whole section is raised.
     */
    private class Sec(
        val chords: List<Ch>, val tune: List<N>, val lead: Int, val drums: Int, val harp: Boolean = false, val stabs: Boolean = false,
        val up: Boolean = false, val fanfare: Set<Int> = emptySet(), val rollOut: Boolean = false, val shift: Int = 0,
    )

    /** A written piece: its tempo, its sections, and the seed of its drummer's small variations. */
    private class Piece(val bpm: Float, val secs: List<Sec>, val seed: Int) {
        /** Its length in stereo frames: bars of four beats. */
        fun frames(sr: Int): Int = (secs.sumOf { it.chords.size } * 4 * (60f / bpm) * sr).toInt()

        /** A fingerprint of everything written in it: tempo, chords, notes, voices, drums. */
        fun hash(): String {
            val b = StringBuilder("v$WRITTEN_VERSION|$bpm|$seed")
            for (sec in secs) {
                b.append("|S${sec.lead},${sec.drums},${sec.harp},${sec.stabs},${sec.up},${sec.fanfare.sorted()},${sec.rollOut},${sec.shift}")
                for (c in sec.chords) b.append(";C${c.notes.joinToString(",")}/${c.bass}")
                for (n in sec.tune) b.append(";N${n.b},${n.len},${n.m}")
            }
            val d = java.security.MessageDigest.getInstance("SHA-256").digest(b.toString().toByteArray())
            return d.joinToString("") { "%02x".format(it) }
        }
    }

    /** Whether a board's music is one of the two written pieces (the opening on board 1, the victory theme on the last). */
    fun isWritten(level: Int) = level == 1 || level == Levels.COUNT

    /** A written piece's exact length in stereo frames, without rendering it. */
    fun writtenFrames(level: Int, sr: Int): Int = (if (level == 1) openingPiece() else victoryPiece()).frames(sr)

    /**
     * A written piece's fingerprint: the shipped recording must have been made from the same score.
     * It covers the score and [WRITTEN_VERSION] only, not how the orchestra sounds: after changing an
     * instrument, the mix or the hall, bump [WRITTEN_VERSION] and render the recordings again.
     */
    fun writtenHash(level: Int): String = (if (level == 1) openingPiece() else victoryPiece()).hash()

    /**
     * Synthesizes a written piece straight into [out] (exactly [writtenFrames] long), leaving the
     * samples before [from] as they are: for a device that cannot decode the shipped recording, or
     * stopped decoding it partway. Writing into the player's own buffer saves a second full copy.
     */
    fun writtenInto(level: Int, sr: Int, out: ShortArray, from: Int = 0) { orchestra(if (level == 1) openingPiece() else victoryPiece(), sr, out, from) }

    /**
     * Plays a written piece on the orchestra: brass, flute, strings, harp, bass, timpani, snare,
     * bells, in a hall. Each group of instruments is rendered in turn into one scratch track and
     * mixed straight into left and right, so a three-minute piece needs only three full-length
     * buffers at once.
     */
    private fun orchestra(piece: Piece, sr: Int, into: ShortArray? = null, from: Int = 0): ShortArray {
        val bpm = piece.bpm; val secs = piece.secs; val seed = piece.seed
        val beat = 60f / bpm
        val total = piece.frames(sr)
        val size = total + 4 * sr
        fun hz(m: Int) = 440f * 2f.pow((m - 69) / 12f)
        fun at(b: Float) = (b * beat * sr).toInt()
        val starts = secs.runningFold(0) { acc, s -> acc + s.chords.size }.map { it * 4f }
        fun eachNote(f: (Sec, Float, Int, Float, N) -> Unit) { for ((i, sec) in secs.withIndex()) for (nt in sec.tune) f(sec, starts[i] + nt.b, nt.m + sec.shift, nt.len * beat, nt) }
        fun eachBar(f: (Sec, Int, Float, IntArray, Int) -> Unit) {
            for ((i, sec) in secs.withIndex()) for ((k, chord) in sec.chords.withIndex()) f(sec, k, starts[i] + k * 4, IntArray(3) { chord.notes[it] + sec.shift }, chord.bass + sec.shift)
        }
        val score = ArrayList<Pair<Int, Int>>()
        eachNote { _, b, m, _, _ -> score.add((b * 2).toInt() to m) }
        lastScore = score + listOf(-99 to 1)

        val l = FloatArray(size); val r = FloatArray(size); val one = FloatArray(size)
        fun pass(pan: Float, fill: (FloatArray) -> Unit) {
            one.fill(0f); fill(one)
            val (gl, gr) = panGains(pan)
            for (i in 0 until size) { val v = one[i]; if (v != 0f) { l[i] += v * gl; r[i] += v * gr } }
        }
        // The melody on brass (and doubled up an octave in the full sections)
        pass(0.05f) { buf -> eachNote { sec, b, m, d, nt ->
            if (sec.lead == 0) brass(buf, at(b), hz(m), d * 0.95f, 0.30f, sr)
            if (sec.lead == 2) brass(buf, at(b), hz(m), d * 0.95f, 0.26f, sr)
            if (sec.up) brass(buf, at(b), hz(m + 12), d * 0.95f, 0.12f, sr)
        } }
        // The flute: leading the quiet sections, or above the brass an octave up
        pass(0.2f) { buf -> eachNote { sec, b, m, d, _ ->
            if (sec.lead == 1) note(buf, at(b), hz(m), d * 0.97f, 0.42f, Instrument.FLUTE, sr)
            if (sec.lead == 2) note(buf, at(b), hz(m + 12), d * 0.97f, 0.2f, Instrument.FLUTE, sr)
        } }
        // Bells on the long notes of the full sections
        pass(0.45f) { buf -> eachNote { sec, b, m, _, nt -> if (sec.up && nt.len >= 1f) note(buf, at(b), hz(m + 24), 1.2f, 0.05f, Instrument.BELL, sr) } }
        // Strings hold the chord under everything, the upper part on the left, the lower on the right
        pass(-0.6f) { buf -> eachBar { sec, _, bb, ch, _ -> for (m in ch) strings(buf, at(bb), hz(m), 4 * beat, if (sec.lead == 1) 0.05f else 0.06f, sr, 0.004f) } }
        pass(0.6f) { buf -> eachBar { sec, _, bb, ch, _ -> for (m in ch) strings(buf, at(bb), hz(m - 12), 4 * beat, if (sec.lead == 1) 0.035f else 0.045f, sr, 0.005f) } }
        // Harp: rising arpeggios in eighths
        pass(-0.45f) { buf -> eachBar { sec, _, bb, ch, _ -> if (sec.harp) for (e in 0 until 8) pluck(buf, at(bb + e * 0.5f), hz(ch[e % 3] + 12 * (e / 3)), 1.1f, 0.14f, sr) } }
        // Bass: a march on root and fifth, or long notes in the quiet sections
        pass(0f) { buf -> eachBar { sec, _, bb, _, root ->
            if (sec.drums == 0) pluck(buf, at(bb), hz(root), 4 * beat * 0.95f, 0.38f, sr)
            else for (q in 0 until 4) pluck(buf, at(bb + q), hz(if (q % 2 == 0) root else root + 7), beat * 0.9f, if (q == 0) 0.42f else 0.32f, sr)
        } }
        // Brass chords: held swells in the fanfares, or march stabs, spread left and right
        for ((side, pan) in listOf(0 to -0.3f, 1 to 0.3f)) pass(pan) { buf -> eachBar { sec, k, bb, ch, _ ->
            for ((j, m) in ch.withIndex()) {
                if (j % 2 != side) continue
                if (k in sec.fanfare) brass(buf, at(bb), hz(m), 4 * beat * 0.98f, 0.12f, sr, swell = 0.6f)
                else if (sec.stabs) for (q in listOf(0f, 2f, 3.5f)) brass(buf, at(bb + q), hz(m), beat * 0.4f, 0.07f, sr)
            }
        } }
        // Drums: 1 a march with a triplet ruff every other bar, 2 a heavier march, 3 a fanfare timpani roll; rolls leading on
        val rng = Random(seed)
        pass(0.1f) { buf -> eachBar { sec, k, bb, _, root ->
            when (sec.drums) {
                1 -> { timpani(buf, at(bb), hz(root + 12), 0.3f, sr); snare(buf, at(bb + 1), 0.16f, rng, sr); snare(buf, at(bb + 3), 0.16f, rng, sr)
                       if (k % 2 == 1) for (r in 0 until 3) snare(buf, at(bb + 3.5f + r * (0.5f / 3)), 0.09f, rng, sr) }
                2 -> { timpani(buf, at(bb), hz(root + 12), 0.32f, sr); snare(buf, at(bb + 1), 0.15f, rng, sr); snare(buf, at(bb + 3), 0.15f, rng, sr) }
                3 -> for (r in 0 until 14) timpani(buf, at(bb + r * (4f / 14)), hz(root + 12), 0.07f + 0.012f * r, sr)
            }
            if (sec.rollOut && k == sec.chords.size - 1) for (r in 0 until 16) snare(buf, at(bb + r * 0.25f), 0.04f + 0.011f * r, rng, sr)
        } }
        reverb(l, r, sr, wet = 0.38f, room = 0.84f)
        return loopStereo(l, r, total, 0.85f, rms = 0.14f, into, from)
    }

    /**
     * The opening tune: what the game sounds like on the main screen and on the first board. An
     * original adventure march, composed note by note, three minutes in B-flat at 120 beats a minute,
     * looping: a fanfare, the theme, the theme climbing, a quiet interlude in G minor, a bridge of
     * brass calls rising, the theme in full, a travelling march, a pastoral rest, the theme turned
     * to G minor, a hush, the theme raised a step, and a finale whose fanfare cadence leads back to
     * the start.
     */
    private fun opening(sr: Int): ShortArray = orchestra(openingPiece(), sr)

    private fun openingPiece(): Piece {
        val Bb = Ch(intArrayOf(58, 62, 65), 46); val Gm = Ch(intArrayOf(55, 58, 62), 43); val Eb = Ch(intArrayOf(55, 58, 63), 39); val F = Ch(intArrayOf(53, 57, 60), 41)
        val Cm = Ch(intArrayOf(55, 60, 63), 36); val D = Ch(intArrayOf(54, 57, 62), 38); val Gb = Ch(intArrayOf(54, 58, 61), 42); val Ab = Ch(intArrayOf(56, 60, 63), 44)
        val C = Ch(intArrayOf(55, 60, 64), 36); val FoA = Ch(intArrayOf(57, 60, 65), 45)
        val themeA = n(0, 1.5, 74, 1.5, .5, 75, 2, 2, 77, 4, 1, 79, 5, .5, 77, 5.5, .5, 74, 6, 2, 70,
            8, T3, 75, 8 + T3, T3, 74, 8 + 2 * T3, T3, 72, 10, 2, 79, 12, 1.5, 77, 13.5, .5, 75, 14, 1, 74, 15, 1, 72,
            16, 1.5, 74, 17.5, .5, 75, 18, 1, 77, 19, 1, 82, 20, 1, 81, 21, 1, 79, 22, 2, 77,
            24, 1, 75, 25, .5, 74, 25.5, .5, 75, 26, 1, 72, 27, 1, 77, 28, 3, 74)
        val themeB = themeA.filter { it.b < 16 } + n(16, 1.5, 77, 17.5, .5, 79, 18, 1, 81, 19, 1, 82, 20, 2, 84, 22, 1, 82, 23, 1, 79,
            24, 1, 81, 25, 1, 77, 26, T3, 79, 26 + T3, T3, 81, 26 + 2 * T3, T3, 82, 28, 3.5, 82)
        val minor = n(0, 2, 67, 2, 1, 70, 3, 1, 74, 4, 3, 75, 7, 1, 74, 8, 1, 72, 9, 1, 70, 10, 1, 69, 11, 1, 67, 12, 2, 66, 14, 2, 69,
            16, 1, 67, 17, 1, 69, 18, 1, 70, 19, 1, 74, 20, 2, 79, 22, 1, 77, 23, 1, 75, 24, 1.5, 74, 25.5, .5, 72, 26, 1, 70, 27, 1, 69, 28, 4, 74)
        val calls = n(0, 1.5, 67, 1.5, .5, 70, 2, 2, 75, 4, 1.5, 69, 5.5, .5, 72, 6, 2, 77, 8, 1.5, 70, 9.5, .5, 74, 10, 2, 79,
            12, 1.5, 72, 13.5, .5, 77, 14, 2, 81, 16, 1, 74, 17, 1, 77, 18, 2, 82, 20, 1, 76, 21, 1, 79, 22, 2, 84,
            24, 1, 78, 25, 1, 81, 26, 2, 86, 28, 2, 84, 30, 1, 81, 31, 1, 77)
        val rest = n(0, 4, 70, 4, 4, 74, 8, 2, 75, 10, 2, 72, 12, 4, 77, 16, 4, 79, 20, 4, 77, 24, 2, 74, 26, 2, 70, 28, 4, 72)
        val finale = themeA.filter { it.b < 16 } + n(16, 1.5, 73, 17.5, .5, 75, 18, 2, 78, 20, 1.5, 75, 21.5, .5, 77, 22, 2, 80, 24, 6, 82)
        val travel = n(0, 1, 70, 1, 1, 74, 2, 1, 77, 3, 1, 82, 4, 2, 81, 6, 1, 77, 7, 1, 72, 8, 1, 74, 9, 1, 79, 10, 2, 82,
            12, 1.5, 79, 13.5, .5, 77, 14, 2, 75, 16, 1, 74, 17, 1, 77, 18, 1, 82, 19, 1, 86, 20, 2, 84, 22, 1, 81, 23, 1, 77,
            24, 1, 79, 25, 1, 75, 26, 1, 72, 27, 1, 70, 28, 4, 72)
        val darkTheme = n(0, 1.5, 67, 1.5, .5, 70, 2, 2, 74, 4, 1, 75, 5, .5, 74, 5.5, .5, 72, 6, 2, 70,
            8, T3, 74, 8 + T3, T3, 72, 8 + 2 * T3, T3, 70, 10, 2, 77, 12, 1.5, 77, 13.5, .5, 75, 14, 1, 74, 15, 1, 72,
            16, 1.5, 74, 17.5, .5, 75, 18, 1, 79, 19, 1, 82, 20, 2, 79, 22, 1, 75, 23, 1, 74,
            24, 1, 72, 25, .5, 70, 25.5, .5, 72, 26, 1, 75, 27, 1, 74, 28, 4, 78)
        val hush = n(0, 4, 79, 8, 4, 75, 16, 4, 79, 20, 2, 77, 22, 2, 74, 24, 4, 72, 28, 4, 77)
        return Piece(120f, listOf(
            Sec(listOf(Bb, Eb, Cm, F), emptyList(), 0, 3, fanfare = setOf(0, 1, 2, 3), rollOut = true),        // the intro: brass swelling from home up to the dominant, a roll leading into the theme
            Sec(listOf(Bb, Gm, Eb, F, Bb, Gm, Cm, Bb), themeA, 0, 1, stabs = true),                             // theme
            Sec(listOf(Bb, Gm, Eb, F, Bb, Eb, F, Bb), themeB, 2, 1, harp = true, stabs = true),                 // theme climbing, flute joins
            Sec(listOf(Gm, Eb, Cm, D, Gm, Eb, Cm, D), minor, 1, 0, harp = true),                                // interlude in G minor
            Sec(listOf(Eb, F, Gm, FoA, Bb, C, D, F), calls, 0, 2, stabs = true, rollOut = true),                // bridge: calls rising
            Sec(listOf(Bb, Gm, Eb, F, Bb, Eb, F, Bb), themeB, 2, 1, harp = true, stabs = true, up = true),      // the theme in full
            Sec(listOf(Bb, F, Gm, Eb, Bb, F, Eb, F), travel, 1, 1, harp = true),                                // travelling on: flute over a light march
            Sec(listOf(Eb, Bb, Cm, F, Eb, Bb, Gm, F), rest, 1, 0, harp = true),                                 // pastoral rest
            Sec(listOf(Gm, Eb, Bb, F, Gm, Eb, Cm, D), darkTheme, 0, 2, stabs = true, rollOut = true),           // the theme turned to G minor
            Sec(listOf(Eb, Bb, Cm, Gm, Eb, Bb, F, F), hush, 1, 0, harp = true),                                 // a hush before the end
            Sec(listOf(Bb, Gm, Eb, F, Bb, Eb, F, Bb), themeB, 2, 1, harp = true, stabs = true, up = true, shift = 2), // the theme raised a step
            Sec(listOf(Bb, Gm, Eb, F, Gb, Ab, Bb, Bb), finale, 0, 3, stabs = true, fanfare = setOf(4, 5, 6, 7)), // finale and cadence, home in B-flat
        ), 1)
    }

    /**
     * The last board's music: an original victory theme, three minutes in D major at 108 beats a
     * minute, looping: a fanfare, the theme and its answer, a lyrical interlude in B minor, a light
     * trio, the theme in full, a bridge rising through new keys, a brass chorale in G, the theme
     * lifted a step into E major with its answer, and a coda that turns home to D and holds the
     * final chord, so the loop comes round in key.
     */
    private fun victory(sr: Int): ShortArray = orchestra(victoryPiece(), sr)

    private fun victoryPiece(): Piece {
        val D = Ch(intArrayOf(57, 62, 66), 38); val G = Ch(intArrayOf(55, 59, 62), 43); val A = Ch(intArrayOf(57, 61, 64), 45); val Bm = Ch(intArrayOf(54, 59, 62), 47)
        val Em = Ch(intArrayOf(55, 59, 64), 40); val Fsm = Ch(intArrayOf(54, 57, 61), 42); val B = Ch(intArrayOf(54, 59, 63), 47); val Bb = Ch(intArrayOf(58, 62, 65), 46)
        val C = Ch(intArrayOf(55, 60, 64), 36)
        val theme = n(0, 1, 69, 1, 1, 74, 2, 1.5, 78, 3.5, .5, 76, 4, 2, 74, 6, 1, 71, 7, 1, 74, 8, 3, 81, 11, 1, 78,
            12, 2, 76, 14, T3, 76, 14 + T3, T3, 78, 14 + 2 * T3, T3, 79,
            16, 1.5, 81, 17.5, .5, 78, 18, 1, 74, 19, 1, 78, 20, 2, 83, 22, 1, 81, 23, 1, 79, 24, 1, 78, 25, 1, 76, 26, 1, 73, 27, 1, 76, 28, 4, 74)
        val answer = theme.filter { it.b < 16 } + n(16, 1.5, 86, 17.5, .5, 85, 18, 1, 83, 19, 1, 81, 20, 1, 79, 21, 1, 83, 22, 2, 86,
            24, 2, 88, 26, 1, 85, 27, 1, 81, 28, 4, 86)
        val lyric = n(0, 3, 74, 3, 1, 73, 4, 2, 71, 6, 2, 74, 8, 3, 78, 11, 1, 76, 12, 4, 73,
            16, 2, 74, 18, 1, 76, 19, 1, 78, 20, 2, 79, 22, 2, 83, 24, 2, 79, 26, 1, 78, 27, 1, 76, 28, 4, 76)
        val trio = n(0, .5, 83, .5, .5, 81, 1, 1, 79, 2, 1, 83, 3, 1, 86, 4, 1, 86, 5, .5, 85, 5.5, .5, 83, 6, 2, 81,
            8, 1, 85, 9, 1, 81, 10, 1, 76, 11, 1, 81, 12, 2, 78, 14, 2, 74,
            16, .5, 83, 16.5, .5, 81, 17, 1, 79, 18, 1, 83, 19, 1, 88, 20, 1, 86, 21, .5, 85, 21.5, .5, 83, 22, 2, 81,
            24, 1, 79, 25, 1, 78, 26, 1, 76, 27, 1, 73, 28, 4, 74)
        val rising = n(0, 1.5, 71, 1.5, .5, 74, 2, 2, 79, 4, 1.5, 73, 5.5, .5, 76, 6, 2, 81, 8, 1.5, 73, 9.5, .5, 78, 10, 2, 81,
            12, 1.5, 75, 13.5, .5, 78, 14, 2, 83, 16, 1, 74, 17, 1, 79, 18, 2, 83, 20, 1, 76, 21, 1, 81, 22, 2, 85,
            24, 1, 77, 25, 1, 82, 26, 2, 86, 28, 1, 79, 29, 1, 84, 30, 2, 88)
        val chorale = n(0, 2, 79, 2, 2, 83, 4, 2, 84, 6, 2, 83, 8, 2, 79, 10, 2, 81, 12, 4, 81,
            16, 2, 79, 18, 2, 83, 20, 2, 84, 22, 2, 88, 24, 2, 86, 26, 2, 81, 28, 4, 83)
        // The coda turns from E back home to D (E, then A, then D), so the loop lands in key on the fanfare
        val coda = n(0, 4, 80, 4, 4, 81, 8, 2, 85, 10, 2, 88, 12, 4, 86)
        val E = Ch(intArrayOf(56, 59, 64), 40)
        val themeChords = listOf(D, G, D, A, D, G, A, D)
        return Piece(108f, listOf(
            Sec(listOf(D, G, A, D), emptyList(), 0, 3, fanfare = setOf(0, 1, 2, 3), rollOut = true),            // fanfare
            Sec(themeChords, theme, 0, 1, stabs = true),                                                         // the victory theme
            Sec(listOf(D, G, D, A, D, G, A, D), answer, 2, 1, harp = true, stabs = true),                         // its answer, higher
            Sec(listOf(Bm, G, D, A, Bm, G, Em, A), lyric, 1, 0, harp = true),                                    // lyrical, in B minor
            Sec(listOf(G, D, A, D, G, D, A, D), trio, 1, 1, harp = true),                                        // a light trio
            Sec(themeChords, answer, 2, 1, harp = true, stabs = true, up = true, rollOut = true),                // the theme in full
            Sec(listOf(Em, A, Fsm, B, G, A, Bb, C), rising, 0, 2, stabs = true, rollOut = true),                // rising through new keys
            Sec(listOf(G, C, G, D, Em, C, D, G), chorale, 0, 0, fanfare = setOf(0, 1, 2, 3, 4, 5, 6, 7)), // brass chorale in G
            Sec(themeChords, theme, 2, 1, harp = true, stabs = true, shift = 2),                                 // the theme, a step higher: E major
            Sec(themeChords, answer, 2, 1, harp = true, stabs = true, up = true, shift = 2),                     // and its answer
            Sec(listOf(E, A, A, D), coda, 0, 3, stabs = true, fanfare = setOf(2, 3)),                            // coda: home to D, the final chord held
        ), 100)
    }

    /** A soft held chord tone: two sines a hair apart, swelling in and fading out. */
    private fun pad(buf: FloatArray, at: Int, f: Float, dur: Float, vol: Float, detune: Float, sr: Int) {
        val len = min((dur * sr).toInt(), buf.size - at)
        for (i in 0 until len) {
            val t = i / sr.toFloat()
            val env = min(1f, t / 0.45f) * min(1f, max(0f, (dur - t) / 0.5f))
            buf[at + i] += (sin(TWO_PI * f * detune * t) + 0.35f * sin(TWO_PI * f * 2 * detune * t + 0.6f)) * env * vol
        }
    }

    // ================================================================== ambience

    /**
     * Twenty seconds of the board's surroundings, in stereo: its weather and water and its animals,
     * each set for this board. Broad sounds (wind, rain, waves) fill both sides; single creatures and drips each sit
     * in their own place left or right. All share a room whose size suits the world.
     */
    fun ambience(env: Environment, sr: Int): ShortArray {
        val total = (AMB * sr).toInt()
        val size = total + 6 * sr
        val rng = Random(env.seed + 1)
        val breath = 0.6f + 0.4f * env.density // the surroundings press closer as the boards climb
        val palette = PALETTES.getValue(env.theme)
        // The theme's signature surroundings always; then up to two more from its palette
        val count = 1 + (if (rng.nextFloat() < 0.4f + 0.5f * env.density) 1 else 0) + (if (env.density > 0.6f && rng.nextFloat() < 0.5f) 1 else 0)
        val chosen = mutableListOf(palette[0])
        for (e in palette.drop(1).shuffled(rng)) { if (chosen.size >= count) break; chosen.add(e) }
        val l = FloatArray(size); val r = FloatArray(size)
        val haas = (0.013f * sr).toInt()
        for (e in chosen) {
            val buf = FloatArray(size)
            element(e, buf, rng, sr, env, breath)
            if (e in BROAD) { // both sides, the right a breath later: wide
                for (i in 0 until size) { l[i] += buf[i] * 0.75f; r[i] += buf[(i - haas + size) % size] * 0.75f }
            } else {
                val (gl, gr) = panGains(rng.nextFloat() * 1.3f - 0.65f)
                for (i in 0 until size) { l[i] += buf[i] * gl; r[i] += buf[i] * gr }
            }
        }
        // The animals: a few species of the world, more on later boards, each with one or two of
        // its kind, every one at its own pitch, in its own place, near or far
        val pool = FAUNA.getValue(env.theme)
        val kinds = min(pool.size - 1, 3 + (env.density * 3f).toInt() + (if (rng.nextFloat() < 0.5f) 1 else 0)).coerceAtLeast(0) // always one of the world's animals left out, so boards differ
        // the world's commonest species are likelier, but any may come
        val species = pool.withIndex().sortedBy { (i, _) -> rng.nextFloat() * (1f + i * 0.15f) }.take(kinds).map { it.value }
        lastFauna = species
        val one = FloatArray(size)
        for (a in species) repeat(if (rng.nextFloat() < 0.4f) 2 else 1) {
            one.fill(0f)
            animal(a, one, rng, sr, 0.86f + 0.3f * rng.nextFloat())
            val far = rng.nextFloat() // 0 near, 1 far: quieter, and the far ones lose their brightness
            val gain = 1f - 0.55f * far; val alpha = 1f - 0.6f * far
            var y = 0f
            for (i in 0 until size) { y += alpha * (one[i] - y); one[i] = y * gain }
            val (gl, gr) = panGains(rng.nextFloat() * 1.6f - 0.8f)
            for (i in 0 until size) { l[i] += one[i] * gl; r[i] += one[i] * gr }
        }
        // Take the hiss off the top of the whole soundscape: two gentle poles at about four kilohertz
        for (side in listOf(l, r)) { var y1 = 0f; var y2 = 0f; for (i in side.indices) { y1 += 0.72f * (side[i] - y1); y2 += 0.72f * (y1 - y2); side[i] = y2 } }
        val room = when (env.theme) { Theme.CAVE -> 0.6f; Theme.TEMPLE -> 0.55f; Theme.STARS -> 0.5f; Theme.SNOW -> 0.35f; else -> 0.25f }
        reverb(l, r, sr, wet = room, room = 0.84f)
        return loopStereo(l, r, total, 0.7f, rms = 0.045f * (0.75f + 0.25f * env.density)) // set by average level, not by its loudest instant
    }

    /** The surroundings that are not animals: weather, water, earth, and the made things of each world. */
    private enum class Element { WIND, RAIN, WAVES, DRIPS, STREAM, RUMBLE, DRONE, SHIMMER, CHIMES, BELL, THUNDER, STEAM, FIRE, CREAKS, CLOCK, ECHO_DRIPS }

    /** Sounds that fill the space rather than come from one place. */
    private val BROAD = setOf(Element.WIND, Element.RAIN, Element.WAVES, Element.STREAM, Element.RUMBLE, Element.DRONE, Element.SHIMMER, Element.FIRE)

    private val PALETTES = mapOf(
        Theme.WORKSHOP to listOf(Element.CLOCK, Element.CREAKS),
        Theme.FOREST to listOf(Element.WIND, Element.STREAM, Element.CREAKS, Element.CHIMES),
        Theme.SEA to listOf(Element.WAVES, Element.WIND, Element.BELL, Element.CHIMES, Element.CREAKS),
        Theme.DESERT to listOf(Element.WIND, Element.RUMBLE, Element.DRONE, Element.CREAKS),
        Theme.CAVE to listOf(Element.DRIPS, Element.RUMBLE, Element.ECHO_DRIPS, Element.STREAM, Element.DRONE, Element.WIND),
        Theme.TEMPLE to listOf(Element.DRONE, Element.BELL, Element.CHIMES, Element.CLOCK, Element.WIND),
        Theme.STORM to listOf(Element.RAIN, Element.THUNDER, Element.WIND, Element.CREAKS, Element.DRIPS, Element.RUMBLE),
        Theme.SNOW to listOf(Element.WIND, Element.CHIMES, Element.SHIMMER, Element.CREAKS, Element.DRIPS, Element.BELL),
        Theme.NIGHT to listOf(Element.WIND, Element.SHIMMER, Element.STREAM),
        Theme.VOLCANO to listOf(Element.RUMBLE, Element.STEAM, Element.FIRE, Element.THUNDER, Element.DRONE, Element.WIND),
        Theme.STARS to listOf(Element.SHIMMER, Element.DRONE, Element.CHIMES, Element.BELL, Element.WIND),
    )

    // ================================================================== animals

    /** The creatures a world can hold. Each has its own voice and its own way of calling. */
    internal enum class Animal {
        SONGBIRD, WARBLER, CUCKOO, DOVE, WOODPECKER, SQUIRREL, CROW, RAVEN, OWL, INSECT,
        GULL, TERN, SEAL, WHALE, LOON, HAWK, COYOTE, WOLF, CICADA, RATTLESNAKE, BATS,
        MONKEY, PEACOCK, CHICKADEE, NIGHTJAR, FROG, CRICKET,
    }

    /** Who lives where. The first few of each world are its commonest. */
    private val FAUNA = mapOf(
        Theme.WORKSHOP to emptyList(),
        Theme.FOREST to listOf(Animal.SONGBIRD, Animal.CUCKOO, Animal.WOODPECKER, Animal.WARBLER, Animal.DOVE, Animal.SQUIRREL, Animal.CROW, Animal.OWL, Animal.INSECT),
        Theme.SEA to listOf(Animal.GULL, Animal.TERN, Animal.SEAL, Animal.WHALE, Animal.LOON),
        Theme.DESERT to listOf(Animal.HAWK, Animal.CICADA, Animal.COYOTE, Animal.DOVE, Animal.RATTLESNAKE, Animal.RAVEN),
        Theme.CAVE to listOf(Animal.BATS, Animal.FROG, Animal.OWL, Animal.CRICKET, Animal.RAVEN),
        Theme.TEMPLE to listOf(Animal.MONKEY, Animal.PEACOCK, Animal.DOVE, Animal.CICADA, Animal.CROW, Animal.SONGBIRD, Animal.INSECT, Animal.WOODPECKER),
        Theme.STORM to listOf(Animal.CROW, Animal.FROG, Animal.RAVEN, Animal.GULL, Animal.OWL, Animal.TERN, Animal.WOLF),
        Theme.SNOW to listOf(Animal.CHICKADEE, Animal.RAVEN, Animal.WOLF, Animal.OWL, Animal.LOON, Animal.WOODPECKER, Animal.CROW, Animal.SQUIRREL),
        Theme.NIGHT to listOf(Animal.CRICKET, Animal.FROG, Animal.OWL, Animal.NIGHTJAR, Animal.BATS, Animal.WOLF, Animal.SONGBIRD, Animal.INSECT),
        Theme.VOLCANO to listOf(Animal.RAVEN, Animal.HAWK, Animal.CROW, Animal.BATS, Animal.CICADA, Animal.RATTLESNAKE, Animal.COYOTE, Animal.INSECT),
        Theme.STARS to listOf(Animal.NIGHTJAR, Animal.OWL, Animal.WHALE, Animal.LOON, Animal.WOLF, Animal.CRICKET, Animal.DOVE, Animal.INSECT, Animal.FROG),
    )

    /** The animals each world can hold, for tests. */
    internal fun fauna(theme: Theme): List<Animal> = FAUNA.getValue(theme)

    /** The species in the last soundscape built, for tests. */
    internal var lastFauna: List<Animal> = emptyList(); private set

    /**
     * One animal's calls over the loop, at its own pitch ([p], 1 is the species' usual), each with
     * its own small differences. Times run over the whole loop; the end folds onto the start.
     */
    private fun animal(a: Animal, buf: FloatArray, rng: Random, sr: Int, p: Float) {
        fun r(lo: Float, hi: Float) = lo + (hi - lo) * rng.nextFloat()
        // Start times every [every] seconds or so, jittered, across the loop
        fun times(every: Float): List<Float> {
            val out = ArrayList<Float>(); var t = rng.nextFloat() * every
            while (t < AMB) { out.add(t); t += every * r(0.7f, 1.3f) }
            return out
        }
        fun at(t: Float) = (t * sr).toInt()
        when (a) {
            Animal.SONGBIRD -> { // phrases of a few notes from the bird's own set of pitches: slurred, steady or trilled
                val pitches = FloatArray(4) { r(2000f, 3400f) * p }
                for (t0 in times(r(3f, 6f))) {
                    var t = t0
                    repeat(4 + rng.nextInt(6)) {
                        val f = pitches[rng.nextInt(4)]; val d = r(0.06f, 0.14f); val kind = rng.nextInt(4)
                        voice(buf, at(t), d, sr, 0.09f, { u -> when (kind) { 1 -> f * (0.85f + 0.3f * u); 2 -> f * (1.15f - 0.3f * u); else -> f } }, floatArrayOf(1f, 0.2f),
                            vibRate = if (kind == 3) 32f else 0f, vibDepth = if (kind == 3) 0.07f else 0f, attack = 0.008f, release = 0.02f)
                        t += d + r(0.03f, 0.07f)
                    }
                }
            }
            Animal.WARBLER -> for (t0 in times(r(5f, 9f))) { // a rapid jumble of rising notes
                var t = t0
                repeat(12 + rng.nextInt(12)) { val f = r(2400f, 3800f) * p; voice(buf, at(t), 0.04f, sr, 0.06f, { u -> f * (0.9f + 0.25f * u) }, floatArrayOf(1f, 0.1f), attack = 0.004f, release = 0.01f); t += r(0.045f, 0.07f) }
            }
            Animal.CUCKOO -> for (t0 in times(r(10f, 16f))) { // cu-coo, cu-coo: a falling minor third
                val f1 = 700f * p; var t = t0
                repeat(3 + rng.nextInt(4)) {
                    voice(buf, at(t), 0.22f, sr, 0.12f, { f1 }, floatArrayOf(1f, 0.15f), attack = 0.02f, release = 0.06f)
                    voice(buf, at(t + 0.32f), 0.3f, sr, 0.11f, { f1 * 0.84f }, floatArrayOf(1f, 0.15f), attack = 0.02f, release = 0.1f)
                    t += r(0.85f, 1.0f)
                }
            }
            Animal.DOVE -> for (t0 in times(r(8f, 12f))) { // coo-COOO-coo-coo, soft and low
                val f = 440f * p
                voice(buf, at(t0), 0.35f, sr, 0.07f, { u -> f * (0.95f + 0.1f * u) }, floatArrayOf(1f, 0.2f), attack = 0.06f, release = 0.1f)
                voice(buf, at(t0 + 0.5f), 0.65f, sr, 0.1f, { u -> f * (1.12f - 0.08f * u) }, floatArrayOf(1f, 0.2f), attack = 0.08f, release = 0.15f)
                voice(buf, at(t0 + 1.3f), 0.3f, sr, 0.07f, { f }, floatArrayOf(1f, 0.2f), attack = 0.05f, release = 0.1f)
                voice(buf, at(t0 + 1.75f), 0.3f, sr, 0.06f, { f * 0.97f }, floatArrayOf(1f, 0.2f), attack = 0.05f, release = 0.1f)
            }
            Animal.WOODPECKER -> for (t0 in times(r(8f, 14f))) { // a drum roll on dead wood
                val gap = 1f / r(15f, 20f)
                for (k in 0 until 14 + rng.nextInt(9)) {
                    val s0 = at(t0 + k * gap); if (s0 >= buf.size) break
                    val fade = 1f - 0.4f * k / 22f
                    for (j in 0 until min((0.025f * sr).toInt(), buf.size - s0)) {
                        val t = j / sr.toFloat()
                        buf[s0 + j] += (sin(TWO_PI * 1100f * p * t) * 0.6f + sin(TWO_PI * 2350f * p * t) * 0.3f) * exp(-t * 160f) * 0.2f * fade
                    }
                }
            }
            Animal.SQUIRREL -> for (t0 in times(r(9f, 14f))) { // a scolding chatter
                for (k in 0 until 12 + rng.nextInt(14)) voice(buf, at(t0 + k / 13f), 0.03f, sr, 0.05f, { u -> 2500f * p * (1.1f - 0.2f * u) }, floatArrayOf(1f, 0.4f), rough = 0.5f, attack = 0.003f, release = 0.01f)
            }
            Animal.CROW -> for (t0 in times(r(7f, 12f))) { // caw caw caw: nasal and rough
                val step = r(0.4f, 0.5f)
                for (k in 0 until 2 + rng.nextInt(3)) voice(buf, at(t0 + k * step), 0.3f, sr, 0.06f, { u -> 600f * p * (1f - 0.12f * u) }, floatArrayOf(1f, 0.8f, 0.6f, 0.5f, 0.3f), rough = 0.6f, attack = 0.02f, release = 0.06f)
            }
            Animal.RAVEN -> for (t0 in times(r(8f, 14f))) { // a deep, knocking croak
                for (k in 0 until 1 + rng.nextInt(3)) voice(buf, at(t0 + k * 0.6f), 0.35f, sr, 0.07f, { u -> 330f * p * (1f - 0.1f * u) }, floatArrayOf(1f, 0.7f, 0.5f, 0.4f), rough = 0.8f, attack = 0.02f, release = 0.08f)
            }
            Animal.OWL -> for (t0 in times(r(9f, 15f))) { // hoo... hoo-hoo... hoooo
                val f = 380f * p
                for ((dt, d) in listOf(0f to 0.35f, 0.75f to 0.18f, 1.0f to 0.18f, 1.45f to 0.7f))
                    voice(buf, at(t0 + dt), d, sr, 0.1f, { u -> f * (1f - 0.05f * u) }, floatArrayOf(1f, 0.1f), vibRate = 5f, vibDepth = 0.008f, attack = 0.04f, release = 0.12f)
            }
            Animal.INSECT -> for (t0 in times(r(7f, 12f))) { // a fly or a bee passing by, swelling and fading
                val f = r(170f, 250f) * p
                voice(buf, at(t0), r(1.5f, 2.5f), sr, 0.035f, { u -> f * (1f + 0.04f * sin(u * 9f)) }, floatArrayOf(1f, 0.5f, 0.3f), attack = 0.6f, release = 0.6f)
            }
            Animal.GULL -> for (t0 in times(r(6f, 10f))) { // kyow kyow: a rising and falling cry
                val step = r(0.33f, 0.4f)
                for (k in 0 until 3 + rng.nextInt(3)) voice(buf, at(t0 + k * step), 0.28f, sr, 0.08f, { u -> 950f * p * (1f + 0.4f * sin(u * PI.toFloat())) }, floatArrayOf(1f, 0.4f, 0.2f), rough = 0.3f, attack = 0.02f, release = 0.06f)
            }
            Animal.TERN -> for (t0 in times(r(5f, 9f))) { // kee-arr, sharp and raspy
                for (k in 0 until 2 + rng.nextInt(2)) voice(buf, at(t0 + k * 0.5f), 0.35f, sr, 0.05f, { u -> 2000f * p * (1f - 0.35f * u) }, floatArrayOf(1f, 0.5f, 0.3f), rough = 0.5f, attack = 0.01f, release = 0.05f)
            }
            Animal.SEAL -> for (t0 in times(r(8f, 13f))) { // ar ar ar
                for (k in 0 until 2 + rng.nextInt(3)) voice(buf, at(t0 + k * 0.3f), 0.18f, sr, 0.07f, { u -> 380f * p * (1f - 0.13f * u) }, floatArrayOf(1f, 0.9f, 0.7f, 0.5f), rough = 0.9f, attack = 0.01f, release = 0.05f)
            }
            Animal.WHALE -> for (t0 in times(r(11f, 17f))) { // a long, low song gliding up and down
                val lo = 160f * p
                voice(buf, at(t0), r(3f, 4.5f), sr, 0.1f, { u -> lo * (1f + 1.6f * sin(u * PI.toFloat()) - 0.3f * u) }, floatArrayOf(1f, 0.4f, 0.2f), vibRate = 3f, vibDepth = 0.02f, attack = 0.5f, release = 0.8f)
            }
            Animal.LOON -> for (t0 in times(r(12f, 18f))) { // a haunting wail of three notes
                val f = 620f * p
                voice(buf, at(t0), 1f, sr, 0.08f, { u -> f * (1f + 0.7f * u) }, floatArrayOf(1f, 0.3f), vibRate = 5f, vibDepth = 0.01f, attack = 0.15f, release = 0.05f)
                voice(buf, at(t0 + 1f), 0.8f, sr, 0.08f, { f * 1.7f }, floatArrayOf(1f, 0.3f), vibRate = 5f, vibDepth = 0.012f, attack = 0.03f, release = 0.05f)
                voice(buf, at(t0 + 1.8f), 0.9f, sr, 0.07f, { u -> f * (1.7f - 0.28f * u) }, floatArrayOf(1f, 0.3f), vibRate = 5f, vibDepth = 0.012f, attack = 0.03f, release = 0.4f)
            }
            Animal.HAWK -> for (t0 in times(r(9f, 15f))) { // kee-eeee-arr from high above
                voice(buf, at(t0), 1.3f, sr, 0.06f, { u -> 2700f * p * (1f - 0.12f * u - 0.12f * u * u) }, floatArrayOf(1f, 0.4f, 0.2f), rough = 0.4f, attack = 0.05f, release = 0.3f)
            }
            Animal.COYOTE -> for (t0 in times(r(13f, 19f))) { // a few yips, then the howl
                var t = t0
                repeat(3 + rng.nextInt(4)) { voice(buf, at(t), 0.12f, sr, 0.06f, { u -> 700f * p * (1f + 0.5f * u) }, floatArrayOf(1f, 0.35f, 0.15f), attack = 0.01f, release = 0.03f); t += r(0.15f, 0.25f) }
                voice(buf, at(t + 0.2f), r(2.5f, 3.5f), sr, 0.07f, { u -> 420f * p * (if (u < 0.25f) 1f + 0.9f * u / 0.25f else 1.9f - 0.5f * (u - 0.25f)) }, floatArrayOf(1f, 0.35f, 0.15f), vibRate = 6f, vibDepth = 0.03f, attack = 0.1f, release = 0.4f)
            }
            Animal.WOLF -> for (t0 in times(r(12f, 18f))) { // a long howl, rising then sinking
                voice(buf, at(t0), r(3.5f, 4.5f), sr, 0.08f, { u -> 350f * p * (if (u < 0.25f) 1f + 0.6f * u / 0.25f else 1.6f - 0.45f * (u - 0.25f) / 0.75f) }, floatArrayOf(1f, 0.3f, 0.1f), vibRate = 5f, vibDepth = 0.02f, attack = 0.3f, release = 0.6f)
            }
            Animal.CICADA -> for (t0 in times(r(9f, 14f))) { // a buzzing song that swells and dies away
                val d = r(4f, 7f)
                voice(buf, at(t0), d, sr, 0.03f, { 2900f * p }, floatArrayOf(1f, 0.25f), pulse = 120f, attack = d * 0.4f, release = d * 0.3f)
            }
            Animal.RATTLESNAKE -> for (t0 in times(r(12f, 18f))) { // a dry rattle, kept off the very top
                val d = r(1.2f, 2f); val s0 = at(t0); var lo = 0f; var lo2 = 0f; var hp = 0f
                for (j in 0 until min((d * sr).toInt(), buf.size - s0)) {
                    val t = j / sr.toFloat(); val x = rng.nextFloat() * 2 - 1
                    lo += 0.45f * (x - lo); lo2 += 0.45f * (lo - lo2); hp += 0.2f * (lo2 - hp)
                    val gate = if ((t * 50f) % 1f < 0.35f) 1f else 0.1f
                    buf[s0 + j] += (lo2 - hp) * gate * 0.12f * min(1f, t * 8f) * min(1f, (d - t) * 3f)
                }
            }
            Animal.BATS -> for (t0 in times(r(5f, 9f))) { // a burst of tiny falling chirps
                val rate = r(12f, 18f)
                for (k in 0 until 8 + rng.nextInt(8)) voice(buf, at(t0 + k / rate), 0.008f, sr, 0.05f, { u -> 3800f * p * (1f - 0.4f * u) }, floatArrayOf(1f), attack = 0.001f, release = 0.004f)
            }
            Animal.MONKEY -> for (t0 in times(r(12f, 18f))) { // whoops quickening, then a screech
                var t = t0; var gap = 0.45f
                repeat(6 + rng.nextInt(5)) { voice(buf, at(t), 0.18f, sr, 0.07f, { u -> 450f * p * (1f + u) }, floatArrayOf(1f, 0.5f, 0.3f), rough = 0.3f, attack = 0.02f, release = 0.04f); t += gap; gap = max(0.18f, gap * 0.85f) }
                repeat(2 + rng.nextInt(2)) { voice(buf, at(t), 0.25f, sr, 0.07f, { u -> 1100f * p * (1f - 0.27f * u) }, floatArrayOf(1f, 0.6f, 0.4f), rough = 0.7f, attack = 0.01f, release = 0.05f); t += 0.32f }
            }
            Animal.PEACOCK -> for (t0 in times(r(12f, 18f))) { // may-AWE, loud and brassy
                for (k in 0 until 2 + rng.nextInt(2)) {
                    val t = t0 + k * 1.2f
                    voice(buf, at(t), 0.35f, sr, 0.07f, { u -> 650f * p * (1f + 0.9f * u) }, floatArrayOf(1f, 0.6f, 0.4f, 0.2f), rough = 0.5f, attack = 0.02f, release = 0.04f)
                    voice(buf, at(t + 0.38f), 0.5f, sr, 0.08f, { u -> 1150f * p * (1f - 0.26f * u) }, floatArrayOf(1f, 0.6f, 0.4f, 0.2f), rough = 0.5f, attack = 0.02f, release = 0.12f)
                }
            }
            Animal.CHICKADEE -> for (t0 in times(r(4f, 8f))) { // fee-bee, two clear whistles
                voice(buf, at(t0), 0.35f, sr, 0.07f, { 2900f * p }, floatArrayOf(1f, 0.05f), attack = 0.03f, release = 0.06f)
                voice(buf, at(t0 + 0.43f), 0.4f, sr, 0.065f, { u -> 2450f * p * (if (u in 0.45f..0.55f) 0.97f else 1f) }, floatArrayOf(1f, 0.05f), attack = 0.03f, release = 0.08f)
            }
            Animal.NIGHTJAR -> for (t0 in times(r(10f, 16f))) { // a long mechanical churring
                val d = r(3f, 6f)
                voice(buf, at(t0), d, sr, 0.05f, { u -> 950f * p * (1f + 0.06f * sin(u * 7f)) }, floatArrayOf(1f, 0.3f), pulse = 32f, attack = 0.3f, release = 0.4f)
            }
            Animal.FROG -> { // a chorus: ribbits now and then through the loop
                val f = 260f * p
                for (t0 in times(r(1.4f, 2.6f))) {
                    voice(buf, at(t0), 0.07f, sr, 0.09f, { f }, floatArrayOf(1f, 0.6f, 0.3f), pulse = 90f, attack = 0.01f, release = 0.02f)
                    voice(buf, at(t0 + 0.1f), 0.09f, sr, 0.09f, { f * 1.08f }, floatArrayOf(1f, 0.6f, 0.3f), pulse = 90f, attack = 0.01f, release = 0.03f)
                }
            }
            Animal.CRICKET -> { // chirping on and off, each on its own pulse
                val f = r(3300f, 4200f) * p; val rate = r(12f, 22f)
                for (t0 in times(r(4f, 7f))) voice(buf, at(t0), r(2f, 4f), sr, 0.045f, { f }, floatArrayOf(1f), pulse = rate, attack = 0.2f, release = 0.2f)
            }
        }
    }

    /**
     * A call: a pitch path over [dur] seconds ([pitch] of the fraction gone), with [harm] harmonic
     * weights, vibrato, roughness (a fast flutter that makes caws and barks hoarse), or a [pulse]
     * that chops the sound into the beats of an insect's song.
     */
    private fun voice(
        buf: FloatArray, at: Int, dur: Float, sr: Int, vol: Float, pitch: (Float) -> Float, harm: FloatArray,
        vibRate: Float = 0f, vibDepth: Float = 0f, rough: Float = 0f, pulse: Float = 0f, attack: Float = 0.01f, release: Float = 0.05f,
    ) {
        if (at < 0) return
        val len = min((dur * sr).toInt(), buf.size - at)
        var ph = 0f
        for (i in 0 until len) {
            val t = i / sr.toFloat(); val u = t / dur
            val f = pitch(u) * (1f + vibDepth * sin(TWO_PI * vibRate * t))
            ph += TWO_PI * f / sr
            if (ph > TWO_PI * 64f) ph -= TWO_PI * 64f
            var v = 0f
            for (h in harm.indices) v += harm[h] * sin(ph * (h + 1))
            if (rough > 0f) v *= 1f - rough * 0.5f * (1f + sin(TWO_PI * 73f * t))
            if (pulse > 0f) v *= if ((t * pulse) % 1f < 0.5f) 1f else 0f
            val env = min(1f, t / attack) * min(1f, max(0f, (dur - t) / release))
            buf[at + i] += v * env * vol
        }
    }


    private fun element(e: Element, buf: FloatArray, rng: Random, sr: Int, env: Environment, breath: Float) {
        fun r(a: Float, b: Float) = a + (b - a) * rng.nextFloat()
        when (e) {
            Element.WIND -> wind(buf, rng, sr, r(0.08f, 0.3f) * breath, r(0.93f, 0.995f), r(4f, 12f))
            Element.RAIN -> rain(buf, rng, sr, r(0.2f, 0.4f) * breath, r(0.3f, 0.7f), r(3f, 7f))
            Element.WAVES -> waves(buf, rng, sr, r(0.6f, 1f), r(4f, 7f), r(0.03f, 0.1f))
            Element.DRIPS -> drips(buf, rng, sr, 4 + (r(2f, 14f) * breath).toInt(), r(700f, 2200f), r(0.4f, 1.6f), r(0.2f, 0.45f))
            Element.ECHO_DRIPS -> { for (echo in 0 until 3) drips(buf, rng, sr, 3, 900f, 1.4f, 0.4f / (1 + echo), delay = echo * r(0.18f, 0.3f)) }
            Element.STREAM -> stream(buf, rng, sr, r(0.12f, 0.3f), r(0.6f, 0.9f))
            Element.RUMBLE -> rumble(buf, rng, sr, r(0.15f, 0.5f) * breath, r(2f, 5f))
            Element.DRONE -> drone(buf, sr, env.root / (if (rng.nextBoolean()) 4 else 2), r(0.1f, 0.22f), r(1.002f, 1.006f), rng.nextBoolean())
            Element.SHIMMER -> shimmer(buf, sr, env.root * (if (rng.nextBoolean()) 2 else 3), r(0.04f, 0.09f), r(3f, 6f))
            Element.CHIMES -> chimes(buf, rng, sr, env, 4 + rng.nextInt(8), r(0.06f, 0.14f))
            Element.BELL -> bell(buf, rng, sr, env.root / (if (rng.nextBoolean()) 2 else 1), 1 + rng.nextInt(2), r(0.12f, 0.25f))
            Element.THUNDER -> thunder(buf, rng, sr, 1 + rng.nextInt(2), r(1.5f, 3f))
            Element.STEAM -> steam(buf, rng, sr, 2 + rng.nextInt(3), r(0.08f, 0.18f))
            Element.FIRE -> fire(buf, rng, sr, r(0.08f, 0.2f), (r(40f, 120f) * breath).toInt())
            Element.CREAKS -> creaks(buf, rng, sr, 2 + rng.nextInt(4), r(0.1f, 0.25f))
            Element.CLOCK -> clock(buf, rng, sr, if (rng.nextBoolean()) 1f else 0.5f, r(0.15f, 0.3f))
        }
    }

    // ------------------------------------------------------------------ voices

    private fun note(buf: FloatArray, at: Int, f: Float, dur: Float, vel: Float, voice: Instrument, sr: Int) {
        val len = min((dur * sr).toInt(), buf.size - at)
        if (len <= 0) return
        for (i in 0 until len) {
            val t = i / sr.toFloat()
            val s = when (voice) {
                Instrument.MARIMBA -> (sin(TWO_PI * f * t) * 0.7f + sin(TWO_PI * f * 4 * t) * 0.18f * exp(-t * 18) + sin(TWO_PI * f * 9.2f * t) * 0.06f * exp(-t * 30)) * exp(-t * 5.5f) * attack(t, 0.004f)
                Instrument.KALIMBA -> (sin(TWO_PI * f * t) * 0.8f + sin(TWO_PI * f * 2.02f * t) * 0.2f * exp(-t * 9) + sin(TWO_PI * f * 7.3f * t) * 0.05f * exp(-t * 40)) * exp(-t * 4f) * attack(t, 0.003f)
                Instrument.MUSICBOX -> (sin(TWO_PI * f * 2 * t) * 0.6f + sin(TWO_PI * f * 6 * t) * 0.25f * exp(-t * 6) + sin(TWO_PI * f * 10.8f * t) * 0.1f * exp(-t * 12)) * exp(-t * 3.2f) * attack(t, 0.002f)
                Instrument.BELL -> (sin(TWO_PI * f * t) * 0.5f + sin(TWO_PI * f * 2f * t) * 0.3f * exp(-t * 1.2f) + sin(TWO_PI * f * 2.76f * t) * 0.25f * exp(-t * 2.2f) + sin(TWO_PI * f * 5.4f * t) * 0.1f * exp(-t * 4f)) * exp(-t * 1.6f) * attack(t, 0.003f)
                Instrument.FLUTE -> (sin(TWO_PI * f * t + 0.15f * sin(TWO_PI * 5.5f * t)) * 0.8f + sin(TWO_PI * f * 2 * t) * 0.12f) * attack(t, 0.06f) * release(t, dur, 0.08f) * 0.8f
                Instrument.PLUCK -> { var v = 0f; for (h in 1..6) v += sin(TWO_PI * f * h * t) / h * exp(-t * (2.5f + 1.8f * h)); v * 0.9f * attack(t, 0.002f) }
                Instrument.ORGAN -> (sin(TWO_PI * f * t) * 0.6f + sin(TWO_PI * f * 2 * t) * 0.3f + sin(TWO_PI * f * 3 * t) * 0.15f + sin(TWO_PI * f * 4 * t) * 0.1f) * (0.9f + 0.1f * sin(TWO_PI * 6f * t)) * attack(t, 0.03f) * release(t, dur, 0.06f) * 0.7f
                Instrument.VIBES -> sin(TWO_PI * f * t) * (0.8f + 0.2f * sin(TWO_PI * 4.5f * t)) * exp(-t * 2.5f) * attack(t, 0.004f) + sin(TWO_PI * f * 4 * t) * 0.1f * exp(-t * 10)
            }
            buf[at + i] += s * vel
        }
    }

    private fun attack(t: Float, a: Float) = min(1f, t / a)
    private fun release(t: Float, dur: Float, r: Float) = min(1f, max(0f, (dur - t) / r))

    /** A soft drum: a sine dropping in pitch. */
    private fun thump(buf: FloatArray, at: Int, f: Float, vol: Float, sr: Int) {
        var ph = 0f
        for (i in 0 until min((0.25f * sr).toInt(), buf.size - at)) {
            val t = i / sr.toFloat(); ph += TWO_PI * f * (1f + 2f * exp(-t * 40)) / sr
            buf[at + i] += sin(ph) * vol * exp(-t * 14) * attack(t, 0.002f)
        }
    }

    /** A soft shaker: a short burst of seeds, kept between about one and four kilohertz. */
    private fun shaker(buf: FloatArray, at: Int, rng: Random, vol: Float, sr: Int) {
        var lo = 0f; var lo2 = 0f; var hp = 0f
        for (i in 0 until min((0.06f * sr).toInt(), buf.size - at)) {
            val x = rng.nextFloat() * 2 - 1
            lo += 0.55f * (x - lo); lo2 += 0.55f * (lo - lo2); hp += 0.25f * (lo2 - hp)
            buf[at + i] += (lo2 - hp) * vol * 1.4f * exp(-i / (0.015f * sr))
        }
    }

    // ------------------------------------------------------------------ surroundings

    /** Filtered noise breathing slowly: [alpha] sets how dark it is, [period] how slowly it gusts. */
    private fun wind(buf: FloatArray, rng: Random, sr: Int, vol: Float, alpha: Float, period: Float) {
        var y = 0f; var z = 0f
        val gain = 3f / (1f - alpha).pow(0.35f) * 0.3f
        for (i in 0 until buf.size) {
            val x = rng.nextFloat() * 2 - 1
            y += (1 - alpha) * (x - y); z += (1 - alpha) * (y - z)
            val t = i / sr.toFloat()
            buf[i] += z * vol * gain * (0.55f + 0.45f * sin(TWO_PI * t / period) * sin(TWO_PI * t / (period * 0.37f) + 1f))
        }
    }

    /** Rain: a soft band of patter around one to three kilohertz, not a hiss, swelling in gusts, with drops ticking on it. */
    private fun rain(buf: FloatArray, rng: Random, sr: Int, vol: Float, bright: Float, period: Float) {
        var lo = 0f; var lo2 = 0f; var hp = 0f
        val a = 0.25f + 0.2f * bright // where the patter's top lies
        for (i in 0 until buf.size) {
            val x = rng.nextFloat() * 2 - 1
            lo += a * (x - lo); lo2 += a * (lo - lo2); hp += 0.06f * (lo2 - hp)
            buf[i] += (lo2 - hp) * vol * 1.6f * (0.7f + 0.3f * sin(TWO_PI * i / sr.toFloat() / period))
        }
        for (k in 0 until (60 * bright).toInt() + 20) { // drops on leaves and stone
            val at = rng.nextInt(buf.size); val f = 900f + 1400f * rng.nextFloat()
            for (j in 0 until min((0.03f * sr).toInt(), buf.size - at)) buf[at + j] += sin(TWO_PI * f * j / sr) * exp(-j / (0.006f * sr)) * vol * 0.6f
        }
    }

    private fun waves(buf: FloatArray, rng: Random, sr: Int, vol: Float, period: Float, alpha: Float) {
        var y = 0f
        for (i in 0 until buf.size) {
            y += alpha * ((rng.nextFloat() * 2 - 1) - y)
            val s = 0.5f + 0.5f * sin(TWO_PI * (i / sr.toFloat()) / period)
            buf[i] += y * (0.15f + 0.85f * s * s) * vol
        }
    }

    /**
     * A brook: a low murmur of water (noise kept under about a kilohertz, its top end gone) and on
     * it the bubbles, little rising blips, many of them, that make water sound like water.
     */
    private fun stream(buf: FloatArray, rng: Random, sr: Int, vol: Float, alpha: Float) {
        var lo = 0f; var lo2 = 0f; var hp = 0f
        val a = 0.12f + 0.12f * (alpha - 0.6f) / 0.3f
        for (i in 0 until buf.size) {
            val x = rng.nextFloat() * 2 - 1
            lo += a * (x - lo); lo2 += a * (lo - lo2); hp += 0.015f * (lo2 - hp)
            val t = i / sr.toFloat()
            buf[i] += (lo2 - hp) * vol * 2.2f * (0.8f + 0.2f * sin(TWO_PI * t * 1.7f) * sin(TWO_PI * t * 0.23f))
        }
        for (k in 0 until 140) {
            val at = rng.nextInt(buf.size); val f0 = 350f + 650f * rng.nextFloat(); val len = ((0.02f + 0.04f * rng.nextFloat()) * sr).toInt()
            var ph = 0f
            for (j in 0 until min(len, buf.size - at)) {
                val u = j / len.toFloat(); ph += TWO_PI * f0 * (1f + 0.8f * u) / sr
                buf[at + j] += sin(ph) * sin(u * PI.toFloat()) * vol * 0.5f
            }
        }
    }

    /** Low brown rumble, slowly heaving. */
    private fun rumble(buf: FloatArray, rng: Random, sr: Int, vol: Float, period: Float) {
        var y = 0f; var z = 0f
        for (i in 0 until buf.size) {
            y += 0.004f * ((rng.nextFloat() * 2 - 1) - y); z += 0.02f * (y - z)
            buf[i] += z * vol * 40f * (0.7f + 0.3f * sin(TWO_PI * i / sr.toFloat() / period))
        }
    }

    private fun drone(buf: FloatArray, sr: Int, f: Float, vol: Float, detune: Float, fifth: Boolean) {
        for (i in 0 until buf.size) {
            val t = i / sr.toFloat()
            buf[i] += (sin(TWO_PI * f * t) + 0.4f * sin(TWO_PI * f * detune * t) + (if (fifth) 0.4f * sin(TWO_PI * f * 1.5f * t) else 0.3f * sin(TWO_PI * f * 2f * t))) * vol
        }
    }

    private fun shimmer(buf: FloatArray, sr: Int, f: Float, vol: Float, period: Float) {
        for (i in 0 until buf.size) {
            val t = i / sr.toFloat()
            buf[i] += (sin(TWO_PI * f * t) + sin(TWO_PI * f * 1.004f * t) + 0.6f * sin(TWO_PI * f * 1.5f * t + sin(t * 0.7f))) * vol * (0.6f + 0.4f * sin(TWO_PI * t / period))
        }
    }

    /** Wind chimes: high bell pings on the board's scale at random moments. */
    private fun chimes(buf: FloatArray, rng: Random, sr: Int, env: Environment, count: Int, vol: Float) {
        for (k in 0 until count) {
            val at = (rng.nextFloat() * AMB * sr).toInt()
            val f = env.root * 4 * env.scale[rng.nextInt(env.scale.size)]
            note(buf, at, f, 1.6f, vol, Instrument.BELL, sr)
        }
    }

    /** A low bell, far away, once or twice. */
    private fun bell(buf: FloatArray, rng: Random, sr: Int, f: Float, count: Int, vol: Float) {
        for (k in 0 until count) note(buf, (rng.nextFloat() * AMB * sr).toInt(), f, 3.5f, vol, Instrument.BELL, sr)
    }

    /** Short pings at random moments, [f] Hz, each ringing for [ring] seconds. */
    private fun drips(buf: FloatArray, rng: Random, sr: Int, count: Int, f: Float, ring: Float, vol: Float, delay: Float = 0f) {
        for (k in 0 until count) {
            val at = ((rng.nextFloat() * AMB + delay) * sr).toInt(); val ff = f * (0.8f + 0.4f * rng.nextFloat())
            for (i in 0 until min((ring * sr).toInt(), buf.size - at)) { val t = i / sr.toFloat(); buf[at + i] += sin(TWO_PI * ff * t) * exp(-t * 4f / ring) * vol * min(1f, t * 400f) }
        }
    }

    private fun thunder(buf: FloatArray, rng: Random, sr: Int, count: Int, length: Float) {
        for (k in 0 until count) {
            val at = (rng.nextFloat() * (AMB - 3f) * sr).toInt(); var r = 0f
            for (i in 0 until min((length * sr).toInt(), buf.size - at)) { r += 0.02f * ((rng.nextFloat() * 2 - 1) - r); buf[at + i] += r * 2.5f * min(1f, i / (0.4f * sr)) * exp(-i / (length * 0.4f * sr)) }
        }
    }

    private fun steam(buf: FloatArray, rng: Random, sr: Int, count: Int, vol: Float) {
        for (k in 0 until count) {
            val at = (rng.nextFloat() * (AMB - 1f) * sr).toInt(); val len = ((0.6f + rng.nextFloat()) * sr).toInt(); var y = 0f
            var z = 0f
            for (i in 0 until min(len, buf.size - at)) { val x = rng.nextFloat() * 2 - 1; y += 0.35f * (x - y); z += 0.35f * (y - z); buf[at + i] += (y - z * 0.6f) * vol * 1.3f * sin(min(1f, i / len.toFloat()) * PI.toFloat()) }
        }
    }

    private fun fire(buf: FloatArray, rng: Random, sr: Int, vol: Float, crackles: Int) {
        var y = 0f
        for (i in 0 until buf.size) { y += 0.05f * ((rng.nextFloat() * 2 - 1) - y); buf[i] += y * vol * 1.5f }
        for (k in 0 until crackles) {
            val at = (rng.nextFloat() * AMB * sr).toInt()
            for (i in 0 until min((0.012f * sr).toInt(), buf.size - at)) buf[at + i] += (rng.nextFloat() * 2 - 1) * 0.35f * exp(-i / (0.003f * sr))
        }
    }

    private fun creaks(buf: FloatArray, rng: Random, sr: Int, count: Int, vol: Float) {
        for (k in 0 until count) {
            val at = (rng.nextFloat() * AMB * sr).toInt(); val len = ((0.2f + 0.3f * rng.nextFloat()) * sr).toInt(); val f0 = 180f + 200f * rng.nextFloat()
            var ph = 0f
            for (i in 0 until min(len, buf.size - at)) { val u = i / len.toFloat(); ph += TWO_PI * f0 * (1f + 0.6f * u + 0.1f * sin(u * 60f)) / sr; buf[at + i] += sin(ph) * (0.6f + 0.4f * (rng.nextFloat() * 2 - 1)) * vol * sin(u * PI.toFloat()) }
        }
    }

    private fun clock(buf: FloatArray, rng: Random, sr: Int, every: Float, vol: Float) {
        var t0 = 0f
        while (t0 < AMB) {
            val at = (t0 * sr).toInt()
            for (i in 0 until min(400, buf.size - at)) buf[at + i] += (rng.nextFloat() * 2 - 1) * vol * exp(-i / 60f)
            t0 += every
        }
    }

    /** Equal-power stereo gains for a place from -1 (left) to 1 (right). */
    private fun panGains(pan: Float): Pair<Float, Float> {
        val a = (pan.coerceIn(-1f, 1f) + 1f) * (PI.toFloat() / 4f)
        return kotlin.math.cos(a) to sin(a)
    }

    /**
     * A small room: four damped combs and two allpasses per side (after Freeverb), the sides tuned
     * apart for width, fed by the mid of both. The wet signal is scaled to [wet] of the dry's level.
     */
    private fun reverb(l: FloatArray, r: FloatArray, sr: Int, wet: Float, room: Float) {
        val k = sr / 44100f
        fun side(spread: Int): FloatArray {
            val combs = intArrayOf(1116, 1188, 1277, 1356).map { ((it + spread) * k).toInt().coerceAtLeast(1) }
            val alls = intArrayOf(556, 441).map { ((it + spread) * k).toInt().coerceAtLeast(1) }
            val cb = combs.map { FloatArray(it) }; val ci = IntArray(4); val store = FloatArray(4)
            val ab = alls.map { FloatArray(it) }; val ai = IntArray(2)
            val out = FloatArray(l.size); val damp = 0.3f
            for (i in l.indices) {
                val input = (l[i] + r[i]) * 0.5f
                var acc = 0f
                for (c in 0 until 4) {
                    val b = cb[c]; val o = b[ci[c]]
                    store[c] = o * (1 - damp) + store[c] * damp
                    b[ci[c]] = input + store[c] * room
                    ci[c] = (ci[c] + 1) % b.size
                    acc += o
                }
                for (a in 0 until 2) {
                    val b = ab[a]; val o = b[ai[a]]
                    val y = -acc + o
                    b[ai[a]] = acc + o * 0.5f
                    ai[a] = (ai[a] + 1) % b.size
                    acc = y
                }
                out[i] = acc
            }
            return out
        }
        val wl = side(0); val wr = side(23)
        fun rms(x: FloatArray): Double { var s = 0.0; for (v in x) s += v * v; return kotlin.math.sqrt(s / x.size) }
        val dry = (rms(l) + rms(r)) / 2; val w = (rms(wl) + rms(wr)) / 2
        if (w < 1e-9) return
        val g = (wet * dry / w).toFloat()
        for (i in l.indices) { l[i] += wl[i] * g; r[i] += wr[i] * g }
    }

    /**
     * Folds the tails beyond [n] onto the starts, sets both sides together to the average level [rms]
     * without passing [peak], and packs them interleaved to 16 bits: into [into] when given (from
     * sample [from] on, the samples before it left as they are), else into a new array.
     */
    private fun loopStereo(l: FloatArray, r: FloatArray, n: Int, peak: Float, rms: Float, into: ShortArray? = null, from: Int = 0): ShortArray {
        for (i in n until l.size) { l[i - n] += l[i]; r[i - n] += r[i] }
        var e = 0.0
        for (i in 0 until n) e += l[i] * l[i] + r[i] * r[i]
        val level = kotlin.math.sqrt(e / (2 * n)).toFloat().coerceAtLeast(1e-6f)
        // To the target average level; the rare sharp peak (a thunderclap, a tick) is rounded off
        // gently near [peak] rather than holding the whole loop down
        val k = rms / level
        fun soft(v: Float): Float { val a = abs(v); return if (a < peak * 0.7f) v else kotlin.math.sign(v) * (peak * 0.7f + peak * 0.3f * kotlin.math.tanh((a - peak * 0.7f) / (peak * 0.3f))) }
        val out = into ?: ShortArray(2 * n)
        for (i in from / 2 until minOf(n, out.size / 2)) {
            out[2 * i] = (soft(l[i] * k) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
            out[2 * i + 1] = (soft(r[i] * k) * 32767f).toInt().coerceIn(-32767, 32767).toShort()
        }
        return out
    }

    /** Left and right of an interleaved loop, averaged: for comparing loops. */
    fun mono(stereo: ShortArray): ShortArray = ShortArray(stereo.size / 2) { ((stereo[2 * it] + stereo[2 * it + 1]) / 2).toShort() }

    /**
     * A small fingerprint of a loop for comparing boards: the loudness curve over 24 windows, the
     * brightness (zero crossings) over 8, and the weight of the twelve pitch classes, all normalised.
     * Two boards whose fingerprints lie close sound alike.
     */
    fun fingerprint(pcm: ShortArray, sr: Int): FloatArray {
        val out = FloatArray(44)
        val w = pcm.size / 24
        var maxRms = 1e-6f
        for (k in 0 until 24) { var s = 0.0; for (i in k * w until (k + 1) * w) { val v = pcm[i] / 32767.0; s += v * v }; out[k] = kotlin.math.sqrt(s / w).toFloat(); maxRms = max(maxRms, out[k]) }
        for (k in 0 until 24) out[k] /= maxRms
        val w8 = pcm.size / 8
        for (k in 0 until 8) { var z = 0; for (i in k * w8 + 1 until (k + 1) * w8) if ((pcm[i] >= 0) != (pcm[i - 1] >= 0)) z++; out[24 + k] = z / w8.toFloat() * 4f }
        // Pitch classes by Goertzel over three octaves, on the first four seconds
        val n = min(pcm.size, 4 * sr)
        var maxC = 1e-9
        val chroma = DoubleArray(12)
        for (pc in 0 until 12) for (oct in 0 until 3) {
            val f = 196.0 * 2.0.pow(pc / 12.0 + oct)
            val coeff = 2 * kotlin.math.cos(2 * PI * f / sr)
            var s1 = 0.0; var s2 = 0.0
            for (i in 0 until n) { val s0 = pcm[i] / 32767.0 + coeff * s1 - s2; s2 = s1; s1 = s0 }
            chroma[pc] += s1 * s1 + s2 * s2 - coeff * s1 * s2
        }
        for (pc in 0 until 12) maxC = max(maxC, chroma[pc])
        for (pc in 0 until 12) out[32 + pc] = (chroma[pc] / maxC).toFloat()
        return out
    }
}
