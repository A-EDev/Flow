package io.github.aedev.flow.ui.screens.player

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasScrollToNodeAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import kotlinx.coroutines.flow.MutableStateFlow
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Pins which detail layout [EnhancedVideoPlayerScreen] picks for each window class. The window is
 * shaped through Robolectric qualifiers so both the activity window and `LocalConfiguration`
 * (which [io.github.aedev.flow.ui.screens.player.state.playerLayoutModeFor] reads) agree.
 *
 * Related videos are deliberately absent: every related card calls `hiltViewModel()`
 * (VideoCard.kt, `VideoCardFullWidth` / `CompactVideoCard`), which androidx.hilt 1.4.0 resolves
 * through the host activity's Hilt component, so the two-per-row tablet grid cannot be mounted
 * under a plain `ComponentActivity`. The grid-versus-list choice itself is covered by
 * `PlayerLayoutModeTest`; here the observable is the side column, which only the WIDE layout
 * mounts.
 */
@OptIn(UnstableApi::class)
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
class EnhancedVideoPlayerScreenLayoutTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private val video = fakeVideo()

    private fun setScreen() {
        val uiState = MutableStateFlow(fakeUiState(video = video, isLiveChatAvailable = true))
        val viewModel = relaxedVideoPlayerViewModel(uiState = uiState)
        val screenState = PlayerScreenState()
        rule.setContent {
            MaterialTheme {
                EnhancedVideoPlayerScreen(
                    viewModel = viewModel,
                    video = video,
                    alpha = { 1f },
                    screenState = screenState,
                    onVideoClick = {},
                    onChannelClick = {},
                )
            }
        }
        rule.waitForIdle()
    }

    /** The close button on the side column's live chat header; nothing else in the tree carries it. */
    private val sideColumnCloseChat
        get() = rule.onNodeWithContentDescription(context.getString(R.string.close))

    private fun assertSingleColumn() {
        rule.onNodeWithText(video.title).assertExists()
        rule.onNode(hasScrollToNodeAction() and hasAnyDescendant(hasText(video.title))).assertExists()
        sideColumnCloseChat.assertDoesNotExist()
    }

    @Test
    @Config(qualifiers = "sw411dp-w411dp-h891dp-port")
    fun phonePortraitIsOneLazyColumn() {
        setScreen()

        assertSingleColumn()
    }

    @Test
    @Config(qualifiers = "sw411dp-w891dp-h411dp-land")
    fun phoneLandscapeStaysCompact() {
        setScreen()

        assertSingleColumn()
    }

    @Test
    @Config(qualifiers = "sw600dp-w600dp-h960dp-port")
    fun tabletPortraitHasNoSideColumn() {
        setScreen()

        assertSingleColumn()
    }

    @Test
    @Config(qualifiers = "sw800dp-w1280dp-h800dp-land")
    fun tabletLandscapeAddsTheSideColumn() {
        setScreen()

        rule.onNodeWithText(video.title).assertExists()
        sideColumnCloseChat.assertExists()
    }
}
