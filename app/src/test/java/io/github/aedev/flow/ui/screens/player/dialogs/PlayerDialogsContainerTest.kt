package io.github.aedev.flow.ui.screens.player.dialogs

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.ui.test.assertCountEquals
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onChildren
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.onRoot
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.DownloadDialogStyle
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.ui.screens.player.fakePlayerState
import io.github.aedev.flow.ui.screens.player.fakeUiState
import io.github.aedev.flow.ui.screens.player.fakeVideo
import io.github.aedev.flow.ui.screens.player.relaxedVideoPlayerViewModel
import io.github.aedev.flow.ui.screens.player.state.PlayerScreenState
import kotlinx.coroutines.runBlocking
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Pins which surface each [PlayerScreenState] dialog flag mounts through [PlayerDialogsContainer],
 * including the flag that mounts nothing today.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class PlayerDialogsContainerTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private fun string(id: Int) = context.getString(id)

    private fun setContainer(screenState: PlayerScreenState) {
        val video = fakeVideo()
        rule.setContent {
            MaterialTheme {
                PlayerDialogsContainer(
                    screenState = screenState,
                    playerState = fakePlayerState(),
                    uiState = fakeUiState(video = video),
                    video = video,
                    viewModel = relaxedVideoPlayerViewModel(),
                )
            }
        }
        rule.waitForIdle()
    }

    private fun waitForText(id: Int) {
        rule.waitUntil(timeoutMillis = 5_000) {
            rule.onAllNodesWithText(string(id)).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun setDownloadDialogStyle(style: DownloadDialogStyle) {
        runBlocking { PlayerPreferences(context).setDownloadDialogStyle(style) }
    }

    @Test
    fun rendersNothingWhenNoFlagIsSet() {
        setContainer(PlayerScreenState())

        rule.onRoot().onChildren().assertCountEquals(0)
    }

    @Test
    fun showSettingsMenuMountsTheSettingsSheet() {
        setContainer(PlayerScreenState().apply { showSettingsMenu = true })

        rule.onNodeWithText(string(R.string.player_settings)).assertExists()
    }

    @Test
    fun showQualitySelectorOpensTheSheetOnTheQualityPage() {
        setContainer(PlayerScreenState().apply { showQualitySelector = true })

        rule.onNodeWithText(string(R.string.video_quality_title)).assertExists()
        rule.onNodeWithText(string(R.string.player_settings)).assertDoesNotExist()
    }

    @Test
    fun showDownloadDialogMountsTheFullDialogWhenPreferred() {
        setDownloadDialogStyle(DownloadDialogStyle.FULL)
        setContainer(PlayerScreenState().apply { showDownloadDialog = true })

        waitForText(R.string.download_video)
        rule.onNodeWithText(string(R.string.select_quality)).assertExists()
    }

    @Test
    @Ignore(
        "Any Compose text field inside a Dialog window never reaches idle under Robolectric with " +
            "Compose 1.13.0-alpha01; DownloadQualityDialogCompact's title field trips it. " +
            "Device twin: DownloadDialogsInstrumentedTest.",
    )
    fun showDownloadDialogMountsTheCompactDialogWhenPreferred() {
        setDownloadDialogStyle(DownloadDialogStyle.COMPACT)
        setContainer(PlayerScreenState().apply { showDownloadDialog = true })

        waitForText(R.string.download_video)
        rule.onNodeWithText(string(R.string.download_title_label)).assertExists()
    }

    @Test
    fun showDlnaDialogMountsNothing() {
        setContainer(PlayerScreenState().apply { showDlnaDialog = true })

        // Pins the DLNA defect fixed in Phase 2: the flag is written by the cast row but no
        // surface in this container reads it, so casting silently shows nothing.
        rule.onRoot().onChildren().assertCountEquals(0)
    }

    @Test
    fun showSubtitleStyleCustomizerMountsTheStyleSheet() {
        setContainer(PlayerScreenState().apply { showSubtitleStyleCustomizer = true })

        waitForText(R.string.filter_subtitles)
        rule.onNodeWithContentDescription(string(R.string.back)).assertExists()
    }
}
