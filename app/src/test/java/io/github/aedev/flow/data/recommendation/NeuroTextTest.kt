/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

/** #907: styled and short words read as the topics they are. */
class NeuroTextTest {
    private val tokenizer = NeuroTokenizer()

    @Test
    fun `styled letters fold to plain lowercase`() {
        listOf("𝙋𝙃𝙊𝙉𝙆", "ｐｈｏｎｋ", "ⓟⓗⓞⓝⓚ", "ᴘʜᴏɴᴋ", "PHONK", "𝐏𝐡𝐨𝐧𝐤").forEach {
            assertThat(NeuroText.fold(it)).isEqualTo("phonk")
        }
    }

    @Test
    fun `accents are kept so learned keys stay valid`() {
        assertThat(NeuroText.fold("Pokémon Música")).isEqualTo("pokémon música")
    }

    @Test
    fun `thai sara am survives folding`() {
        assertThat(NeuroText.fold("สำหรับ")).isEqualTo("สำหรับ")
    }

    @Test
    fun `trailing vowel and tone marks stay on their word`() {
        assertThat(tokenizer.tokenize("गाना")).containsExactly("गाना")
        assertThat(NeuroText.isWordChar('\u0E4C')).isTrue()
        assertThat(NeuroText.isWordChar('!')).isFalse()
    }

    @Test
    fun `styled titles tokenize to their topics`() {
        assertThat(tokenizer.tokenize("𝙋𝙃𝙊𝙉𝙆 𝙈𝙄𝙓 aggressive")).containsAtLeast("phonk", "mix")
        assertThat(tokenizer.tokenize("ᴘʜᴏɴᴋ gym motivation")).contains("phonk")
    }

    @Test
    fun `short topic names survive and other short words do not`() {
        assertThat(tokenizer.tokenize("F1 race highlights")).contains("f1")
        assertThat(tokenizer.tokenize("AI news this week")).contains("ai")
        assertThat(tokenizer.tokenize("3D printing a PC case")).containsAtLeast("3d", "pc")
        assertThat(tokenizer.tokenize("we go to it")).isEmpty()
        assertThat(tokenizer.isNoiseTopic("ai")).isFalse()
        assertThat(tokenizer.isNoiseTopic("ok")).isTrue()
    }

    @Test
    fun `a blocked topic catches styled titles`() {
        val matchers = NeuroScoring.buildBlockedMatchers(setOf("phonk"), emptyList(), tokenizer::normalizeLemma)

        assertThat(NeuroScoring.isBlockedByText("𝙋𝙃𝙊𝙉𝙆 𝙈𝙄𝙓 2026", "Drift Kings", matchers, tokenizer::normalizeLemma)).isTrue()
    }

    @Test
    fun `v17 merges styled keys into their plain form`() {
        val brain =
            UserBrain(
                schemaVersion = 16,
                globalVector = ContentVector(topics = mapOf("𝙥𝙝𝙤𝙣𝙠" to 0.2, "phonk" to 0.1, "guitar" to 0.3)),
                topicAffinities = mapOf("𝙥𝙝𝙤𝙣𝙠|drift" to 0.4),
                rejectionPatterns = mapOf("𝙥𝙝𝙤𝙣𝙠" to RejectionSignal(2, 10L), "phonk" to RejectionSignal(1, 20L)),
            )

        val updated = NeuroMaintenance.runIfNeeded(brain, tokenizer)

        assertThat(updated.globalVector.topics).containsExactly("phonk", 0.2, "guitar", 0.3)
        assertThat(updated.topicAffinities).containsExactly("drift|phonk", 0.4)
        assertThat(updated.rejectionPatterns).containsExactly("phonk", RejectionSignal(2, 20L))
    }
}
