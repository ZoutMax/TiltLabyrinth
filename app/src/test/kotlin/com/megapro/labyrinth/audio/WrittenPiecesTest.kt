package com.megapro.labyrinth.audio

import com.megapro.labyrinth.game.Environment
import com.megapro.labyrinth.game.Levels
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * The two written pieces ship in the app as recordings made by the game's own synthesizer
 * (tools/render_written.sh). These tests make sure the recordings are never stale.
 */
class WrittenPiecesTest {
    private val pieces = listOf("opening" to 1, "victory" to Levels.COUNT)

    @Test
    fun theShippedRecordingsAreMadeFromTheCurrentScores() {
        val record = File("../tools/written_scores.txt")
        assertTrue("no record of the shipped recordings: run tools/render_written.sh", record.exists())
        val made = record.readLines().filter { '=' in it }.associate { it.substringBefore('=') to it.substringAfter('=') }
        for ((name, level) in pieces) {
            assertEquals("the $name changed since it was recorded: run tools/render_written.sh", Synth.writtenHash(level), made[name])
            val ogg = File("src/main/res/raw/$name.ogg")
            assertTrue("$name.ogg is missing: run tools/render_written.sh", ogg.exists())
            assertTrue("$name.ogg has an unlikely size (${ogg.length()} bytes)", ogg.length() in 300_000L..4_000_000L)
        }
    }

    /** Only when tools/render_written.sh asks for it: renders both pieces to WAV for encoding. */
    /** A device that stopped decoding partway synthesizes only the rest, exactly as it was recorded, after what already plays. */
    @Test
    fun theFallbackFillsInOnlyWhatIsMissing() {
        val sr = 22050
        val whole = Synth.music(Environment.of(1), sr)
        assertEquals(Synth.writtenFrames(1, sr) * 2, whole.size)
        val out = ShortArray(whole.size) { 7 }
        val from = 2 * sr * 10 // ten seconds already decoded and playing
        Synth.writtenInto(1, sr, out, from)
        assertTrue("samples already playing were overwritten", (0 until from).all { out[it] == 7.toShort() })
        assertArrayEquals(whole.copyOfRange(from, whole.size), out.copyOfRange(from, out.size))
    }

    /** Whoever waits for samples is woken when they arrive, and never waits forever for samples that cannot come. */
    @Test
    fun waitingForSamplesEndsEitherWay() {
        val arrives = Pcm(ShortArray(100))
        Thread { Thread.sleep(50); arrives.fill(60) }.start()
        assertTrue(arrives.await(50))
        assertEquals(60, arrives.filled)
        val fails = Pcm(ShortArray(100))
        Thread { Thread.sleep(50); fails.fill(10); fails.fail() }.start()
        assertFalse(fails.await(50))
        val dropped = Pcm(ShortArray(100))
        Thread { Thread.sleep(50); dropped.drop() }.start()
        assertFalse(dropped.await(50))
        assertTrue(Pcm.whole(ShortArray(100)).await(100))
    }

    @Test
    fun render() {
        val out = System.getProperty("render.out") ?: ""
        assumeTrue(out.isNotEmpty())
        val dir = File(out).apply { mkdirs() }
        val sr = 22050
        val lines = ArrayList<String>()
        for ((name, level) in pieces) {
            val pcm = Synth.music(Environment.of(level), sr)
            assertEquals(Synth.writtenFrames(level, sr) * 2, pcm.size)
            val b = java.nio.ByteBuffer.allocate(44 + pcm.size * 2).order(java.nio.ByteOrder.LITTLE_ENDIAN)
            b.put("RIFF".toByteArray()).putInt(36 + pcm.size * 2).put("WAVEfmt ".toByteArray()).putInt(16).putShort(1).putShort(2)
                .putInt(sr).putInt(sr * 4).putShort(4).putShort(16).put("data".toByteArray()).putInt(pcm.size * 2)
            for (v in pcm) b.putShort(v)
            File(dir, "$name.wav").writeBytes(b.array())
            lines.add("$name=${Synth.writtenHash(level)}")
        }
        File(dir, "written_scores.txt").writeText(
            "# Fingerprints of the scores the shipped recordings were made from (tools/render_written.sh).\n" + lines.joinToString("\n") + "\n"
        )
    }
}
