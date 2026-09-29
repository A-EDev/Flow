package io.github.aedev.flow.ui.components.videoplayer.sheet

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.transcript.TranscriptCue
import org.junit.Test

class TranscriptItemsTest {
    private val cues =
        listOf(
            TranscriptCue(startMs = 0L, text = "opening"),
            TranscriptCue(startMs = 10_000L, text = "still opening"),
            TranscriptCue(startMs = 30_000L, text = "the specs"),
            TranscriptCue(startMs = 90_000L, text = "the verdict"),
        )

    private val chapters =
        listOf(
            TranscriptChapter(startMs = 0L, title = "Intro"),
            TranscriptChapter(startMs = 25_000L, title = "Full system specs"),
            TranscriptChapter(startMs = 80_000L, title = "Verdict"),
        )

    @Test
    fun `no chapters leaves the lines as they were`() {
        val items = transcriptItems(cues, emptyList())

        assertThat(items.map { (it as TranscriptItem.Line).cue }).isEqualTo(cues)
    }

    @Test
    fun `each chapter heads the first line inside it`() {
        val items = transcriptItems(cues, chapters)

        assertThat(items.map { it.label() })
            .containsExactly(
                "# Intro",
                "opening",
                "still opening",
                "# Full system specs",
                "the specs",
                "# Verdict",
                "the verdict",
            ).inOrder()
    }

    @Test
    fun `a chapter nothing was said in never heads another chapters lines`() {
        val silent = chapters + TranscriptChapter(startMs = 60_000L, title = "Silence")

        val items = transcriptItems(cues.filter { it.startMs < 60_000L }, silent)

        assertThat(items.map { it.label() }).doesNotContain("# Silence")
    }

    @Test
    fun `a search that leaves one line still heads it with its own chapter`() {
        val items = transcriptItems(cues.filter { it.text == "the verdict" }, chapters)

        assertThat(items.map { it.label() }).containsExactly("# Verdict", "the verdict").inOrder()
    }

    @Test
    fun `an untitled chapter is not a heading`() {
        val items = transcriptItems(cues, listOf(TranscriptChapter(startMs = 0L, title = " ")))

        assertThat(items).hasSize(cues.size)
    }

    private fun TranscriptItem.label(): String =
        when (this) {
            is TranscriptItem.Heading -> "# ${chapter.title}"
            is TranscriptItem.Line -> cue.text
        }
}
