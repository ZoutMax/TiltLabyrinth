package com.megapro.labyrinth

import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.semantics.SemanticsNode
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.test.SemanticsMatcher
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasSetTextAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.longClick
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextReplacement
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.megapro.labyrinth.data.Leaderboard
import com.megapro.labyrinth.data.SaveSlot
import com.megapro.labyrinth.game.Game
import com.megapro.labyrinth.game.Levels
import com.megapro.labyrinth.game.Phase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The player's workflows on a real device, card by card: a board finished (SAVE, RESTART, the next
 * board), the run lost (name, top boards), all hundred boards done, free play and replays, the top
 * 10 card, and the Back button. Every card is checked to fit: each text lies inside the screen and
 * no two texts on one line run into each other.
 *
 * The game itself is moved on directly (its clock, the ball), so a test needs no hand on the
 * device; the screen is then drawn by pumping the paused Compose clock.
 */
@RunWith(AndroidJUnit4::class)
class WorkflowTest {
  @get:Rule val rule = createAndroidComposeRule<MainActivity>()

  @Before fun pumpByHand() { rule.mainClock.autoAdvance = false }

  private fun pump(ms: Long = 700) = rule.mainClock.advanceTimeBy(ms)

  @Suppress("UNCHECKED_CAST")
  private fun <T> held(name: String): T {
    val f = MainActivity::class.java.getDeclaredField("$name\$delegate").apply { isAccessible = true }
    return (f.get(rule.activity) as Lazy<*>).value as T
  }

  private val game: Game get() = held("game")

  private fun onGame(block: Game.() -> Unit) = rule.runOnUiThread { game.block() }

  /** Runs the game's own clock for [seconds] without drawing. */
  private fun Game.advance(seconds: Float) { var t = 0f; while (t < seconds) { update(1f / 120f); t += 1f / 120f } }

  /** Plays the current board to its end: waits for the finish hole to open, then drops the ball in. */
  private fun finishBoard() = onGame {
    advance(0.6f); placeBall(2.5f, 2.5f); advance(0.5f)
    var t = 0f
    while (!finishOpen && t < 30f) { update(1f / 120f); t += 1f / 120f }
    placeBall(maze.finish.x + 0.5f, maze.finish.y + 0.5f); update(1f / 120f)
  }

  /** Loses the ball to the clock: moves it once so the clock starts, then lets three minutes pass. */
  private fun timeOut() = onGame { advance(0.6f); placeBall(maze.cols - 0.5f, 0.5f); advance(Game.TIME_LIMIT + 2f) }

  /** The game's own window (a dialog, when one is open, is a second root on top of it). */
  private fun board() = rule.onAllNodes(isRoot())[0]

  /** The Back button, through the same dispatcher the key and the gesture reach (Espresso's own
   *  Back waits for a still screen, which the animated board never gives on Android 17). */
  private fun pressBack() { rule.runOnUiThread { rule.activity.onBackPressedDispatcher.onBackPressed() }; pump() }

  private fun tapBoard() { board().performTouchInput { click(androidx.compose.ui.geometry.Offset(width * 0.05f, height * 0.3f)) }; pump() }

  /** The name dialog, if the run ranks: a long name entered and saved. */
  private fun enterName() {
    pump()
    val fields = rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes()
    if (fields.isEmpty()) return
    rule.onAllNodes(hasSetTextAction())[0].performTextReplacement("WWWWWWWWWWWW"); pump()
    assertEverythingFits("name dialog")
    rule.onNodeWithText("SAVE").performClick(); pump()
  }

