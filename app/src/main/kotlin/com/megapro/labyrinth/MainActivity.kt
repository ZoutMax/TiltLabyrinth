package com.megapro.labyrinth

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.OnBackPressedCallback
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.megapro.labyrinth.data.Leaderboard
import com.megapro.labyrinth.data.LevelBoards
import com.megapro.labyrinth.data.SaveSlot
import com.megapro.labyrinth.data.Settings
import com.megapro.labyrinth.audio.Music
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.io.File
import com.megapro.labyrinth.audio.Sounds
import com.megapro.labyrinth.haptics.Haptics
import com.megapro.labyrinth.game.Game
import com.megapro.labyrinth.ui.GameScreen
import com.megapro.labyrinth.ui.theme.TiltLabyrinthTheme

class MainActivity : ComponentActivity(), SensorEventListener {
  private lateinit var sensorManager: SensorManager
  private var sensor: Sensor? = null
  private var smooth = false
  private var fx = 0f
  private var fy = 0f
  private var fz = 0f

  private val leaderboard by lazy { Leaderboard(this) }
  private val saveSlot by lazy { SaveSlot(this) }
  private val settings by lazy { Settings(this) }
  private val music by lazy { Music(this) }
  private var crashReport by mutableStateOf<String?>(null)
  private val sounds by lazy { Sounds(this) }
  private val haptics by lazy { Haptics(this) }
  private val levelBoards by lazy { LevelBoards(this) }
  private val game by lazy {
    // Every start is a fresh run from level 1; a saved run is continued through the LOAD button
    Game(haptics, sounds).also { g ->
      g.onBoardDone = { level, time -> levelBoards.record(level, g.runId, time) }
    }
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    enableEdgeToEdge()
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
    sensorManager = getSystemService(Context.SENSOR_SERVICE) as SensorManager
    sensor = sensorManager.getDefaultSensor(Sensor.TYPE_GRAVITY)
    if (sensor == null) {
      sensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER)
      smooth = true
    }
    installCrashReporter()
    // Back never throws a run away: the game pauses it or returns to the menu, and otherwise the
    // game steps into the background with everything kept, exactly like the Home button
    onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
      override fun handleOnBackPressed() { if (!game.onBack()) moveTaskToBack(true) }
    })
    window.decorView.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> refreshTurn() }
    setContent {
      TiltLabyrinthTheme(darkTheme = true) {
        GameScreen(game, leaderboard, levelBoards, saveSlot, settings, music)
        crashReport?.let { report ->
          AlertDialog(
            onDismissRequest = { crashReport = null },
            title = { Text("The game crashed last time") },
            text = { Text(report.take(700)) },
            confirmButton = { TextButton(onClick = { copyToClipboard(report); crashReport = null }) { Text("COPY") } },
            dismissButton = { TextButton(onClick = { crashReport = null }) { Text("DISMISS") } },
          )
        }
      }
    }
  }

  override fun onResume() {
    super.onResume()
    refreshTurn()
    music.muted = settings.musicMuted
    music.board(game.level)
    music.start()
    sensor?.let { sensorManager.registerListener(this, it, SensorManager.SENSOR_DELAY_FASTEST) }
  }

  override fun onPause() {
    super.onPause()
    sensorManager.unregisterListener(this)
    game.pause()
    music.hold(game.phase == com.megapro.labyrinth.game.Phase.PAUSED) // back from the background the game waits paused, and so does the music
    sounds.pauseAll()
    haptics.stop()
    music.stop()
  }

  override fun onDestroy() {
    super.onDestroy()
    sounds.release()
    music.shutdown()
  }

  override fun onSensorChanged(event: SensorEvent) {
    var gx = event.values[0]; var gy = event.values[1]; var gz = event.values[2]
    if (smooth) {
      fx += (gx - fx) * 0.25f; fy += (gy - fy) * 0.25f; fz += (gz - fz) * 0.25f
      gx = fx; gy = fy; gz = fz
    }
    // The board is drawn upright in portrait. On a phone, and on tablets whose natural way is
    // portrait, that is the device's own frame and the raw axes are right. On a tablet whose
    // natural way is landscape, portrait is a rotation of 90 or 270 degrees, so the reading is
    // turned into the screen's frame first.
    when (turn) {
      1 -> { val x = gx; gx = -gy; gy = x }
      3 -> { val x = gx; gx = gy; gy = -x }
    }
    game.onSensor(gx, gy, gz)
  }

  override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit

  /** Keeps the last crash's stack trace in a file, so it can be shown and copied at the next start. */
  private fun installCrashReporter() {
    val file = File(filesDir, "crash.txt")
    if (file.exists()) { crashReport = file.readText(); file.delete() }
    val previous = Thread.getDefaultUncaughtExceptionHandler()
    Thread.setDefaultUncaughtExceptionHandler { thread, error ->
      try { file.writeText("${java.util.Date()}\n${android.util.Log.getStackTraceString(error)}") } catch (_: Exception) {}
      previous?.uncaughtException(thread, error)
    }
  }

  private fun copyToClipboard(text: String) {
    val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    clipboard.setPrimaryClip(android.content.ClipData.newPlainText("Tilt Labyrinth crash", text))
  }

  @Suppress("DEPRECATION")
  private fun rotationNow(): Int = windowManager.defaultDisplay.rotation

  /** How the sensor reading is turned into the screen's frame: 0 none, 1 a quarter turn, 3 three. Set when the window is laid out, not on every reading. */
  @Volatile private var turn = 0

  private fun refreshTurn() {
    val v = window.decorView
    val portraitWindow = v.height >= v.width
    val rot = rotationNow()
    turn = when {
      // A tablet whose natural way is landscape, held in portrait: the screen is a quarter turn from the device
      portraitWindow && rot == android.view.Surface.ROTATION_90 -> 1
      portraitWindow && rot == android.view.Surface.ROTATION_270 -> 3
      // The same tablet held in landscape (newer Android lets large screens turn the app): the
      // board is drawn turned a quarter to the left to fit, so the reading is turned with it
      !portraitWindow && (rot == android.view.Surface.ROTATION_0 || rot == android.view.Surface.ROTATION_180) -> 1
      else -> 0
    }
  }
}
