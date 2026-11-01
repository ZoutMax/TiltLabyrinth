package com.megapro.labyrinth.audio

import com.megapro.labyrinth.game.Environment
import com.megapro.labyrinth.game.Levels
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

class SynthTest {
    private val sr = 22050

    @Test
    fun everyBoardRendersASoundingLoopOfTheRightLength() {
        for (level in listOf(0, 1, 7, 20, 35, 48, 60, 73, 88, 95, Levels.COUNT)) {
            val env = Environment.of(level)
            val t0 = System.nanoTime()
            val music = Synth.music(env, sr); val amb = Synth.ambience(env, sr)
            val ms = (System.nanoTime() - t0) / 1_000_000
            if (level == 1) assertEquals("the opening tune is 92 bars at 120: about three minutes, in stereo", 2 * (92 * 4 * 0.5f * sr).toInt(), music.size)
            else if (level == Levels.COUNT) assertTrue("the victory theme is 80 bars at 108: about three minutes, in stereo", abs(2 * (80 * 4 * 60.0 / 108.0 * sr) - music.size) < 8)
            else assertEquals("music loop is eight bars, in stereo", 2 * (8 * 4 * 60f / env.bpm * sr).toInt(), music.size)
            assertEquals("ambience loop is twenty seconds, in stereo", 2 * 20 * sr, amb.size)
            assertTrue("music sounds (board $level)", music.count { abs(it.toInt()) > 1000 } > music.size / 20)
            assertTrue("ambience sounds (board $level)", amb.count { abs(it.toInt()) > 300 } > amb.size / 50)
            assertTrue("no clipping", music.none { it.toInt() == 32767 || it.toInt() == -32767 } && amb.none { it.toInt() == 32767 || it.toInt() == -32767 })
            // The written three-minute pieces are built ahead and kept on the device; the rest are built as a board comes up
            assertTrue("renders quickly enough for a board change ($ms ms)", ms < (if (level == 1 || level == Levels.COUNT) 15000 else 4000))
        }
    }

    @Test
    fun theTunesComeHomeAndTheSoundIsWide() {
        for (level in listOf(3, 42, 77)) {
            val env = Environment.of(level)
            val pcm = Synth.music(env, sr)
            // Stereo: left and right differ (pad, pan, room), but not wildly
            var diff = 0.0; var sum = 0.0
            for (i in 0 until pcm.size / 2) { val a = pcm[2 * i].toDouble(); val b = pcm[2 * i + 1].toDouble(); diff += (a - b) * (a - b); sum += a * a + b * b }
            val width = kotlin.math.sqrt(diff / sum)
            assertTrue("board $level is stereo ($width)", width > 0.05 && width < 1.2)
            // The last bar rings on the home note: its pitch class leads the last bar's chroma
            val frames = pcm.size / 2; val bar = frames / 8
            val last = Synth.mono(pcm).copyOfRange(frames - bar, frames)
            val chroma = Synth.fingerprint(last, sr).copyOfRange(32, 44)
            val homeClass = Math.floorMod(Math.round(12 * kotlin.math.ln(env.root / 196.0) / kotlin.math.ln(2.0)).toInt(), 12)
            val rank = chroma.sortedDescending().indexOf(chroma[homeClass])
            assertTrue("board $level ends at home (home class ranks $rank)", rank <= 2)
        }
    }

    @Test
    fun noBoardHisses() {
        // The top of the spectrum against the whole, by the second difference of the left channel.
        // The hissing stream that once filled board 4 measured 4.5; water, rain, birds and bells
        // as they are now stay under 0.7.
        fun hiss(pcm: ShortArray): Double {
            var d = 0.0; var e = 0.0
            for (i in 2 until pcm.size / 2) {
                val x0 = pcm[2 * i].toDouble(); val x1 = pcm[2 * i - 2].toDouble(); val x2 = pcm[2 * i - 4].toDouble()
                val s = x0 - 2 * x1 + x2; d += s * s; e += x0 * x0
            }
            return d / (e + 1e-9)
        }
        for (level in 1..Levels.COUNT) {
            val env = Environment.of(level)
            assertTrue("board $level ambience hisses", hiss(Synth.ambience(env, sr)) < 1.5)
            assertTrue("board $level tune hisses", hiss(Synth.music(env, sr)) < 1.5)
        }
    }

    @Test
    fun eachWorldHasItsOwnAnimalsInManyMixes() {
        val worlds = com.megapro.labyrinth.game.Theme.values().filter { it != com.megapro.labyrinth.game.Theme.WORKSHOP }
        val all = HashSet<Synth.Animal>()
        for (w in worlds) {
            val pool = Synth.fauna(w)
            val boards = (1..Levels.COUNT).filter { Environment.of(it).theme == w }
            val mixes = boards.map { Synth.ambience(Environment.of(it), 4000); Synth.lastFauna }
            for ((b, m) in boards.zip(mixes)) {
                assertTrue("board $b has at least three kinds of animal, or all its world has", m.size >= minOf(3, pool.size))
                assertTrue("board $b only has animals of its world", pool.containsAll(m))
            }
            val used = mixes.flatten().toSet()
            assertTrue("$w: most of its animals are heard somewhere (${used.size} of ${pool.size})", used.size >= pool.size - 1)
            assertTrue("$w: its boards mix them differently", mixes.map { it.toSet() }.toSet().size >= minOf(5, boards.size))
            all += used
        }
        assertTrue("across the worlds, many species (${all.size})", all.size >= 25)
    }

