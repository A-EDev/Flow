package io.github.aedev.flow.player.sponsorblock

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.model.SponsorBlockSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import org.junit.Test

class SponsorBlockHandlerTest {
    private val outro =
        SponsorBlockSegment(
            category = "outro",
            segment = listOf(156.5f, 157.2f),
            uuid = "outro-id",
            actionType = "skip",
        )

    private fun handler() =
        SponsorBlockHandler(CoroutineScope(UnconfinedTestDispatcher())).apply {
            loadSegmentsFromList("sh04x4jzCPw", listOf(outro))
        }

    @Test
    fun `entering a segment returns its end as the skip target`() {
        assertThat(handler().checkForSkip(156_700L)).isEqualTo(157_200L)
    }

    @Test
    fun `filler skip segments are skipped`() {
        val handler =
            SponsorBlockHandler(CoroutineScope(UnconfinedTestDispatcher())).apply {
                loadSegmentsFromList(
                    "video",
                    listOf(
                        SponsorBlockSegment(
                            category = "filler",
                            segment = listOf(10f, 20f),
                            uuid = "filler-id",
                            actionType = "skip",
                        ),
                    ),
                )
            }

        assertThat(handler.checkForSkip(12_000L)).isEqualTo(20_000L)
    }

    @Test
    fun `exclusive access full segments are never auto skipped`() {
        val exclusive =
            SponsorBlockSegment(
                category = "exclusive_access",
                segment = listOf(0f, 180f),
                uuid = "full-id",
                actionType = "full",
            )
        val handler =
            SponsorBlockHandler(CoroutineScope(UnconfinedTestDispatcher())).apply {
                categoryActions = mapOf("exclusive_access" to SponsorBlockAction.SKIP)
                loadSegmentsFromList("video", listOf(exclusive))
            }

        assertThat(handler.checkForSkip(1_000L)).isNull()
    }

    @Test
    fun `api mute segments mute instead of skipping`() {
        val mute =
            SponsorBlockSegment(
                category = "sponsor",
                segment = listOf(5f, 15f),
                uuid = "mute-id",
                actionType = "mute",
            )
        val handler =
            SponsorBlockHandler(CoroutineScope(UnconfinedTestDispatcher())).apply {
                categoryActions = mapOf("sponsor" to SponsorBlockAction.SKIP)
                loadSegmentsFromList("video", listOf(mute))
            }

        assertThat(handler.checkForSkip(8_000L)).isNull()
    }

    @Test
    fun `landing just short of the segment end does not re-skip`() {
        val handler = handler()
        handler.checkForSkip(156_700L)

        assertThat(handler.checkForSkip(156_400L)).isNull()
        assertThat(handler.checkForSkip(156_600L)).isNull()
    }

    @Test
    fun `rewinding well before the segment re-arms the skip`() {
        val handler = handler()
        handler.checkForSkip(156_700L)

        assertThat(handler.checkForSkip(120_000L)).isNull()
        assertThat(handler.checkForSkip(156_700L)).isEqualTo(157_200L)
    }

    @Test
    fun `empty API result loads on-device fallback segments`() =
        runTest {
            val fallback = SponsorBlockSegment("sponsor", listOf(10f, 20f), "ml-id")
            val handler =
                SponsorBlockHandler(
                    scope = this,
                    apiSegments = { emptyList() },
                    fallbackSegments = { listOf(fallback) },
                )
            handler.setEnabled(true)

            handler.loadSegments("video")
            advanceUntilIdle()

            assertThat(handler.getSegments()).containsExactly(fallback)
        }

    @Test
    fun `disabled handler performs no load work`() =
        runTest {
            var fetched = false
            val handler =
                SponsorBlockHandler(
                    scope = this,
                    apiSegments = {
                        fetched = true
                        emptyList()
                    },
                )

            handler.loadSegments("video")
            advanceUntilIdle()

            assertThat(fetched).isFalse()
            assertThat(handler.getSegments()).isEmpty()
        }

    @Test
    fun `provisional segments apply only for the enabled current video`() {
        val provisional = SponsorBlockSegment("sponsor", listOf(10f, 20f), "provisional-id")
        val handler = SponsorBlockHandler(CoroutineScope(UnconfinedTestDispatcher()))
        handler.loadSegmentsFromList("video", emptyList())
        handler.setEnabled(true)
        handler.loadSegmentsFromList("video", emptyList())

        handler.setProvisionalSegments("other", listOf(provisional))
        assertThat(handler.getSegments()).isEmpty()

        handler.setProvisionalSegments("video", listOf(provisional))
        assertThat(handler.getSegments()).containsExactly(provisional)
        assertThat(handler.checkForSkip(12_000L)).isEqualTo(20_000L)
    }
}
