package com.megapro.labyrinth.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import com.megapro.labyrinth.R
import com.megapro.labyrinth.game.Environment
import com.megapro.labyrinth.game.Levels

/**
 * The music bed and the ambience of the current board. Each board's loops are rendered by [Synth]
 * on a background thread the first time the board comes up and kept while it is the current board;
 * they play on two looping AudioTracks. [muted] silences both, leaving the game's own sounds alone.
 */
class Music(context: android.content.Context) {
  private val app = context.applicationContext

  init {
    // Earlier versions kept the written pieces as 16 MB files: they are not needed any more
    Thread({ app.filesDir.listFiles { _, name -> name.startsWith("written-") || name.startsWith("opening-") }?.forEach { it.delete() } }, "music-tidy")
      .apply { isDaemon = true; priority = Thread.MIN_PRIORITY }.start()
  }
  private val sr = 22050
  private val main = Handler(Looper.getMainLooper())
  @Volatile private var level = -1
  /** Set once the screen is gone: late results are ignored and nothing new is built. */
  @Volatile private var dead = false
  private var rendered: Pair<Pcm, Pcm>? = null
  private var tracks: List<Loop> = emptyList()
  private var playing = false
  private var rendering = false
  /** Set while the game is paused: the loops wait, and pick up where they were on resume. */
  private var held = false
  @Volatile private var generation = 0
  var muted = false
    set(value) { field = value; applyVolume() }

  /** The board now being played: its loops are rendered and, if the music is running, started.
   *  The test board, 0, has no music at all; every other board, in a run or in free play, has its own. */
  fun board(n: Int) {
    if (n == level) return
    level = n; rendered.drop(); rendered = null; rebuilds = 0
    release()
    if (level <= 0) { generation++; rendering = false; return } // the test board is silent: a build still running for another board is dropped
    val ready = ahead
    if (n == aheadLevel && ready != null) { // built ahead while the last board was played: no silent start
      generation++; rendering = false
      rendered = ready; ahead = null; aheadLevel = -1
      if (playing) build()
      prepare(n + 1)
    } else if (n == aheadLevel) { // still being built ahead: wait for that build rather than start a second, and hurry it
      generation++; rendering = true
      builderThread?.priority = Thread.NORM_PRIORITY
    } else {
      ahead.drop(); ahead = null; aheadLevel = -1 // built for a board that is not coming: let it go
      render()
    }
  }

  fun start() {
    playing = true
    if (tracks.isEmpty()) { if (rendered != null) build() else if (level > 0 && !rendering) render() } // a build under way plays when it is done
  }

  fun stop() { playing = false; release() }

  /** The screen is gone for good: stop the builder thread too, so nothing of this player is kept. */
  fun shutdown() { dead = true; generation++; aheadLevel = -1; stop(); builder.shutdownNow(); rendered.drop(); rendered = null; ahead.drop(); ahead = null }

  /** Loops no longer wanted: a written piece still being decoded or synthesized for them stops. */
  private fun Pair<Pcm, Pcm>?.drop() { this?.first?.drop() }

  private var lastRebuild = 0L
  private var rebuilds = 0

  /** A loop's audio output died (the audio system restarted, a headset came or went): build the loops again, at most every few seconds. */
  private fun lost(loop: Any) {
    if (loop !in tracks) return
    release()
    val now = System.currentTimeMillis()
    if (!playing || rendered == null || rebuilds >= 3) return // an output that keeps dying: give up on music for this board
    rebuilds++
    if (now - lastRebuild > 3000) { lastRebuild = now; build() }
    else main.postDelayed({ if (!dead && tracks.isEmpty() && playing && rendered != null) { lastRebuild = System.currentTimeMillis(); build() } }, 3000) // failed again so soon: try once more in a moment
  }

  /** The game paused or resumed. A mute set by the player is kept either way. */
  fun hold(on: Boolean) {
    if (on == held) return
    held = on
    for (t in tracks) if (on) t.pause() else t.play()
  }

  /**
   * All building happens on this one thread, one board at a time: a board passed by before its
   * turn is skipped, so rolling through the boards never piles up builds (the written pieces take
   * a lot of memory while they are made). A build for the board being played runs at normal
   * priority, one for the board after it in the background.
   */
  @Volatile private var builderThread: Thread? = null
  private val builder = java.util.concurrent.Executors.newSingleThreadExecutor { r -> Thread(r, "music-build").also { it.isDaemon = true; builderThread = it } } // a daemon: it never outlives the app

