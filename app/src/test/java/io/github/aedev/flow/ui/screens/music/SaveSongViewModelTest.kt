package io.github.aedev.flow.ui.screens.music

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.PlaylistRepository
import io.github.aedev.flow.data.music.model.MusicTrack
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import io.mockk.unmockkAll
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SaveSongViewModelTest {
    private val testDispatcher = StandardTestDispatcher()
    private val repository: PlaylistRepository =
        mockk(relaxed = true) {
            every { getMusicPlaylistsFlow() } returns flowOf(emptyList())
            coEvery { getPlaylistIdsForVideo("song") } returns listOf("road")
        }
    private val track = MusicTrack(videoId = "song", title = "Salt Line", artist = "Harbour Lights", thumbnailUrl = "", duration = 200)

    @Before
    fun setUp() {
        Dispatchers.setMain(testDispatcher)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `a playlist that already holds the song shows as saved`() =
        runTest {
            val viewModel = SaveSongViewModel(repository)
            viewModel.loadMembership("song")
            testDispatcher.scheduler.advanceUntilIdle()

            assertThat(viewModel.savedIds.value).containsExactly("road")
        }

    @Test
    fun `toggling a saved playlist removes the song instead of adding it again`() =
        runTest {
            val viewModel = SaveSongViewModel(repository)
            viewModel.loadMembership("song")
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.toggle(track, "road")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { repository.removeVideoFromPlaylist("road", "song") }
            coVerify(exactly = 0) { repository.addVideoToPlaylist(any(), any()) }
            assertThat(viewModel.savedIds.value).isEmpty()
        }

    @Test
    fun `toggling an unsaved playlist adds the song as music`() =
        runTest {
            val viewModel = SaveSongViewModel(repository)
            viewModel.loadMembership("song")
            testDispatcher.scheduler.advanceUntilIdle()

            viewModel.toggle(track, "gym")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 1) { repository.addVideoToPlaylist("gym", match { it.id == "song" && it.isMusic }) }
            assertThat(viewModel.savedIds.value).containsExactly("road", "gym")
        }

    @Test
    fun `nothing is written before membership has loaded`() =
        runTest {
            val viewModel = SaveSongViewModel(repository)

            viewModel.toggle(track, "gym")
            testDispatcher.scheduler.advanceUntilIdle()

            coVerify(exactly = 0) { repository.addVideoToPlaylist(any(), any()) }
        }
}
