/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/Flow-Tube/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import io.github.aedev.flow.data.model.Video
import org.junit.Test

/** The Han fold is decided once per video, and blocking reads text fully folded (#1341). */
class NeuroHanFoldDecisionTest {
    private val tokenizer = NeuroTokenizer(IcuWordBreaker)
    private val empty = IdfSnapshot(emptyMap(), 0)

    private fun video(
        title: String,
        channel: String = "",
        tags: List<String> = emptyList(),
    ) = Video("v", title, channel, "", "", 600, 1L, uploadDate = "", tags = tags)

    private fun terms(video: Video) = tokenizer.documentTerms(video, tokenizer.extractFeatures(video, empty))

    @Test
    fun `a video with kana anywhere keeps Han unfolded in every field`() {
        val title = "鬼滅 無限城編 予告"

        assertThat(tokenizer.hanFoldOf(video(title, channel = "アニメ公式"))).isEqualTo(HanFold.NONE)
        assertThat(tokenizer.hanFoldOf(video(title, tags = listOf("アニメ")))).isEqualTo(HanFold.NONE)
        assertThat(terms(video(title, channel = "アニメ公式"))).contains("無限")
        assertThat(terms(video(title, channel = "アニメ公式"))).doesNotContain("无限")
    }

    @Test
    fun `a video without kana folds every field`() {
        val title = "鬼滅 無限城編 予告"

        assertThat(tokenizer.hanFoldOf(video(title, channel = "央視新聞"))).isEqualTo(HanFold.ALWAYS)
        assertThat(terms(video(title, channel = "央視新聞"))).contains("无限")
    }

    @Test
    fun `one video reads the same word one way whatever field it is in`() {
        val withKanaTag = video("無限城編 予告", tags = listOf("無限", "アニメ"))

        assertThat(terms(withKanaTag)).doesNotContain("无限")
        assertThat(tokenizer.hanFoldOf(withKanaTag)).isEqualTo(HanFold.NONE)
    }

    @Test
    fun `description keywords follow the video decision`() {
        val description = "intro: 無限城編の予告です。機器學習 演唱會 周杰倫 的最新消息和內容介紹都在這裡。" + "詳細は概要欄をご覧ください。".repeat(2)
        val plain = video("機器學習 演唱會", channel = "央視新聞").copy(description = description)
        val kana = video("機器學習 演唱會", channel = "アニメ公式").copy(description = description)

        val folded = tokenizer.extractDescriptionKeywords(description, empty, tokenizer.hanFoldOf(plain)).keys
        val kept = tokenizer.extractDescriptionKeywords(description, empty, tokenizer.hanFoldOf(kana)).keys

        assertThat(folded.any { '学' in it }).isTrue()
        assertThat(kept.any { '学' in it }).isFalse()
        assertThat(kept.any { '學' in it }).isTrue()
    }

    @Test
    fun `a Japanese viewer folds nothing at runtime`() {
        val japanese = NeuroTokenizer(IcuWordBreaker).also { it.languageProfile = "ja" }

        assertThat(japanese.hanFolds).isFalse()
        assertThat(japanese.fold("鬼滅")).isEqualTo("鬼滅")
        assertThat(japanese.chunk("周杰倫 演唱會").merged).isEmpty()
        assertThat(japanese.hanFoldOf(video("機器學習"))).isEqualTo(HanFold.NONE)
    }

    @Test
    fun `other viewers fold and merge`() {
        assertThat(tokenizer.hanFolds).isTrue()
        assertThat(tokenizer.fold("鬼滅")).isEqualTo("鬼灭")
    }

    @Test
    fun `blocking matches with both sides folded and kana ignored`() {
        val matchers = NeuroScoring.buildBlockedMatchers(setOf("鬼灭"), emptyList(), tokenizer::normalizeLemma)

        assertThat(NeuroScoring.isBlockedByText("鬼滅 アニメ 最新話", "", matchers, tokenizer::normalizeLemma)).isTrue()
        assertThat(NeuroScoring.isBlockedByText("鬼灭 アニメ", "", matchers, tokenizer::normalizeLemma)).isTrue()
        assertThat(NeuroScoring.isBlockedByText("別的 アニメ", "", matchers, tokenizer::normalizeLemma)).isFalse()
    }

    @Test
    fun `a blocked keyword stored in either spelling matches`() {
        val traditional = NeuroScoring.buildBlockedMatchers(setOf("鬼滅"), emptyList(), tokenizer::normalizeLemma)

        assertThat(NeuroScoring.isBlockedByText("鬼灭 最新话", "", traditional, tokenizer::normalizeLemma)).isTrue()
    }

    @Test
    fun `Japanese lists keep their kanji`() {
        assertThat(NeuroLanguages.stopList("ja")).containsAtLeast("私達", "貴方", "貴方方")
    }
}
