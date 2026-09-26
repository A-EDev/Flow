package io.github.aedev.flow.player.sponsorblock

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.local.SponsorBlockAction
import io.github.aedev.flow.data.model.SponsorBlockSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
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
