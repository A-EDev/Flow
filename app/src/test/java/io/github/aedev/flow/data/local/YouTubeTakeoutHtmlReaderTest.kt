package io.github.aedev.flow.data.local

import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.Test
import java.io.StringReader

class YouTubeTakeoutHtmlReaderTest {
    private fun cell(
        videoId: String,
        title: String,
        channelId: String,
        channel: String,
    ) = """<div class="content-cell mdl-cell">Watched <a href="https://www.youtube.com/watch?v=$videoId">$title</a><br>""" +
        """<a href="https://www.youtube.com/channel/$channelId">$channel</a><br>Sep 3, 2026</div>"""

    private suspend fun read(
        html: String,
        requireActivityMarkup: Boolean = true,
    ): Pair<TakeoutHtmlActivity, List<TakeoutWatch>> {
        val seen = mutableListOf<TakeoutWatch>()
        val result = readTakeoutHtmlActivity(StringReader(html), now = 1_000, requireActivityMarkup = requireActivityMarkup) { seen += it }
        return result to seen
    }

    @Test
    fun `each watched video comes with its channel, newest first`() =
        runTest {
            val (result, watches) =
                read(
                    cell("dQw4w9WgXcQ", "Never &amp; Ever", "UCuAXFkgsw1L7xaCfnd5JJOw", "Rick") +
                        cell("kcxK1Tnwy5M", "Second", "UC2", "Other"),
                )

            assertThat(result.watches).isEqualTo(2)
            assertThat(watches.map { it.videoId }).containsExactly("dQw4w9WgXcQ", "kcxK1Tnwy5M").inOrder()
            assertThat(watches.first().title).isEqualTo("Never & Ever")
            assertThat(watches.first().channelName).isEqualTo("Rick")
            assertThat(watches.first().channelId).isEqualTo("UCuAXFkgsw1L7xaCfnd5JJOw")
            assertThat(watches.map { it.watchedAt }).containsExactly(1_000L, 999L).inOrder()
        }

    @Test
    fun `other html in the archive gives nothing unless it is known to be history`() =
        runTest {
            val page = """<html><a href="https://www.youtube.com/watch?v=dQw4w9WgXcQ">Link</a></html>"""

            assertThat(read(page).first.watches).isEqualTo(0)
            assertThat(read(page).second).isEmpty()
            assertThat(read(page, requireActivityMarkup = false).first.watches).isEqualTo(1)
        }

    @Test
    fun `youtube music watches and searches from both services are read too`() =
        runTest {
            val html =
                """<div class="content-cell">Watched <a href="https://music.youtube.com/watch?v=ccccccccccc">Song</a></div>""" +
                    """<div class="content-cell">Searched for """ +
                    """<a href="https://www.youtube.com/results?search_query=lofi+%26+rain">lofi &amp; rain</a></div>""" +
                    """<div class="content-cell">Searched for <a href="https://music.youtube.com/search?q=jazz">jazz</a></div>"""

            val (result, watches) = read(html)

            assertThat(watches.single().isMusic).isTrue()
            assertThat(result.searches.map { it.query to it.isMusic }).containsExactly("lofi & rain" to false, "jazz" to true).inOrder()
        }
}
