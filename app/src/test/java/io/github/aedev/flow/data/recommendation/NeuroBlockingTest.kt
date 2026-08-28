/*
 * Copyright (C) 2025-2026 Flow | A-EDev
 *
 * This file is part of Flow (https://github.com/A-EDev/Flow).
 */

package io.github.aedev.flow.data.recommendation

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class NeuroBlockingTest {
    private val tokenizer = NeuroTokenizer()
    private val lemma: (String) -> String = tokenizer::normalizeLemma

    private fun matchers(vararg blocked: String) =
        NeuroScoring.buildBlockedMatchers(blocked.toSet(), NeuroTopicCatalog.TOPIC_CATEGORIES, lemma)

    private fun blocked(
        title: String,
        channel: String = "",
        vararg blockedTopics: String,
    ) = NeuroScoring.isBlockedByText(title, channel, matchers(*blockedTopics), lemma)

    @Test
    fun `blocking one topic does not block its category siblings`() {
        // The old expansion nuked ALL of Gaming when any Gaming topic was blocked.
        assertThat(blocked("Minecraft castle build", "", "fortnite")).isFalse()
        assertThat(blocked("Best esports moments", "", "fortnite")).isFalse()
        assertThat(blocked("Fortnite chapter 5 gameplay", "", "fortnite")).isTrue()
    }

    @Test
    fun `blocking the category NAME still blocks the whole category`() {
        assertThat(blocked("Fortnite chapter 5", "", "gaming")).isTrue()
        assertThat(blocked("Minecraft survival ep 1", "", "gaming")).isTrue()
        assertThat(blocked("Sourdough baking basics", "", "gaming")).isFalse()
    }

    @Test
    fun `token boundaries - art does not block startup`() {
        assertThat(blocked("My startup journey", "", "art")).isFalse()
        assertThat(blocked("Watercolor art tutorial", "", "art")).isTrue()
    }

    @Test
    fun `single-word block matches by token and lemma`() {
        assertThat(blocked("Digital art timelapse", "", "art")).isTrue()
        assertThat(blocked("ART SHOWCASE 2026", "", "art")).isTrue()
        assertThat(blocked("Gaming setup tour", "", "game")).isTrue()
        assertThat(blocked("Cartography for beginners", "", "art")).isFalse()
    }

    @Test
    fun `multi-word block matches as phrase`() {
        assertThat(blocked("Best hip hop tracks of 2026", "", "hip hop")).isTrue()
        assertThat(blocked("Hip mobility exercises", "", "hip hop")).isFalse()
    }

    @Test
    fun `channel names are matched too`() {
        assertThat(blocked("Weekly recap", "Crypto Daily", "crypto")).isTrue()
        assertThat(blocked("Weekly recap", "Cooking Daily", "crypto")).isFalse()
    }
}