  /** Every text lies inside the screen, and no two texts sharing a line in one window overlap. */
  private fun assertEverythingFits(where: String) {
    pump(1200) // rows fade in one after another
    val roots = rule.onAllNodes(isRoot(), useUnmergedTree = true).fetchSemanticsNodes()
    // Bounds as drawn (a card shrunk to fit is measured at its drawn size), each window on its own
    val screen = roots.first().boundsInWindow
    var seen = 0
    for (root in roots) {
      val window = root.boundsInWindow
      val texts = mutableListOf<Pair<String, Rect>>()
      // A row that scrolls sideways (YOUR BOARDS) may run past the edge: only its height must fit
      fun walk(n: SemanticsNode, sideways: Boolean = false) {
        val inRow = sideways || SemanticsProperties.HorizontalScrollAxisRange in n.config
        if (SemanticsProperties.Text in n.config && !inRow) {
          val t = n.config[SemanticsProperties.Text].joinToString("")
          val b = n.boundsInWindow
          if (t.isNotBlank() && b.width > 0f && b.height > 0f) texts += t to b
        }
        n.children.forEach { walk(it, inRow) }
      }
      walk(root); seen += texts.size
      for ((t, b) in texts) assertTrue("$where: '$t' lies outside the screen ($b in $window, screen $screen)", b.left >= window.left - 1 && b.right <= window.right + 1 && b.top >= window.top - 1 && b.bottom <= window.bottom + 1)
      for (i in texts.indices) for (j in i + 1 until texts.size) {
        val (ta, a) = texts[i]; val (tb, b) = texts[j]
        if (a.contains(b) || b.contains(a)) continue // a label inside its own button or field
        val sameLine = minOf(a.bottom, b.bottom) - maxOf(a.top, b.top) > 0.5f * minOf(a.height, b.height)
        val overlap = minOf(a.right, b.right) - maxOf(a.left, b.left)
        assertFalse("$where: '$ta' runs into '$tb' ($a, $b)", sameLine && overlap > 2f)
      }
    }
    assertTrue("$where: some text on screen", seen > 0)
  }

  private fun Rect.contains(o: Rect) = o.left >= left && o.right <= right && o.top >= top && o.bottom <= bottom

  /** The longest names and times a list can hold, ten of each. */
  private fun fillTheBoards() = rule.runOnUiThread {
    val board: Leaderboard = held("leaderboard")
    for (i in 0 until Leaderboard.MAX) board.record(1_700_000_000_000L + i, if (i % 2 == 0) "WWWWWWWWWWWW" else "MMMMMMMMMMMM", Levels.COUNT - i * 7, 17_900f - i * 61f)
    val saves: SaveSlot = held("saveSlot")
    saves.write(57, 4100f, 1_700_000_000_999L, List(56) { 40f + it * 1.7f })
  }

  @Test
  fun aFinishedBoardOffersSaveAndRestartAndLeadsOn() {
    pump(); tapBoard() // level 1 starts
    finishBoard(); pump()
    rule.onNodeWithText("LEVEL 1 DONE").assertIsDisplayed()
    rule.onNodeWithText("SAVE").assertIsDisplayed()
    rule.onNodeWithText("RESTART").assertIsDisplayed()
    assertEverythingFits("level done")
    rule.onNodeWithText("SAVE").performClick(); pump()
    rule.onNodeWithText("SAVED").assertIsDisplayed()
    rule.onNodeWithText("RESTART").performClick(); pump()
    assertEquals("RESTART plays the board again", Phase.PLAYING, game.phase); assertEquals(1, game.level)
    finishBoard(); pump()
    rule.onNodeWithText("previous try", substring = true).assertIsDisplayed()
    assertEverythingFits("level done after a restart")
    tapBoard()
    assertEquals("a tap leads on to the next board", 2, game.level)
  }

