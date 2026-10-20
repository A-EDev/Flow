/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import com.google.common.truth.Truth.assertWithMessage
import org.junit.Test

/** Channel names give topics only for words the engine knows (#1341). */
class NeuroChannelNameTest {
    private val tokenizer = NeuroTokenizer()
    private val icu = NeuroTokenizer(IcuWordBreaker)

    @Test
    fun `spaced names keep their topical words`() {
        assertThat(tokenizer.tokenizeChannelName("Linus Tech Tips")).containsExactly("tech")
        assertThat(tokenizer.tokenizeChannelName("Python Engineer")).containsExactly("python")
        assertThat(tokenizer.tokenizeChannelName("Fireship")).isEmpty()
        assertThat(tokenizer.tokenizeChannelName("Binging with Babish")).isEmpty()
    }

    @Test
    fun `a name of a script without spaces has no topics unless it holds a topical word`() {
        assertThat(tokenizer.tokenizeChannelName("")).isEmpty()
        assertThat(tokenizer.tokenizeChannelName("央")).isEmpty()
    }

    @Test
    fun `a Chinese channel name gives its topic`() {
        assertThat(tokenizer.tokenizeChannelName("TVBS新聞")).containsExactly("新闻")
        assertThat(icu.tokenizeChannelName("央視新聞")).containsExactly("新闻")
        assertThat(icu.tokenizeChannelName("阿滴英文")).containsExactly("英文")
        assertThat(icu.tokenizeChannelName("英文教室 科技")).containsExactly("英文", "科技")
    }

    @Test
    fun `the Chinese topic list is folded, whole words`() {
        val topics = NeuroLanguages.channelTopics
        assertThat(topics).contains("新闻")
        assertThat(topics).doesNotContain("新聞")
        topics.forEach { word ->
            assertWithMessage(word).that(word).isEqualTo(NeuroText.fold(word))
            assertWithMessage(word).that(word.length).isAtLeast(2)
            assertWithMessage(word).that(word.any(Char::isWhitespace)).isFalse()
            assertWithMessage(word).that(NeuroText.words(word, IcuWordBreaker)).containsExactly(word)
        }
    }

    @Test
    fun `no topic word is also a stop word`() {
        val stops = NeuroLanguages.stopList("zh")
        assertThat(NeuroLanguages.channelTopics.filter { it in stops }).isEmpty()
    }
}
