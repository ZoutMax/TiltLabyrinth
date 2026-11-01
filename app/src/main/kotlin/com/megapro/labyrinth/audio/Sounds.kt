package com.megapro.labyrinth.audio

import android.content.Context
import android.media.AudioAttributes
import android.media.SoundPool
import com.megapro.labyrinth.R
import com.megapro.labyrinth.game.GameSounds
import com.megapro.labyrinth.game.TrapKind

class Sounds(appContext: Context) : GameSounds {
  private val pool: SoundPool =
    SoundPool.Builder()
      .setMaxStreams(6)
      .setAudioAttributes(
        AudioAttributes.Builder()
          .setUsage(AudioAttributes.USAGE_GAME)
          .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
          .build()
      )
      .build()

  private val ctx: Context = appContext.applicationContext
  private val loaded = HashSet<Int>()
  private fun load(res: Int): Int = pool.load(ctx, res, 1)

  private val roll = load(R.raw.roll_loop)
  private val grain = load(R.raw.roll_grain)
  private val knock = load(R.raw.knock)
  private val finish = load(R.raw.finish)
  private val fall = load(R.raw.fall)
  private val lid = load(R.raw.lid)
  // Every trap kind: a cue when it switches on (or arms) and, for most, a loop while it is active.
  // All of them are played at a volume set by how near the ball is (see Game.hearing).
  private val starts = mapOf(
    TrapKind.MAGNET to load(R.raw.magnet_on), TrapKind.WIND to load(R.raw.wind_on), TrapKind.RADIOACTIVE to load(R.raw.geiger_on), TrapKind.SPEED to load(R.raw.speed_on),
    TrapKind.TRAPDOOR to load(R.raw.trapdoor_on), TrapKind.CRUSHER to load(R.raw.crusher_on), TrapKind.LASER to load(R.raw.laser_on), TrapKind.ICE to load(R.raw.ice_on), TrapKind.SPINNER to load(R.raw.spin_on),
  )
  private val loops = mapOf(
    TrapKind.MAGNET to load(R.raw.magnet_loop), TrapKind.WIND to load(R.raw.wind_loop), TrapKind.RADIOACTIVE to load(R.raw.geiger_loop), TrapKind.SPEED to load(R.raw.speed_loop),
    TrapKind.CRUSHER to load(R.raw.crusher_loop), TrapKind.LASER to load(R.raw.laser_loop), TrapKind.ICE to load(R.raw.ice_loop), TrapKind.SPINNER to load(R.raw.spin_loop),
  )
  private val zap = load(R.raw.zap)
  private val slam = load(R.raw.slam)
  private val explode = load(R.raw.explode)
  private val firework = load(R.raw.firework)
  private val disintegrate = load(R.raw.disintegrate)
  private val reelTick = load(R.raw.reel_tick)

  private var rollStream = 0
  private var grainStream = 0
  private var rollLevel = 0f
  private var lastKnock = 0L
  private var lastLid = 0L
  private val trapStreams = HashMap<TrapKind, Int>()
  private val trapLevels = HashMap<TrapKind, Float>()
  private val trapPans = HashMap<TrapKind, Float>()

  init {
    pool.setOnLoadCompleteListener { _, sampleId, status -> if (status == 0) loaded.add(sampleId) }
  }

  /**
   * A steel pinball rolling: the body rumble comes in first, the bright grain of steel on the
   * surface grows faster with speed, so a creeping ball is a soft rumble and a fast one hisses
   * and sings. Both climb slightly in pitch with speed.
   */
  override fun roll(level: Float) {
    val v = level.coerceIn(0f, 1f)
    if (v < 0.01f) {
      if (rollStream != 0) { pool.stop(rollStream); rollStream = 0 }
      if (grainStream != 0) { pool.stop(grainStream); grainStream = 0 }
      rollLevel = 0f
      return
    }
    val body = Math.pow(v.toDouble(), 0.8).toFloat()
    val hiss = Math.pow(v.toDouble(), 1.7).toFloat() * 0.9f
    val bodyRate = 0.9f + 0.3f * v
    val hissRate = 0.85f + 0.45f * v
    val changed = kotlin.math.abs(v - rollLevel) > 0.005f
    if (rollStream == 0) {
      if (roll in loaded) rollStream = pool.play(roll, body, body, 1, -1, bodyRate)
    } else if (changed) {
      pool.setVolume(rollStream, body, body); pool.setRate(rollStream, bodyRate)
    }
    if (grainStream == 0) {
      if (grain in loaded && hiss > 0.01f) grainStream = pool.play(grain, hiss, hiss, 1, -1, hissRate)
    } else if (changed) {
      pool.setVolume(grainStream, hiss, hiss); pool.setRate(grainStream, hissRate)
    }
    rollLevel = v
  }

