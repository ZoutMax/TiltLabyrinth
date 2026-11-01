package com.megapro.labyrinth

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.megapro.labyrinth.audio.Recording
import com.megapro.labyrinth.audio.Synth
import com.megapro.labyrinth.game.Environment
import com.megapro.labyrinth.game.Levels
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.abs
import kotlin.math.sqrt

/** The two written pieces, decoded on a real device from the recordings shipped in the app. */
@RunWith(AndroidJUnit4::class)
class RecordingTest {
  /** The game's own music work left from tests that opened the game (decoding, synthesizing the
   *  next board) would share the processor with the measurement: wait until it has finished. */
  @Before fun quietFirst() {
    val busy = { Thread.getAllStackTraces().keys.any { it.isAlive && it.name.startsWith("music-") && it.name != "music-loop" } }
    val until = System.currentTimeMillis() + 60_000
    while (busy() && System.currentTimeMillis() < until) Thread.sleep(200)
  }

  private val context = InstrumentationRegistry.getInstrumentation().targetContext
  private val sr = 22050

  @Test
  fun bothRecordingsDecodeToTheExactLoopQuickly() {
    for ((res, level) in listOf(R.raw.opening to 1, R.raw.victory to Levels.COUNT)) {
      val frames = Synth.writtenFrames(level, sr)
      val pcm = ShortArray(frames * 2)
      val t0 = System.nanoTime()
      var firstFive = -1L
      // How the player uses it: it starts once the first seconds are in, and must stay ahead of the playhead
      var behind = 0L
      val ok = Recording.decode(context, res, sr, frames, pcm) { n ->
        val t = (System.nanoTime() - t0) / 1_000_000
        if (firstFive < 0 && n >= sr * 2 * 5) firstFive = t
        if (firstFive >= 0) { val playedSamples = (t - firstFive) * sr * 2 / 1000 + sr * 2 * 5; if (playedSamples > n) behind = maxOf(behind, playedSamples - n) }
      }
      val ms = (System.nanoTime() - t0) / 1_000_000
      android.util.Log.i("RecordingTest", "board $level: first 5 s in $firstFive ms, all ${frames / sr} s in $ms ms")
      assertTrue("board $level's recording decodes on this device", ok)
      assertEquals("exactly the loop's length", frames * 2, pcm.size)
      // The game starts the music once three seconds are in: phones and tablets have five within half a
      // second, small CI emulators within about two; staying ahead of the playhead is checked strictly below
      assertTrue("the first five seconds are ready within a moment ($firstFive ms)", firstFive in 0..3000)
      assertTrue("decoding stays ahead of the music ($behind samples behind)", behind == 0L)
      // Sound in every five seconds of it
      val step = sr * 5
      for (s in 0 until frames - step step step) {
        var e = 0.0; for (i in s until s + step) { val v = pcm[2 * i].toDouble(); e += v * v }
        assertTrue("board $level sounds at ${s / sr} s", sqrt(e / step) > 300)
      }
      // No click where the loop comes round
      var steps = 0.0; for (i in 1 until frames) steps += abs(pcm[2 * i] - pcm[2 * i - 2])
      val join = abs(pcm[0] - pcm[2 * (frames - 1)]).toDouble()
      assertTrue("board $level loops without a click ($join)", join < steps / (frames - 1) * 8 + 300)
    }
  }

  @Test
  fun theRecordingLinesUpWithTheSynthesizerSampleForSample() {
    val frames = Synth.writtenFrames(1, sr)
    val recorded = ShortArray(frames * 2)
    assertTrue(Recording.decode(context, R.raw.opening, sr, frames, recorded))
    val made = Synth.music(Environment.of(1), sr)
    // A stretch of the theme, 20 s in: the best match must be at no shift at all, and close
    val from = sr * 20; val len = sr / 2
    var bestLag = Int.MAX_VALUE; var best = -1.0
    for (lag in -400..400) {
      var xy = 0.0; var xx = 0.0; var yy = 0.0
      for (i in from until from + len) { val x = made[2 * i].toDouble(); val y = recorded[2 * (i + lag)].toDouble(); xy += x * y; xx += x * x; yy += y * y }
      val r = xy / sqrt(xx * yy)
      if (r > best) { best = r; bestLag = lag }
    }
    assertEquals("the recording is not shifted", 0, bestLag)
    assertTrue("the recording matches what the synthesizer makes ($best)", best > 0.95)
  }
}
