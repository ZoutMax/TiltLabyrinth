package com.megapro.labyrinth.haptics

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import com.megapro.labyrinth.game.GameHaptics

/**
 * Vibration for the game, built to feel as good as the phone allows:
 *  - haptic primitives (click, tick, low tick, thud, spin, quick rise) where the hardware has them,
 *    composed into little sequences for the bigger moments;
 *  - the system's tuned predefined effects (click, heavy click, tick) as the next best thing;
 *  - amplitude-shaped waveforms, and finally plain buzzes, on older phones.
 * Continuous feelings (rolling, wind, spinning, sliding, the magnet's grip) are short pulses the
 * game refreshes every frame, so they start and stop with the ball, and one-off effects own the
 * motor for their duration so textures never cut them short.
 */
class Haptics(context: Context) : GameHaptics {
  private val vibrator: Vibrator =
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
      (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as VibratorManager).defaultVibrator
    } else {
      @Suppress("DEPRECATION")
      context.getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
    }
  private val present = vibrator.hasVibrator()
  private val amplitude = present && vibrator.hasAmplitudeControl()
  private val primitives = present && Build.VERSION.SDK_INT >= Build.VERSION_CODES.R
  private val predefined = present && Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
  private fun supports(primitive: Int): Boolean = primitives && vibrator.areAllPrimitivesSupported(primitive)

  private val click = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supports(VibrationEffect.Composition.PRIMITIVE_CLICK)
  private val tickP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supports(VibrationEffect.Composition.PRIMITIVE_TICK)
  private val quickRise = Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && supports(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE)
  private val thud = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supports(VibrationEffect.Composition.PRIMITIVE_THUD)
  private val lowTick = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supports(VibrationEffect.Composition.PRIMITIVE_LOW_TICK)
  private val spinP = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && supports(VibrationEffect.Composition.PRIMITIVE_SPIN)

  private var lastRoll = 0L
  private var lastWind = 0L
  private var lastSpin = 0L
  private var lastSlide = 0L
  private var lastKnock = 0L
  private var holding = false
  /** Until when a one-off effect owns the motor; texture pulses wait so they do not cut it short. */
  private var busyUntil = 0L

  private fun now() = System.currentTimeMillis()
  private fun amp(v: Float): Int = (1 + 254 * v.coerceIn(0f, 1f)).toInt()
  private fun own(ms: Long) { busyUntil = now() + ms }

  private fun oneShot(ms: Long, strength: Float) {
    if (!present) return
    vibrator.vibrate(VibrationEffect.createOneShot(ms, if (amplitude) amp(strength) else VibrationEffect.DEFAULT_AMPLITUDE))
  }

  private fun waveform(timings: LongArray, amps: IntArray, repeat: Int = -1) {
    if (!present) return
    if (amplitude) vibrator.vibrate(VibrationEffect.createWaveform(timings, amps, repeat))
    else vibrator.vibrate(VibrationEffect.createWaveform(timings, repeat))
  }

  /** One primitive, scaled; false if the phone has no such primitive. */
  private fun primitive(id: Int, scale: Float, ok: Boolean): Boolean {
    if (!ok || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    vibrator.vibrate(VibrationEffect.startComposition().addPrimitive(id, scale.coerceIn(0.05f, 1f)).compose())
    return true
  }

  /** One of the system's tuned effects; false if unavailable. */
  private fun predefined(effect: Int): Boolean {
    if (!predefined || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return false
    vibrator.vibrate(VibrationEffect.createPredefined(effect))
    return true
  }

  private fun composition(build: VibrationEffect.Composition.() -> Unit): Boolean {
    if (!primitives || Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return false
    vibrator.vibrate(VibrationEffect.startComposition().apply(build).compose())
    return true
  }

  // ------------------------------------------------------------------ hits

  override fun knock(strength: Float) {
    if (strength < 0.08f) return
    val t = now()
    if (t - lastKnock < 40) return
    lastKnock = t
    own(if (strength > 0.6f) 80 else 30)
    // A sharp click scaled by the hit; big hits land with a thud under it
    if (strength > 0.6f && thud) {
      composition {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, (strength - 0.4f).coerceIn(0.2f, 1f), 0)
      }
      return
    }
    if (primitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 0.25f + 0.75f * strength, click)) return
    if (strength > 0.6f && predefined(VibrationEffect.EFFECT_HEAVY_CLICK)) return
    if (strength > 0.25f && predefined(VibrationEffect.EFFECT_CLICK)) return
    if (predefined(VibrationEffect.EFFECT_TICK)) return
    oneShot((8 + 22 * strength).toLong(), 0.35f + 0.65f * strength)
  }

  override fun tick(strength: Float) {
    if (strength < 0.08f) return
    val t = now()
    if (t < busyUntil) return
    own(20)
    if (primitive(VibrationEffect.Composition.PRIMITIVE_TICK, strength, tickP)) return
    if (predefined(VibrationEffect.EFFECT_TICK)) return
    oneShot(10, strength)
  }

  // -------------------------------------------------------------- textures

  override fun roll(level: Float) {
    if (holding || level < 0.06f) return
    val t = now()
    if (t < busyUntil) return
    // The grain of the board under a rolling ball: faint ticks, closer and firmer as it speeds up,
    // with a little irregularity so it feels like wood and not a motor
    val interval = (150 - 105 * level).toLong() + (t % 23)
    if (t - lastRoll < interval) return
    lastRoll = t
    val s = 0.10f + 0.40f * level
    if (primitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, s, lowTick)) return
    if (primitive(VibrationEffect.Composition.PRIMITIVE_TICK, s * 0.8f, tickP)) return
    oneShot(8, s * 0.6f)
  }

  override fun wind(level: Float) {
    if (holding || level < 0.05f) return
    val t = now()
    if (t < busyUntil) return
    // Buffeting: irregular soft shoves
    val interval = (120 - 60 * level).toLong() + (t % 37)
    if (t - lastWind < interval) return
    lastWind = t
    val s = 0.15f + 0.6f * level
    if (primitive(VibrationEffect.Composition.PRIMITIVE_TICK, s, tickP)) return
    oneShot(14, s)
  }

  override fun spin(level: Float) {
    if (holding || level < 0.05f) return
    val t = now()
    if (t < busyUntil) return
    // Caught in the vortex: a whirling pull, repeated faster as the current grows
    val interval = (260 - 140 * level).toLong()
    if (t - lastSpin < interval) return
    lastSpin = t
    val s = 0.3f + 0.7f * level
    if (primitive(VibrationEffect.Composition.PRIMITIVE_SPIN, s, spinP)) return
    waveform(longArrayOf(0, 25, 20, 25), intArrayOf(0, amp(s * 0.5f), amp(s * 0.25f), amp(s * 0.6f)))
  }

  override fun slide(level: Float) {
    if (holding || level < 0.5f) return
    val t = now()
    if (t < busyUntil || t - lastSlide < 45) return
    lastSlide = t
    // Shivering over ice: very light, very quick ticks
    if (primitive(VibrationEffect.Composition.PRIMITIVE_LOW_TICK, 0.12f, lowTick)) return
    oneShot(6, 0.12f)
  }

  // ---------------------------------------------------------------- traps

  override fun magnetCatch() {
    own(120)
    if (thud) {
      composition {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.6f, 40) // the snap of the ball locking on
      }
      return
    }
    if (predefined(VibrationEffect.EFFECT_HEAVY_CLICK)) return
    oneShot(45, 1f)
  }

  override fun magnetHold(held: Boolean) {
    if (held == holding) return
    holding = held
    if (held) {
      // A steady electric grip: a fast buzz whose strength wavers, repeating until release
      waveform(longArrayOf(0, 18, 16, 18, 16, 18, 22), intArrayOf(0, 120, 20, 95, 20, 140, 0), repeat = 0)
    } else {
      vibrator.cancel()
    }
  }

  override fun magnetRelease() {
    holding = false
    vibrator.cancel()
    own(60)
    if (primitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.7f, tickP)) return
    if (predefined(VibrationEffect.EFFECT_TICK)) return
    oneShot(20, 0.6f)
  }

  override fun toxic() {
    own(230)
    // A wet, uneven squelch
    waveform(longArrayOf(0, 25, 20, 45, 25, 60, 40), intArrayOf(0, 170, 40, 130, 30, 90, 0))
  }

  override fun boost() {
    own(160)
    // A surge: quick rise into a sharp snap
    if (quickRise && composition {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_QUICK_RISE, 1f)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_CLICK, 1f, 0)
      }) return
    waveform(longArrayOf(0, 20, 20, 20, 20, 35), intArrayOf(0, 50, 100, 160, 220, 255))
  }

  // ------------------------------------------------------------- endings

  // Every death is unmistakable: a long blow at full strength first, then the trap's own signature

  override fun fall() {
    holding = false
    own(800)
    // Dropping in: a heavy full-strength thump, a second knock as the ball hits bottom, then the rumble dying away
    waveform(longArrayOf(0, 180, 50, 90, 40, 140, 200), intArrayOf(0, 255, 90, 255, 70, 160, 50))
  }

  override fun zap() {
    holding = false
    own(900)
    // Cut by the beam: a long searing full-strength burst, then a hard crackle of fast pulses dying away
    waveform(
      longArrayOf(0, 220, 15, 25, 15, 25, 15, 25, 20, 25, 25, 25, 30, 25, 40, 30),
      intArrayOf(0, 255, 0, 255, 0, 255, 0, 230, 0, 200, 0, 160, 0, 120, 0, 70),
    )
  }

  override fun explode() {
    holding = false
    own(1200)
    // Blowing up: a violent full-strength detonation, a second blast, then a long rough rumble shaking itself out
    waveform(
      longArrayOf(0, 260, 30, 120, 25, 60, 30, 70, 40, 90, 60, 120, 90, 160),
      intArrayOf(0, 255, 120, 255, 60, 220, 40, 170, 30, 130, 20, 90, 10, 40),
    )
  }

  override fun finish() {
    holding = false
    own(450)
    // Success: two crisp taps and a satisfied thump
    if (thud && composition {
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.9f)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_TICK, 0.9f, 90)
        addPrimitive(VibrationEffect.Composition.PRIMITIVE_THUD, 1f, 110)
      }) return
    waveform(longArrayOf(0, 30, 70, 30, 90, 90), intArrayOf(0, 200, 0, 200, 0, 255))
  }

  override fun stop() {
    holding = false
    if (present) vibrator.cancel()
  }
}
