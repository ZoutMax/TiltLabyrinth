package com.megapro.labyrinth

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.compose.ui.test.performTouchInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The game on a real device: the main screen comes up, a tap starts level 1, another pauses.
 * The board animates on every frame, so Compose never goes idle by itself: the test clock is
 * paused and pumped by hand instead of waiting for an idle that cannot come.
 */
@RunWith(AndroidJUnit4::class)
class TitleScreenTest {
  @get:Rule val rule = createAndroidComposeRule<MainActivity>()

  @Before fun pumpByHand() { rule.mainClock.autoAdvance = false }

  private fun pump(ms: Long = 600) = rule.mainClock.advanceTimeBy(ms)

  @Test
  fun theMainScreenShowsTheTitleAndTheMusicHint() {
    pump()
    rule.onNodeWithText("TILT LABYRINTH").assertIsDisplayed()
    rule.onNodeWithText("Hold the level counter", substring = true).assertIsDisplayed()
    rule.onNodeWithText("Hold anywhere for free play.").assertIsDisplayed()
  }

  @Test
  fun tappingStartsPausesAndResumesTheGame() {
    pump()
    rule.onNodeWithText("TILT LABYRINTH").assertIsDisplayed()
    rule.onRoot().performTouchInput { click(Offset(width * 0.05f, height * 0.3f)) }
    pump()
    assert(rule.onAllNodes(hasText("TILT LABYRINTH")).fetchSemanticsNodes().isEmpty()) { "the title should give way to the board" }
    rule.onRoot().performTouchInput { click(Offset(width * 0.05f, height * 0.3f)) }
    pump()
    rule.onNodeWithText("PAUSED").assertIsDisplayed()
    rule.onNodeWithText("Tap to resume", substring = true).assertIsDisplayed()
    assert(rule.onAllNodes(hasText("RE-LEVEL")).fetchSemanticsNodes().isEmpty()) { "no re-level button" }
    rule.onRoot().performTouchInput { click(Offset(width * 0.05f, height * 0.3f)) }
    pump()
    assert(rule.onAllNodes(hasText("PAUSED")).fetchSemanticsNodes().isEmpty()) { "a tap resumes the game" }
  }
}
