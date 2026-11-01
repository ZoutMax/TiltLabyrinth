package com.megapro.labyrinth.ui

/** Whole tenths of a second, as the reels and the cards show time. */
internal fun tenths(s: Float): Long = (s.toDouble() * 10.0 + 1e-6).toLong()

internal fun formatTenths(t: Long): String = "%d:%02d.%d".format(t / 600, t / 10 % 60, t % 10)

internal fun formatTime(s: Float): String = formatTenths(tenths(s))

/**
 * How this board's time compares with the try that was given up with RESTART. Both times are the
 * board clock alone, never the run total, and the difference is taken on the displayed tenths, so
 * the three numbers on the card always agree.
 */
internal fun compareText(previousTry: Float, boardTime: Float): String {
    val p = tenths(previousTry); val b = tenths(boardTime); val d = p - b
    val what = "your previous try (${formatTenths(p)})"
    return when {
        d > 0 -> "You are ${formatTenths(d)} faster than $what."
        d < 0 -> "You are ${formatTenths(-d)} slower than $what."
        else -> "Same time as $what."
    }
}
