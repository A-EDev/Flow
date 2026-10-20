package io.github.aedev.flow.data.lyrics

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class UnisonLyricsProviderTest {
    private fun body(
        lyrics: String,
        format: String,
        syncType: String,
        success: Boolean = true,
    ) = """{"success":$success,"data":{"lyrics":"$lyrics","format":"$format","syncType":"$syncType","id":7}}"""

    @Test
    fun `line-synced lrc becomes timed lines`() {
        val entries = UnisonLyricsProvider.parse(body("[00:01.00]First\\n[00:12.50]Second", "lrc", "linesync"))

        assertThat(entries.map { it.time to it.text }).containsExactly(1_000L to "First", 12_500L to "Second").inOrder()
    }

    @Test
    fun `word stamps in lrc become word timings`() {
        val entries = UnisonLyricsProvider.parse(body("[00:01.00]<00:01.00>Hello <00:01.50>there", "lrc", "wordsync"))

        assertThat(entries.single().words?.map { it.text }).containsExactly("Hello", "there").inOrder()
    }

    @Test
    fun `plain text stays one unsynced entry`() {
        val entries = UnisonLyricsProvider.parse(body("First\\nSecond", "lrc", "plain"))

        assertThat(entries.single().time).isEqualTo(0L)
        assertThat(entries.single().text).isEqualTo("First\nSecond")
    }

    @Test
    fun `an unsuccessful answer gives nothing`() {
        assertThat(UnisonLyricsProvider.parse(body("[00:01.00]First", "lrc", "linesync", success = false))).isEmpty()
        assertThat(UnisonLyricsProvider.parse("""{"success":false,"error":"Lyrics not found","code":"NOT_FOUND"}""")).isEmpty()
    }
}
