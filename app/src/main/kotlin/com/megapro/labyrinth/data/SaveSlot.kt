package com.megapro.labyrinth.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** A saved run: the board to continue at, the run's time so far, its identity, and the time of every board finished so far. */
data class SavedRun(val level: Int, val time: Float, val runId: Long, val boardTimes: List<Float> = emptyList())

/** The one save slot. Writing it replaces whatever was saved before. */
class SaveSlot(private val store: KeyValueStore) {
  constructor(context: Context) : this(PrefsStore(context, "save"))

  /** Bumps on every change so the screen refreshes its buttons. */
  var version by mutableIntStateOf(0)
    private set

  fun read(): SavedRun? {
    val p = (store.get("run") ?: return null).split('|')
    if (p.size < 3) return null
    val times = if (p.size >= 4 && p[3].isNotEmpty()) p[3].split(',').mapNotNull { it.toFloatOrNull() } else emptyList()
    return SavedRun(p[0].toIntOrNull() ?: return null, p[1].toFloatOrNull() ?: return null, p[2].toLongOrNull() ?: return null, times)
  }

  fun write(level: Int, time: Float, runId: Long, boardTimes: List<Float>) {
    store.put("run", "$level|$time|$runId|${boardTimes.joinToString(",")}")
    version++
  }
}