  private fun render() {
    if (dead) return
    val gen = ++generation; val env = Environment.of(level)
    rendering = true
    builder.execute {
      if (gen != generation) return@execute // a later board was asked for meanwhile
      Thread.currentThread().priority = Thread.NORM_PRIORITY
      val built = loops(env)
      main.post {
        if (dead || gen != generation) { built.drop(); return@post }
        rendering = false
        if (built == null) return@post
        rendered = built; if (playing) build(); prepare(env.level + 1)
      }
    }
  }

  /** The next board's music, built in the background while this one plays. */
  private var aheadLevel = -1
  private var ahead: Pair<Pcm, Pcm>? = null

  private fun prepare(n: Int) {
    if (dead || n !in 1..Levels.COUNT || n == aheadLevel) return
    aheadLevel = n; ahead.drop(); ahead = null
    val env = Environment.of(n)
    builder.execute {
      if (aheadLevel != n) return@execute // no longer the next board
      Thread.currentThread().priority = if (level == n) Thread.NORM_PRIORITY else Thread.MIN_PRIORITY // the board came up meanwhile: hurry
      val built = loops(env)
      main.post {
        if (dead || aheadLevel != n) { built.drop(); return@post }
        if (built == null) { // could not be built ahead: the board, when it comes, builds it again
          aheadLevel = -1; ahead = null
          if (level == n && rendered == null) render()
          return@post
        }
        if (level == n && rendered == null) { // the board came up while this was being built: play it now
          rendering = false; rendered = built; ahead = null; aheadLevel = -1
          if (playing) build()
          prepare(n + 1)
        } else ahead = built
      }
    }
  }

  private fun build() {
    val (music, ambience) = rendered ?: return
    release()
    // A device without a working audio output cannot make the tracks: then the game simply has no music
    val first = try { Loop(music) } catch (_: Exception) { null }
    val second = if (first == null) null else try { Loop(ambience) } catch (_: Exception) { null }
    if (first == null || second == null) { first?.discard(); return }
    tracks = listOf(first, second)
    applyVolume()
    for (t in tracks) t.start(!held)
  }

  /**
   * A board's two loops, its tune and its ambience; null when they cannot be made (out of memory on
   * a small device): the board is then simply quiet. The ambience comes first, so a written piece,
   * once its first seconds are in, plays at once.
   */
  private fun loops(env: Environment): Pair<Pcm, Pcm>? = try {
    val ambience = Pcm.whole(Synth.ambience(env, sr))
    tune(env) to ambience
  } catch (_: Throwable) { null }

  /**
   * A board's tune. The two written pieces (the opening and the last board's victory theme, about
   * three minutes each) ship in the app as recordings made by this same synthesizer at build time
   * (tools/render_written.sh). They are decoded while they play, so they start within a moment;
   * synthesizing them would take seconds and a lot of memory. Only a device that cannot decode them
   * synthesizes them, one at a time; if decoding stops partway, only the rest is synthesized, after
   * what is already playing. Throws if the piece cannot be made at all.
   */
  private fun tune(env: Environment): Pcm {
    if (!Synth.isWritten(env.level)) return Pcm.whole(Synth.music(env, sr))
    val res = if (env.level == 1) R.raw.opening else R.raw.victory
    val frames = Synth.writtenFrames(env.level, sr)
    val pcm = Pcm(ShortArray(frames * 2))
    // Decoded on its own thread, far ahead of the playhead: the music starts once its first seconds
    // are in, and no other board's music waits behind it. At normal priority whoever starts it: a
    // piece built ahead by the background builder still has to keep ahead of the playhead later.
    val unwanted = { pcm.dropped || dead }
    Thread({
      val ok = try { Recording.decode(app, res, sr, frames, pcm.data, unwanted) { pcm.fill(it) } } catch (_: Throwable) { false }
      val made = ok || !unwanted() && try {
        synchronized(WRITTEN_LOCK) { !unwanted() && run { Synth.writtenInto(env.level, sr, pcm.data, pcm.filled); true } }
      } catch (_: Throwable) { false }
      if (made) pcm.finish() else pcm.fail()
    }, "music-decode").apply { isDaemon = true; priority = Thread.NORM_PRIORITY }.start()
    if (!pcm.await(minOf(pcm.data.size, sr * 2 * 3))) error("no music") // three seconds in hand before it plays
    return pcm
  }


