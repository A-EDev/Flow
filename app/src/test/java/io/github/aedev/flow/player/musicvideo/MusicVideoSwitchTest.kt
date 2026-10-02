package io.github.aedev.flow.player.musicvideo

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import io.github.aedev.flow.data.music.model.MusicArtist
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.video.MusicVideoVersions
import io.github.aedev.flow.innertube.models.Artist
import io.github.aedev.flow.innertube.models.SongItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.Job
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class MusicVideoSwitchTest {
    private class FakeQueue(
        tracks: List<MusicTrack>,
    ) : MusicVideoQueue {
        override val entries = MutableStateFlow(0 to tracks)
        val withPicture = mutableSetOf<String>()

        val tracks get() = entries.value.second

        override fun showsVideo(videoId: String) = videoId in withPicture

        override fun replace(
            index: Int,
            expectedId: String,
            track: MusicTrack,
            showsVideo: Boolean,
        ): Boolean {
            val (current, queue) = entries.value
            if (queue.getOrNull(index)?.videoId != expectedId) return false
            withPicture -= expectedId
            if (showsVideo) withPicture += track.videoId
            entries.value = current to queue.toMutableList().also { it[index] = track }
            return true
        }
    }

    private fun song(
        id: String,
        title: String,
    ) = MusicTrack(id, title, "Artist", "", 200, channelId = "artist", artists = listOf(MusicArtist("Artist", "artist")))

    private fun officialVideo(
        id: String,
        title: String,
    ) = SongItem(id, title, listOf(Artist("Artist", "artist")), duration = 230, musicVideoType = "MUSIC_VIDEO_TYPE_OMV", thumbnail = "")

    private val first = song("s1", "First")
    private val second = song("s2", "Second")
    private val third = song("s3", "Third")
    private val videos = mapOf("First" to officialVideo("v1", "First"), "Second" to officialVideo("v2", "Second"))
    private val warnings = mutableListOf<Int>()
    private var searchFails = false

    private fun TestScope.attached(
        queue: FakeQueue,
        enabled: MutableStateFlow<Boolean> = MutableStateFlow(true),
    ): Pair<MusicVideoSwitch, CoroutineScope> {
        val versions =
            MusicVideoVersions { query ->
                if (searchFails) throw IOException("offline")
                listOfNotNull(videos.entries.firstOrNull { query.endsWith(it.key) }?.value)
            }
        val switch = MusicVideoSwitch(versions, queue, prepareVideo = { true }, warn = { warnings += it })
        val scope = CoroutineScope(coroutineContext + Job())
        switch.attach(scope, enabled)
        return switch to scope
    }

    @Test
    fun `Video swaps the playing song and the next one for their videos, counted as the songs`() =
        runTest(UnconfinedTestDispatcher()) {
            val queue = FakeQueue(listOf(first, second, third))
            val (switch, scope) = attached(queue)

            switch.select(video = true)
            advanceUntilIdle()

            assertThat(queue.tracks.map { it.videoId }).containsExactly("v1", "v2", "s3").inOrder()
            assertThat(queue.withPicture).containsExactly("v1", "v2")
            assertThat(queue.tracks.first().title).isEqualTo("First")
            assertThat(switch.listenId("v1")).isEqualTo("s1")
            assertThat(switch.songFor("v2")).isEqualTo(second)
            assertThat(switch.consumeSwap("v1")).isTrue()
            assertThat(switch.consumeSwap("v1")).isFalse()
            assertThat(warnings).isEmpty()
            scope.cancel()
        }

    @Test
    fun `Song puts the songs back`() =
        runTest(UnconfinedTestDispatcher()) {
            val queue = FakeQueue(listOf(first, second, third))
            val (switch, scope) = attached(queue)
            switch.select(video = true)
            advanceUntilIdle()

            switch.select(video = false)
            advanceUntilIdle()

            assertThat(queue.tracks).containsExactly(first, second, third).inOrder()
            assertThat(queue.withPicture).isEmpty()
            assertThat(switch.consumeSwap("s1")).isTrue()
            scope.cancel()
        }

    @Test
    fun `asking for a video the song does not have falls back to Song and says why`() =
        runTest(UnconfinedTestDispatcher()) {
            val queue = FakeQueue(listOf(third, first))
            val (switch, scope) = attached(queue)

            switch.select(video = true)
            advanceUntilIdle()

            assertThat(switch.videoMode.value).isFalse()
            assertThat(warnings).containsExactly(R.string.music_video_unavailable)
            assertThat(queue.tracks).containsExactly(third, first).inOrder()
            scope.cancel()
        }

    @Test
    fun `a later song without a video keeps its artwork and leaves Video on`() =
        runTest(UnconfinedTestDispatcher()) {
            val queue = FakeQueue(listOf(first, third))
            val (switch, scope) = attached(queue)
            switch.select(video = true)
            advanceUntilIdle()

            queue.entries.value = 1 to queue.tracks
            advanceUntilIdle()

            assertThat(switch.videoMode.value).isTrue()
            assertThat(queue.tracks[1]).isEqualTo(third)
            assertThat(warnings).isEmpty()
            scope.cancel()
        }

    @Test
    fun `a search that fails is reported, not taken for a song without a video`() =
        runTest(UnconfinedTestDispatcher()) {
            searchFails = true
            val queue = FakeQueue(listOf(first))
            val (switch, scope) = attached(queue)

            switch.select(video = true)
            advanceUntilIdle()

            assertThat(switch.videoMode.value).isFalse()
            assertThat(warnings).containsExactly(R.string.music_video_failed)
            scope.cancel()
        }

    @Test
    fun `a track that is a video shows its own picture without a swap`() =
        runTest(UnconfinedTestDispatcher()) {
            val musicVideo = third.copy(isVideoSong = true)
            val queue = FakeQueue(listOf(musicVideo))
            val (switch, scope) = attached(queue)

            switch.select(video = true)
            advanceUntilIdle()

            assertThat(queue.tracks).containsExactly(musicVideo)
            assertThat(queue.withPicture).containsExactly("s3")
            assertThat(switch.listenId("s3")).isEqualTo("s3")
            assertThat(switch.consumeSwap("s3")).isTrue()
            scope.cancel()
        }

    @Test
    fun `turning the switch off in Settings returns to Song`() =
        runTest(UnconfinedTestDispatcher()) {
            val enabled = MutableStateFlow(true)
            val queue = FakeQueue(listOf(first))
            val (switch, scope) = attached(queue, enabled)
            switch.select(video = true)
            advanceUntilIdle()

            enabled.value = false
            advanceUntilIdle()

            assertThat(switch.videoMode.value).isFalse()
            assertThat(queue.tracks).containsExactly(first)
            scope.cancel()
        }
}
