package com.megapro.labyrinth.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** One run's standing: how far it got and how long it took to get there. */
data class RunEntry(val runId: Long, val name: String, val level: Int, val time: Float, val date: Long = runId)

/** Top-10 runs, ranked by level reached then total time, kept in SharedPreferences. */
class Leaderboard(private val store: KeyValueStore) {
  constructor(context: Context) : this(PrefsStore(context, "leaderboard"))

  /** More levels wins; then less time; then the earlier run keeps its place. Declared before
   *  [entries], which sorts with it while loading. */
  private val order = compareByDescending<RunEntry> { it.level }.thenBy { it.time }.thenBy { it.date }

  var entries: List<RunEntry> by mutableStateOf(load())
    private set

  var lastName: String
    get() = store.get("lastName") ?: ""
    private set(value) = store.put("lastName", value)

  private fun better(a: RunEntry, b: RunEntry): Boolean = order.compare(a, b) < 0

  fun qualifies(runId: Long, level: Int, time: Float): Boolean {
    if (level < 1) return false // a run must complete at least one level to be ranked
    val probe = RunEntry(runId, "", level, time, System.currentTimeMillis())
    val others = entries.filter { it.runId != runId }
    return others.size < MAX || better(probe, others.last())
  }

  fun nameFor(runId: Long): String? = entries.firstOrNull { it.runId == runId }?.name

  /** A named run played again from its save: keep whichever standing is better, under its name. */
  fun improve(runId: Long, level: Int, time: Float) {
    val existing = entries.firstOrNull { it.runId == runId } ?: return
    val probe = existing.copy(level = level, time = time)
    if (better(probe, existing)) record(runId, existing.name, level, time)
  }

  /** Insert or update the run's entry and return its 1-based rank. */
  fun record(runId: Long, name: String, level: Int, time: Float): Int {
    val clean = name.trim().replace('|', ' ').take(MAX_NAME).ifEmpty { "PLAYER" }
    lastName = clean
    val updated = (entries.filter { it.runId != runId } + RunEntry(runId, clean, level, time, System.currentTimeMillis()))
      .filter { it.level >= 1 }
      .sortedWith(order)
      .take(MAX)
    entries = updated
    store.put("list", updated.joinToString("\n") { "${it.runId}|${it.name}|${it.level}|${it.time}|${it.date}" })
    return updated.indexOfFirst { it.runId == runId } + 1
  }

  private fun load(): List<RunEntry> =
    (store.get("list") ?: "").lines().mapNotNull { line ->
      val p = line.split('|')
      if (p.size < 4) return@mapNotNull null
      val id = p[0].toLongOrNull() ?: return@mapNotNull null
      RunEntry(id, p[1], p[2].toIntOrNull() ?: 1, p[3].toFloatOrNull() ?: 0f, p.getOrNull(4)?.toLongOrNull() ?: id)
    }.filter { it.level >= 1 }.sortedWith(order).take(MAX)

  companion object {
    const val MAX = 10
    const val MAX_NAME = 12
  }
}
