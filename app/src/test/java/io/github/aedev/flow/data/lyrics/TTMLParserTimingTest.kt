package io.github.aedev.flow.data.lyrics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class TTMLParserTimingTest {
    private fun ttml(vararg lines: Pair<String, String>) =
        """<tt xmlns="http://www.w3.org/ns/ttml"><body><div>""" +
            lines.joinToString("") { (begin, end) ->
                """<p begin="$begin" end="$end"><span begin="$begin" end="$end">Word</span></p>"""
            } +
            "</div></body></tt>"

    @Test
    fun `apple style bare seconds and minute times are read`() {
        val entries = TTMLParser.parseTTMLToLyricsEntries(ttml("27.395" to "28.100", "1:02.345" to "1:03.000", "3:12.818" to "3:14.043"))

        assertThat(entries.map { it.time }).containsExactly(27_395L, 62_345L, 192_818L).inOrder()
        assertThat(
            entries
                .first()
                .words
                ?.single()
                ?.endTime,
        ).isEqualTo(28_100L)
    }

    @Test
    fun `ttml offset and clock times are still read`() {
        val entries = TTMLParser.parseTTMLToLyricsEntries(ttml("9.446s" to "10s", "12500ms" to "13000ms", "00:01:02.5" to "00:01:03.25"))

        assertThat(entries.map { it.time }).containsExactly(9_446L, 12_500L, 62_500L).inOrder()
        assertThat(
            entries
                .last()
                .words
                ?.single()
                ?.endTime,
        ).isEqualTo(63_250L)
    }
}
