package com.megapro.labyrinth.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.setValue

/** One finished board: which run did it, in how long. The name arrives when the run ends. */
data class BoardEntry(val runId: Long, val name: String, val time: Float, val date: Long)

/** The ten fastest times on each board, kept in SharedPreferences. */
class LevelBoards(private val store: KeyValueStore) {
  constructor(context: Context) : this(PrefsStore(context, "levelboards"))
  private val cache = HashMap<Int, List<BoardEntry>>()

  /** Bumps on every change so the screen redraws its lists. */
  var version by mutableIntStateOf(0)
    private set

  private val order = compareBy<BoardEntry> { it.time }.thenBy { it.date }

  fun entries(level: Int): List<BoardEntry> = cache.getOrPut(level) { load(level) }

  /** A board was finished: file the time under the run; the name is filled in at the end of the run.
   *  A run that plays a board again keeps its better time. */
  fun record(level: Int, runId: Long, time: Float) {
    val previous = entries(level).firstOrNull { it.runId == runId }
    if (previous != null && previous.time <= time) return
    val list = (entries(level).filter { it.runId != runId } + BoardEntry(runId, previous?.name ?: nameOf(runId), time, System.currentTimeMillis()))
      .sortedWith(order).take(MAX)
    save(level, list)
  }

  /** The run has a name now: write it on every board time that run set. */
  fun name(runId: Long, name: String) {
    val clean = name.trim().replace('|', ' ').take(Leaderboard.MAX_NAME).ifEmpty { "PLAYER" }
    for (level in levelsWithEntries()) {
      val list = entries(level)
      if (list.none { it.runId == runId }) continue
      save(level, list.map { if (it.runId == runId) it.copy(name = clean) else it })
    }
  }

  /** Does this run hold a place on any board? Then it deserves a name at the end. */
  fun hasRun(runId: Long): Boolean = levelsWithEntries().any { level -> entries(level).any { it.runId == runId } }

  private fun levelsWithEntries(): List<Int> = store.keys().filter { it.startsWith("L") }.mapNotNull { it.drop(1).toIntOrNull() }

  fun rank(level: Int, runId: Long): Int = entries(level).indexOfFirst { it.runId == runId } + 1

  private fun nameOf(runId: Long): String {
    for (list in cache.values) list.firstOrNull { it.runId == runId }?.let { if (it.name.isNotEmpty()) return it.name }
    return ""
  }

  private fun save(level: Int, list: List<BoardEntry>) {
    cache[level] = list
    store.put("L$level", list.joinToString("\n") { "${it.runId}|${it.name}|${it.time}|${it.date}" })
    version++
  }

  private fun load(level: Int): List<BoardEntry> =
    (store.get("L$level") ?: "").lines().mapNotNull { line ->
      val p = line.split('|')
      if (p.size < 4) return@mapNotNull null
      val id = p[0].toLongOrNull() ?: return@mapNotNull null
      BoardEntry(id, p[1], p[2].toFloatOrNull() ?: return@mapNotNull null, p[3].toLongOrNull() ?: id)
    }.sortedWith(order).take(MAX)

  companion object {
    const val MAX = 10
  }
}
