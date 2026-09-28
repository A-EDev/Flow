package io.github.aedev.flow.widget.nowplaying

import android.app.Application
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasText
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aedev.flow.widget.core.state.NowPlayingSnapshot
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class NowPlayingContentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private fun snapshot(
        isPlaying: Boolean = false,
        isLiked: Boolean = false,
    ) = NowPlayingSnapshot(
        mediaId = "id",
        title = "Midnight Transit",
        artist = "Harbor Lights",
        artworkUrl = null,
        isPlaying = isPlaying,
        isLiked = isLiked,
        positionMs = 65_000L,
        durationMs = 180_000L,
        capturedAtElapsedMs = 1_000L,
    )

    @Test
    fun aPausedCardShowsTheTrackAndAStillClock() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(RoomyCard)
            setContext(context)
            provideComposable { NowPlayingContent(snapshot(), artwork = null) }
            onNode(hasTextEqualTo("Midnight Transit")).assertExists()
            onNode(hasTextEqualTo("Harbor Lights")).assertExists()
            onNode(hasTextEqualTo("01:05")).assertExists()
            onNode(hasTextEqualTo("03:00")).assertExists()
        }

    @Test
    fun whilePlayingTheElapsedTimeIsLeftToTheLaunchersClock() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(RoomyCard)
            setContext(context)
            provideComposable { NowPlayingContent(snapshot(isPlaying = true), artwork = null) }
            onNode(hasTextEqualTo("01:05")).assertDoesNotExist()
            onNode(hasTextEqualTo("03:00")).assertExists()
        }

    @Test
    fun aLikedTrackOffersToRemoveTheLike() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(NowPlayingLayout.POSTER.size)
            setContext(context)
            provideComposable { NowPlayingContent(snapshot(isLiked = true), artwork = null) }
            onNode(hasContentDescriptionEqualTo("Remove from liked")).assertExists()
        }

    @Test
    fun theSmallLayoutLeadsWithTheTitle() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(NowPlayingLayout.SMALL.size)
            setContext(context)
            provideComposable { NowPlayingContent(snapshot(), artwork = null) }
            onNode(hasTextEqualTo("Midnight Transit")).assertExists()
        }

    @Test
    fun nothingPlayingSaysSo() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(NowPlayingLayout.STRIP.size)
            setContext(context)
            provideComposable { NowPlayingContent(snapshot = null, artwork = null) }
            onNode(hasText("Nothing playing")).assertExists()
        }
}

private val RoomyCard = DpSize(340.dp, 176.dp)
