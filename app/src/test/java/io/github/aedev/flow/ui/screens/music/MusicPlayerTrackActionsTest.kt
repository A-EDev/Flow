package io.github.aedev.flow.ui.screens.music

import android.content.Context
import io.github.aedev.flow.data.local.LikedVideosRepository
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.music.DownloadManager
import io.github.aedev.flow.data.music.PlaylistRepository
import io.github.aedev.flow.data.music.model.MusicTrack
import io.github.aedev.flow.data.recommendation.music.MusicBrainEngine
import io.github.aedev.flow.data.scrobble.Scrobbler
import io.github.aedev.flow.utils.PerformanceDispatcher
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkObject
import io.mockk.unmockkAll
import io.mockk.verify
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicPlayerTrackActionsTest {
    private val dispatcher = StandardTestDispatcher()
    private val downloadManager: DownloadManager = mockk(relaxed = true)
    private val preferences: PlayerPreferences = mockk(relaxed = true)
    private val scrobbler: Scrobbler = mockk(relaxed = true)
    private val track = MusicTrack(videoId = "song1", title = "Song", artist = "Artist", thumbnailUrl = "", duration = 200)

    @Before
    fun setUp() {
        mockkObject(PerformanceDispatcher)
        every { PerformanceDispatcher.diskIO } returns dispatcher
    }

    @After
    fun tearDown() = unmockkAll()

    private fun actions(isLiked: Boolean) =
        MusicPlayerTrackActions(
            context = mockk<Context>(relaxed = true),
            scope = TestScope(dispatcher),
            uiState = MutableStateFlow(MusicPlayerUiState(currentTrack = track, isLiked = isLiked)),
            playlistRepository = mockk<PlaylistRepository>(relaxed = true),
            likedVideosRepository = mockk<LikedVideosRepository>(relaxed = true),
            downloadManager = downloadManager,
            musicBrain = mockk<MusicBrainEngine>(relaxed = true),
            playerPreferences = preferences,
            scrobbler = scrobbler,
        )

    @Test
    fun `liking a song downloads it when the option is on`() =
        runTest(dispatcher) {
            every { preferences.autoDownloadLikedMusic } returns flowOf(true)

            actions(isLiked = false).toggleLike()
            advanceUntilIdle()

            coVerify(exactly = 1) { downloadManager.downloadTrack(track) }
        }

    @Test
    fun `nothing downloads with the option off or on an unlike`() =
        runTest(dispatcher) {
            every { preferences.autoDownloadLikedMusic } returns flowOf(false)
            actions(isLiked = false).toggleLike()
            advanceUntilIdle()

            every { preferences.autoDownloadLikedMusic } returns flowOf(true)
            actions(isLiked = true).toggleLike()
            advanceUntilIdle()

            coVerify(exactly = 0) { downloadManager.downloadTrack(any()) }
        }

    @Test
    fun `a like and an unlike reach the scrobbler`() =
        runTest(dispatcher) {
            every { preferences.autoDownloadLikedMusic } returns flowOf(false)

            actions(isLiked = false).toggleLike()
            actions(isLiked = true).toggleLike()
            advanceUntilIdle()

            verify { scrobbler.onLikeChanged(track, true) }
            verify { scrobbler.onLikeChanged(track, false) }
        }
}
