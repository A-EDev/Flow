package io.github.aedev.flow.widget.playlist

import android.app.Application
import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.testing.unit.runGlanceAppWidgetUnitTest
import androidx.glance.testing.unit.hasContentDescriptionEqualTo
import androidx.glance.testing.unit.hasTextEqualTo
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.model.Video
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.annotation.ConscryptMode

@RunWith(AndroidJUnit4::class)
@Config(sdk = [34], application = Application::class)
@ConscryptMode(ConscryptMode.Mode.OFF)
class PlaylistContentTest {
    private val context: Context = ApplicationProvider.getApplicationContext()

    private val watchLater =
        WidgetPlaylist(id = PlaylistRepository.WATCH_LATER_ID, name = "Watch later", count = 2, coverUrl = null, isMusic = false)

    private fun video(
        id: String,
        title: String,
        channel: String,
        seconds: Int,
    ) = Video(
        id = id,
        title = title,
        channelName = channel,
        channelId = "c",
        thumbnailUrl = "",
        duration = seconds,
        viewCount = 0,
        uploadDate = "",
    )

    private val items =
        listOf(
            video("a", "Building a cabin by hand", "Northwoods", 754),
            video("b", "The quiet history of maps", "Atlas Lab", 3_725),
        )

    @Test
    fun theListShowsEachVideoWithItsChannelAndLength() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(ListCell)
            setContext(context)
            provideComposable { PlaylistContent(WidgetPlaylistContent(watchLater, items), null, emptyMap(), choose = null) }
            onNode(hasTextEqualTo("Watch later")).assertExists()
            onNode(hasTextEqualTo("Building a cabin by hand")).assertExists()
            onNode(hasTextEqualTo("Northwoods")).assertExists()
            onNode(hasTextEqualTo("12:34")).assertExists()
            onNode(hasTextEqualTo("1:02:05")).assertExists()
        }

    @Test
    fun theListHeaderOffersRefreshAndPlay() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(ListCell)
            setContext(context)
            provideComposable { PlaylistContent(WidgetPlaylistContent(watchLater, items), null, emptyMap(), choose = null) }
            onNode(hasContentDescriptionEqualTo("Refresh")).assertExists()
            onNode(hasContentDescriptionEqualTo("Shuffle")).assertExists()
        }

    @Test
    fun anEmptyPlaylistSaysSoAndOffersNothingToPlay() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(ListCell)
            setContext(context)
            provideComposable {
                PlaylistContent(WidgetPlaylistContent(watchLater.copy(count = 0), emptyList()), null, emptyMap(), choose = null)
            }
            onNode(hasTextEqualTo("Nothing here yet")).assertExists()
            onNode(hasContentDescriptionEqualTo("Shuffle")).assertDoesNotExist()
        }

    @Test
    fun whileRefreshingTheButtonGivesWayToProgress() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(ListCell)
            setContext(context)
            provideComposable {
                PlaylistContent(WidgetPlaylistContent(watchLater, items), null, emptyMap(), choose = null, refreshing = true)
            }
            onNode(hasContentDescriptionEqualTo("Refresh")).assertDoesNotExist()
        }

    @Test
    fun aDeletedPlaylistAsksForAnother() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(ListCell)
            setContext(context)
            provideComposable { PlaylistContent(WidgetPlaylistContent(null, emptyList()), null, emptyMap(), choose = null) }
            onNode(hasTextEqualTo("Choose a playlist")).assertExists()
        }

    @Test
    fun aSmallCellShowsTheCountUnderTheName() =
        runGlanceAppWidgetUnitTest {
            setAppWidgetSize(DpSize(170.dp, 180.dp))
            setContext(context)
            provideComposable { PlaylistContent(WidgetPlaylistContent(watchLater, items), null, emptyMap(), choose = null) }
            onNode(hasTextEqualTo("2 videos")).assertExists()
            onNode(hasTextEqualTo("Northwoods")).assertDoesNotExist()
        }

    private companion object {
        val ListCell = DpSize(320.dp, 200.dp)
    }
}
