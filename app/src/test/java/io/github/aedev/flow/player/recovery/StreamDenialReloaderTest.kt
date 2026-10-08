package io.github.aedev.flow.player.recovery

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.player.stream.InnerTubeVideoStreamExtractor
import io.mockk.mockk
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class StreamDenialReloaderTest {
    private var videoId: String? = "vid_a"
    private var position = 59_814L
    private var extraction: InnerTubeVideoStreamExtractor.VideoExtractionResult? = mockk(relaxed = true)
    private var download: String? = null
    private val playedDownloads = mutableListOf<Pair<String, Long>>()
    private var extractGate: CompletableDeferred<Unit>? = null
    private val extracted = mutableListOf<String>()
    private val prepared = mutableListOf<Pair<String, Long>>()
    private var evictions = 0
    private val abandoned = mutableListOf<String>()
    private val logged = mutableListOf<String>()

    private fun TestScope.reloader() =
        StreamDenialReloader(
            scope = this,
            currentVideoId = { videoId },
            positionMs = { position },
            playDownload = { id, startPositionMs ->
                (download == id).also { if (it) playedDownloads += id to startPositionMs }
            },
            extract = { id ->
                extracted += id
                extractGate?.await()
                extraction
            },
            prepare = { id, _, startPositionMs ->
                prepared += id to startPositionMs
                true
            },
            evictCache = { evictions++ },
            abandon = { abandoned += it },
            log = { logged += it },
        )

    @Test
    fun `a refusal reloads the video where it stopped, with no screen involved`() =
        runTest {
            val reloader = reloader()

            reloader.onStreamsDenied()
            advanceUntilIdle()

            assertThat(extracted).containsExactly("vid_a")
            assertThat(prepared).containsExactly("vid_a" to 59_814L)
            assertThat(evictions).isEqualTo(0)
        }

    @Test
    fun `a download finished meanwhile is played instead of fetching anything`() =
        runTest {
            download = "vid_a"
            val reloader = reloader()

            reloader.onStreamsDenied()
            advanceUntilIdle()

            assertThat(playedDownloads).containsExactly("vid_a" to 59_814L)
            assertThat(extracted).isEmpty()
            assertThat(prepared).isEmpty()
        }

    @Test
    fun `a refusal while the reload is running joins it instead of starting another`() =
        runTest {
            extractGate = CompletableDeferred()
            val reloader = reloader()

            reloader.onStreamsDenied()
            advanceUntilIdle()
            assertThat(reloader.isReloading("vid_a")).isTrue()
            reloader.onStreamsDenied()
            extractGate!!.complete(Unit)
            advanceUntilIdle()

            assertThat(extracted).hasSize(1)
            assertThat(reloader.isReloading("vid_a")).isFalse()
            assertThat(logged.any { "coalesced" in it }).isTrue()
        }

    @Test
    fun `reloads that find nothing spend the budget, then the video is given up once`() =
        runTest {
            extraction = null
            val reloader = reloader()

            reloader.onStreamsDenied()
            advanceUntilIdle()

            assertThat(extracted).hasSize(StreamExpiryRecoveryController.MAX_STREAM_EXPIRY_RETRIES)
            assertThat(evictions).isEqualTo(StreamExpiryRecoveryController.MAX_STREAM_EXPIRY_RETRIES - 1)
            assertThat(abandoned).containsExactly("vid_a")
            assertThat(reloader.hasGivenUpOn("vid_a")).isTrue()

            reloader.onStreamsDenied()
            advanceUntilIdle()
            assertThat(abandoned).hasSize(1)
        }

    @Test
    fun `asking for playback again lifts a give-up`() =
        runTest {
            extraction = null
            val reloader = reloader()
            reloader.onStreamsDenied()
            advanceUntilIdle()

            reloader.onPlaybackRequested()
            extraction = mockk(relaxed = true)
            reloader.onStreamsDenied()
            advanceUntilIdle()

            assertThat(reloader.hasGivenUpOn("vid_a")).isFalse()
            assertThat(prepared).containsExactly("vid_a" to 59_814L)
        }

    @Test
    fun `every video in a playlist gets its own budget`() =
        runTest {
            val reloader = reloader()

            repeat(10) { index ->
                videoId = "vid_$index"
                repeat(2) {
                    reloader.onStreamsDenied()
                    advanceUntilIdle()
                }
            }

            assertThat(abandoned).isEmpty()
            assertThat(prepared).hasSize(20)
        }

    @Test
    fun `a reload whose video was left behind prepares nothing`() =
        runTest {
            extractGate = CompletableDeferred()
            val reloader = reloader()

            reloader.onStreamsDenied()
            advanceUntilIdle()
            videoId = "vid_b"
            extractGate!!.complete(Unit)
            advanceUntilIdle()

            assertThat(prepared).isEmpty()
        }
}
