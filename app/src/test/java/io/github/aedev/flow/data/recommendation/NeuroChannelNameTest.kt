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
    fun `the Chinese topic list is folded, whole words`() {
        val topics = NeuroLanguages.channelTopics
        assertThat(topics).contains("新聞")
        assertThat(topics).contains("新闻")
        topics.forEach { word ->
            assertWithMessage(word).that(word).isEqualTo(NeuroText.fold(word))
            assertWithMessage(word).that(word.length).isAtLeast(2)
            assertWithMessage(word).that(word.any(Char::isWhitespace)).isFalse()
        }
    }

    @Test
    fun `no topic word is also a stop word`() {
        val stops = NeuroLanguages.stopList("zh")
        assertThat(NeuroLanguages.channelTopics.filter { it in stops }).isEmpty()
    }
}
