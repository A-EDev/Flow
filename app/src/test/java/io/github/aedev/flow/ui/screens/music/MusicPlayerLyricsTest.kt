package io.github.aedev.flow.ui.screens.music

import android.content.Context
import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.R
import io.github.aedev.flow.data.local.PlayerPreferences
import io.github.aedev.flow.data.localmedia.LocalLyrics
import io.github.aedev.flow.data.localmedia.LocalLyricsReader
import io.github.aedev.flow.data.localmedia.LocalLyricsSource
import io.github.aedev.flow.data.lyrics.LyricsHelper
import io.mockk.coEvery
import io.mockk.coVerify
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class MusicPlayerLyricsTest {
    private val context = mockk<Context> { every { getString(R.string.lyrics_source_local_file) } returns "Lyrics file" }
    private val helper =
        mockk<LyricsHelper>(relaxed = true) {
            every { entriesAreSynced(any()) } answers { firstArg<List<*>>().size >= 2 }
        }
    private val preferences =
        mockk<PlayerPreferences> {
            every { lyricsShowTranslation } returns flowOf(false)
            every { lyricsShowRomanization } returns flowOf(false)
            every { lyricsAutoRomanize } returns flowOf(false)
        }
    private val reader =
        mockk<LocalLyricsReader> {
            coEvery { read("local_7") } returns
                LocalLyrics("[offset:+250]\n[00:01.00]First\n[00:09.00]Second", LocalLyricsSource.FILE)
        }

    @Test
    fun `a device song reads its lrc and never asks an online provider`() =
        runTest {
            val state = MutableStateFlow(MusicPlayerUiState())
            val lyrics = MusicPlayerLyrics(context, this, state, helper, preferences, reader)

            lyrics.fetch(videoId = "local_7", artist = "Artist", title = "Song")
            state.first { it.lyricsProviderName.isNotEmpty() }

            coVerify(exactly = 0) { helper.getLyrics(any(), any(), any(), any(), any(), any()) }
            assertThat(state.value.syncedLyrics.map { it.text }).containsExactly("First", "Second").inOrder()
            assertThat(state.value.lyricsProviderName).isEqualTo("Lyrics file")
            assertThat(state.value.lyricsSyncOffsetMs).isEqualTo(250L)
            assertThat(state.value.isLyricsLoading).isFalse()
        }
}
