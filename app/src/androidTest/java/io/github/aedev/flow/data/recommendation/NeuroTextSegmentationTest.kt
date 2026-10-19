/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Titles in scripts written without spaces split into real words. Runs on a device: the word
 * dictionaries live in the platform ICU data, which the JVM test runtime does not have.
 */
@RunWith(AndroidJUnit4::class)
class NeuroTextSegmentationTest {
    private val tokenizer = NeuroTokenizer()

    private fun assertTopics(
        title: String,
        vararg expected: String,
    ) {
        val tokens = tokenizer.tokenize(title)
        expected.forEach { assertTrue("$it not in $tokens for \"$title\"", it in tokens) }
    }

    @Test
    fun chineseTitlesSplitIntoWords() = assertTopics("吉他教学零基础入门", "吉他", "教学")

    @Test
    fun japaneseTitlesKeepContentWordsAndDropParticles() {
        val tokens = tokenizer.tokenize("ギターの弾き方を初心者向けに解説")
        assertTrue(tokens.toString(), "ギター" in tokens)
        assertTrue(tokens.toString(), tokens.none { it == "の" || it == "を" })
    }

    @Test
    fun thaiTitlesSplitIntoWords() = assertTopics("สอนเล่นกีตาร์สำหรับมือใหม่", "กีตาร์", "สำหรับ")

    @Test
    fun chineseChannelNamesGiveTheirTopicalWords() {
        listOf("央視新聞" to "新聞", "央视新闻" to "新闻", "英文教室 科技" to "科技").forEach { (name, topic) ->
            val topics = tokenizer.tokenizeChannelName(name)
            assertTrue("$topic not in $topics for \"$name\"", topic in topics)
        }
        assertTrue(tokenizer.tokenizeChannelName("小明").isEmpty())
    }

    @Test
    fun spacedScriptsAreUntouched() = assertTopics("Pixel 10 Pro camera test", "pixel", "camera")
}