    @Test
    fun theOpeningTuneIsWrittenOutAndComesHome() {
        val pcm = Synth.music(Environment.of(1), sr)
        assertEquals("about three minutes long", 184f, pcm.size / 2f / sr, 0.01f)
        val notes = Synth.lastScore.filter { it.first >= 0 }
        assertTrue("a full melody (${notes.size} notes)", notes.size > 150)
        assertEquals("it ends on the home B-flat", 10, Math.floorMod(notes.maxByOrNull { it.first }!!.second, 12))
        // Every section is heard: sound in every bar of the three minutes
        val frames = pcm.size / 2; val bar = frames / 92
        for (k in 0 until 92) {
            var e = 0.0; for (i in k * bar until (k + 1) * bar) { val v = pcm[2 * i].toDouble(); e += v * v }
            assertTrue("bar ${k + 1} sounds", kotlin.math.sqrt(e / bar) > 300)
        }
    }

    @Test
    fun theLastBoardPlaysAThreeMinuteVictoryTheme() {
        val pcm = Synth.music(Environment.of(Levels.COUNT), sr)
        val seconds = pcm.size / 2f / sr
        assertTrue("about three minutes ($seconds s)", seconds in 175f..190f)
        val notes = Synth.lastScore.filter { it.first >= 0 }
        assertTrue("a full melody (${notes.size} notes)", notes.size > 200)
        assertEquals("it comes home to D, so the loop lands in key", 2, Math.floorMod(notes.maxByOrNull { it.first }!!.second, 12))
    }

    @Test
    fun theWrittenPiecesLoopWithoutAJoin() {
        for (level in listOf(1, Levels.COUNT)) {
            val pcm = Synth.music(Environment.of(level), sr)
            val frames = pcm.size / 2
            // Across the loop point the sound runs on: the step from the last frame to the first is no
            // bigger than the usual step between neighbouring frames
            var steps = 0.0; for (i in 1 until frames) steps += abs(pcm[2 * i] - pcm[2 * i - 2])
            val usual = steps / (frames - 1)
            val join = abs(pcm[0] - pcm[2 * (frames - 1)]).toDouble()
            assertTrue("board $level loops without a click (join $join, usual step $usual)", join < usual * 8 + 200)
            // and the last bar and the first are of a kind: neither silent where the other is loud
            val bar = (frames / (if (level == 1) 92 else 80))
            fun rms(from: Int): Double { var e = 0.0; for (i in from until from + bar) { val v = pcm[2 * i].toDouble(); e += v * v }; return kotlin.math.sqrt(e / bar) }
            val first = rms(0); val last = rms(frames - bar)
            assertTrue("board $level: the end and the start match in level ($last vs $first)", first / last in 0.3..3.0)
        }
    }

    @Test
    fun loopsAreTheSameForEveryone() {
        assertArrayEquals(Synth.music(Environment.of(13), sr), Synth.music(Environment.of(13), sr))
        assertArrayEquals(Synth.ambience(Environment.of(13), sr), Synth.ambience(Environment.of(13), sr))
    }

    @Test
    fun noTwoBoardsShareATuneNoteForNote() {
        // A board's tune as its key, chord progression and melody (onset and pitch of every note)
        val scores = (1..Levels.COUNT).map { Synth.music(Environment.of(it), 4000); Synth.lastScore }
        assertEquals("every one of the hundred tunes is its own", Levels.COUNT, scores.toSet().size)
        // and no two share even their opening motif in the same key
        val openings = scores.map { s -> s.filter { it.first >= 0 }.sortedBy { it.first }.take(8).map { it.second } } // the first eight notes of each melody
        assertEquals(Levels.COUNT, openings.toSet().size)
    }

    @Test
    fun noTwoBoardsSoundAlike() {
        // Rendered at a low rate to keep the test quick; fingerprints compare loudness curves and brightness
        val low = 8000
        val music = (1..Levels.COUNT).map { Synth.fingerprint(Synth.mono(Synth.music(Environment.of(it), low)), low) }
        val amb = (1..Levels.COUNT).map { Synth.fingerprint(Synth.mono(Synth.ambience(Environment.of(it), low)), low) }
        fun dist(a: FloatArray, b: FloatArray): Float { var s = 0f; for (i in a.indices) { val d = a[i] - b[i]; s += d * d }; return sqrt(s) }
        var minMusic = Float.MAX_VALUE; var minAmb = Float.MAX_VALUE; var pairM = ""; var pairA = ""
        for (i in music.indices) for (j in i + 1 until music.size) {
            val dm = dist(music[i], music[j]); if (dm < minMusic) { minMusic = dm; pairM = "${i + 1}/${j + 1}" }
            val da = dist(amb[i], amb[j]); if (da < minAmb) { minAmb = da; pairA = "${i + 1}/${j + 1}" }
        }
        println("closest tunes $pairM at $minMusic; closest soundscapes $pairA at $minAmb")
        assertTrue("closest tunes $pairM are too alike ($minMusic)", minMusic > 0.45f)
        assertTrue("closest soundscapes $pairA are too alike ($minAmb)", minAmb > 0.35f)
    }
}
