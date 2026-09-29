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

    private fun segment(
        category: String,
        start: Float,
        end: Float,
        actionType: String = "skip",
    ) = SponsorBlockSegment(category, listOf(start, end), "$category-id", actionType)

    private fun handlerWith(
        segment: SponsorBlockSegment,
        actions: Map<String, SponsorBlockAction> = emptyMap(),
    ) = SponsorBlockHandler(CoroutineScope(UnconfinedTestDispatcher())).apply {
        loadSegmentsFromList("video", listOf(segment))
        categoryActions = actions
    }

    @Test
    fun `filler with no stored action shows without skipping`() {
        assertThat(handlerWith(segment("filler", 10f, 20f)).checkForSkip(15_000L)).isNull()
    }

    @Test
    fun `preview with no stored action shows without skipping`() {
        assertThat(handlerWith(segment("preview", 10f, 20f)).checkForSkip(15_000L)).isNull()
    }

    @Test
    fun `filler set to ignore or notify is not skipped`() {
        val filler = segment("filler", 10f, 20f)

        assertThat(handlerWith(filler, mapOf("filler" to SponsorBlockAction.IGNORE)).checkForSkip(15_000L)).isNull()
        assertThat(handlerWith(filler, mapOf("filler" to SponsorBlockAction.SHOW_TOAST)).checkForSkip(15_000L)).isNull()
    }

    @Test
    fun `filler the user set to skip is skipped`() {
        val handler = handlerWith(segment("filler", 10f, 20f), mapOf("filler" to SponsorBlockAction.SKIP))

        assertThat(handler.checkForSkip(15_000L)).isEqualTo(20_000L)
    }

    @Test
    fun `a sponsor with no stored action is still skipped`() {
        assertThat(handlerWith(segment("sponsor", 10f, 20f)).checkForSkip(15_000L)).isEqualTo(20_000L)
    }

    @Test
    fun `a whole-video label never triggers an action`() {
        val label = segment("exclusive_access", 0f, 0f, actionType = "full")
        val handler = handlerWith(label, mapOf("exclusive_access" to SponsorBlockAction.SKIP))

        assertThat(handler.checkForSkip(0L)).isNull()
        assertThat(handler.checkForSkip(5_000L)).isNull()
    }
}
