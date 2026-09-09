package io.github.aedev.flow.ui.components.shared

import android.app.Application
import android.content.Context
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.semantics.SemanticsProperties
import androidx.compose.ui.semantics.getOrNull
import androidx.compose.ui.test.hasClickAction
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.isRoot
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import io.github.aedev.flow.ui.screens.player.fakeAudioFormats
import io.github.aedev.flow.ui.screens.player.fakeVideo
import io.github.aedev.flow.ui.screens.player.fakeVideoFormats
import org.junit.Ignore
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * Pins that the full and compact download dialogs derive the same resolution ladder from the
 * same InnerTube formats, so one can replace the other without losing an option.
 */
@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class, qualifiers = "w411dp-h891dp")
class DownloadDialogsTest {
    @get:Rule
    val rule = createComposeRule()

    private val context: Context
        get() = ApplicationProvider.getApplicationContext()

    private var showCompact by mutableStateOf(false)

    private fun setDialogs() {
        val video = fakeVideo()
        rule.setContent {
            MaterialTheme {
                if (showCompact) {
                    MediaDownloadDialogCompact(
                        streamInfo = null,
                        streamSizes = emptyMap(),
                        innerTubeVideoFormats = fakeVideoFormats(),
                        innerTubeAudioFormats = fakeAudioFormats(),
                        video = video,
                        onDismiss = {},
                    )
                } else {
                    MediaDownloadDialog(
                        streamInfo = null,
                        streamSizes = emptyMap(),
                        innerTubeVideoFormats = fakeVideoFormats(),
                        innerTubeAudioFormats = fakeAudioFormats(),
                        video = video,
                        onDismiss = {},
                    )
                }
            }
        }
        rule.waitForIdle()
    }

    /** Every height that appears in a rendered text node shaped like `... 1080p` or `1080p`. */
    private fun renderedHeights(): Set<Int> =
        rule
            .onAllNodes(!isRoot(), useUnmergedTree = true)
            .fetchSemanticsNodes()
            .flatMap { node -> node.config.getOrNull(SemanticsProperties.Text).orEmpty() }
            .mapNotNull { text ->
                HEIGHT_LABEL
                    .matchEntire(text.text)
                    ?.groupValues
                    ?.get(1)
                    ?.toInt()
            }.toSet()

    @Test
    fun fullDialogListsEveryResolutionAndAnAudioOnlySection() {
        setDialogs()

        rule.onNodeWithText(context.getString(R.string.download_video)).assertExists()
        assertThat(renderedHeights()).containsExactly(1080, 720)
        // "Audio Only" is a hardcoded literal at VideoPlayerDialogs.kt:309 rather than a string
        // resource; pinned as-is so its move into strings.xml is an explicit change.
        rule.onNodeWithText("Audio Only").assertExists()
    }

    @Test
    @Ignore(
        "Any Compose text field inside a Dialog window never reaches idle under Robolectric with " +
            "Compose 1.13.0-alpha01 (verified with filled/outlined, label/no label, NATIVE and LEGACY " +
            "graphics); the compact dialog's title field trips it. Device twin: DownloadDialogsInstrumentedTest.",
    )
    fun compactDialogOffersTheSameResolutionsAsTheFullDialog() {
        setDialogs()
        val fullHeights = renderedHeights()

        rule.runOnUiThread { showCompact = true }
        rule.waitForIdle()
        rule.onNodeWithText(context.getString(R.string.download_title_label)).assertExists()
        rule.onNode(hasText("1080p") and hasClickAction()).performClick()
        rule.waitForIdle()

        assertThat(renderedHeights()).isEqualTo(fullHeights)
        assertThat(fullHeights).containsExactly(1080, 720)
    }

    private companion object {
        val HEIGHT_LABEL = Regex("""^(?:[A-Z0-9]+ )?(\d+)p$""")
    }
}