  @Test
  fun aLostRunAsksForTheNameAndShowsTheTopBoards() {
    // A long run, so it ranks even on a full top 10, with a fastest board on record: lost on board 99
    fillTheBoards()
    rule.runOnUiThread { held<com.megapro.labyrinth.data.LevelBoards>("levelBoards").record(98, 1_700_000_002_000L, 31.5f) }
    pump()
    onGame { restore(99, 16_000f, 1_700_000_002_000L, List(98) { 60f + it }) }
    pump(); tapBoard()
    assertEquals(99, game.level)
    timeOut(); pump()
    assertTrue("a long run ranks: the name is asked", rule.onAllNodes(hasSetTextAction()).fetchSemanticsNodes().isNotEmpty())
    enterName()
    rule.onNodeWithText("Run over at level 99", substring = true).assertIsDisplayed() // the clock or a trap
    rule.onNodeWithText("TOP BOARDS PLAYERS").assertIsDisplayed()
    assertEverythingFits("run over")
    tapBoard()
    assertEquals("a tap starts again from level 1", Phase.TITLE, game.phase); assertEquals(1, game.level)
  }

  @Test
  fun allHundredBoardsDoneShowsTheFinale() {
    pump()
    onGame { restore(Levels.COUNT, 17_000f, 1_700_000_001_000L, List(Levels.COUNT - 1) { 60f + it }) }
    pump(); tapBoard()
    finishBoard(); pump()
    rule.onNodeWithText("LEVEL ${Levels.COUNT} DONE").assertIsDisplayed()
    assertTrue("no SAVE after the last board", rule.onAllNodes(hasText("SAVE")).fetchSemanticsNodes().isEmpty())
    assertEverythingFits("last board done")
    tapBoard(); enterName()
    rule.onNodeWithText("ALL ${Levels.COUNT} LEVELS DONE", substring = true).assertIsDisplayed()
    assertEverythingFits("all done")
  }

  @Test
  fun theMainScreenAndTheTopTenFitWhenFull() {
    fillTheBoards()
    rule.runOnUiThread { rule.activity.recreate() } // read the filled boards afresh
    rule.waitForIdle(); pump()
    rule.onNodeWithText("LOAD").assertIsDisplayed()
    rule.onNodeWithText("TOP 10").assertIsDisplayed()
    assertEverythingFits("main screen, full")
    rule.onNodeWithText("TOP 10").performClick(); pump()
    rule.onNodeWithText("CLOSE").assertIsDisplayed()
    assertEverythingFits("top 10 card, full")
    rule.onNodeWithText("CLOSE").performClick(); pump()
    rule.onNodeWithText("TILT LABYRINTH").assertIsDisplayed()
  }

  @Test
  fun aSavedBoardReplaysFreelyAndBackReturnsToTheMenu() {
    fillTheBoards()
    rule.runOnUiThread { rule.activity.recreate() }
    rule.waitForIdle(); pump()
    rule.onNodeWithText("YOUR BOARDS").assertIsDisplayed()
    rule.onAllNodes(hasText("1", substring = false))[0].performClick(); pump()
    assertTrue("a board card replays it freely", game.freePlay); assertEquals(1, game.level)
    pressBack()
    rule.onNodeWithText("TILT LABYRINTH").assertIsDisplayed()
    assertFalse(game.freePlay)
  }

  @Test
  fun holdingTheTitleOpensTheTestBoardAndBackReturnsToTheMenu() {
    pump()
    board().performTouchInput { longClick(androidx.compose.ui.geometry.Offset(width * 0.05f, height * 0.3f), durationMillis = 5600) } // beside the card, never on a button
    pump()
    assertEquals("the test board", 0, game.level); assertEquals(Phase.PLAYING, game.phase)
    pressBack()
    rule.onNodeWithText("TILT LABYRINTH").assertIsDisplayed()
    assertEquals(1, game.level)
  }

  @Test
  fun backDuringARunPausesItAndNeverThrowsItAway() {
    pump(); tapBoard()
    assertEquals(Phase.PLAYING, game.phase)
    pressBack()
    rule.onNodeWithText("PAUSED").assertIsDisplayed()
    pressBack() // a second Back steps out of the game
    assertFalse("the game is kept, not closed", rule.activity.isFinishing)
    assertEquals("the run waits paused", Phase.PAUSED, game.phase); assertEquals(1, game.level)
  }
}
