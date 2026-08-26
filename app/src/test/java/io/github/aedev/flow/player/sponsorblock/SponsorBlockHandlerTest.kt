package io.github.aedev.flow.player.sponsorblock

import com.google.common.truth.Truth.assertThat
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
}
