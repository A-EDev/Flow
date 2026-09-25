package io.github.aedev.flow.ui.screens.music.collection

import android.content.Context
import androidx.lifecycle.SavedStateHandle
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.local.entity.PlaylistEntity
import io.github.aedev.flow.data.model.Video
import io.github.aedev.flow.data.music.YouTubeMusicService
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.music.model.PlaylistDetails
import io.github.aedev.flow.data.recommendation.music.DailyMixStore
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicCollectionViewModelTest {
    private val context = mockk<Context>(relaxed = true).also { every { it.getString(any()) } returns "text" }
    private val playlists = mockk<PlaylistRepository>(relaxed = true)

    @Before
    fun setUp() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        mockkObject(YouTubeMusicService)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    private fun viewModel(id: String) =
        MusicCollectionViewModel(
            context,
            SavedStateHandle(mapOf(MUSIC_COLLECTION_ARG to id)),
            playlists,
            DailyMixStore(),
            mockk(relaxed = true),
        )

    private fun entity(
        id: String,
        own: Boolean,
    ) = PlaylistEntity(
        id = id,
        name = "Road trip",
        description = "",
        thumbnailUrl = "",
        isPrivate = true,
        createdAt = 0L,
        isMusic = true,
        isUserCreated = own,
    )

    private fun video(id: String) =
        Video(
            id = id,
            title = id,
            channelName = "Artist",
            channelId = "UC1",
            thumbnailUrl = "t",
            duration = 226,
            viewCount = 0L,
            uploadDate = "",
            addedAtInPlaylist = 7L,
        )

    private fun track(id: String) = MusicTrack(videoId = id, title = id, artist = "Artist", thumbnailUrl = "t", duration = 200)

    private fun remote(
        id: String,
        tracks: List<MusicTrack>,
        continuation: String? = null,
    ) = PlaylistDetails(
        id = id,
        title = "Remote",
        thumbnailUrl = "",
        author = "Artist",
        trackCount = tracks.size,
        tracks = tracks,
        continuation = continuation,
    )

    private fun MusicCollectionViewModel.settled(): MusicCollectionUiState =
        runBlocking {
            withTimeout(2_000) { state.first { !it.isLoading } }
        }

    @Test
    fun `a playlist you own opens from the database with its lengths and dates`() {
        val id = "sync_4f2a"
        coEvery { playlists.getPlaylistEntity(id) } returns entity(id, own = true)
        every { playlists.observePlaylistEntity(id) } returns flowOf(entity(id, own = true))
        every { playlists.getPlaylistVideosWithAddedAtFlow(id) } returns flowOf(listOf(video("a"), video("b")))

        val state = viewModel(id).settled()

        assertThat(state.kind).isEqualTo(MusicCollectionKind.OWN)
        assertThat(state.isOwn).isTrue()
        assertThat(state.details?.tracks?.map { it.duration }).containsExactly(226, 226)
        assertThat(state.addedAt).containsEntry("a", 7L)
    }

    @Test
    fun `your playlist follows edits and removals`() {
        val id = "3f9c"
        val videos = MutableStateFlow(listOf(video("a"), video("b")))
        coEvery { playlists.getPlaylistEntity(id) } returns entity(id, own = true)
        every { playlists.observePlaylistEntity(id) } returns flowOf(entity(id, own = true))
        every { playlists.getPlaylistVideosWithAddedAtFlow(id) } returns videos
        val viewModel = viewModel(id)
        viewModel.settled()

        videos.value = listOf(video("b"))

        val tracks = runBlocking { withTimeout(2_000) { viewModel.state.first { it.details?.tracks?.size == 1 } } }
        assertThat(
            tracks.details
                ?.tracks
                ?.single()
                ?.videoId,
        ).isEqualTo("b")
    }

    @Test
    fun `a saved album opens from the saved copy and refreshes it once complete`() {
        val id = "MPREb_album"
        coEvery { playlists.getPlaylistEntity(id) } returns entity(id, own = false)
        every { playlists.getPlaylistVideosWithAddedAtFlow(id) } returns flowOf(listOf(video("a")))
        coEvery { YouTubeMusicService.fetchPlaylistDetails(id) } returns remote(id, listOf(track("a"), track("b")))

        val viewModel = viewModel(id)

        val refreshed = runBlocking { withTimeout(2_000) { viewModel.state.first { it.details?.tracks?.size == 2 } } }
        assertThat(refreshed.isSaved).isTrue()
        assertThat(refreshed.kind).isEqualTo(MusicCollectionKind.SAVED)
        coVerify(timeout = 2_000) { playlists.syncSavedPlaylistVideos(id, match { it.map(Video::id) == listOf("a", "b") }) }
    }

    @Test
    fun `a failed load shows an error and Retry loads again`() {
        val id = "PLremote"
        coEvery { playlists.getPlaylistEntity(id) } returns null
        coEvery { YouTubeMusicService.fetchPlaylistDetails(id) } returns null
        val viewModel = viewModel(id)
        assertThat(viewModel.settled().failed).isTrue()

        coEvery { YouTubeMusicService.fetchPlaylistDetails(id) } returns remote(id, listOf(track("a")))
        viewModel.retry()

        val loaded = runBlocking { withTimeout(2_000) { viewModel.state.first { it.details != null } } }
        assertThat(loaded.kind).isEqualTo(MusicCollectionKind.PLAYLIST)
        assertThat(loaded.failed).isFalse()
    }

    @Test
    fun `a failed next page keeps its token so the next try retries`() {
        val id = "PLremote"
        coEvery { playlists.getPlaylistEntity(id) } returns null
        coEvery { YouTubeMusicService.fetchPlaylistDetails(id) } returns remote(id, listOf(track("a")), continuation = "next")
        coEvery { YouTubeMusicService.fetchPlaylistContinuation(id, "next") } returns (emptyList<MusicTrack>() to null)
        val viewModel = viewModel(id)
        viewModel.settled()

        viewModel.loadMore()

        val failed = runBlocking { withTimeout(2_000) { viewModel.state.first { it.moreFailed } } }
        assertThat(failed.details?.continuation).isEqualTo("next")
    }

    @Test
    fun `pages that repeat a song add it once`() {
        val first = remote("PL", listOf(track("a"), track("b")))

        val merged = first.appending(listOf(track("b"), track("c")), next = null)

        assertThat(merged.tracks.map { it.videoId }).containsExactly("a", "b", "c").inOrder()
        assertThat(merged.continuation).isNull()
    }
}