  /**
   * A loop played by streaming: a writer thread feeds the track round and round, so a loop of any
   * length plays without holding it all in the audio system at once. Pausing the track pauses the
   * feeding with it; stopping ends the thread, which then frees the track.
   */
  private inner class Loop(private val source: Pcm) {
    private val pcm = source.data
    private val track: AudioTrack = AudioTrack.Builder()
      .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_GAME).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build())
      .setAudioFormat(AudioFormat.Builder().setEncoding(AudioFormat.ENCODING_PCM_16BIT).setSampleRate(sr).setChannelMask(AudioFormat.CHANNEL_OUT_STEREO).build())
      .setTransferMode(AudioTrack.MODE_STREAM)
      .setBufferSizeInBytes(maxOf(AudioTrack.getMinBufferSize(sr, AudioFormat.CHANNEL_OUT_STEREO, AudioFormat.ENCODING_PCM_16BIT) * 4, sr * 2) / 4 * 4) // half a second, in whole stereo frames
      .build()
    @Volatile private var running = true
    private val feeder = Thread({
      android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO) // fed ahead of everything else, so it never runs dry
      var pos = 0
      val chunk = 4096
      // Never blocks for long: when the track is full (or paused) the writer naps briefly and
      // checks again, so stopping always ends the thread and frees the track
      while (running) {
        val ready = source.filled
        if (pos >= ready && ready < pcm.size) { // still being made: it keeps well ahead, so this is rare
          if (source.failed || source.dropped) break // the rest can never come: this loop ends, quiet
          try { source.await(pos + 1) } catch (_: InterruptedException) { break }
          continue
        }
        val n = minOf(chunk, ready - pos)
        val written = try { track.write(pcm, pos, n, AudioTrack.WRITE_NON_BLOCKING) } catch (_: Exception) { -1 }
        if (written < 0) { main.post { lost(this) }; break } // the audio output died: build the loops again
        if (written == 0) { try { Thread.sleep(if (paused) 300 else 40) } catch (_: InterruptedException) { break }; continue } // the buffer holds half a second: a short nap never starves it
        pos += written
        if (pos >= pcm.size) pos = 0
      }
      try { track.pause(); track.flush() } catch (_: Exception) {}
      try { track.stop() } catch (_: Exception) {}
      try { track.release() } catch (_: Exception) {}
    }, "music-loop")

    fun start(play: Boolean) {
      paused = !play
      if (play) try { track.play() } catch (_: IllegalStateException) { paused = true } // a broken output: silent, never a crash
      feeder.start()
    }
    /** For a loop never started: free its track at once. */
    fun discard() { try { track.release() } catch (_: Exception) {} }
    @Volatile private var paused = false
    fun pause() { paused = true; try { track.pause() } catch (_: IllegalStateException) {} }
    fun play() { paused = false; try { track.play() } catch (_: IllegalStateException) {} }
    fun volume(v: Float) { try { track.setVolume(v) } catch (_: IllegalStateException) {} }
    fun stop() {
      running = false
      try { track.pause() } catch (_: IllegalStateException) {} // silent at once; the writer itself flushes, stops and frees the track as it ends
      feeder.interrupt()
    }
  }

  private fun applyVolume() {
    if (tracks.size < 2) return
    val on = if (muted) 0f else 1f
    tracks[0].volume(0.32f * on)
    tracks[1].volume(0.30f * on)
  }

  private fun release() {
    for (t in tracks) t.stop()
    tracks = emptyList()
  }

  companion object {
    /** Shared by every player, so a written piece is never synthesized twice at once. */
    private val WRITTEN_LOCK = Any()
  }
}

/**
 * A loop's samples, which may still be arriving: [filled] of them are ready, and [done] once all
 * are. [failed] if the rest can never come; [dropped] once nobody wants them, so whoever is still
 * making them stops. Whoever waits for samples is woken the moment they arrive.
 */
class Pcm(val data: ShortArray) {
  @Volatile var filled = 0; private set
  @Volatile var done = false; private set
  @Volatile var failed = false; private set
  @Volatile var dropped = false; private set
  private val lock = java.util.concurrent.locks.ReentrantLock()
  private val arrived = lock.newCondition()

  private inline fun change(f: () -> Unit) { lock.lock(); try { f(); arrived.signalAll() } finally { lock.unlock() } }
  fun fill(n: Int) = change { filled = n }
  fun finish() = change { filled = data.size; done = true }
  fun fail() = change { failed = true }
  fun drop() = change { dropped = true }

  /** Waits until [n] samples are ready; false if they never will be (it failed or was dropped). */
  fun await(n: Int): Boolean {
    lock.lock()
    try {
      while (filled < n && !done && !failed && !dropped) arrived.await()
      return filled >= n || done
    } finally { lock.unlock() }
  }

  companion object {
    fun whole(data: ShortArray) = Pcm(data).also { it.finish() }
  }
}