  override fun knock(strength: Float, size: Float) {
    val now = System.currentTimeMillis()
    if (now - lastKnock < 60) return
    lastKnock = now
    if (knock in loaded) pool.play(knock, strength, strength, 1, 0, GameSounds.knockRate(strength, size))
  }

  override fun finish() {
    if (finish in loaded) pool.play(finish, 1f, 1f, 1, 0, 1f)
  }

  /** Left and right volumes for a sound placed at [pan], -1 left to 1 right: the far ear gets less. */
  private fun stereo(v: Float, pan: Float): Pair<Float, Float> {
    val p = pan.coerceIn(-1f, 1f)
    return v * (1f - 0.6f * p.coerceAtLeast(0f)) to v * (1f - 0.6f * (-p).coerceAtLeast(0f))
  }

  override fun trap(kind: TrapKind, level: Float, pan: Float) {
    val sample = loops[kind] ?: return
    val v = level.coerceIn(0f, 1f)
    val stream = trapStreams[kind] ?: 0
    if (v < 0.02f) {
      if (stream != 0) { pool.stop(stream); trapStreams.remove(kind) }
      trapLevels[kind] = 0f
      return
    }
    val (l, r) = stereo(v, pan)
    if (stream == 0) {
      if (sample !in loaded) return
      trapStreams[kind] = pool.play(sample, l, r, 1, -1, 1f)
    } else if (kotlin.math.abs(v - (trapLevels[kind] ?: 0f)) > 0.02f || kotlin.math.abs(pan - (trapPans[kind] ?: 0f)) > 0.05f) {
      pool.setVolume(stream, l, r)
    }
    trapLevels[kind] = v; trapPans[kind] = pan
  }

  override fun activate(kind: TrapKind, strength: Float, pan: Float) {
    val sample = starts[kind] ?: return
    val v = strength.coerceIn(0f, 1f)
    val (l, r) = stereo(v, pan)
    if (v > 0.02f && sample in loaded) pool.play(sample, l, r, 1, 0, 1f)
  }

  override fun slam(strength: Float, pan: Float) {
    val v = strength.coerceIn(0f, 1f)
    val (l, r) = stereo(v, pan)
    if (v > 0.02f && slam in loaded) pool.play(slam, l, r, 1, 0, 1f)
  }

  override fun reelTick() {
    if (reelTick in loaded) pool.play(reelTick, 0.22f, 0.22f, 0, 0, 1f) // barely there, under everything else
  }

  override fun disintegrate() {
    if (disintegrate in loaded) pool.play(disintegrate, 1f, 1f, 1, 0, 1f)
  }

  override fun explode() {
    if (explode in loaded) pool.play(explode, 1f, 1f, 1, 0, 1f)
  }

  private val fireworkTimes = ArrayDeque<Long>()

  override fun firework(strength: Float, pan: Float) {
    // At most three bursts sounding at once, and below every game sound, so the celebration never
    // takes all the channels
    if (firework !in loaded) return // not ready yet: nothing to hear, nothing spent
    val now = System.currentTimeMillis()
    while (fireworkTimes.isNotEmpty() && now - fireworkTimes.first() > 2200) fireworkTimes.removeFirst()
    if (fireworkTimes.size >= 3) return
    fireworkTimes.addLast(now)
    val v = (0.35f + 0.45f * strength).coerceIn(0f, 1f)
    val (l, r) = stereo(v, pan)
    pool.play(firework, l, r, 0, 0, 0.85f + 0.3f * kotlin.random.Random.nextFloat())
  }

  override fun boost() {
    if (zap in loaded) pool.play(zap, 1f, 1f, 1, 0, 1f)
  }

  override fun lid(strength: Float, pan: Float) {
    val now = System.currentTimeMillis()
    if (now - lastLid < 40) return
    lastLid = now
    val v = strength.coerceIn(0f, 1f)
    val (l, r) = stereo(v, pan)
    if (v > 0.02f && lid in loaded) pool.play(lid, l, r, 1, 0, 0.95f + 0.1f * v)
  }

  override fun fall() {
    if (fall in loaded) pool.play(fall, 1f, 1f, 1, 0, 1f)
  }

  fun pauseAll() {
    if (rollStream != 0) { pool.stop(rollStream); rollStream = 0 }
    if (grainStream != 0) { pool.stop(grainStream); grainStream = 0 }
    for (s in trapStreams.values) pool.stop(s)
    trapStreams.clear(); trapLevels.clear(); trapPans.clear()
    pool.autoPause()
  }

  fun release() = pool.release()
}
