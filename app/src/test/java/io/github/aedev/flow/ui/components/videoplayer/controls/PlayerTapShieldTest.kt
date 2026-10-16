package io.github.aedev.flow.ui.components.videoplayer.controls

import android.app.Application
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.dp
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The shield has to do two things at once: keep the tap from the button under it, and leave the
 * tap unconsumed for the player's detector above it, which ignores consumed ups.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class PlayerTapShieldTest {
    @get:Rule
    val rule = createComposeRule()

    private var buttonClicks = 0
    private var playerTaps = 0

    private fun setContent(shielded: Boolean) {
        rule.setContent {
            Box(
                modifier =
                    Modifier.size(200.dp).pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(requireUnconsumed = false)
                            if (waitForUpOrCancellation() != null) playerTaps++
                        }
                    },
            ) {
                Box(Modifier.size(200.dp).testTag("button").clickable { buttonClicks++ })
                if (shielded) PlayerTapShield(Modifier.size(200.dp))
            }
        }
    }

    @Test
    fun `without the shield the button takes the tap`() {
        setContent(shielded = false)

        rule.onNodeWithTag("button").performTouchInput { click() }
        rule.waitForIdle()

        assertThat(buttonClicks).isEqualTo(1)
        assertThat(playerTaps).isEqualTo(0)
    }

    @Test
    fun `with the shield the player gets the tap and the button does not`() {
        setContent(shielded = true)

        rule.onNodeWithTag("button").performTouchInput { click() }
        rule.waitForIdle()

        assertThat(buttonClicks).isEqualTo(0)
        assertThat(playerTaps).isEqualTo(1)
    }
}
