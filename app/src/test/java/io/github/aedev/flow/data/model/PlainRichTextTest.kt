package io.github.aedev.flow.data.model

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class PlainRichTextTest {
    @Test
    fun `line breaks and text come through untouched`() {
        val text = "Обзор смартфона Xiaomi 17T\nПлюсы и минусы"

        assertThat(plainRichText(text).text).isEqualTo(text)
    }

    @Test
    fun `links, timestamps and hashtags become spans`() {
        val text = "Розыгрыши: https://t.me/modnox/4579\n0:00 Вступление\n1:02:03 Итоги #xiaomi"

        val spans = plainRichText(text).spans

        assertThat(spans.map { it.target })
            .containsExactly(
                RichTextTarget.Url("https://t.me/modnox/4579"),
                RichTextTarget.Timestamp(0),
                RichTextTarget.Timestamp(3_723),
                RichTextTarget.Hashtag("xiaomi"),
            ).inOrder()
        assertThat(text.substring(spans[0].start, spans[0].end)).isEqualTo("https://t.me/modnox/4579")
    }

    @Test
    fun `a time inside a link stays part of the link`() {
        val spans = plainRichText("https://example.com/at/10:20 more").spans

        assertThat(spans.map { it.target }).containsExactly(RichTextTarget.Url("https://example.com/at/10:20"))
    }

    @Test
    fun `an anchor inside a link is not a hashtag`() {
        val spans = plainRichText("https://example.com/page#top").spans

        assertThat(spans).hasSize(1)
    }
}
