package io.github.aedev.flow.ui.components.videoplayer.controls

import android.app.Application
import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.Modifier
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsNotDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Pins the visibility contract of [PremiumControlsOverlay]: what is placed when the controls are
 * shown, hidden, or touch-locked, and what the bottom pill row says about quality and duration.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class PremiumControlsOverlayTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun string(id: Int) = context.getString(id)

    private fun setOverlay(
        isVisible: Boolean = true,
        isPlaying: Boolean = false,
        isLive: Boolean = false,
        isTouchLocked: Boolean = false,
        qualityLabel: String? = "1080p",
        duration: Long = 125_000L,
        onPlayPause: () -> Unit = {},
    ) {
        rule.setContent {
            MaterialTheme {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(231.dp),
                ) {
                    PremiumControlsOverlay(
                        isVisible = isVisible,
                        isPlaying = isPlaying,
                        hasEnded = false,
                        isBuffering = false,
                        currentPosition = { 0L },
                        duration = duration,
                        qualityLabel = qualityLabel,
                        videoTitle = "Fixture video",
                        resizeMode = 0,
                        onResizeClick = {},
                        onPlayPause = onPlayPause,
                        onSeek = {},
                        onBack = {},
                        onSettingsClick = {},
                        onFullscreenClick = {},
                        isFullscreen = false,
                        isLive = isLive,
                        isTouchLocked = isTouchLocked,
                    )
                }
            }
        }
    }

    @Test
    fun visibleControlsShowPlayAndBack() {
        setOverlay(isVisible = true)

        rule.onNodeWithContentDescription(string(R.string.play)).assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.btn_minimize)).assertIsDisplayed()
    }

    @Test
    fun playControlReportsTheTap() {
        var toggled = false
        setOverlay(onPlayPause = { toggled = true })

        rule.onNodeWithContentDescription(string(R.string.play)).performClick()

        assertThat(toggled).isTrue()
    }

    @Test
    fun hiddenControlsStayComposedButAreNotPlaced() {
        setOverlay(isVisible = false)
        rule.waitForIdle()

        rule.onNodeWithContentDescription(string(R.string.play)).assertExists().assertIsNotDisplayed()
        rule.onNodeWithContentDescription(string(R.string.btn_minimize)).assertExists().assertIsNotDisplayed()
    }

    @Test
    fun touchLockShowsTheUnlockAffordanceAndHidesTheTransport() {
        setOverlay(isTouchLocked = true)

        rule.onNodeWithContentDescription(string(R.string.player_unlock_controls)).assertIsDisplayed()
        rule.onNodeWithContentDescription(string(R.string.play)).assertDoesNotExist()
        rule.onNodeWithContentDescription(string(R.string.btn_minimize)).assertDoesNotExist()
    }

    @Test
    fun qualityPillShowsTheCompactLabel() {
        setOverlay(qualityLabel = "1080p")

        // The compact label table is a literal mapping inside PremiumControlsOverlay.kt
        // (toCompactQualityLabel: 1080 -> "FHD"), so the expectation is pinned as a literal too.
        rule.onNodeWithText("FHD").assertIsDisplayed()
    }

    @Test
    fun durationIsShownForVideoOnDemand() {
        setOverlay(isLive = false, duration = 125_000L)

        rule.onNodeWithText("02:05").assertIsDisplayed()
        rule.onNodeWithText(string(R.string.player_live_label)).assertDoesNotExist()
    }

    @Test
    fun liveHidesTheDurationAndShowsTheLiveLabel() {
        // The live badge pulses forever, so the frame clock is driven by hand instead of
        // waiting for an idle it can never reach.
        rule.mainClock.autoAdvance = false
        setOverlay(isLive = true, duration = 125_000L)
        rule.mainClock.advanceTimeBy(1_000L)

        rule.onNodeWithText("02:05").assertDoesNotExist()
        rule.onNodeWithText(string(R.string.player_live_label)).assertExists()
    }
}
