package io.github.aedev.flow.ui.screens.player.effects

import android.app.Application
import androidx.activity.BackEventCompat
import androidx.activity.ComponentActivity
import androidx.compose.animation.core.Animatable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.ui.components.videoplayer.PlayerDraggableState
import io.github.aedev.flow.ui.components.videoplayer.PlayerSheetValue
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * The sheet's back gesture must never leave its progress flow uncollected: activity-compose then
 * throws "You must collect the progress flow" and takes the app down (#1307).
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class PlayerBackEffectsTest {
    @get:Rule
    val rule = createAndroidComposeRule<ComponentActivity>()

    private lateinit var sheet: PlayerDraggableState

    private fun setContent() {
        rule.setContent {
            val scope = rememberCoroutineScope()
            sheet = remember { PlayerDraggableState(Animatable(0f), Animatable(0f), Animatable(0f), scope) }
            PlayerBackEffects(
                playerSheetState = sheet,
                screenState = remember { PlayerScreenState() },
                isInPipMode = false,
            )
        }
        rule.waitForIdle()
    }

    private val backDispatcher get() = rule.activity.onBackPressedDispatcher

    private fun backStarted() = backDispatcher.dispatchOnBackStarted(BackEventCompat(0f, 0f, 0f, BackEventCompat.EDGE_LEFT))

    /** Holds the sheet's offsets move past its own cancellation, so the scrub waits inside beginBackScrub. */
    private fun holdOffsetsMove(): CompletableDeferred<Unit> {
        val release = CompletableDeferred<Unit>()
        rule.runOnIdle {
            sheet.scope.launch { sheet.motion.moveOffsets { withContext(NonCancellable) { release.await() } } }
        }
        rule.waitForIdle()
        return release
    }

    @Test
    fun `a back gesture cancelled before the scrub starts springs the sheet back without crashing`() {
        setContent()
        val release = holdOffsetsMove()

        rule.runOnIdle { backStarted() }
        rule.waitForIdle()
        rule.runOnIdle { backDispatcher.dispatchOnBackCancelled() }
        rule.runOnIdle { release.complete(Unit) }
        rule.waitForIdle()

        assertThat(sheet.currentValue).isEqualTo(PlayerSheetValue.Expanded)
    }

    @Test
    fun `a completed back gesture collapses the sheet`() {
        setContent()

        rule.runOnIdle { backStarted() }
        rule.waitForIdle()
        rule.runOnIdle { backDispatcher.dispatchOnBackProgressed(BackEventCompat(0f, 0f, 0.5f, BackEventCompat.EDGE_LEFT)) }
        rule.waitForIdle()
        rule.runOnIdle { backDispatcher.onBackPressed() }
        rule.waitForIdle()

        assertThat(sheet.currentValue).isEqualTo(PlayerSheetValue.Collapsed)
    }
}
