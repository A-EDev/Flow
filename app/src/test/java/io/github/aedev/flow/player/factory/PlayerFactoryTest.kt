package io.github.aedev.flow.player.factory

import android.content.Context
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.analytics.PlayerId
import androidx.media3.exoplayer.source.MediaSource
import androidx.media3.exoplayer.source.SinglePeriodTimeline
import io.github.aedev.flow.data.local.PlayerPreferences
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.yield
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

@UnstableApi
class PlayerFactoryTest {
    @Test
    fun `preload refreshes preferences and release invalidation makes synchronous creation fresh`() =
        runBlocking {
            val context = mockk<Context>(relaxed = true)
            val preferences = mockk<PlayerPreferences>()
            val values = PreferenceValues(playbackMs = 1_000, rebufferMs = 2_000, minMs = 10_000, maxMs = 30_000)
            every { preferences.preferredAudioLanguage } answers { flowOf("") }
            every { preferences.minBufferMs } answers { flowOf(values.minMs) }
            every { preferences.maxBufferMs } answers { flowOf(values.maxMs) }
            every { preferences.bufferForPlaybackMs } answers { flowOf(values.playbackMs) }
            every { preferences.bufferForPlaybackAfterRebufferMs } answers { flowOf(values.rebufferMs) }
            every { preferences.playDuringCalls } answers {
                if (values.failRead) {
                    flow<Boolean> { throw IOException("preference read failed") }
                } else if (values.awaitRead) {
                    flow<Boolean> { awaitCancellation() }
                } else {
                    flowOf(true)
                }
            }
            var preferenceInstances = 0
            val factory =
                PlayerFactory {
                    preferenceInstances++
                    preferences
                }

            factory.preloadPreferences(context)
            val firstLoadControl = factory.createLoadControl(context) as DefaultLoadControl
            assertTrue(shouldStartPlayback(firstLoadControl, bufferedMs = 1_500))
            factory.createLoadControl(context)
            factory.createLoadControl(context)
            assertEquals(1, preferenceInstances)

            values.playbackMs = 2_000
            factory.preloadPreferences(context)
            val refreshedLoadControl = factory.createLoadControl(context) as DefaultLoadControl
            assertFalse(shouldStartPlayback(refreshedLoadControl, bufferedMs = 1_500))
            assertTrue(shouldStartPlayback(firstLoadControl, bufferedMs = 1_500))
            assertEquals(2, preferenceInstances)

            values.playbackMs = 500
            values.failRead = true
            try {
                factory.preloadPreferences(context)
                throw AssertionError("Expected the preference read to fail")
            } catch (_: IOException) {
            }
            values.failRead = false
            assertFalse(shouldStartPlayback(factory.createLoadControl(context) as DefaultLoadControl, bufferedMs = 1_500))

            values.awaitRead = true
            val cancelledRefresh = launch { factory.preloadPreferences(context) }
            yield()
            cancelledRefresh.cancelAndJoin()
            values.awaitRead = false
            assertFalse(shouldStartPlayback(factory.createLoadControl(context) as DefaultLoadControl, bufferedMs = 1_500))

            factory.invalidatePreferences()
            val invalidatedLoadControl = factory.createLoadControl(context) as DefaultLoadControl
            assertTrue(shouldStartPlayback(invalidatedLoadControl, bufferedMs = 1_500))
            assertEquals(5, preferenceInstances)
        }

    private fun shouldStartPlayback(
        loadControl: DefaultLoadControl,
        bufferedMs: Long,
    ): Boolean {
        loadControl.onPrepared(PlayerId.UNSET)
        return try {
            loadControl.shouldStartPlayback(parameters(bufferedUs = bufferedMs * 1_000))
        } finally {
            loadControl.onReleased(PlayerId.UNSET)
        }
    }

    private fun parameters(bufferedUs: Long): LoadControl.Parameters {
        val timeline = SinglePeriodTimeline(60_000_000L, true, false, false, null, MediaItem.EMPTY)
        return LoadControl.Parameters(
            PlayerId.UNSET,
            timeline,
            MediaSource.MediaPeriodId(timeline.getUidOfPeriod(0)),
            0L,
            bufferedUs,
            1f,
            true,
            false,
            C.TIME_UNSET,
            0L,
        )
    }

    private data class PreferenceValues(
        var playbackMs: Int,
        var rebufferMs: Int,
        var minMs: Int,
        var maxMs: Int,
        var failRead: Boolean = false,
        var awaitRead: Boolean = false,
    )
}
